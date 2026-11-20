package com.pixel.moneytracker

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.data.entity.Ledger
import com.pixel.moneytracker.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** File-backed, isolated AUTO_TEST databases; never clear or migrate the user's DB in this test. */
@RunWith(AndroidJUnit4::class)
class PendingPaymentDatabaseDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val name = "AUTO_TEST_PENDING_DB_" + System.nanoTime()
    private var database: AppDatabase? = null
    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_4_5).build().also { database = it }
    private val edit = PaymentEdit("餐饮", "", "AUTO_TEST", false)

    @After fun cleanup() {
        database?.close()
        context.deleteDatabase(name)
    }

    @Test fun realRoomCandidateAndConsumptionSurviveDatabaseReopen() = runBlocking {
        var db = open()
        db.ledgerDao().insert(Ledger(1, "AUTO_TEST", "", true))
        val candidate = PendingPaymentStore(RoomPendingPaymentBackend(db)).create(12.34, "支付宝", "AUTO_TEST", true)
        db.close()
        db = open()
        val reopened = PendingPaymentStore(RoomPendingPaymentBackend(db))
        assertEquals(candidate, reopened.load(candidate.id))
        assertEquals(PaymentResolution.APPLIED, reopened.confirm(candidate.id, edit))
        db.close()
        db = open()
        val restarted = PendingPaymentStore(RoomPendingPaymentBackend(db))
        assertNull(restarted.load(candidate.id))
        assertEquals(PaymentResolution.UNAVAILABLE, restarted.confirm(candidate.id, edit))
        assertEquals(PaymentResolution.UNAVAILABLE, restarted.ignore(candidate.id))
        assertEquals(1, db.recordDao().getAllRecords().first().size)
    }

    @Test fun realRoomFailureAfterInsertRollsBackBothWritesAndAllowsRetry() = runBlocking {
        val db = open()
        db.ledgerDao().insert(Ledger(1, "AUTO_TEST", "", true))
        val store = PendingPaymentStore(RoomPendingPaymentBackend(db))
        val candidate = store.create(12.34, "支付宝", "AUTO_TEST", false)
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER AUTO_TEST_FAIL_CONSUME BEFORE UPDATE ON pending_payments
            WHEN NEW.status='CONFIRMED' BEGIN SELECT RAISE(ABORT,'AUTO_TEST forced failure after record insert'); END
        """.trimIndent())
        var failed = false
        try { store.confirm(candidate.id, edit) } catch (_: android.database.sqlite.SQLiteException) { failed = true }
        assertTrue("Candidate update must fail after the record insert", failed)
        assertTrue(db.recordDao().getAllRecords().first().isEmpty())
        assertEquals(candidate, store.load(candidate.id))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER AUTO_TEST_FAIL_CONSUME")
        assertEquals(PaymentResolution.APPLIED, store.confirm(candidate.id, edit))
        assertEquals(1, db.recordDao().getAllRecords().first().size)
    }

    @Test fun realRoomSerializesConcurrentConfirmAndIgnore() = runBlocking {
        val db = open()
        db.ledgerDao().insert(Ledger(1, "AUTO_TEST", "", true))
        val store = PendingPaymentStore(RoomPendingPaymentBackend(db))
        val candidate = store.create(12.34, "支付宝", "AUTO_TEST", false)
        val outcomes = coroutineScope {
            (1..20).map { index -> async(Dispatchers.IO) {
                if (index % 2 == 0) store.confirm(candidate.id, edit) else store.ignore(candidate.id)
            } }.awaitAll()
        }
        assertEquals(1, outcomes.count { it == PaymentResolution.APPLIED })
        assertTrue(db.recordDao().getAllRecords().first().size <= 1)
    }

    @Test fun actualRoomFourToFiveUpgradeValidatesSchemaAndKeepsOldData() = runBlocking {
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            instrumentation.context.assets.open("pixelmoney-v4.sql").bufferedReader().use { it.readText() }
                .split(";").filter { it.isNotBlank() }.forEach { old.execSQL(it) }
            old.execSQL("INSERT INTO ledgers VALUES(1,'AUTO_TEST_OLD_LEDGER','',1,100)")
            old.execSQL("INSERT INTO records VALUES(7,12.34,'EXPENSE','餐饮','','AUTO_TEST_OLD_NOTE',2026,11,17,1,100)")
            old.execSQL("INSERT INTO budgets VALUES(3,2026,11,2000)")
            old.execSQL("INSERT INTO recurring_records VALUES(9,20,'INCOME','工资','','AUTO_TEST_OLD_RECURRING','MONTHLY',1,0,100)")
        }
        val db = open() // No fallback: Room must run the registered migration and validate all columns.
        assertEquals("AUTO_TEST_OLD_NOTE", db.recordDao().getAllRecords().first().single().note)
        assertEquals("AUTO_TEST_OLD_LEDGER", db.ledgerDao().getById(1)!!.name)
        db.query("SELECT amount FROM budgets WHERE id=3", null).use { assertTrue(it.moveToFirst()); assertEquals(2000.0, it.getDouble(0), 0.0) }
        db.query("SELECT note FROM recurring_records WHERE id=9", null).use { assertTrue(it.moveToFirst()); assertEquals("AUTO_TEST_OLD_RECURRING", it.getString(0)) }
        assertEquals(5, db.openHelper.readableDatabase.version)
        assertNotNull(PendingPaymentStore(RoomPendingPaymentBackend(db)).create(1.0, "支付宝", "AUTO_TEST", false))
    }
}
