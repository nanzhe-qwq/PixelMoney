package com.pixel.moneytracker.util

import org.junit.Assert.*
import org.junit.Test

class CurrencyExchangeTest {
    private val sample = """
        <Envelope xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref"><Cube><Cube time="2026-04-17">
        <Cube currency="CNY" rate="8.00"/><Cube currency="USD" rate="1.25"/><Cube currency="JPY" rate="160"/>
        </Cube></Cube></Envelope>
    """.trimIndent()

    @Test fun convertsViaEuroInTheRightDirectionAndKeepsPublicationDate() {
        val usd = CurrencyExchange.parseQuote(sample, "USD")
        assertEquals("6.4", usd.rate)
        assertEquals("2026-04-17", usd.date.toString())
        assertEquals("8", CurrencyExchange.parseQuote(sample, "EUR").rate)
        assertEquals("0.05", CurrencyExchange.parseQuote(sample, "JPY").rate)
        assertEquals("20.00", CurrencyExchange.convert("400", "0.05")!!.toPlainString())
    }

    @Test fun convertsToCentsWithHalfUpRoundingAndRejectsInvalidMoney() {
        assertEquals("10.01", CurrencyExchange.convert("10.00", "1.0005")!!.toPlainString())
        for (amount in listOf("-1", "1e3", ".1", "1.000", "0", "NaN")) {
            assertNull(amount, CurrencyExchange.convert(amount, "1"))
        }
        for (rate in listOf("", "0", "-7", "Infinity", "1e10", "0.00001")) {
            assertNull(rate, CurrencyExchange.convert("3", rate))
        }
        assertNull(CurrencyExchange.convert("9999999999", "2"))
    }

    @Test fun displayedRateKeepsFourDecimalsAndToleratesGarbage() {
        assertEquals("6.6667", CurrencyExchange.displayRate("6.666666666667"))
        assertEquals("7.2", CurrencyExchange.displayRate("7.2000"))
        assertEquals("8", CurrencyExchange.displayRate("8.0000"))
        assertEquals("", CurrencyExchange.displayRate(""))
        assertEquals("abc", CurrencyExchange.displayRate("abc"))
    }

    @Test fun invalidProviderResponseCannotBecomeARate() {
        val invalid = listOf(sample.replace("8.00", "-8.00"), sample.replace("160", "0"),
            sample.replace("JPY", "ABC"), sample.replace("2026-04-17", "2026-02-30"),
            "<!DOCTYPE rates>" + sample)
        for (xml in invalid) assertTrue(runCatching { CurrencyExchange.parseQuote(xml, "JPY") }.isFailure)
    }
}
