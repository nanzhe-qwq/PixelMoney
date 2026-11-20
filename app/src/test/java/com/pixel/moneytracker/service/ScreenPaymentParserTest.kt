package com.pixel.moneytracker.service

import org.junit.Assert.*
import org.junit.Test

class ScreenPaymentParserTest {
    @Test fun alipayReceiptWithoutMerchantReconciliationLinkStillUsesMainAmount() {
        for (page in listOf("com.alipay.android.msp.ui.views.MspContainerActivity",
            "android.widget.FrameLayout", "android.widget.TextView", "android.view.View", "android.webkit.WebView")) {
            for (header in listOf(listOf("支付成功￥0.01"), listOf("支付成功", "0.01"))) {
                val payment = ScreenPaymentParser.parse("支付宝", page, header + listOf(
                    "测试商户", "￥0.01", "付款方式", "测试银行卡", "完成", "回首页", "回首页",
                    "支付有礼", "优惠券￥88.00"
                ))!!
                assertEquals(page, 0.01, payment.amount, 0.001)
                assertFalse(page, payment.isIncome)
            }
        }
    }

    @Test fun alipayNewReceiptMustKeepItsControlsAndCannotMatchHomeOrAuthentication() {
        val receipt = listOf("支付成功￥0.01", "付款方式", "完成", "回首页")
        for (control in listOf("付款方式", "完成", "回首页")) {
            assertNull(control, ScreenPaymentParser.parse("支付宝", "android.widget.FrameLayout", receipt - control))
        }
        for (page in listOf("HomeActivity", "Cashier", "payment.PasswordDialog", "payment.LoadingDialog")) {
            assertNull(page, ScreenPaymentParser.parse("支付宝", page, receipt))
        }
        assertNull(ScreenPaymentParser.parse("微信", "android.widget.FrameLayout", receipt))
        assertNull(ScreenPaymentParser.parse("支付宝", "android.widget.FrameLayout", receipt + "待支付"))
    }

    @Test fun alipayNewReceiptZeroMustNotUseMerchantOrPromotionalPrice() {
        val result = ScreenPaymentParser.inspect("支付宝", "android.widget.FrameLayout",
            listOf("支付成功￥0.00", "测试商户", "￥0.01", "付款方式", "完成", "回首页", "优惠￥88.00"))
        assertNull(result.payment)
        assertEquals(ScreenParseReason.ZERO_AMOUNT, result.reason)
    }

    @Test fun alipayNewReceiptStillRejectsConflictingMainAndSettledAmounts() {
        for (conflict in listOf(listOf("支付成功", "0.02"), listOf("实际支付", "￥0.02"))) {
            val result = ScreenPaymentParser.inspect("支付宝", "android.widget.FrameLayout",
                listOf("支付成功￥0.01", "付款方式", "完成", "回首页") + conflict)
            assertNull(result.payment)
            assertEquals(ScreenParseReason.INVALID_AMOUNT, result.reason)
        }
    }

    @Test fun wechatScanBillUsesTransferTimeAndAlipayKeepsTransactionTime() {
        for ((source, label, value) in listOf(
            Triple("微信", "转账时间", "2026年11月19日 21:46:41"),
            Triple("支付宝", "交易时间", "2026-11-19 21:46:41")
        )) {
            val payment = ScreenPaymentParser.parse(source, "BillDetail", listOf(
                "账单详情", "-0.01", "当前状态", "支付成功", label, value
            ))!!
            val date = java.time.Instant.ofEpochMilli(payment.transactionTime!!).atZone(java.time.ZoneId.systemDefault())
            assertEquals(source, java.time.LocalDateTime.of(2026, 11, 19, 21, 46, 41), date.toLocalDateTime())
            assertFalse(source, payment.isIncome)
        }
    }

