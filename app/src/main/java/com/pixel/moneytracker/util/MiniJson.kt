package com.pixel.moneytracker.util

/**
 * 极简 JSON 读写（只为本机备份服务）。
 *
 * 为什么不用现成库：项目至今零额外依赖，而 `org.json` 在 JVM 单元测试里是空桩
 * （调用直接抛 "not mocked"），备份格式又必须能被纯 JVM 测试覆盖，所以这里自己写。
 * 只支持 JSON 的完整子集：object / array / string / number / true / false / null。
 *
 * 两条安全约束来自「备份文件是外部输入」：
 * - 嵌套深度上限 [MAX_DEPTH]，避免恶意文件把解析栈打爆；
 * - 长度上限 [MAX_CHARS]，避免超大文件直接把内存吃满。
 */
object MiniJson {

    const val MAX_DEPTH = 32
    const val MAX_CHARS = 16 * 1024 * 1024

    class JsonException(message: String) : Exception(message)

    // ====== 写 ======

    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Double -> {
                if (!value.isFinite()) throw JsonException("金额不能是 NaN/Infinity")
                sb.append(if (value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString())
            }
            is Float -> write(sb, value.toDouble())
            is Number -> sb.append(value.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k?.toString() ?: "")
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, item)
                }
                sb.append(']')
            }
            else -> throw JsonException("不支持的类型：${value::class.java.name}")
        }
    }

    private fun writeString(sb: StringBuilder, text: String) {
        sb.append('"')
        for (ch in text) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append("\\u").append(String.format("%04x", ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
    }

    // ====== 读 ======

    fun parse(text: String): Any? {
        if (text.length > MAX_CHARS) throw JsonException("文件过大")
        return Parser(text).parseDocument()
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun parseDocument(): Any? {
            val value = parseValue(0)
            skipWhitespace()
            if (pos != src.length) fail("结尾有多余内容")
            return value
        }

        private fun parseValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("嵌套过深")
            skipWhitespace()
            if (pos >= src.length) fail("内容不完整")
            return when (val c = src[pos]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                else -> if (c == '-' || c in '0'..'9') parseNumber() else fail("非法字符 '$c'")
            }
        }

        private fun parseObject(depth: Int): Map<String, Any?> {
            pos++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                if (pos >= src.length || src[pos] != '"') fail("对象的键必须是字符串")
                val key = parseString()
                skipWhitespace()
                if (pos >= src.length || src[pos] != ':') fail("键后面缺少冒号")
                pos++
                map[key] = parseValue(depth + 1)
                skipWhitespace()
                if (pos >= src.length) fail("对象未闭合")
                when (src[pos]) {
                    ',' -> { pos++ }
                    '}' -> { pos++; return map }
                    else -> fail("对象里出现意外字符 '${src[pos]}'")
                }
            }
        }

        private fun parseArray(depth: Int): List<Any?> {
            pos++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(parseValue(depth + 1))
                skipWhitespace()
                if (pos >= src.length) fail("数组未闭合")
                when (src[pos]) {
                    ',' -> { pos++ }
                    ']' -> { pos++; return list }
                    else -> fail("数组里出现意外字符 '${src[pos]}'")
                }
            }
        }

        private fun parseString(): String {
            pos++ // "
            val sb = StringBuilder()
            while (true) {
                if (pos >= src.length) fail("字符串未闭合")
                val c = src[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (pos >= src.length) fail("转义不完整")
                        when (val e = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) fail("\\u 转义不完整")
                                val hex = src.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: fail("\\u 转义非法")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("非法转义 \\$e")
                        }
                    }
                    c < ' ' -> fail("字符串里有未转义的控制字符")
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Any {
            val start = pos
            if (pos < src.length && src[pos] == '-') pos++
            while (pos < src.length && src[pos] in '0'..'9') pos++
            var isDecimal = false
            if (pos < src.length && src[pos] == '.') {
                isDecimal = true
                pos++
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
                isDecimal = true
                pos++
                if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
                while (pos < src.length && src[pos] in '0'..'9') pos++
            }
            val raw = src.substring(start, pos)
            if (!NUMBER.matches(raw)) fail("非法数字：$raw")
            if (!isDecimal) {
                raw.toLongOrNull()?.let { return it }
            }
            val d = raw.toDoubleOrNull() ?: fail("非法数字：$raw")
            if (!d.isFinite()) fail("数字越界：$raw")
            return d
        }

        private fun parseLiteral(word: String, value: Any?): Any? {
            if (!src.startsWith(word, pos)) fail("非法字面量")
            pos += word.length
            return value
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
        }

        private fun fail(message: String): Nothing =
            throw JsonException("$message（位置 $pos）")
    }

    private val NUMBER = Regex("""-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?""")
}
