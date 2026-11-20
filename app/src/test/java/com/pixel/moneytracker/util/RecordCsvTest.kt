package com.pixel.moneytracker.util

import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import org.junit.Assert.*
import org.junit.Test

class RecordCsvTest {
    @Test fun exportsUnicodeQuotesNewlinesAndNeutralizesSpreadsheetFormulas() {
        val record = Record(amount = 12.5, type = RecordType.EXPENSE, category = "购物", emoji = "",
            note = "=HYPERLINK(\"bad\")\n第二行,备注", year = 2026, month = 11, day = 19)
        val csv = RecordCsv.encode(listOf(record), mapOf(1L to "日常开销"))
        assertTrue(csv.startsWith("\uFEFF日期,收支,金额,分类,账本,备注\r\n"))
        assertTrue(csv.contains("\"2026-11-19\",\"支出\",\"12.50\",\"购物\",\"日常开销\""))
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"bad\"\")\n第二行,备注\""))
        assertEquals(1, RecordCsv.encode(emptyList(), emptyMap()).count { it == '\n' })
    }
}