    @Test fun transferTimeRequiresItsOwnValidDate() {
        for (fields in listOf(
            listOf("转账时间", "2026年2月30日 21:46:41"),
            listOf("转账单号", "2026年11月19日 21:46:41"),
            listOf("转账时间", "转账单号", "2026年11月19日 21:46:41")
        )) {
            assertNull(ScreenPaymentParser.parse("微信", "BillDetail",
                listOf("账单详情", "支付成功", "-0.01") + fields)!!.transactionTime)
        }
    }

    @Test fun rejectsExtraPrecisionAndKeepsJdTakeoutSource() {
        assertNull(ScreenPaymentParser.parse("微信", "BillDetail", listOf("支付成功", "￥12.345")))
        val food = ScreenPaymentParser.parse("京东", "WebView", listOf("订单详情", "京东外卖", "已支付", "实付", "￥12.50"))!!
        assertEquals("京东外卖", food.source)
        assertTrue(ScreenPaymentParser.parse("支付宝", "BillDetail", listOf("退款详情", "当前状态：退款成功", "￥12.50"))!!.isIncome)
    }
    @Test fun historicalBillKeepsItsPaymentTime() {
        val payment = ScreenPaymentParser.parse("微信", "BillDetail", listOf("账单详情", "支付成功", "￥5.00", "支付时间", "2026年11月16日 18:05:12"))!!
        val date = java.time.Instant.ofEpochMilli(payment.transactionTime!!).atZone(java.time.ZoneId.systemDefault())
        assertEquals(java.time.LocalDateTime.of(2026, 11, 16, 18, 5, 12), date.toLocalDateTime())
        assertNull(ScreenPaymentParser.parse("微信", "BillDetail", listOf("支付成功", "￥5.00", "支付时间 2026-02-30 18:05"))!!.transactionTime)
    }
    @Test fun shoppingDetailsRequireSettledAmountAndCompletedStatus() {
        for (source in listOf("淘宝", "拼多多", "京东", "美团", "饿了么")) {
            val receipt = listOf("订单详情", "已付款", "订单金额", "￥99.00", "实付款", "￥18.80", "店铺名称", "测试店铺")
            val payment = ScreenPaymentParser.parse(source, "WebView", receipt)!!
            assertEquals(18.8, payment.amount, 0.001)
            assertEquals("测试店铺", payment.merchant)
            assertFalse(payment.isIncome)
            assertNull(ScreenPaymentParser.parse(source, "WebView", receipt + "待付款"))
            assertNull(ScreenPaymentParser.parse(source, "WebView", receipt - "已付款"))
            assertNull(ScreenPaymentParser.parse(source, "WebView", listOf("订单详情", "交易成功", "原价￥99.00")))
        }
    }

    @Test fun refundIsIncomeAndRecipientNameDoesNotChangeExpenseDirection() {
        val refund = ScreenPaymentParser.parse("支付宝", "BillDetail", listOf("退款详情", "退款成功", "退款金额", "￥12.50"))!!
        assertTrue(refund.isIncome)
        val expense = ScreenPaymentParser.parse("微信", "BillDetail", listOf("支付成功", "￥12.50", "商户名称", "收入到账商店"))!!
        assertFalse(expense.isIncome)
        val income = ScreenPaymentParser.parse("支付宝", "BillDetail", listOf("账单详情", "交易成功", "+12.50"))!!
        assertTrue(income.isIncome)
        assertEquals(12.5, income.amount, 0.001)
    }
    @Test fun mspMergedHeaderUsesItsAmountAndRejectsUntrustedContexts() {
        val receipt = listOf("支付成功￥18.80", "测试商户", "￥20.00", "百次立减", "-￥1.20", "点击给商家对账", "完成")
        for (page in listOf("com.alipay.android.msp.ui.views.MspContainerActivity", "android.widget.FrameLayout", "android.widget.TextView")) {
            val result = ScreenPaymentParser.inspect("支付宝", page, receipt)
            assertEquals(ScreenParseReason.MATCH, result.reason)
            assertEquals(18.80, result.payment!!.amount, 0.001)
            assertFalse(result.payment.isIncome)
        }
        assertNull(ScreenPaymentParser.parse("支付宝", "HomeActivity", receipt))
        assertNull(ScreenPaymentParser.parse("微信", "android.widget.FrameLayout", receipt))
        assertNull(ScreenPaymentParser.parse("支付宝", "android.widget.FrameLayout", receipt - "完成"))
        assertNull(ScreenPaymentParser.parse("支付宝", "android.widget.FrameLayout", receipt - "点击给商家对账"))
        assertNull(ScreenPaymentParser.parse("支付宝", "android.widget.FrameLayout", receipt + "待支付"))
        assertNull(ScreenPaymentParser.parse("支付宝", "payment.PasswordDialog", receipt))
        assertNull(ScreenPaymentParser.parse("支付宝", "android.widget.FrameLayout", receipt.map { it.replace("支付成功￥18.80", "支付成功￥18.80后领奖") }))
    }

