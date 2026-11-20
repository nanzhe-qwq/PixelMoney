package com.pixel.moneytracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pixel.moneytracker.data.dao.BudgetDao
import com.pixel.moneytracker.data.dao.LedgerDao
import com.pixel.moneytracker.data.dao.RecordDao
import com.pixel.moneytracker.data.dao.RecurringRecordDao
import com.pixel.moneytracker.data.entity.Budget
import com.pixel.moneytracker.data.entity.Ledger
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecurringRecord
import com.pixel.moneytracker.data.dao.ImportKeyDao
import com.pixel.moneytracker.data.entity.ImportKey

@Database(
    entities = [Record::class, Budget::class, RecurringRecord::class, Ledger::class, com.pixel.moneytracker.service.PendingPayment::class, ImportKey::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun importKeyDao(): ImportKeyDao

    abstract fun pendingPaymentDao(): com.pixel.moneytracker.data.dao.PendingPaymentDao
    abstract fun recordDao(): RecordDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRecordDao(): RecurringRecordDao
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 从 version 1 升级到 version 2
         * 添加 recurring_records 表
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `recurring_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amount` REAL NOT NULL,
                        `type` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `note` TEXT NOT NULL DEFAULT '',
                        `frequency` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL DEFAULT 1,
                        `lastExecutedDate` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * 从 version 2 升级到 version 3
         * 添加 ledgers 表，records 表添加 ledgerId 外键
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建 ledgers 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ledgers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 插入默认账本
                db.execSQL("""
                    INSERT INTO `ledgers` (`name`, `emoji`, `isDefault`, `createdAt`)
                    VALUES ('日常开销', '📝', 1, ${System.currentTimeMillis()})
                """.trimIndent())

                // 创建临时表（添加 ledgerId 列）
                db.execSQL("""
                    CREATE TABLE `records_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `amount` REAL NOT NULL,
                        `type` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `note` TEXT NOT NULL DEFAULT '',
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `day` INTEGER NOT NULL,
                        `ledgerId` INTEGER NOT NULL DEFAULT 1,
                        `timestamp` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`ledgerId`) REFERENCES `ledgers`(`id`) ON DELETE SET DEFAULT
                    )
                """.trimIndent())

                // 复制数据
                db.execSQL("""
                    INSERT INTO `records_new` (`id`, `amount`, `type`, `category`, `emoji`, `note`, `year`, `month`, `day`, `timestamp`)
                    SELECT `id`, `amount`, `type`, `category`, `emoji`, `note`, `year`, `month`, `day`, `timestamp` FROM `records`
                """.trimIndent())

                // 删除旧表，重命名新表
                db.execSQL("DROP TABLE IF EXISTS `records`")
                db.execSQL("ALTER TABLE `records_new` RENAME TO `records`")

                // 创建索引
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_records_ledgerId` ON `records` (`ledgerId`)")
            }
        }

        /**
         * 从 version 3 升级到 version 4
         * 添加 budgets 表的 year/month 唯一约束
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // budgets 表结构已经正确，这里只是确保索引
                // 如果有其他变更可以在这里添加
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pending_payments` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `amount` REAL NOT NULL,
                        `source` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `isIncome` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `status` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `import_keys` (`key` TEXT NOT NULL PRIMARY KEY, `recordId` INTEGER NOT NULL, FOREIGN KEY(`recordId`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_import_keys_recordId` ON `import_keys` (`recordId`)")
            }
        }

        /**
         * version 6 -> 7：
         * 1) budgets (year, month) 唯一索引 —— 历史代码每保存一次预算就 INSERT 一行
         *    （没有唯一约束），所以先按月份去重，只保留 id 最大（最后写入）的那条，再建唯一索引。
         *    既不清库，也不会让老用户的「月预算」在两条里随机读到一条。
         * 2) recurring_records.ledgerId —— 周期记账规则记住自己的账本，后台执行不再依赖界面状态；
         *    老规则统一落到 1（日常开销），与历史默认行为一致。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM `budgets` WHERE `id` NOT IN (SELECT MAX(`id`) FROM `budgets` GROUP BY `year`, `month`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_year_month` ON `budgets` (`year`, `month`)")
                db.execSQL("ALTER TABLE `recurring_records` ADD COLUMN `ledgerId` INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * version 7 -> 8：回收站。
         *
         * 只加一列 + 一个索引，不重建表 —— 老记录默认 `deletedAt = NULL`，
         * 也就是「全部仍在账本里」，升级后账目数字必须一分不差。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `records` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_records_deletedAt` ON `records` (`deletedAt`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pixel_money_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
