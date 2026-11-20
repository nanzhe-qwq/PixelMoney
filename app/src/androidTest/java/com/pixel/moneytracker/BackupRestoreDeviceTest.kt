package com.pixel.moneytracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.data.database.BackupStore
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import com.pixel.moneytracker.util.BackupJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 真机往返验证：用 App 自己导出的备份文件恢复整库。
 *
 * 备份文件由设置页「导出备份」写到 /sdcard/Download，再放进应用私有目录 files/backup-test.json，
 * 避免依赖系统文件选择器（MIUI 的 picker 拒绝 adb 合成点击）。
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db get() = AppDatabase.getDatabase(context)

    @Test
    fun restoringAnExportedBackupDropsDataAddedAfterExport() = runBlocking {
        val file = File(context.filesDir, "backup-test.json")
        assertTrue("备份文件必须先放进 files/backup-test.json", file.exists())
        val decoded = BackupJson.decode(file.readText())
        val expected = decoded.data.records.count { it.deletedAt == null }

        // 备份之后新增的一笔，恢复时应当被覆盖掉
        db.recordDao().insert(
            Record(
                amount = 0.05, type = RecordType.EXPENSE, category = "测试覆盖", emoji = "",
                note = "SHOULD_BE_GONE", year = 2026, month = 11, day = 20, ledgerId = 1
            )
        )
        assertTrue(db.recordDao().getAllRecords().first().any { it.note == "SHOULD_BE_GONE" })

        val result = BackupStore.write(db, decoded.data)

        val after = db.recordDao().getAllRecords().first()
        assertFalse("备份之后新增的账必须消失", after.any { it.note == "SHOULD_BE_GONE" })
        assertEquals(expected, after.size)
        assertEquals(decoded.data.ledgers.size, result.ledgers)
        assertEquals(decoded.data.ledgers.size, db.ledgerDao().getAll().first().size)
        assertEquals(
            0.11,
            after.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount },
            0.0001
        )
        assertEquals(
            0.01,
            after.filter { it.type == RecordType.INCOME }.sumOf { it.amount },
            0.0001
        )
        // 恢复后备份自带的账本名与顺序都要在
        assertEquals(
            decoded.data.ledgers.map { it.name },
            db.ledgerDao().getAll().first().map { it.name }
        )
    }
}