    @Test fun conflictingMergedAndStandaloneReceiptHeadersAreRejected() {
        for (header in listOf(listOf("支付成功￥18.80", "支付成功", "20.00"),
                              listOf("支付成功￥18.80", "付款成功￥20.00"))) {
            val result = ScreenPaymentParser.inspect("支付宝", "android.widget.FrameLayout",
                header + listOf("点击给商家对账", "完成"))
            assertNull(result.payment)
            assertEquals(ScreenParseReason.INVALID_AMOUNT, result.reason)
        }
    }

    @Test fun mspMergedZeroHeaderIsAnAuthoritativeCompletedZeroPayment() {
        val result = ScreenPaymentParser.inspect("支付宝", "com.alipay.android.msp.ui.views.MspContainerActivity",
            listOf("支付成功￥0.00", "测试商户", "￥0.01", "百次立减", "-￥0.01", "点击给商家对账", "完成", "回首页", "回首页"))
        assertNull(result.payment)
        assertEquals(ScreenParseReason.ZERO_AMOUNT, result.reason)
    }

    @Test fun mspMainMustAgreeWithEveryExplicitSettledAmount() {
        val result = ScreenPaymentParser.inspect("支付宝", "android.widget.FrameLayout",
            listOf("支付成功", "18.80", "实际支付", "￥18.80", "实付金额", "￥11.86", "点击给商家对账", "完成"))
        assertNull(result.payment)
        assertEquals(ScreenParseReason.INVALID_AMOUNT, result.reason)
    }

    @Test fun nonFiniteAmountsAreRejectedOnEveryAmountPath() {
        val overflow = "9".repeat(400) + ".00"
        val cases = listOf(
            "BillDetail" to listOf("支付成功", "实际支付", "￥$overflow"),
            "BillDetail" to listOf("支付成功", "￥$overflow"),
            "com.alipay.android.msp.ui.views.MspContainerActivity" to
                listOf("支付成功", overflow, "点击给商家对账", "完成")
        )
        for ((page, texts) in cases) {
            val result = ScreenPaymentParser.inspect("支付宝", page, texts)
            assertNull(result.payment)
            assertEquals(ScreenParseReason.INVALID_AMOUNT, result.reason)
        }
    }

    @Test fun alipayBareMainWithoutTrustedReceiptMustNotFallBackToOrderPrice() {
        val cases = listOf(
            "com.alipay.android.msp.ui.views.MspContainerActivity" to listOf("支付成功", "18.80", "￥20.00", "完成"),
            "android.widget.FrameLayout" to listOf("支付成功", "18.80", "￥20.00", "点击给商家对账"),
            "HomeActivity" to listOf("首页", "支付成功", "18.80", "￥20.00", "点击给商家对账", "完成"),
            "Cashier" to listOf("支付成功", "18.80", "￥20.00", "点击给商家对账", "完成")
        )
        for ((page, texts) in cases) {
            val result = ScreenPaymentParser.inspect("支付宝", page, texts)
            assertNull(page, result.payment)
            assertEquals(page, ScreenParseReason.NO_RECEIPT_CONTEXT, result.reason)
        }
    }

