package com.pixel.moneytracker.service

/** Pure screen seam; never shares notification matching rules. */
internal data class ScreenPayment(val source: String, val amount: Double, val merchant: String, val isIncome: Boolean, val transactionTime: Long? = null)

/** Fixed, content-free decisions safe for callers to use in redacted diagnostics. */
internal enum class ScreenParseReason {
    TRANSIENT_PAGE, BLOCKED_STATUS, NO_COMPLETED_STATUS, NO_RECEIPT_CONTEXT,
    NO_AMOUNT, ZERO_AMOUNT, INVALID_AMOUNT, MATCH
}

internal data class ScreenParseResult(val payment: ScreenPayment?, val reason: ScreenParseReason)

internal object ScreenPaymentParser {
    // Page-state rejection runs before inspecting any underlying receipt text.
    private val transientPageMarkers = listOf("Password", "Fingerprint", "Biometric", "FaceVerify", "ProgressDialog", "LoadingDialog")
    private val settledAmountPattern = Regex("""(?:实际付款|实际支付|实付金额|实付款|支付金额|付款金额|实付|退款金额|收款金额|到账金额)\s*[：:]?\s*[¥￥]?\s*([+-]?[\d,]+(?:\.\d{1,2})?)(?![\d.])""")

    fun parse(source: String, className: String, texts: List<String>): ScreenPayment? =
        inspect(source, className, texts).payment

    fun inspect(source: String, className: String, texts: List<String>): ScreenParseResult {
        if (transientPageMarkers.any { className.contains(it, ignoreCase = true) })
            return ScreenParseResult(null, ScreenParseReason.TRANSIENT_PAGE)
        val fullText = texts.joinToString(" ")
        if (Regex("""[¥￥]\s*[+-]?[\d,]+\.\d{3,}|[+-]?[\d,]+\.\d{3,}\s*元""").containsMatchIn(fullText))
            return ScreenParseResult(null, ScreenParseReason.INVALID_AMOUNT)
        val statusLines = texts.flatMap { it.split('\n') }.map { it.trim() }
        // MSP exposes this header as one contentDescription to ordinary accessibility.
        // Require the receipt controls as well: homepage reminders must not become payments.
        val mspReceipt = source == "支付宝" &&
            (className == "com.alipay.android.msp.ui.views.MspContainerActivity" ||
                className.startsWith("android.widget.") || className.startsWith("android.view.") ||
                className == "android.webkit.WebView") &&
            "完成" in statusLines && ("点击给商家对账" in statusLines ||
                ("付款方式" in statusLines && "回首页" in statusLines))
        val mergedHeaders = if (mspReceipt) statusLines.mapNotNull {
            Regex("""^(?:支付成功|付款成功)\s*[¥￥]\s*(\d+\.\d{2})$""").matchEntire(it)
        } else emptyList()
        val mergedHeader = mergedHeaders.firstOrNull()
        val statusPattern = Regex("^(?:(?:当前状态|交易状态|支付状态)[：:]?\\s*)?(?:支付成功|付款成功|交易成功|转账成功|收款成功|已收款|已到账|退款成功|退款到账|已支付|已付款)$")
        val blocked = listOf("待付款", "待支付", "等待付款", "等待支付", "未付款", "未支付", "支付失败", "交易失败", "交易关闭", "已取消", "已关闭", "等待对方收款", "待收款", "待入账", "待到账", "到账中")
        if (blocked.any(fullText::contains)) return ScreenParseResult(null, ScreenParseReason.BLOCKED_STATUS)
        val completed = statusLines.any(statusPattern::matches) || mergedHeader != null
        if (!completed) return ScreenParseResult(null, ScreenParseReason.NO_COMPLETED_STATUS)
        val detail = listOf("账单详情", "交易详情", "付款详情", "支付详情", "转账详情", "红包详情", "退款详情").any(fullText::contains) ||
            listOf("BillDetail", "TradeRecord", "WalletOrderInfo").any { className.contains(it, true) }
        val receipt = mergedHeader != null || listOf("支付成功", "付款成功", "转账成功", "收款成功", "已收款", "已到账", "退款成功", "退款到账").any { it in statusLines }
        val shoppingDetail = source in listOf("淘宝", "拼多多", "京东", "美团", "饿了么") &&
            listOf("订单详情", "订单信息").any(fullText::contains) && settledAmountPattern.containsMatchIn(fullText)
        if (!detail && !receipt && !shoppingDetail) return ScreenParseResult(null, ScreenParseReason.NO_RECEIPT_CONTEXT)
        // This layout is specific to Alipay's MSP receipt, not a general decimal rule.
        val successIndex = statusLines.indexOfFirst { it == "支付成功" || it == "付款成功" }
        val adjacentMainText = if (successIndex >= 0) statusLines.getOrNull(successIndex + 1) else null
        val bareMainText = adjacentMainText?.takeIf { Regex("""\d+\.\d{2}""").matches(it) }
        if (source == "支付宝" && bareMainText != null && !mspReceipt)
            return ScreenParseResult(null, ScreenParseReason.NO_RECEIPT_CONTEXT)
        val mainAmount = if (mspReceipt)
            mergedHeader?.groupValues?.get(1)?.toDoubleOrNull() ?: bareMainText?.toDoubleOrNull()
        else null
        val explicitAmounts = settledAmountPattern.findAll(fullText)
            .mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.toList()
        val explicitAmount = explicitAmounts.firstOrNull()
        if (source in listOf("淘宝", "拼多多", "京东", "美团", "饿了么") && explicitAmount == null)
            return ScreenParseResult(null, ScreenParseReason.NO_AMOUNT)
        if (mainAmount != null && (
                mergedHeaders.any { it.groupValues[1].toDoubleOrNull() != mainAmount } ||
                (bareMainText != null && bareMainText.toDoubleOrNull() != mainAmount) ||
                explicitAmounts.any { mainAmount != kotlin.math.abs(it) }))
            return ScreenParseResult(null, ScreenParseReason.INVALID_AMOUNT)
        val raw = (if (mspReceipt) mainAmount ?: explicitAmount else extractAmountFromScreen(fullText))
            ?: return ScreenParseResult(null, ScreenParseReason.NO_AMOUNT)
        if (!raw.isFinite()) return ScreenParseResult(null, ScreenParseReason.INVALID_AMOUNT)
        if (raw == 0.0) return ScreenParseResult(null, ScreenParseReason.ZERO_AMOUNT)
        val income = statusLines.any { Regex("""^(?:(?:当前状态|交易状态|支付状态)[：:]?\s*)?(?:退款成功|退款到账|收款成功|已收款|已到账)$""").matches(it) } ||
            (raw > 0 && (listOf("收入", "转入", "收款成功", "已收款", "已到账").any { it in statusLines } ||
                Regex("""(?:收支类型|交易类型)\s*[：:]?\s*收入""").containsMatchIn(fullText) ||
                Regex("""(?:^|\s)\+\d+\.\d{2}(?:\s|$)""").containsMatchIn(fullText)))
        val paymentSource = if (source == "京东" && fullText.contains("外卖")) "京东外卖" else source
        return ScreenParseResult(ScreenPayment(paymentSource, kotlin.math.abs(raw), extractMerchantFromScreen(statusLines), income, extractTransactionTime(fullText)), ScreenParseReason.MATCH)
    }

