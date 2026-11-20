package com.pixel.moneytracker.util

import com.pixel.moneytracker.data.entity.RecordType
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

internal data class ImportBill(val time: LocalDateTime, val type: RecordType, val cents: Long,
    val category: String, val ledger: String?, val note: String, val key: String)

internal data class BillFile(val source: String, val bills: List<ImportBill>, val skipped: Map<String, Int>)

internal object BillCsv {
    const val MAX_BYTES = 4 * 1024 * 1024
    private val pixelColumns = setOf("日期", "收支", "金额", "分类", "账本", "备注")
    private val decimal = Regex("""(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d{1,2})?""")
    private val completed = setOf("支付成功", "交易成功", "收款成功", "转账成功", "已转账", "已收钱",
        "对方已收钱", "已存入零钱", "已到账", "朋友已收钱", "退款成功", "已退款")

    fun parse(bytes: ByteArray): BillFile {
        require(bytes.size <= MAX_BYTES) { "请选择不超过 4 MB 的账单文件" }
        val text = if (bytes.size >= 4 && bytes[0] == 80.toByte() && bytes[1] == 75.toByte())
            readBillXlsx(bytes) else try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            try { Charset.forName("GB18030").newDecoder().decode(ByteBuffer.wrap(bytes)).toString() }
            catch (_: CharacterCodingException) { throw IllegalArgumentException("无法读取文件编码，请选择 CSV 或 XLSX 账单") }
        }
        require(text.length <= 8 * 1024 * 1024) { "账单解压后过大，请缩短导出日期范围" }
        return parse(text)
    }

    fun parse(text: String): BillFile {
        val rows = readRows(text.removePrefix("\uFEFF"))
        var columns = emptyMap<String, Int>()
        var source = ""
        var headerWidth = 0
        val bills = mutableListOf<ImportBill>()
        val skipped = linkedMapOf<String, Int>()
        val occurrences = mutableMapOf<String, Int>()
        fun skip(reason: String) { skipped[reason] = 1 + (skipped[reason] ?: 0) }
        for (cells in rows) {
            if (cells.all { it.isBlank() }) continue
            if (source.isEmpty()) {
                val names = cells.map { it.trim().replace("（", "(").replace("）", ")") }
                source = when {
                    names.containsAll(pixelColumns) -> "像素记账"
                    names.containsAll(listOf("交易时间", "收/支", "当前状态", "交易单号", "金额(元)")) -> "微信"
                    names.containsAll(listOf("交易时间", "收/支", "交易状态", "交易订单号")) &&
                        names.any { it == "金额" || it == "金额(元)" } -> "支付宝"
                    else -> continue
                }
                require(names.filter { it.isNotEmpty() }.distinct().size == names.count { it.isNotEmpty() }) { "表头包含重复列名，无法确认字段" }
                columns = names.withIndex().associate { it.value to it.index }
                headerWidth = names.indexOfLast { it.isNotEmpty() } + 1
                continue
            }
            if (cells.drop(headerWidth).any { it.isNotBlank() }) { skip("列数与表头不符"); continue }
            fun value(label: String) = columns[label]?.let { cells.getOrNull(it) }.orEmpty()
            val native = source == "像素记账"
            val direction = value(if (native) "收支" else "收/支").trim()
            val type = when (direction) {
                "支出" -> RecordType.EXPENSE
                "收入" -> RecordType.INCOME
                else -> { skip(if (native) "收支类型无效" else "不计收支或非明细"); continue }
            }
            val status = value(if (source == "微信") "当前状态" else "交易状态").trim()
            if (!native && (status !in completed || ("退款" in status && type != RecordType.INCOME))) {
                skip("未完成或退款状态待核对"); continue
            }
            val amount = value(if (columns.containsKey("金额(元)")) "金额(元)" else "金额").trim()
                .removePrefix("¥").removePrefix("￥").trim()
            val cents = if (amount.length <= 20 && decimal.matches(amount)) {
                amount.replace(",", "").toBigDecimal().movePointRight(2).takeIf {
                    it > BigDecimal.ZERO && it <= BigDecimal("99999999999999")
                }?.toLong()
            } else null
            if (cents == null) { skip("金额无效"); continue }
            val dateText = value(if (native) "日期" else "交易时间").trim().replace('/', '-')
            val time = try {
                if (native) LocalDate.parse(dateText).atStartOfDay()
                else LocalDateTime.parse(dateText.replace(' ', 'T'))
            } catch (_: DateTimeParseException) { null }
            if (time == null || time.year !in 1900..9999) { skip("日期无效"); continue }
            val ledger = if (native) unescape(value("账本")) else null
            val category = if (native) unescape(value("分类")) else BillCategoryRules.infer(
                type, status,
                value("交易类型"), value("交易对方"),
                value(if (source == "微信") "商品" else "商品说明"), value("备注"),
            )
            if (native && (category.isBlank() || ledger.isNullOrBlank())) { skip("缺少账本或分类"); continue }
            val note = if (native) unescape(value("备注")) else
                listOf("交易对方", if (source == "微信") "商品" else "商品说明", "备注")
                    .map { value(it).trim() }.filter { it.isNotBlank() && it != "/" }.distinct().joinToString(" · ")
            val key = if (native) {
                val digest = hash(listOf(source, time.toString(), type.name, cents.toString(), category, ledger.orEmpty(), note))
                val number = 1 + (occurrences[digest] ?: 0)
                occurrences[digest] = number
                "$digest:$number"
            } else {
                val order = value(if (source == "微信") "交易单号" else "交易订单号").trim()
                if (order.isBlank() || order == "/" || order.matches(Regex("""\d+(?:\.\d+)?[eE][+-]?\d+"""))) { skip("缺少完整交易单号"); continue }
                hash(listOf(source, type.name, order))
            }
            bills += ImportBill(time, type, cents, category, ledger, note, key)
        }
        require(source.isNotEmpty()) { "未找到支持的账单表头，请选择像素记账、微信或支付宝导出的明细" }
        return BillFile(source, bills, skipped)
    }

    private fun hash(parts: List<String>): String {
        val input = parts.joinToString("") { "${it.length}:$it" }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(input).joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        }
    }

    private fun unescape(value: String): String {
        val unquoted = value.removePrefix("'")
        return if (value.startsWith("'") && (unquoted.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
                unquoted.startsWith('\t') || unquoted.startsWith('\r'))) unquoted else value
    }

    private fun readRows(text: String): Sequence<List<String>> = sequence {
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var closed = false
        var i = 0
        var rowCount = 0
        fun finishCell() {
            require(row.size < 64) { "账单列数过多" }
            row += cell.toString()
            cell.setLength(0)
            closed = false
        }
        while (i < text.length) {
            val c = text[i++]
            when {
                inQuotes && c == '"' -> if (i < text.length && text[i] == '"') { cell.append('"'); i++ }
                    else { inQuotes = false; closed = true }
                inQuotes -> cell.append(c)
                c == ',' -> finishCell()
                c == '\r' || c == '\n' -> {
                    if (c == '\r' && i < text.length && text[i] == '\n') i++
                    finishCell()
                    require(++rowCount <= 10_100) { "单次导入最多约 1 万行，请分批导出" }
                    yield(row)
                    row = mutableListOf()
                }
                closed -> throw IllegalArgumentException("CSV 引号后的分隔符无效")
                c == '"' && cell.isEmpty() -> inQuotes = true
                c == '"' -> throw IllegalArgumentException("CSV 字段内的引号未转义")
                else -> cell.append(c)
            }
            require(cell.length <= 65_536) { "账单单个字段过长" }
        }
        require(!inQuotes) { "CSV 引号未闭合，请重新导出完整文件" }
        if (row.isNotEmpty() || cell.isNotEmpty() || closed) { finishCell(); yield(row) }
    }
}
