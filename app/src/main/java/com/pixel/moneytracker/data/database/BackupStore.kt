package com.pixel.moneytracker.data.database

import androidx.room.withTransaction
import com.pixel.moneytracker.data.entity.Budget
import com.pixel.moneytracker.data.entity.ImportKey
import com.pixel.moneytracker.data.entity.Ledger
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecurringRecord
import com.pixel.moneytracker.util.BackupJson
import kotlinx.coroutines.flow.first

/**
 * 备份的读库 / 写库部分。
 *
 * 恢复是「整包替换」：先清空再按原 id 写回，所以
 * - 导入去重键（import_keys）仍然指向正确的账单，重新导入不会被记成双份；
 * - 回收站里的账单也原样回来；
 * - 整个过程在一个事务里，中途失败不会留下半份账本。
 *
 * 不做「合并」：两边的账单 id 会撞，合并要么改 id（去重键失效）要么靠金额猜重复（会误删真账）。
 * 与其做一个猜的合并，不如把「替换」做干净，并在界面上说清楚它会覆盖当前数据。
 */
object BackupStore {

    data class RestoreResult(
        val ledgers: Int,
        val records: Int,
        val trash: Int,
        val budgets: Int,
        val recurring: Int,
        val importKeys: Int,
        val droppedImportKeys: Int
    )

    suspend fun read(db: AppDatabase): BackupJson.BackupData = db.withTransaction {
        BackupJson.BackupData(
            ledgers = db.ledgerDao().getAll().first(),
            records = db.recordDao().getAllRecordsRaw(),
            budgets = db.budgetDao().getAllOnce(),
            recurring = db.recurringRecordDao().getAll().first(),
            importKeys = db.importKeyDao().getAll()
        )
    }

    suspend fun write(db: AppDatabase, data: BackupJson.BackupData): RestoreResult = db.withTransaction {
        val sql = db.openHelper.writableDatabase

        // 子表先删：records 依赖 ledgers，import_keys 依赖 records
        sql.execSQL("DELETE FROM `import_keys`")
        sql.execSQL("DELETE FROM `records`")
        sql.execSQL("DELETE FROM `budgets`")
        sql.execSQL("DELETE FROM `recurring_records`")
        sql.execSQL("DELETE FROM `ledgers`")

        db.ledgerDao().let { dao ->
            data.ledgers.forEach { ledger -> dao.insert(ledger.copy(id = ledger.id)) }
        }
        data.records.forEach { record ->
            sql.execSQL(
                "INSERT INTO `records` (`id`,`amount`,`type`,`category`,`emoji`,`note`,`year`,`month`,`day`,`ledgerId`,`timestamp`,`deletedAt`) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(
                    record.id, record.amount, record.type.name, record.category, record.emoji,
                    record.note, record.year, record.month, record.day, record.ledgerId,
                    record.timestamp, record.deletedAt
                )
            )
        }
        data.budgets.forEach { budget ->
            sql.execSQL(
                "INSERT INTO `budgets` (`id`,`year`,`month`,`amount`) VALUES (?,?,?,?)",
                arrayOf(budget.id, budget.year, budget.month, budget.amount)
            )
        }
        data.recurring.forEach { rule ->
            sql.execSQL(
                "INSERT INTO `recurring_records` (`id`,`amount`,`type`,`category`,`emoji`,`note`,`frequency`,`enabled`,`lastExecutedDate`,`createdAt`,`ledgerId`) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf(
                    rule.id, rule.amount, rule.type.name, rule.category, rule.emoji, rule.note,
                    rule.frequency.name, if (rule.enabled) 1 else 0, rule.lastExecutedDate,
                    rule.createdAt, rule.ledgerId
                )
            )
        }
        data.importKeys.forEach { key ->
            sql.execSQL(
                "INSERT OR REPLACE INTO `import_keys` (`key`,`recordId`) VALUES (?,?)",
                arrayOf(key.key, key.recordId)
            )
        }

        RestoreResult(
            ledgers = data.ledgers.size,
            records = data.records.size,
            trash = data.records.count { it.deletedAt != null },
            budgets = data.budgets.size,
            recurring = data.recurring.size,
            importKeys = data.importKeys.size,
            droppedImportKeys = 0
        )
    }
}
