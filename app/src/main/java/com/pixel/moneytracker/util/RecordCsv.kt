package com.pixel.moneytracker.util

import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import java.util.Locale

internal object RecordCsv {
    private fun cell(text: String): String {
        // Spreadsheet apps execute formula-like text even in a quoted CSV cell.
        val safe = if (text.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
            text.startsWith('\t') || text.startsWith('\r')) "'$text" else text
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    fun encode(records: List<Record>, ledgers: Map<Long, String>): String = buildString {
        append("\uFEFF日期,收支,金额,分类,账本,备注\r\n")
        records.forEach { record ->
            append(listOf(
                String.format(Locale.ROOT, "%04d-%02d-%02d", record.year, record.month, record.day),
                if (record.type == RecordType.INCOME) "收入" else "支出",
                String.format(Locale.ROOT, "%.2f", record.amount), record.category,
                ledgers[record.ledgerId].orEmpty(), record.note
            ).joinToString(",") { cell(it) })
            append("\r\n")
        }
    }
}
