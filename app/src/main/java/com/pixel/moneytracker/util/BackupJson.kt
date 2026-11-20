package com.pixel.moneytracker.util

import com.pixel.moneytracker.data.entity.Budget
import com.pixel.moneytracker.data.entity.Frequency
import com.pixel.moneytracker.data.entity.ImportKey
import com.pixel.moneytracker.data.entity.Ledger
import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import com.pixel.moneytracker.data.entity.RecurringRecord

/**
 * 本机备份的 JSON 格式（版本 1）。
 *
 * 备份是「整包」：账本、账单（含回收站）、预算、周期规则、导入去重键一起带走。
 * 少任何一类，换机后都会缺一块——尤其是导入去重键，丢了会让重新导入的账单变成双份。
 *
 * 只做纯函数转换（不碰 Context / Room），因此能在 JVM 单元测试里逐字段验证；
 * 读写数据库的部分在 [com.pixel.moneytracker.data.database.BackupStore]。
 */
object BackupJson {

    const val FORMAT = "pixelmoney-backup"
    const val VERSION = 1

    /** 单份备份里允许的账单条数上限，防止一份异常文件把内存和数据库灌满。 */
    const val MAX_RECORDS = 200_000
    const val MAX_LEDGERS = 500

    class BackupException(message: String) : Exception(message)

    /** 一份备份的全部内容。 */
    data class BackupData(
        val ledgers: List<Ledger>,
        val records: List<Record>,
        val budgets: List<Budget>,
        val recurring: List<RecurringRecord>,
        val importKeys: List<ImportKey>
    )

    data class Decoded(
        val data: BackupData,
        val exportedAt: Long,
        val appVersion: String,
        /** 指向不存在账单、被丢弃的导入去重键数量（不致命，只报告）。 */
        val droppedImportKeys: Int
    )

    val empty: BackupData = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    // ====== 写 ======

    fun encode(data: BackupData, exportedAt: Long, appVersion: String): String {
        val root = linkedMapOf<String, Any?>(
            "format" to FORMAT,
            "version" to VERSION,
            "exportedAt" to exportedAt,
            "appVersion" to appVersion,
            "counts" to linkedMapOf(
                "ledgers" to data.ledgers.size,
                "records" to data.records.size,
                "trash" to data.records.count { it.deletedAt != null },
                "budgets" to data.budgets.size,
                "recurring" to data.recurring.size,
                "importKeys" to data.importKeys.size
            ),
            "ledgers" to data.ledgers.map {
                linkedMapOf(
                    "id" to it.id, "name" to it.name, "emoji" to it.emoji,
                    "isDefault" to it.isDefault, "createdAt" to it.createdAt
                )
            },
            "records" to data.records.map {
                linkedMapOf(
                    "id" to it.id, "amount" to it.amount, "type" to it.type.name,
                    "category" to it.category, "emoji" to it.emoji, "note" to it.note,
                    "year" to it.year, "month" to it.month, "day" to it.day,
                    "ledgerId" to it.ledgerId, "timestamp" to it.timestamp,
                    "deletedAt" to it.deletedAt
                )
            },
            "budgets" to data.budgets.map {
                linkedMapOf("id" to it.id, "year" to it.year, "month" to it.month, "amount" to it.amount)
            },
            "recurring" to data.recurring.map {
                linkedMapOf(
                    "id" to it.id, "amount" to it.amount, "type" to it.type.name,
                    "category" to it.category, "emoji" to it.emoji, "note" to it.note,
                    "frequency" to it.frequency.name, "enabled" to it.enabled,
                    "lastExecutedDate" to it.lastExecutedDate, "createdAt" to it.createdAt,
                    "ledgerId" to it.ledgerId
                )
            },
            "importKeys" to data.importKeys.map {
                linkedMapOf("key" to it.key, "recordId" to it.recordId)
            }
        )
        return MiniJson.stringify(root)
    }

    // ====== 读 ======

