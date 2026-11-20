package com.pixel.moneytracker.util

/**
 * 导入时可选的账本名称处理。
 *
 * 只做「用户输入 → 落库名称」的一次性规范化，保证同一个名称不会因为多余空格
 * 变成两条账本（ledgers 表本身没有唯一约束，重名会让「按名称找账本」产生歧义）。
 */
internal object LedgerNames {

    const val MAX_LENGTH = 20

    private val WHITESPACE = Regex("\\s+")

    /** 去掉首尾空白，把中间连续空白折叠成一个半角空格。 */
    fun normalize(raw: String): String = WHITESPACE.replace(raw.trim(), " ")

    /** 返回错误文案；null 表示可以落库。 */
    fun validate(raw: String): String? {
        val name = normalize(raw)
        return when {
            name.isEmpty() -> "账本名称不能为空"
            name.length > MAX_LENGTH -> "账本名称最多 $MAX_LENGTH 个字符"
            else -> null
        }
    }
}
