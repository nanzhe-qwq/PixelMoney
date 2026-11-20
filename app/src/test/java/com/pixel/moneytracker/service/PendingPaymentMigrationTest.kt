package com.pixel.moneytracker.service

import com.pixel.moneytracker.data.database.AppDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import java.sql.DriverManager
import org.junit.Assert.*
import org.junit.Test

class PendingPaymentMigrationTest {
    @Test fun explicitFourToFiveMigrationPreservesEveryExistingTableAndRow() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val statement = connection.createStatement()
            javaClass.getResource("/pixelmoney-v4.sql")!!.readText().split(";").filter { it.isNotBlank() }.forEach { statement.execute(it) }
            statement.execute("INSERT INTO ledgers VALUES(1,'旧账本','',1,100)")
            statement.execute("INSERT INTO records VALUES(7,12.34,'EXPENSE','餐饮','','旧备注',2026,11,17,1,100)")
            statement.execute("INSERT INTO budgets VALUES(3,2026,11,2000)")
            statement.execute("INSERT INTO recurring_records VALUES(9,20,'INCOME','工资','','旧周期','MONTHLY',1,0,100)")
            val tables = listOf("records", "ledgers", "budgets", "recurring_records")
            fun snapshot() = tables.associateWith { table ->
                statement.executeQuery("SELECT * FROM $table").use { cursor ->
                    buildList { while (cursor.next()) add((1..cursor.metaData.columnCount).map { cursor.getString(it) }) }
                }
            }
            val before = snapshot()
            val sqlite = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
                check(method.name == "execSQL") { "Unexpected migration operation: ${method.name}" }
                statement.execute(args!![0] as String)
                null
            } as SupportSQLiteDatabase
            AppDatabase.MIGRATION_4_5.migrate(sqlite)
            assertEquals(before, snapshot())
            statement.executeQuery("SELECT count(*) FROM pending_payments").use { assertTrue(it.next()); assertEquals(0, it.getInt(1)) }
            statement.executeQuery("PRAGMA foreign_key_check").use { assertFalse(it.next()) }
        }
    }
}
