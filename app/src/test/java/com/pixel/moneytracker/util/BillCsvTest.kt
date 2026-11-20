package com.pixel.moneytracker.util

import com.pixel.moneytracker.data.entity.Record
import com.pixel.moneytracker.data.entity.RecordType
import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Test

class BillCsvTest {
    @Test fun ownExportRoundTripsQuotedMultilineNotesAndIdenticalPayments() {
        val r = Record(amount = 15.25, type = RecordType.EXPENSE, category = "餐饮",
            emoji = "", note = "=含逗号,双引号\"\r\n备注", ledgerId = 7, year = 2024, month = 2, day = 29)
        val data = BillCsv.parse(RecordCsv.encode(listOf(r,r), mapOf(7L to "家用")).toByteArray())
        assertEquals(2, data.bills.size)
        assertTrue(data.skipped.isEmpty())
        assertEquals(r.note, data.bills.first().note)
        assertEquals("家用", data.bills.first().ledger)
        assertEquals(1525L, data.bills.first().cents)
        assertNotEquals(data.bills.first().key, data.bills.last().key)
    }

    @Test fun alipayGbkSkipsUnsettledAndKeepsDistinctTransactionIds() {
        val lines = listOf("支付宝账单导出", "", "交易时间,收/支,金额,交易状态,交易订单号,交易对方,商品说明",
            "2026-11-16 10:15:30,支出,1.23,交易成功,TESTE001,小店,早餐",
            "2026-11-16 10:15:30,支出,1.23,交易成功,TESTE002,小店,早餐",
            "2026-11-16 10:15:30,支出,1.23,等待付款,002,小店,早餐",
            "2026-11-16 10:15:30,支出,1.23,交易成功,1.234E+15,小店,早餐")
        val f = BillCsv.parse(lines.joinToString("\r\n").toByteArray(Charset.forName("GBK")))
        assertEquals(2, f.bills.size)
        assertEquals(2, f.skipped.values.sum())
        assertEquals("支付宝", f.source)
        assertEquals(123L, f.bills[0].cents)
        assertNotEquals(f.bills[0].key, f.bills[1].key)
    }

    @Test fun wechatReadsYuanAndNeverImportsMalformedValuesAsMoney() {
        val h = "交易时间,收/支,金额（元）,当前状态,交易单号,交易对方,商品\n"
        val f = BillCsv.parse(h + listOf(
            "2026-01-02 03:04:05,收入,￥1.50,已收钱,WA1,测试,收款",
            "2026-01-02 03:04:05,收入,￥1.501,已收钱,WA2,测试,收款",
            "2026-02-30 03:04:05,收入,￥1.50,已收钱,WA3,测试,收款",
            "2026-01-02 03:04:05,收入,-￥1.50,已收钱,WA4,测试,收款",
            "2026-01-02 03:04:05,不计收支,￥1.50,已收钱,WA5,测试,收款"
        ).joinToString("\n"))
        assertEquals(1, f.bills.size)
        assertEquals(150L, f.bills.single().cents)
        assertEquals(RecordType.INCOME, f.bills.single().type)
        assertEquals(4, f.skipped.values.sum())
    }

    @Test fun importedBillsGetAnInferredCategoryInsteadOfAllOther() {
        val h = "交易时间,交易类型,交易对方,商品,收/支,金额(元),当前状态,交易单号\n"
        val f = BillCsv.parse(h + listOf(
            "2026-11-18 12:30:00,商户消费,美团外卖,午餐,支出,28.50,支付成功,INF001",
            "2026-11-18 09:10:00,商户消费,星巴克,拿铁,支出,32.00,支付成功,INF002",
            "2026-11-18 20:00:00,商户消费,淘宝,女装,支出,99.00,支付成功,INF003",
            "2026-11-18 21:00:00,商户消费,美团外卖,晚餐,收入,28.50,退款成功,INF004"
        ).joinToString("\n"))
        assertEquals(listOf("餐饮", "咖啡", "购物", "退款"), f.bills.map { it.category })
        assertEquals(RecordType.INCOME, f.bills.last().type)
    }

    @Test fun brokenFilesCannotPartiallyImportEarlierRows() {
        val h = "日期,收支,金额,分类,账本,备注\n"
        for (s in listOf(h + "2026-11-01,支出,2.00,购物,日常,\"截断",
            h + "2026-11-01,支出,2.00,购物,日常,\"备注\"拼接", "不是交易文件")) {
            assertTrue(runCatching { BillCsv.parse(s) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
