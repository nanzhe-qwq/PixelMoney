package com.pixel.moneytracker.util

import com.pixel.moneytracker.data.entity.Budget
import com.pixel.moneytracker.data.entity.Frequency
import com.pixel.moneytracker.data.entity.ImportKey
import com.pixel.moneytracker.data.entity.Ledger
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import com.pixel.moneytracker.data.entity.RecurringRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {

    private fun sample() = BackupJson.BackupData(
        ledgers = listOf(
            Ledger(id = 1, name = "日常开销", emoji = "📝", isDefault = true, createdAt = 1000),
            Ledger(id = 7, name = "旅行基金", emoji = "✈️", createdAt = 2000)
        ),
        records = listOf(
            Record(
                id = 11, amount = 12.34, type = RecordType.EXPENSE, category = "餐饮", emoji = "🍜",
                note = "午饭 \"加辣\"", year = 2026, month = 11, day = 20, ledgerId = 1, timestamp = 3000
            ),
            Record(
                id = 12, amount = 5000.0, type = RecordType.INCOME, category = "工资", emoji = "💰",
                note = "", year = 2026, month = 11, day = 1, ledgerId = 7, timestamp = 4000,
                deletedAt = 5000
            )
        ),
        budgets = listOf(Budget(id = 3, year = 2026, month = 11, amount = 2000.0)),
        recurring = listOf(
            RecurringRecord(
                id = 9, amount = 15.0, type = RecordType.EXPENSE, category = "通讯", emoji = "📱",
                note = "话费", frequency = Frequency.MONTHLY, enabled = true,
                lastExecutedDate = 6000, createdAt = 7000, ledgerId = 7
            )
        ),
        importKeys = listOf(ImportKey("abc", 11))
    )

    @Test
    fun encodeDecodeRoundTripsEveryTable() {
        val text = BackupJson.encode(sample(), exportedAt = 1_700_000_000_000, appVersion = "4.4.0")
        val decoded = BackupJson.decode(text)
        assertEquals(sample().ledgers, decoded.data.ledgers)
        assertEquals(sample().records, decoded.data.records)
        assertEquals(sample().budgets, decoded.data.budgets)
        assertEquals(sample().recurring, decoded.data.recurring)
        assertEquals(sample().importKeys, decoded.data.importKeys)
        assertEquals(1_700_000_000_000, decoded.exportedAt)
        assertEquals("4.4.0", decoded.appVersion)
        assertEquals(0, decoded.droppedImportKeys)
    }

    @Test
    fun trashFlagSurvivesTheRoundTrip() {
        val decoded = BackupJson.decode(BackupJson.encode(sample(), 0, "x"))
        val trashed = decoded.data.records.first { it.id == 12L }
        assertEquals(5000L, trashed.deletedAt)
        assertNull(decoded.data.records.first { it.id == 11L }.deletedAt)
    }

    @Test(expected = BackupJson.BackupException::class)
    fun foreignJsonIsRejected() {
        BackupJson.decode("""{"format":"something-else","version":1,"ledgers":[]}""")
    }

    @Test(expected = BackupJson.BackupException::class)
    fun newerFormatIsRejected() {
        BackupJson.decode("""{"format":"pixelmoney-backup","version":2,"ledgers":[]}""")
    }

    @Test(expected = BackupJson.BackupException::class)
    fun backupWithoutLedgersIsRejected() {
        BackupJson.decode("""{"format":"pixelmoney-backup","version":1,"ledgers":[],"records":[]}""")
    }

    @Test(expected = BackupJson.BackupException::class)
    fun recordPointingAtMissingLedgerIsRejected() {
        // 恢复后会出现「孤儿账单」，账本筛选里直接看不见，宁可整份拒绝
        BackupJson.decode(
            """{"format":"pixelmoney-backup","version":1,
               "ledgers":[{"id":1,"name":"日常","emoji":"📝"}],
               "records":[{"id":5,"amount":1,"type":"EXPENSE","category":"餐饮","emoji":"🍜",
                           "note":"","year":2026,"month":11,"day":20,"ledgerId":99,"timestamp":0}]}"""
        )
    }

    @Test
    fun importKeysForMissingRecordsAreDroppedNotFatal() {
        val decoded = BackupJson.decode(
            """{"format":"pixelmoney-backup","version":1,
               "ledgers":[{"id":1,"name":"日常","emoji":"📝"}],
               "records":[{"id":5,"amount":1,"type":"EXPENSE","category":"餐饮","emoji":"🍜",
                           "note":"","year":2026,"month":11,"day":20,"ledgerId":1,"timestamp":0}],
               "importKeys":[{"key":"keep","recordId":5},{"key":"orphan","recordId":404}]}"""
        )
        assertEquals(1, decoded.data.importKeys.size)
        assertEquals("keep", decoded.data.importKeys.first().key)
        assertEquals(1, decoded.droppedImportKeys)
    }

    @Test
    fun duplicateBudgetMonthsCollapseToTheFirstOne() {
        // budget 表对 (year, month) 有唯一索引，重复会在恢复时抛异常
        val decoded = BackupJson.decode(
            """{"format":"pixelmoney-backup","version":1,
               "ledgers":[{"id":1,"name":"日常","emoji":"📝"}],
               "budgets":[{"id":1,"year":2026,"month":11,"amount":100},
                          {"id":2,"year":2026,"month":11,"amount":200}]}"""
        )
        assertEquals(1, decoded.data.budgets.size)
        assertEquals(100.0, decoded.data.budgets.first().amount, 0.0)
    }

    @Test(expected = BackupJson.BackupException::class)
    fun zeroAmountIsRejected() {
        BackupJson.decode(
            """{"format":"pixelmoney-backup","version":1,
               "ledgers":[{"id":1,"name":"日常","emoji":"📝"}],
               "records":[{"id":5,"amount":0,"type":"EXPENSE","category":"餐饮","emoji":"🍜",
                           "note":"","year":2026,"month":11,"day":20,"ledgerId":1,"timestamp":0}]}"""
        )
    }

    @Test(expected = BackupJson.BackupException::class)
    fun duplicateRecordIdsAreRejected() {
        val record = """{"id":5,"amount":1,"type":"EXPENSE","category":"餐饮","emoji":"🍜",
                         "note":"","year":2026,"month":11,"day":20,"ledgerId":1,"timestamp":0}"""
        BackupJson.decode(
            """{"format":"pixelmoney-backup","version":1,
               "ledgers":[{"id":1,"name":"日常","emoji":"📝"}],
               "records":[$record,$record]}"""
        )
    }

    @Test
    fun unknownRecurringLedgerFallsBackToFirstLedger() {
        val decoded = BackupJson.decode(
            """{"format":"pixelmoney-backup","version":1,
               "ledgers":[{"id":3,"name":"日常","emoji":"📝"}],
               "recurring":[{"id":1,"amount":9,"type":"EXPENSE","category":"通讯","emoji":"📱",
                             "note":"","frequency":"MONTHLY","enabled":true,"ledgerId":42}]}"""
        )
        assertEquals(3L, decoded.data.recurring.first().ledgerId)
    }

    @Test
    fun countsBlockRecordsWhatWasExported() {
        val text = BackupJson.encode(sample(), 0, "4.4.0")
        assertTrue(text.contains(""""trash":1"""))
        assertTrue(text.contains(""""format":"pixelmoney-backup""""))
    }
}
