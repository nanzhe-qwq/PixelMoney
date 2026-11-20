package com.pixel.moneytracker.service

import androidx.sqlite.db.SupportSQLiteDatabase
import com.pixel.moneytracker.data.database.AppDatabase
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * schema 6 → 7：预算按月唯一 + 周期记账记住所属账本。
 *
 * 历史背景：MIGRATION_3_4 当年只写了注释、没有真的建唯一索引，
 * 所以老库里同一个 (year, month) 可能有多行 budgets，升级前必须先按 id 去重。
 */
class BudgetMigrationTest {

    private fun Connection.applyFixture(name: String) {
        val statement = createStatement()
        javaClass.getResource("/$name")!!.readText().split(";").filter { it.isNotBlank() }.forEach { statement.execute(it) }
    }

    private fun Connection.migrate6to7() {
        val statement = createStatement()
        val sqlite = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            check(method.name == "execSQL") { "Unexpected migration operation: ${method.name}" }
            statement.execute(args!![0] as String)
            null
        } as SupportSQLiteDatabase
        AppDatabase.MIGRATION_6_7.migrate(sqlite)
    }

    @Test
    fun sixToSevenDedupesBudgetsAndKeepsLatestRow() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.applyFixture("pixelmoney-v6.sql")
            val statement = connection.createStatement()
            statement.execute("INSERT INTO ledgers VALUES(1,'日常开销','🍜',1,100)")
            statement.execute("INSERT INTO records VALUES(7,12.34,'EXPENSE','餐饮','🍜','旧备注',2026,11,17,1,100)")
            // 同一个月两条预算：模拟 MIGRATION_3_4 落空留下的历史脏数据
            statement.execute("INSERT INTO budgets VALUES(3,2026,11,2000)")
            statement.execute("INSERT INTO budgets VALUES(8,2026,11,2600)")
            statement.execute("INSERT INTO budgets VALUES(9,2026,10,1800)")
            statement.execute("INSERT INTO recurring_records VALUES(5,20,'INCOME','工资','💰','旧周期','MONTHLY',1,0,100)")

            connection.migrate6to7()

            statement.executeQuery("SELECT year, month, amount FROM budgets ORDER BY year, month").use { cursor ->
                val rows = buildList {
                    while (cursor.next()) add("${cursor.getInt(1)}-${cursor.getInt(2)}:${cursor.getDouble(3)}")
                }
                // 11 月只留下 id 最大的那条（2600），10 月不受影响
                assertEquals(listOf("2026-10:1800.0", "2026-11:2600.0"), rows)
            }

            statement.executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_budgets_year_month'"
            ).use { cursor ->
                assertTrue(cursor.next())
                assertEquals("index_budgets_year_month", cursor.getString(1))
            }

            // 唯一索引生效：再插同月预算会被 SQLite 拒绝
            var rejected = false
            try {
                statement.execute("INSERT INTO budgets (year, month, amount) VALUES (2026, 11, 900)")
            } catch (_: java.sql.SQLException) {
                rejected = true
            }
            assertTrue(rejected)

            statement.executeQuery("SELECT ledgerId FROM recurring_records WHERE id = 5").use { cursor ->
                assertTrue(cursor.next())
                assertEquals(1, cursor.getInt(1))
            }

            statement.executeQuery("SELECT amount FROM records WHERE id = 7").use { cursor ->
                assertTrue(cursor.next())
                assertEquals(12.34, cursor.getDouble(1), 0.0)
            }

            statement.executeQuery("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.next()) }
        }
    }
}
