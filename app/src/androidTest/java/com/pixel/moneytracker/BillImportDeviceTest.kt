package com.pixel.moneytracker

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.data.database.BillImport
import com.pixel.moneytracker.data.entity.*
import com.pixel.moneytracker.util.BillCsv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BillImportDeviceTest {
    private val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
    private val importer = BillImport(db)
    @After fun close() = db.close()

    @Test fun distinctOrderIdsSurviveRepeatImportIncludingAfterEditing() = runBlocking {
        val ledgerId = db.ledgerDao().insert(Ledger(name = "测试日常", emoji = "", isDefault = true))
        val h = "交易时间,收/支,金额(元),当前状态,交易单号,交易对方,商品\n"
        val file = BillCsv.parse(h + listOf("001", "002", "001").joinToString("\n") {
            "2026-11-16 12:00:01,支出,￥5.00,支付成功,$it,测试店,午餐"
        })
        val preview = importer.preview(file)
        assertEquals(2, preview.pending.size)
        assertEquals(1, preview.duplicates)
        assertEquals(0, db.recordDao().getTotalRecordCount().first())
        assertEquals(2, importer.save(preview))
        val records = db.recordDao().getAllRecords().first()
        assertTrue(records.all { it.ledgerId == ledgerId && it.amount == 5.0 })
        db.recordDao().update(records.first().copy(note = "手动修改"))
        assertEquals(0, importer.save(preview))
        assertTrue(importer.preview(file).pending.isEmpty())
        assertEquals(2, db.recordDao().getTotalRecordCount().first())
    }

    @Test fun nativeCsvReusesEquivalentRowsAndCreatesMissingLedgerOnlyOnConfirm() = runBlocking {
        val ledger = db.ledgerDao().insert(Ledger(name = "旧账本", emoji = "", isDefault = true))
        db.recordDao().insert(Record(amount = 3.50, type = RecordType.EXPENSE, category = "购物", emoji = "",
            note = "旧备注", year = 2026, month = 11, day = 4, ledgerId = ledger))
        val file = BillCsv.parse("日期,收支,金额,分类,账本,备注\n" +
            "2026-11-04,支出,3.50,购物,旧账本,旧备注\n".repeat(2) + "2026-10-01,收入,100.00,工资,家庭,收入")
        val preview = importer.preview(file)
        assertEquals(1, preview.duplicates)
        assertEquals(1, db.ledgerDao().getAll().first().size)
        assertEquals(2, importer.save(preview))
        val family = db.ledgerDao().getAll().first().single { it.name == "家庭" }
        assertEquals(1, db.ledgerDao().getRecordCount(family.id))
        assertEquals(2, db.ledgerDao().getRecordCount(ledger))
        assertEquals(0, importer.save(preview))
    }

    @Test fun uncheckedOrderCanStillBeImportedLater() = runBlocking {
        val file = BillCsv.parse("交易时间,收/支,金额,交易状态,交易订单号\n" +
            "2026-10-01 09:10:00,支出,5.00,交易成功,AA01\n2026-10-01 10:00:00,支出,7.00,交易成功,AA02")
        val preview = importer.preview(file)
        assertEquals(1, importer.save(preview.copy(pending = preview.pending.take(1))))
        val later = importer.preview(file)
        assertEquals(700L, later.pending.single().cents)
        assertEquals(1, importer.save(later))
        assertEquals(2, db.recordDao().getTotalRecordCount().first())
    }

    @Test fun versionFiveFileMigratesAndKeepsExistingLedgerAndRecord() = runBlocking {
        val ins = InstrumentationRegistry.getInstrumentation()
        val context = ins.targetContext
        val name = "migration-import-${java.util.UUID.randomUUID()}.db"
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                val ddl = ins.context.assets.open("pixelmoney-v5.sql").bufferedReader().use { it.readText() }
                ddl.split(';').filter { it.isNotBlank() }.forEach { old.execSQL(it) }
                old.execSQL("INSERT INTO ledgers VALUES(8,'迁移样本','',1,0)")
                old.execSQL("INSERT INTO records VALUES(16,1.25,'EXPENSE','购物','','旧账',2026,9,28,8,0)")
                old.version = 5
            }
            val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
                // 库版本已到 8，5→8 需要整条迁移链都在，否则 Room 会报缺少迁移
                .addMigrations(
                    AppDatabase.MIGRATION_5_6,
                    AppDatabase.MIGRATION_6_7,
                    AppDatabase.MIGRATION_7_8
                ).build()
            try {
                assertEquals("迁移样本", migrated.ledgerDao().getById(8)!!.name)
                assertEquals("旧账", migrated.recordDao().getAllRecords().first().single().note)
                val service = BillImport(migrated)
                val f = BillCsv.parse("交易时间,收/支,金额,交易状态,交易订单号\n2026-10-01 10:00:00,支出,7.00,交易成功,AA02")
                assertEquals(1, service.save(service.preview(f)))
                assertEquals(2, migrated.ledgerDao().getRecordCount(8))
                val newId = migrated.importKeyDao().find(f.bills.map { it.key }).single().recordId
                migrated.recordDao().deleteById(newId)
                assertTrue(migrated.importKeyDao().find(f.bills.map { it.key }).isEmpty())
                assertEquals(1, migrated.recordDao().getTotalRecordCount().first())
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun insertFailureRollsBackRowsLedgersAndImportKeys() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER abort_second BEFORE INSERT ON records WHEN NEW.note='失败' BEGIN SELECT RAISE(ABORT, 'test'); END")
        val input = BillCsv.parse("日期,收支,金额,分类,账本,备注\n" +
            "2026-10-01,收入,100.00,工资,家庭,第一笔\n2026-10-02,收入,10.00,工资,家庭,失败")
        val preview = importer.preview(input)
        assertTrue(runCatching { importer.save(preview) }.isFailure)
        assertEquals(0, db.recordDao().getTotalRecordCount().first())
        assertTrue(db.ledgerDao().getAll().first().isEmpty())
        assertTrue(db.importKeyDao().find(input.bills.map { it.key }).isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER abort_second")
        assertEquals(2, importer.save(preview))
    }
}