    fun decode(text: String): Decoded {
        val root = runCatching { MiniJson.parse(text) }.getOrElse {
            throw BackupException("不是有效的备份文件（JSON 解析失败）")
        } as? Map<*, *> ?: throw BackupException("不是有效的备份文件（顶层不是对象）")

        if (root["format"] != FORMAT) throw BackupException("这不是像素记账的备份文件")
        val version = (root["version"] as? Long)?.toInt()
            ?: throw BackupException("备份文件缺少版本号")
        if (version > VERSION) throw BackupException("备份来自更新的版本（v$version），请先升级 App")
        if (version < 1) throw BackupException("备份版本号异常")

        val appVersion = (root["appVersion"] as? String) ?: "unknown"
        val exportedAt = (root["exportedAt"] as? Long) ?: 0L

        val ledgers = parseList(root["ledgers"], "账本") { item, index -> parseLedger(item, index) }
        if (ledgers.isEmpty()) throw BackupException("备份里没有任何账本")
        if (ledgers.size > MAX_LEDGERS) throw BackupException("账本数量异常（${ledgers.size}）")
        val ledgerIds = ledgers.map { it.id }.toSet()
        if (ledgerIds.size != ledgers.size) throw BackupException("备份里有重复的账本 id")

        val records = parseList(root["records"], "账单") { item, index -> parseRecord(item, index) }
        if (records.size > MAX_RECORDS) throw BackupException("账单数量异常（${records.size}）")
        if (records.map { it.id }.toSet().size != records.size) throw BackupException("备份里有重复的账单 id")
        records.firstOrNull { it.ledgerId !in ledgerIds }?.let {
            throw BackupException("账单 #${it.id} 指向了不存在的账本")
        }

        val recordIds = records.map { it.id }.toSet()
        val rawKeys = parseList(root["importKeys"], "导入记录") { item, index ->
            val map = item as? Map<*, *> ?: throw BackupException("导入记录第 ${index + 1} 项格式错误")
            val key = map["key"] as? String ?: throw BackupException("导入记录第 ${index + 1} 项缺少 key")
            val recordId = (map["recordId"] as? Long) ?: throw BackupException("导入记录第 ${index + 1} 项缺少 recordId")
            ImportKey(key, recordId)
        }
        val importKeys = rawKeys.filter { it.recordId in recordIds }

        val budgets = parseList(root["budgets"], "预算") { item, index ->
            val map = item as? Map<*, *> ?: throw BackupException("预算第 ${index + 1} 项格式错误")
            Budget(
                id = number(map["id"], "预算 id", index),
                year = number(map["year"], "预算年份", index).toInt().also {
                    if (it !in 1900..2999) throw BackupException("预算第 ${index + 1} 项年份异常")
                },
                month = number(map["month"], "预算月份", index).toInt().also {
                    if (it !in 1..12) throw BackupException("预算第 ${index + 1} 项月份异常")
                },
                amount = amount(map["amount"], "预算金额", index)
            )
        }
        // (year, month) 唯一：备份里若重复，只留第一条，避免恢复时撞唯一索引
        val uniqueBudgets = budgets.distinctBy { it.year to it.month }

        val recurring = parseList(root["recurring"], "周期规则") { item, index ->
            val map = item as? Map<*, *> ?: throw BackupException("周期规则第 ${index + 1} 项格式错误")
            val type = enumOf<RecordType>(map["type"], "周期规则收/支", index)
            val frequency = enumOf<Frequency>(map["frequency"], "周期规则频率", index)
            RecurringRecord(
                id = number(map["id"], "周期规则 id", index),
                amount = amount(map["amount"], "周期规则金额", index),
                type = type,
                category = text(map["category"], "周期规则分类", index),
                emoji = (map["emoji"] as? String) ?: "📝",
                note = (map["note"] as? String) ?: "",
                frequency = frequency,
                enabled = (map["enabled"] as? Boolean) ?: true,
                lastExecutedDate = (map["lastExecutedDate"] as? Long) ?: 0L,
                createdAt = (map["createdAt"] as? Long) ?: 0L,
                ledgerId = ((map["ledgerId"] as? Long) ?: 1L).takeIf { it in ledgerIds } ?: ledgers.first().id
            )
        }
        if (recurring.map { it.id }.toSet().size != recurring.size) throw BackupException("备份里有重复的周期规则 id")

        return Decoded(
            data = BackupData(ledgers, records, uniqueBudgets, recurring, importKeys),
            exportedAt = exportedAt,
            appVersion = appVersion,
            droppedImportKeys = rawKeys.size - importKeys.size
        )
    }