    @Test fun mspReceiptWithoutAdjacentMainAmountMustNotUsePromotionalCurrency() {
        val result = ScreenPaymentParser.inspect("支付宝", "com.alipay.android.msp.ui.views.MspContainerActivity",
            listOf("支付成功", "广告", "￥88.00", "点击给商家对账", "完成"))
        assertNull(result.payment)
        assertEquals(ScreenParseReason.NO_AMOUNT, result.reason)
    }

    @Test fun inspectionReportsTheSameDecisionAsLegacyParseWithoutInputInReason() {
        // Every row exercises a decision in the real parse path, not a diagnostic approximation.
        val cases = listOf(
            Triple("payment.PasswordDialog", listOf("支付成功", "￥18.80"), ScreenParseReason.TRANSIENT_PAGE),
            Triple("BillDetail", listOf("支付成功", "待支付", "￥18.80"), ScreenParseReason.BLOCKED_STATUS),
            Triple("BillDetail", listOf("支付成功后发货", "￥18.80"), ScreenParseReason.NO_COMPLETED_STATUS),
            Triple("WalletWebView", listOf("交易成功", "￥980.00"), ScreenParseReason.NO_RECEIPT_CONTEXT),
            Triple("BillDetail", listOf("支付成功", "零钱余额", "980.00"), ScreenParseReason.NO_AMOUNT),
            Triple("BillDetail", listOf("支付成功", "实付", "￥0.00", "￥0.01"), ScreenParseReason.ZERO_AMOUNT),
            Triple("com.alipay.android.msp.ui.views.MspContainerActivity",
                listOf("支付成功", "0.00", "测试商户", "￥0.01", "百次立减", "-￥0.01", "点击给商家对账", "完成"), ScreenParseReason.ZERO_AMOUNT),
            Triple("android.widget.FrameLayout",
                listOf("支付成功", "18.80", "实付", "￥11.86", "点击给商家对账", "完成"), ScreenParseReason.INVALID_AMOUNT),
            Triple("android.widget.FrameLayout",
                listOf("支付成功", "18.80", "点击给商家对账", "完成"), ScreenParseReason.MATCH)
        )
        for ((page, texts, expected) in cases) {
            val result: ScreenParseResult = ScreenPaymentParser.inspect("支付宝", page, texts)
            assertEquals(expected, result.reason)
            assertEquals(ScreenPaymentParser.parse("支付宝", page, texts), result.payment)
            assertEquals(expected == ScreenParseReason.MATCH, result.payment != null)
        }
    }

    @Test fun conflictingExplicitPaymentMustNotOverrideOrBeIgnoredByMspMainAmount() {
        for (explicit in listOf("11.86", "0.00")) {
            assertNull(explicit, ScreenPaymentParser.parse("支付宝", "com.alipay.android.msp.ui.views.MspContainerActivity",
                listOf("支付成功", "18.80", "实际支付", "￥$explicit", "订单金额", "￥20.00", "点击给商家对账", "完成")))
        }
    }

    @Test fun mspReceiptAcceptsWidgetEventClassWithoutCurrencyFallback() {
        for (page in listOf("android.widget.FrameLayout", "android.widget.TextView", "android.view.View", "android.webkit.WebView")) {
            val payment = ScreenPaymentParser.parse("支付宝", page,
                listOf("支付成功", "18.80", "点击给商家对账", "完成"))
            assertNotNull(page, payment)
            assertEquals(page, 18.80, payment!!.amount, 0.001)
        }
    }

    @Test fun mspBareMainAmountBeatsOrderPriceAndDiscount() {
        val payment = ScreenPaymentParser.parse("支付宝", "com.alipay.android.msp.ui.views.MspContainerActivity",
            listOf("支付成功", "18.80", "测试商户", "￥20.00", "百次立减", "-￥1.20", "点击给商家对账", "完成"))!!
        assertEquals(18.80, payment.amount, 0.001)
        assertFalse(payment.isIncome)
    }

    @Test fun explicitZeroPaymentMustNotFallBackToOriginalPrice() {
        for (label in listOf("实际支付", "实付金额", "支付金额", "付款金额", "实付")) {
            assertNull(label, ScreenPaymentParser.parse("支付宝", "BillDetail",
                listOf("账单详情", "支付成功", "订单金额", "￥0.01", label, "￥0.00")))
        }
    }