    private fun extractTransactionTime(text: String): Long? {
        val match = Regex("""(?:交易时间|付款时间|支付时间|转账时间|收款时间|到账时间)\s*[：:]?\s*(\d{4})[-年/](\d{1,2})[-月/](\d{1,2})日?\s+(\d{1,2}):(\d{2})(?::(\d{2}))?""").find(text) ?: return null
        return try {
            val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
            java.time.LocalDateTime.of(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: java.time.DateTimeException) { null }
    }

    private fun extractAmountFromScreen(text: String): Double? {
        // Prefer settled amounts to an original order price or promotional total.
        settledAmountPattern.find(text)?.let {
            val amount = it.groupValues[1].replace(",", "").toDoubleOrNull()
            // Zero is authoritative too: never recover an original order price.
            if (amount != null) return amount
        }
        // 1. 最优先：匹配 "支出XX元" 或 "收入XX元" 格式
        Regex("""(?:支出|收入)\s*([\d,]+\.?\d{0,2})\s*元""").find(text)?.let {
            val amount = it.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null && amount > 0) return amount
        }
        // 2. 匹配负数金额（如 -11.86）= 实际支付金额，优先于订单金额
        Regex("""(?:^|\s)([+-][\d,]+\.\d{2})(?:\s|$)""").find(text)?.let {
            val amount = it.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null) return amount
        }
        // 3. 匹配 ¥XX.XX
        Regex("""[¥￥]\s*(-?[\d,]+\.?\d{0,2})""").find(text)?.let {
            val amount = it.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null && kotlin.math.abs(amount) > 0) return amount
        }
        // 4. 匹配 "订单金额" / "支付金额" / "金额" 后面的数字（最低优先级）
        Regex("""(?:订单金额|支付金额|金额|转账金额)\s*[¥￥]?\s*([\d,]+\.?\d{0,2})""").find(text)?.let {
            val amount = it.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null && amount > 0) return amount
        }
        // 5. 匹配 XX.XX元 或 XX元
        Regex("""(-?[\d,]+\.?\d{0,2})\s*元""").find(text)?.let {
            val amount = it.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null && kotlin.math.abs(amount) > 0) return amount
        }
        // An arbitrary decimal may be a balance, discount, time, or identifier.
        return null
    }

    /**
     * 提取商户名
     */
    private fun extractMerchantFromScreen(texts: List<String>): String {
        for (i in texts.indices) {
            val text = texts[i]

            Regex("""付款给\s*(.+)""").find(text)?.let { return it.groupValues[1].trim() }
            Regex("""向(.+?)付款""").find(text)?.let { return it.groupValues[1].trim() }
            Regex("""(?:转账给|转给|发给)\s*(.+)""").find(text)?.let { return it.groupValues[1].trim() }

            Regex("""(?:收款方全称|收款方|收款方备注|商户全称|商户名称|商家|付款方)[：:]\s*(.+)""").find(text)?.let {
                val name = it.groupValues[1].trim()
                if (name.isNotEmpty() && name.length < 30) return name
            }

            if (text in listOf("商品", "商品说明", "付款说明", "商户", "商户名称", "收款方", "付款方", "来自", "店铺名称", "商家名称", "收款方全称", "收款方备注")) {
                if (i + 1 < texts.size) {
                    val next = texts[i + 1].trim()
                    if (next.isNotEmpty() && next.length < 30 && !next.contains("¥") && !next.contains("元")) {
                        return next
                    }
                }
            }
        }

        for (i in texts.indices) {
            if (texts[i].contains("商品说明") || texts[i].contains("交易说明")) {
                if (i + 1 < texts.size) {
                    val desc = texts[i + 1].trim()
                    if (desc.isNotEmpty() && desc.length < 30) return desc
                }
            }
        }

        return ""
    }

}
