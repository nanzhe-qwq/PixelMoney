package com.pixel.moneytracker.data.database

import androidx.room.withTransaction
import com.pixel.moneytracker.data.entity.*
import com.pixel.moneytracker.util.BillFile
import com.pixel.moneytracker.util.Categories
import com.pixel.moneytracker.util.ImportBill
import com.pixel.moneytracker.util.LedgerNames
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * 外部账单（微信 / 支付宝）导入时，用户为这些行选择的目标账本。
 *
 * 只有「文件本身没带账本名」的行会用到它；像素记账自己导出的 CSV 仍然按文件里的
 * 账本名称导入，语义完全不变。
 */
internal sealed interface ImportTarget {

    /** 旧行为：写入默认账本（「日常开销」）。 */
    data object Default : ImportTarget

    /** 写入已存在的账本（按 id 定位，改名后依然有效）。 */
    data class Existing(val id: Long) : ImportTarget

    /** 按名称写入；同名账本已存在就复用，不存在就新建。 */
    data class Named(val name: String) : ImportTarget
}

internal data class ImportPreview(
    val file: BillFile,
    val pending: List<ImportBill>,
    val defaultLedger: String,
    /** 预览时刻的账本快照，供导入界面选择目标账本；保存时会重新读库校验。 */
    val ledgers: List<Ledger>
) {
    val duplicates get() = file.bills.size - pending.size
}

// The app's original CSV has no transaction IDs or time of day. Compare its six exported
// fields as a multiset so two identical rows can represent two actual payments.
private data class ExportFields(val year: Int, val month: Int, val day: Int, val type: RecordType,
    val cents: Long, val category: String, val ledger: String, val note: String)

internal class BillImport(private val db: AppDatabase) {
    suspend fun preview(file: BillFile): ImportPreview = db.withTransaction { plan(file) }

    /**
     * 写入预览中勾选的账单。
     *
     * 事务、去重（import_keys + 像素记账导出文件的字段多重集）与「同名账本复用」都保持不变，
     * 唯一新增的是：外部账单的 ledgerId 来自 [target]，而不是固定的默认账本。
     */
    suspend fun save(preview: ImportPreview, target: ImportTarget = ImportTarget.Default): Int = db.withTransaction {
        val planned = plan(preview.file)
        val approved = preview.pending.mapTo(hashSetOf()) { it.key }
        val rows = planned.pending.filter { it.key in approved }
        if (rows.isEmpty()) return@withTransaction 0

        val ledgers = db.ledgerDao().getAll().first()
        val idsByName = ledgers.associate { it.name to it.id }.toMutableMap()
        val external = rows.any { it.ledger == null }

        // 外部账单统一落在一个账本里；像素记账文件仍然逐行按名称解析。
        var forcedLedgerId: Long? = null
        var targetName = planned.defaultLedger
        if (external) {
            when (val chosen = target) {
                is ImportTarget.Default -> {
                    // 保留旧护栏：默认账本在预览之后被改名/改默认，就要求重新预览。
                    require(planned.defaultLedger == preview.defaultLedger) { "默认账本已更改，请重新预览" }
                    targetName = planned.defaultLedger
                }

                is ImportTarget.Existing -> {
                    val ledger = ledgers.firstOrNull { it.id == chosen.id }
                        ?: error("所选账本已不存在，请重新选择")
                    forcedLedgerId = ledger.id
                    targetName = ledger.name
                }

                is ImportTarget.Named -> {
                    val name = LedgerNames.normalize(chosen.name)
                    LedgerNames.validate(name)?.let { error(it) }
                    require(ledgers.count { it.name == name } <= 1) { "存在与目标同名的账本，请先区分账本名称" }
                    targetName = name
                }
            }
        }

        val zone = ZoneId.systemDefault()
        for (bill in rows) {
            val ledgerId = if (bill.ledger == null) {
                forcedLedgerId ?: ledgerIdOf(targetName, idsByName)
            } else {
                ledgerIdOf(bill.ledger, idsByName)
            }
            val emoji = Categories.getByType(bill.type).firstOrNull { it.name == bill.category }?.emoji ?: "📝"
            val id = db.recordDao().insert(Record(amount = bill.cents / 100.0, type = bill.type,
                category = bill.category, emoji = emoji, note = bill.note, ledgerId = ledgerId,
                year = bill.time.year, month = bill.time.monthValue, day = bill.time.dayOfMonth,
                timestamp = bill.time.atZone(zone).toInstant().toEpochMilli()))
            db.importKeyDao().insert(ImportKey(bill.key, id))
        }
        rows.size
    }

    private suspend fun ledgerIdOf(name: String, idsByName: MutableMap<String, Long>): Long =
        idsByName[name] ?: db.ledgerDao().insert(
            Ledger(name = name, emoji = "📝", isDefault = idsByName.isEmpty())
        ).also { idsByName[name] = it }

    private suspend fun plan(file: BillFile): ImportPreview {
        val ledgers = db.ledgerDao().getAll().first()
        val default = ledgers.firstOrNull { it.isDefault } ?: ledgers.firstOrNull()
        val defaultName = default?.name ?: "日常开销"
        val wanted = file.bills.mapTo(hashSetOf()) { it.ledger ?: defaultName }
        require(ledgers.filter { it.name in wanted }.groupBy { it.name }.none { it.value.size > 1 }) {
            "存在与导入目标同名的账本，请先区分账本名称"
        }
        val known = file.bills.map { it.key }.distinct().chunked(500).flatMap { db.importKeyDao().find(it) }
        val seen = known.mapTo(hashSetOf()) { it.key }
        val counts = mutableMapOf<ExportFields, Int>()
        if (file.source == "像素记账") {
            val reserved = known.mapTo(hashSetOf()) { it.recordId }
            val names = ledgers.associate { it.id to it.name }
            for (r in db.recordDao().getAllRecords().first()) {
                if (r.id in reserved) continue
                val k = ExportFields(r.year, r.month, r.day, r.type, (r.amount * 100).roundToLong(),
                    r.category, names[r.ledgerId] ?: continue, r.note)
                counts[k] = (counts[k] ?: 0) + 1
            }
        }
        val pending = file.bills.filter { bill ->
            if (!seen.add(bill.key)) return@filter false
            if (bill.ledger == null) return@filter true
            val k = ExportFields(bill.time.year, bill.time.monthValue, bill.time.dayOfMonth, bill.type,
                bill.cents, bill.category, bill.ledger, bill.note)
            val count = counts[k] ?: 0
            if (count > 0) counts[k] = count - 1
            count == 0
        }
        return ImportPreview(file, pending, defaultName, ledgers)
    }
}
