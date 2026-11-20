package com.pixel.moneytracker.util

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** Read the first visible worksheet of a local bill export, then use the same CSV validation. */
internal fun readBillXlsx(bytes: ByteArray): String {
    val parts = mutableMapOf<String, ByteArray>()
    var total = 0
    var entries = 0
    ZipInputStream(bytes.inputStream()).use { zip ->
        val buffer = ByteArray(8192)
        while (true) {
            val path = zip.nextEntry?.name ?: break
            require(++entries <= 256) { "Excel 文件包含过多项目" }
            val keep = path == "xl/workbook.xml" || path == "xl/_rels/workbook.xml.rels" ||
                path == "xl/sharedStrings.xml" || (path.startsWith("xl/worksheets/") && path.endsWith(".xml"))
            val part = if (keep) ByteArrayOutputStream() else null
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 24 * 1024 * 1024) { "Excel 解压后过大，请缩短导出时间范围" }
                part?.write(buffer, 0, count)
            }
            if (part != null) {
                require(path !in parts) { "Excel 文件有重复的工作表组件" }
                parts[path] = part.toByteArray()
            }
        }
    }
    val workbook = parts["xl/workbook.xml"] ?: throw IllegalArgumentException("请先解压下载的账单，选择 .xlsx 或 CSV 文件")
    val relations = parts["xl/_rels/workbook.xml.rels"] ?: throw IllegalArgumentException("缺少 Excel 工作表索引")
    var date1904 = false
    var sheetId: String? = null
    parseBillXml(workbook, object : DefaultHandler() {
        override fun startElement(uri: String, name: String, q: String, a: Attributes) {
            if (name == "workbookPr") date1904 = a.getValue("date1904") in listOf("1", "true")
            if (name == "sheet" && sheetId == null && a.getValue("state") !in listOf("hidden", "veryHidden")) {
                sheetId = (0 until a.length).firstOrNull { a.getLocalName(it) == "id" }?.let(a::getValue)
            }
        }
    })
    var sheetPath: String? = null
    parseBillXml(relations, object : DefaultHandler() {
        override fun startElement(uri: String, name: String, q: String, a: Attributes) {
            if (name == "Relationship" && sheetId != null && a.getValue("Id") == sheetId &&
                a.getValue("Type")?.endsWith("/worksheet") == true && a.getValue("TargetMode") != "External") {
                val target = a.getValue("Target") ?: return
                sheetPath = if (target.startsWith("/")) target.drop(1) else "xl/$target"
            }
        }
    })
    val sheet = parts[sheetPath] ?: throw IllegalArgumentException("未找到可读取的工作表，请另存为 CSV")
    val strings = mutableListOf<String>()
    parts["xl/sharedStrings.xml"]?.let { data ->
        parseBillXml(data, object : DefaultHandler() {
            private val value = StringBuilder()
            private var reading = false
            override fun startElement(uri: String, name: String, q: String, a: Attributes) {
                if (name == "si") value.clear()
                if (name == "t") reading = true
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (reading) value.append(ch, start, length)
            }
            override fun endElement(uri: String, name: String, q: String) {
                if (name == "t") reading = false
                if (name == "si") {
                    require(strings.size < 150_000) { "Excel 文本过多" }
                    strings.add(value.toString())
                }
            }
        })
    }
    val csv = StringBuilder()
    parseBillXml(sheet, object : DefaultHandler() {
        private val row = mutableListOf<String>()
        private val value = StringBuilder()
        private var reading = false
        private var kind: String? = null
        private var dateColumn = -1
        private var dateOnly = false
        private var rows = 0
        override fun startElement(uri: String, name: String, q: String, a: Attributes) {
            when (name) {
                "row" -> { row.clear(); require(++rows <= 11_000) { "工作表行数过多，请缩短导出时间范围" } }
                "c" -> {
                    val letters = a.getValue("r")?.takeWhile { it in 'A'..'Z' }
                    val column = if (letters.isNullOrEmpty()) row.size else {
                        require(letters.length <= 2) { "账单列数过多" }
                        letters.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
                    }
                    require(column in row.size until 64) { "账单列号无效或超过 64 列" }
                    while (row.size < column) row.add("")
                    kind = a.getValue("t")
                    value.clear()
                }
                "v", "t" -> reading = true
                "f" -> throw IllegalArgumentException("账单含有公式，请导出原始账单或先另存为仅含数值的 CSV")
            }
        }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (reading) value.append(ch, start, length)
        }
        override fun endElement(uri: String, name: String, q: String) {
            when (name) {
                "v", "t" -> reading = false
                "c" -> {
                    val raw = value.toString()
                    val text = when {
                        kind == "s" -> raw.toIntOrNull()?.let(strings::getOrNull)
                            ?: throw IllegalArgumentException("Excel 字符串索引无效")
                        row.size == dateColumn && kind in listOf(null, "n") && raw.isNotEmpty() ->
                            excelBillDate(raw, date1904).let { if (dateOnly) it.substringBefore(' ') else it }
                        kind == "d" -> raw.replace('T', ' ').let { if (dateOnly && row.size == dateColumn) it.substringBefore(' ') else it }
                        else -> raw
                    }
                    row.add(text)
                }
                "row" -> {
                    val dateIndex = row.indexOfFirst { it.trim() in listOf("交易时间", "日期") }
                    if (dateIndex >= 0 && row.any { it.trim() in listOf("收支", "收/支", "收/支/其他") }) {
                        dateColumn = dateIndex
                        dateOnly = row[dateIndex].trim() == "日期"
                    }
                    if (row.any { it.isNotEmpty() }) {
                        row.forEachIndexed { i, field ->
                            if (i > 0) csv.append(',')
                            require(csv.length.toLong() + field.length * 2L + 4 <= 8 * 1024 * 1024) { "Excel 账单内容过大" }
                            csv.append('"').append(field.replace("\"", "\"\"")).append('"')
                        }
                        csv.append('\n')
                    }
                }
            }
        }
    })
    return csv.toString()
}

private val excelDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
private val serialDatePattern = Regex("""\d{1,7}(?:\.\d{1,20})?""")

private fun excelBillDate(raw: String, date1904: Boolean): String {
    if (!serialDatePattern.matches(raw)) return raw
    val days = raw.toBigDecimal()
    if (days.signum() < 0 || days > 2_958_465.toBigDecimal()) return raw
    val wholeDays = days.toLong()
    if (!date1904 && wholeDays == 60L) return raw // Excel's fictitious 1900-02-29.
    val epoch = when {
        date1904 -> LocalDate.of(1904, 1, 1)
        wholeDays < 60 -> LocalDate.of(1899, 12, 31)
        else -> LocalDate.of(1899, 12, 30)
    }
    val seconds = (days - wholeDays.toBigDecimal()).multiply(86400.toBigDecimal())
        .setScale(0, RoundingMode.HALF_UP).toLong()
    return epoch.plusDays(wholeDays).atStartOfDay().plusSeconds(seconds).format(excelDateFormat)
}

private fun parseBillXml(data: ByteArray, handler: DefaultHandler) {
    val text = data.toString(Charsets.UTF_8)
    require('\u0000' !in text && "<!DOCTYPE" !in text && "<!ENTITY" !in text) { "Excel XML 格式不受支持" }
    val parser = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser()
    parser.parse(InputSource(StringReader(text.removePrefix("\uFEFF"))), handler)
}