    @Test fun pendingCreditMustNotCountAsCompletedIncome() {
        for (pending in listOf("已收款，待入账", "待到账", "到账中")) {
            assertNull(pending, ScreenPaymentParser.parse("微信", "WalletOrderInfo",
                listOf("转账详情", "已收款", pending, "¥18.80")))
        }
    }

    @Test fun authenticationAndLoadingPagesMustNotReuseUnderlyingReceipt() {
        for (page in listOf("payment.PasswordDialog", "payment.FingerprintDialog", "payment.BiometricPrompt", "payment.FaceVerifyActivity", "android.app.ProgressDialog", "payment.LoadingDialog")) {
            assertNull(page, ScreenPaymentParser.parse("支付宝", page,
                listOf("账单详情", "支付成功", "实际支付", "¥18.80")))
        }
    }

    @Test fun negativeSettledAmountIsExpenseDespiteIncomeNavigationText() {
        val payment = ScreenPaymentParser.parse("支付宝", "BillDetail",
            listOf("账单详情", "交易成功", "收入", "-11.86", "订单金额", "¥20.00"))!!
        assertFalse(payment.isIncome)
        assertEquals(11.86, payment.amount, 0.001)
    }

    @Test fun unlabeledDecimalIsNotAPaymentAmount() {
        assertNull(ScreenPaymentParser.parse("微信", "WebView",
            listOf("支付详情", "支付成功", "零钱余额", "980.00")))
    }

    @Test fun actualPaymentLabelsBeatOriginalOrderCurrency() {
        for (label in listOf("支付金额", "实付", "实际支付", "实付金额")) {
            val payment = ScreenPaymentParser.parse("微信", "WebView",
                listOf("支付详情", "支付成功", "订单金额", "¥20.00", label, "¥11.86"))!!
            assertEquals(label, 11.86, payment.amount, 0.001)
        }
    }

    @Test fun pendingOrPromotionalSuccessTextIsNotCompletedStatus() {
        val screens = listOf(
            listOf("账单详情", "待付款", "支付成功后发货", "支付金额", "¥11.86"),
            listOf("交易详情", "支付失败", "历史交易成功", "¥11.86"),
            listOf("交易详情", "等待对方收款", "转账成功", "¥11.86"),
            listOf("付款详情", "已取消", "支付成功", "¥11.86"),
            listOf("账单详情", "未支付成功", "¥11.86")
        )
        for (texts in screens) assertNull(ScreenPaymentParser.parse("支付宝", "Cashier", texts))
    }

    @Test fun explicitPaymentReceiptDoesNotNeedDetailActivity() {
        for (source in listOf("支付宝", "微信")) {
            val payment = ScreenPaymentParser.parse(source, "WebView",
                listOf("支付成功", "¥11.86", "商户名称", "测试便利店"))
            assertNotNull(source, payment)
            assertEquals(11.86, payment!!.amount, 0.001)
        }
    }

    @Test fun genericCompletedStatusNeedsActualDetailOrPaymentReceipt() {
        assertNull(ScreenPaymentParser.parse("微信", "WalletWebView",
            listOf("钱包", "交易成功", "零钱余额", "¥980.00")))
    }

    @Test fun walletClassAndCurrencyDoNotProveCompletedPayment() {
        assertNull(ScreenPaymentParser.parse("微信", "WalletWebView",
            listOf("钱包", "零钱余额", "¥980.00", "支付方式")))
    }

    @Test fun completedDetailKeepsAmountMerchantAndDirection() {
        val result = ScreenPaymentParser.parse("支付宝", "BillDetailActivity",
            listOf("账单详情", "交易成功", "-11.86", "商户名称", "测试便利店"))!!
        assertEquals(11.86, result.amount, 0.001)
        assertEquals("测试便利店", result.merchant)
        assertFalse(result.isIncome)
    }
}
