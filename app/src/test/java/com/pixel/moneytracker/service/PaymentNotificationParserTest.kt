package com.pixel.moneytracker.service

import org.junit.Assert.*
import org.junit.Test

class PaymentNotificationParserTest {
    @Test fun refundAndMerchantColonAreParsed() {
        val result = PaymentNotificationParser.parse("com.eg.android.AlipayGphone", "退款成功", "退款金额：￥12.50\n商户：测试商店")!!
        assertTrue(result.isIncome)
        assertEquals(12.5, result.amount, 0.0)
        assertEquals("测试商店", result.note)
    }

    @Test fun overflowAndExtraDecimalPlacesAreRejected() {
        for (amount in listOf("9".repeat(400), "12.345")) {
            assertNull(PaymentNotificationParser.parse("com.tencent.mm", "付款成功", "￥$amount"))
        }
    }
    @Test fun pendingCreditNotificationIsNotCompletedIncome() {
        for (pending in listOf("待入账", "到账中")) {
            assertNull(pending, PaymentNotificationParser.parse("com.tencent.mm", "收款成功", "转账18.80元，$pending"))
        }
    }

    @Test fun rejectsIncompleteOrNonTransactionalNotificationsEvenWithPaymentWords() {
        listOf(
            "未支付成功 ¥8.00", "未付款成功 ¥8.00", "支付成功前请确认 ¥8.00",
            "支付失败，上次支付成功 ¥8.00", "支付已取消，支付成功后可重试 ¥8.00",
            "订单已支付 ¥8.00，正在配送", "订单已支付 ¥8.00，已发货",
            "支付成功可领红包 ¥8.00", "支付成功返现 ¥8.00",
            "待付款 ¥8.00", "订单完成 ¥8.00", "行程结束，车费 ¥8.00"
        ).forEach { text ->
            assertNull(text, PaymentNotificationParser.parse("com.taobao.taobao", "", text))
        }
    }

    @Test fun acceptsCompletedPayment() {
        val result = PaymentNotificationParser.parse("com.eg.android.AlipayGphone", "支付成功", "向便利店付款12.50元")!!
        assertEquals(12.50, result.amount, 0.0)
        assertFalse(result.isIncome)
        assertEquals("支付宝", result.source)
        assertEquals("便利店", result.note)
    }

    @Test fun preservesThousandsSeparators() {
        listOf("¥1,234.56", "￥12,345,678.90", "1,234.56元").zip(listOf(1234.56, 12345678.90, 1234.56)).forEach { (money, expected) ->
            assertEquals(expected, PaymentNotificationParser.parse("com.tencent.mm", "支付成功", money)!!.amount, 0.0)
        }
    }

    @Test fun prefersActualPaymentOverEarlierReferenceAmounts() {
        listOf(
            "原价¥99.00，实付¥12.50",
            "余额￥900.00，实际付款：12.50元",
            "原价99.00元，实付金额：￥12.50"
        ).forEach { text ->
            assertEquals(text, 12.50, PaymentNotificationParser.parse("com.eg.android.AlipayGphone", "支付成功", text)!!.amount, 0.0)
        }
    }

    @Test fun prefersTransactionAmountOverEarlierBalance() {
        listOf(
            "余额¥900.00，支付金额：￥12.50",
            "原价￥99.00，付款12.50元",
            "余额900.00元，扣款成功12.50元"
        ).forEach { text ->
            assertEquals(text, 12.50, PaymentNotificationParser.parse("com.eg.android.AlipayGphone", "支付成功", text)!!.amount, 0.0)
        }
    }

    @Test fun acceptsExplicitlyCompletedIncome() {
        listOf(
            Triple("com.eg.android.AlipayGphone", "收款成功", "支付宝(收入)"),
            Triple("com.tencent.mm", "已到账", "微信(收入)"),
            Triple("com.unionpay", "到账成功", "云闪付(收入)"),
            Triple("com.icbc", "转入成功", "工商银行(收入)")
        ).forEach { (pkg, title, source) ->
            val result = PaymentNotificationParser.parse(pkg, title, "￥28.60")
            assertNotNull(title, result)
            assertEquals(28.60, result!!.amount, 0.0)
            assertTrue(title, result.isIncome)
            assertEquals(source, result.source)
        }
    }

    @Test fun recipientLabelDoesNotTurnPaymentIntoIncome() {
        listOf("com.eg.android.AlipayGphone", "com.tencent.mm").forEach { pkg ->
            val result = PaymentNotificationParser.parse(pkg, "付款成功", "付款￥12.50，收款方：便利店")!!
            assertFalse(pkg, result.isIncome)
            assertFalse(result.source.contains("收入"))
        }
    }

    @Test fun rejectsOrderNumbersAndUnlabelledNumbersAsAmounts() {
        listOf("订单号：202611170001", "订单号：1234.56", "88", "12.50").forEach { text ->
            assertNull(text, PaymentNotificationParser.parse("com.eg.android.AlipayGphone", "支付成功", text))
        }
    }

    @Test fun rejectsUncompletedIncomeEvenWhenSuccessIsMentioned() {
        listOf(
            "未收款成功 ￥28.60", "未到账成功 ￥28.60", "未转入成功 ￥28.60",
            "待到账，上次收款成功 ￥28.60", "收款失败，上次收款成功 ￥28.60",
            "收款成功可领红包 ￥28.60", "已到账 ￥28.60，限时活动"
        ).forEach { text ->
            assertNull(text, PaymentNotificationParser.parse("com.eg.android.AlipayGphone", "收款通知", text))
        }
    }

    @Test fun rejectsUnpaidShoppingOrder() {
        assertNull(PaymentNotificationParser.parse("com.taobao.taobao", "待付款", "订单待付款 ¥88.00"))
    }
}
