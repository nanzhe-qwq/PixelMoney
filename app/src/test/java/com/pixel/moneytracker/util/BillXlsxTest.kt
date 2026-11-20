package com.pixel.moneytracker.util

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class BillXlsxTest {
    private fun workbook(sheetXml: String, date1904: Boolean = false): ByteArray {
        val parts = mapOf(
            "xl/workbook.xml" to """<workbook xmlns:r="urn:rels"><workbookPr date1904="$date1904"/><sheets><sheet state="hidden" r:id="rId2"/><sheet r:id="rId3"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships><Relationship Id="rId3" Type="urn:rels/worksheet" Target="/xl/worksheets/sheet3.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst><si><r><t>交易</t></r><r><t>时间</t></r></si><si><t>收/支</t></si></sst>""",
            "xl/worksheets/sheet3.xml" to sheetXml
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> for ((name, xml) in parts) {
            zip.putNextEntry(ZipEntry(name)); zip.write(xml.toByteArray()); zip.closeEntry()
        } }
        return out.toByteArray()
    }

    private val dates = """<worksheet><sheetData>
        <row><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row>
        <row><c r="A2"><v>45000.5</v></c><c r="C2" t="inlineStr"><is><t>支出,"示例"</t></is></c></row>
        </sheetData></worksheet>"""

    @Test fun resolvesFirstVisibleSheetSharedAndInlineStringsAndSparseDateColumns() {
        assertEquals("\"交易时间\",\"\",\"收/支\"\n\"2023-03-15 12:00:00\",\"\",\"支出,\"\"示例\"\"\"\n",
            readBillXlsx(workbook(dates)))
        assertTrue(readBillXlsx(workbook(dates, true)).contains("2027-03-16 12:00:00"))
    }

    @Test fun rejectsDtdFormulasAndExcessiveColumns() {
        for (invalid in listOf("<!DOCTYPE worksheet>"+dates, dates.replace("<v>45000.5</v>", "<f>1+1</f><v>2</v>"),
            dates.replace("C2", "ZZ2"))) {
            assertThrows(IllegalArgumentException::class.java) { readBillXlsx(workbook(invalid)) }
        }
    }
}
