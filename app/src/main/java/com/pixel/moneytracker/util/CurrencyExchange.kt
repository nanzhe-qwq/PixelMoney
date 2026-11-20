package com.pixel.moneytracker.util

import java.io.StringReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URL
import java.time.LocalDate
import javax.net.ssl.HttpsURLConnection
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal object CurrencyExchange {
    val currencies = linkedMapOf("USD" to "美元", "EUR" to "欧元", "HKD" to "港元",
        "JPY" to "日元", "GBP" to "英镑", "KRW" to "韩元", "SGD" to "新加坡元",
        "AUD" to "澳元", "CAD" to "加元", "THB" to "泰铢", "CHF" to "瑞士法郎")
    private val amountPattern = Regex("""[0-9]{1,10}(?:\.[0-9]{1,2})?""")
    private val ratePattern = Regex("""[0-9]{1,7}(?:\.[0-9]{1,12})?""")

    fun convert(amount: String, cnyPerUnit: String): BigDecimal? {
        if (!amountPattern.matches(amount) || !ratePattern.matches(cnyPerUnit)) return null
        return amount.toBigDecimal().multiply(cnyPerUnit.toBigDecimal()).setScale(2, RoundingMode.HALF_UP)
            .takeIf { it.signum() > 0 && it < BigDecimal("10000000000") }
    }

    /** 界面上显示的汇率保留 4 位，避免把 6.666666666667 这种长尾塞进输入框。 */
    fun displayRate(rate: String): String =
        runCatching { BigDecimal(rate).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() }
            .getOrDefault(rate)

    data class Quote(val rate: String, val date: LocalDate)

    // ECB publishes foreign units per EUR. Divide its CNY quote by its foreign quote.
    fun parseQuote(xml: String, currency: String): Quote {
        require(currency in currencies && xml.length <= 32768 && !xml.contains("<!DOCTYPE"))
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw IllegalArgumentException("Unexpected external entity") }
        val document = builder.parse(InputSource(StringReader(xml)))
        val cubes = document.getElementsByTagNameNS("http://www.ecb.int/vocabulary/2002-08-01/eurofxref", "Cube")
        val dated = (0 until cubes.length).map { cubes.item(it) as Element }.single { it.hasAttribute("time") }
        val date = LocalDate.parse(dated.getAttribute("time"))
        val children = (0 until dated.childNodes.length).mapNotNull { dated.childNodes.item(it) as? Element }
        fun perEuro(code: String): BigDecimal {
            if (code == "EUR") return BigDecimal.ONE
            val value = children.single { it.getAttribute("currency") == code }.getAttribute("rate")
            require(ratePattern.matches(value))
            return value.toBigDecimal().also { require(it.signum() > 0) }
        }
        val rate = perEuro("CNY").divide(perEuro(currency), 6, RoundingMode.HALF_UP).stripTrailingZeros()
        require(rate.signum() > 0 && ratePattern.matches(rate.toPlainString()))
        return Quote(rate.toPlainString(), date)
    }

    fun fetchQuote(currency: String): Quote {
        // One public file for all currencies. The request contains no account or transaction details.
        val connection = URL("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml")
            .openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.instanceFollowRedirects = false
            check(connection.responseCode == 200)
            val xml = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val chars = CharArray(32769)
                var count = 0
                while (count < chars.size) {
                    val read = reader.read(chars, count, chars.size - count)
                    if (read < 0) break
                    count += read
                }
                require(count < chars.size)
                String(chars, 0, count)
            }
            return parseQuote(xml, currency)
        } finally {
            connection.disconnect()
        }
    }
}