    // ====== 字段解析 ======

    private fun <T> parseList(raw: Any?, label: String, build: (Any?, Int) -> T): List<T> {
        if (raw == null) return emptyList()
        val list = raw as? List<*> ?: throw BackupException("备份里的「$label」不是数组")
        return list.mapIndexed { index, item -> build(item, index) }
    }

    private fun parseLedger(item: Any?, index: Int): Ledger {
        val map = item as? Map<*, *> ?: throw BackupException("账本第 ${index + 1} 项格式错误")
        val name = text(map["name"], "账本名称", index).trim()
        if (name.isEmpty()) throw BackupException("账本第 ${index + 1} 项名称为空")
        if (name.length > 20) throw BackupException("账本第 ${index + 1} 项名称超过 20 个字")
        return Ledger(
            id = number(map["id"], "账本 id", index),
            name = name,
            emoji = (map["emoji"] as? String)?.takeIf { it.isNotBlank() } ?: "📝",
            isDefault = (map["isDefault"] as? Boolean) ?: false,
            createdAt = (map["createdAt"] as? Long) ?: 0L
        )
    }

    private fun parseRecord(item: Any?, index: Int): Record {
        val map = item as? Map<*, *> ?: throw BackupException("账单第 ${index + 1} 项格式错误")
        val year = number(map["year"], "账单年份", index).toInt()
        val month = number(map["month"], "账单月份", index).toInt()
        val day = number(map["day"], "账单日期", index).toInt()
        if (year !in 1900..2999) throw BackupException("账单第 ${index + 1} 项年份异常（$year）")
        if (month !in 1..12) throw BackupException("账单第 ${index + 1} 项月份异常（$month）")
        if (day !in 1..31) throw BackupException("账单第 ${index + 1} 项日期异常（$day）")
        val deletedAt = map["deletedAt"]?.let { (it as? Long)?.takeIf { t -> t > 0 } ?: 0L }
        return Record(
            id = number(map["id"], "账单 id", index),
            amount = amount(map["amount"], "账单金额", index),
            type = enumOf(map["type"], "账单收/支", index),
            category = text(map["category"], "账单分类", index).trim().ifEmpty { "其他" },
            emoji = (map["emoji"] as? String)?.takeIf { it.isNotBlank() } ?: "📝",
            note = (map["note"] as? String) ?: "",
            year = year, month = month, day = day,
            ledgerId = number(map["ledgerId"], "账单账本", index),
            timestamp = (map["timestamp"] as? Long) ?: 0L,
            deletedAt = deletedAt
        )
    }

    private fun number(raw: Any?, label: String, index: Int): Long =
        (raw as? Long) ?: (raw as? Double)?.takeIf { it == Math.floor(it) }?.toLong()
        ?: throw BackupException("第 ${index + 1} 项的「$label」不是整数")

    private fun amount(raw: Any?, label: String, index: Int): Double {
        val value = when (raw) {
            is Long -> raw.toDouble()
            is Double -> raw
            else -> throw BackupException("第 ${index + 1} 项的「$label」不是数字")
        }
        if (!value.isFinite() || value <= 0) throw BackupException("第 ${index + 1} 项的「$label」必须大于 0")
        if (value > Money.MAX_CENTS / 100.0) throw BackupException("第 ${index + 1} 项的「$label」超出上限")
        return value
    }

    private fun text(raw: Any?, label: String, index: Int): String =
        raw as? String ?: throw BackupException("第 ${index + 1} 项的「$label」不是文本")

    private inline fun <reified T : Enum<T>> enumOf(raw: Any?, label: String, index: Int): T {
        val name = raw as? String ?: throw BackupException("第 ${index + 1} 项的「$label」缺失")
        return enumValues<T>().firstOrNull { it.name == name }
            ?: throw BackupException("第 ${index + 1} 项的「$label」取值无法识别（$name）")
    }
}
