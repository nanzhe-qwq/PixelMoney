package com.pixel.moneytracker.service

/** Pure notification parsing; no Android dependencies or side effects. */
internal object PaymentNotificationParser {
    data class ParsedPayment(
        val amount: Double,
        val source: String,
        val title: String,
        val content: String,
        val note: String,
        val isIncome: Boolean = false
    )

        // ====== 支付类 App 包名 ======
        private const val ALIPAY = "com.eg.android.AlipayGphone"
        private const val WECHAT = "com.tencent.mm"
        private const val UNIONPAY = "com.unionpay"
        private const val HUAWEI_WALLET = "com.huawei.wallet"
        private const val XIAOMI_WALLET = "com.miui.tsmclient"
        private const val SAMSUNG_PAY = "com.samsung.android.spay"
        private const val NFC_SERVICE = "com.android.nfc"

        // ====== 外卖/打车/购物类 App ======
        private const val MEITUAN = "com.sankuai.meituan"           // 美团
        private const val ELEME = "me.ele"                          // 饿了么
        private const val TAOBAO = "com.taobao.taobao"              // 淘宝
        private const val TMALL = "com.tmall.wireless"              // 天猫
        private const val JD = "com.jingdong.app.mall"              // 京东
        private const val PINDUODUO = "com.xunmeng.pinduoduo"      // 拼多多
        private const val DIDI = "com.sdu.didi.psnger"              // 滴滴出行
        private const val GAODE = "com.autonavi.minimap"            // 高德地图
        private const val DAZHONG = "com.dianping.v1"               // 大众点评
        private const val CTRIP = "ctrip.android.view"              // 携程
        private const val SHENZHOU = "com.ichinait.gbpassenger"     // 神州专车
        private const val CAOCAO = "cn.caocao.passenger"            // 曹操出行
        private const val T3 = "com.t3go.driver"                    // T3出行

        // ====== 银行 App ======
        private const val ICBC = "com.icbc"                         // 工商银行
        private const val CCB = "com.chinamworld.bocmbci"           // 建设银行
        private const val ABC = "com.android.bankabc"               // 农业银行
        private const val BOC = "com.chinamworld.main"              // 中国银行
        private const val CMB = "cmb.pb"                            // 招商银行

        // ====== 支付宝关键词 ======
        private val ALIPAY_KEYWORDS = listOf(
            "付款", "支付", "消费", "支出", "已付款", "扣款", "成功支付",
            "成功付款", "收款", "转账", "已转出", "花呗", "余额宝",
            "碰一碰", "NFC", "乘车", "地铁", "公交", "付款成功",
            "向.*付款", "自动扣款", "代扣", "还款", "到账", "转入", "退款"
        )

        // ====== 微信关键词 ======
        private val WECHAT_KEYWORDS = listOf(
            "微信支付", "支付成功", "付款", "消费", "已支付",
            "转账", "已转出", "零钱", "扣款", "自动扣费",
            "付款成功", "收款", "红包", "商户消费",
            "乘车码", "地铁", "公交", "到账", "转入", "退款"
        )

        // ====== 银行/云闪付/钱包关键词 ======
        private val BANK_KEYWORDS = listOf(
            "消费", "支出", "扣款", "支付", "付款", "交易",
            "POS", "刷卡", "闪付", "NFC", "碰一碰",
            "转入", "转出", "转账", "成功", "完成", "到账", "收款"
        )

        // ====== 外卖关键词 ======
        private val TAKEOUT_KEYWORDS = listOf(
            "下单成功", "订单", "已支付", "支付成功", "外卖",
            "配送", "送达", "骑手", "取餐"
        )

        // ====== 购物关键词 ======
        private val SHOPPING_KEYWORDS = listOf(
            "已付款", "订单", "发货", "已发货", "支付成功",
            "待付款", "已下单", "购买成功", "交易成功"
        )

        // ====== 打车关键词 ======
        private val TAXI_KEYWORDS = listOf(
            "行程", "已支付", "车费", "订单完成", "已到账",
            "打车", "用车", "快车", "专车", "代驾"
        )

        // ====== 需要排除的非支付通知 ======
        private val EXCLUDE_KEYWORDS = listOf(
            "待付", "待支付", "未支付", "未付款", "未收款", "未到账", "未转入", "待到账", "待入账", "到账中", "待收款", "尚未", "失败", "取消", "关闭",
            "配送", "发货", "送达", "骑手", "取餐", "返现", "可领", "领取", "成功前", "成功后可",
            "验证码", "登录", "注册", "安全", "活动", "红包来了",
            "优惠", "推荐", "升级", "版本", "更新", "生日",
            "好友", "聊天", "消息", "朋友圈", "群聊",
            "快递", "物流", "外卖已送达", "评价", "签到",
            "积分", "抽奖", "限时", "秒杀", "拼团"
        )

    private fun isCompletedIncome(text: String): Boolean =
        Regex("收款成功|成功收款|已收款|转入成功|已转入|到账成功|已到账|退款成功|退款到账").containsMatchIn(text)

    fun parse(packageName: String, title: String, content: String, bigText: String = "", subText: String = ""): ParsedPayment? {
        val fullText = "$title\n$content\n$bigText\n$subText"
        if (!Regex("支付成功|付款成功|成功支付|成功付款|已支付|已付款|扣款成功|扣费成功|消费成功|转账成功|已转出").containsMatchIn(fullText) && !isCompletedIncome(fullText)) return null

        // 先排除非支付通知
        if (EXCLUDE_KEYWORDS.any { fullText.contains(it) }) {
            return null
        }

        return when (packageName) {
            // 支付类
            ALIPAY -> handleAlipay(fullText, title, content)
            WECHAT -> handleWechat(fullText, title, content)
            UNIONPAY -> handleBank(fullText, title, content, "云闪付")
            HUAWEI_WALLET -> handleBank(fullText, title, content, "华为钱包")
            XIAOMI_WALLET -> handleBank(fullText, title, content, "小米钱包")
            SAMSUNG_PAY -> handleBank(fullText, title, content, "Samsung Pay")
            NFC_SERVICE -> handleNfc(fullText, title, content)

            // 外卖类
            MEITUAN -> handleTakeout(fullText, title, content, "美团")
            ELEME -> handleTakeout(fullText, title, content, "饿了么")
            DAZHONG -> handleTakeout(fullText, title, content, "大众点评")

            // 购物类
            TAOBAO -> handleShopping(fullText, title, content, "淘宝")
            TMALL -> handleShopping(fullText, title, content, "天猫")
            JD -> handleShopping(fullText, title, content, "京东")
            PINDUODUO -> handleShopping(fullText, title, content, "拼多多")

            // 打车类
            DIDI -> handleTaxi(fullText, title, content, "滴滴")
            GAODE -> handleTaxiOrNav(fullText, title, content, "高德")
            SHENZHOU -> handleTaxi(fullText, title, content, "神州专车")
            CAOCAO -> handleTaxi(fullText, title, content, "曹操出行")
            T3 -> handleTaxi(fullText, title, content, "T3出行")

            // 旅行类
            CTRIP -> handleTravel(fullText, title, content, "携程")

            // 银行类
            ICBC, CCB, ABC, BOC, CMB -> handleBankApp(fullText, title, content, packageName)
            else -> null
        }
    }

    /**
     * 支付宝支付处理
     */
    private fun handleAlipay(text: String, title: String, content: String): ParsedPayment? {
        val isPayment = ALIPAY_KEYWORDS.any { text.contains(it) }
        if (!isPayment) return null

        val amount = extractAmount(text) ?: return null
        if (!amount.isFinite() || amount <= 0) return null

        val isIncome = isCompletedIncome(text)
        val source = if (isIncome) "支付宝(收入)" else "支付宝"
        val note = extractMerchantName(text) ?: extractDescription(text) ?: ""

        return ParsedPayment(amount, source, title, content, note, isIncome)
    }

    /**
     * 微信支付处理
     */
    private fun handleWechat(text: String, title: String, content: String): ParsedPayment? {
        val isPayment = WECHAT_KEYWORDS.any { text.contains(it) }
        if (!isPayment) return null

        val amount = extractAmount(text) ?: return null
        if (!amount.isFinite() || amount <= 0) return null

        val isIncome = isCompletedIncome(text)
        val source = if (isIncome) "微信(收入)" else "微信"
        val note = extractMerchantName(text) ?: extractDescription(text) ?: ""

        return ParsedPayment(amount, source, title, content, note, isIncome)
    }

    /**
     * 银行/云闪付/钱包处理
     */
    private fun handleBank(text: String, title: String, content: String, appName: String): ParsedPayment? {
        val isPayment = BANK_KEYWORDS.any { text.contains(it) }
        if (!isPayment) return null

        val amount = extractAmount(text) ?: return null
        if (!amount.isFinite() || amount <= 0) return null

        val note = extractMerchantName(text) ?: extractDescription(text) ?: ""
        val isIncome = isCompletedIncome(text)
        return ParsedPayment(amount, if (isIncome) "$appName(收入)" else appName, title, content, note, isIncome)
    }

    /**
     * 银行 App 通知处理
     */
    private fun handleBankApp(text: String, title: String, content: String, packageName: String): ParsedPayment? {
        val isPayment = BANK_KEYWORDS.any { text.contains(it) }
        if (!isPayment) return null

        val amount = extractAmount(text) ?: return null
        if (!amount.isFinite() || amount <= 0) return null

        val bankName = when (packageName) {
            ICBC -> "工商银行"
            CCB -> "建设银行"
            ABC -> "农业银行"
            BOC -> "中国银行"
            CMB -> "招商银行"
            else -> "银行"
        }
        val note = extractMerchantName(text) ?: extractDescription(text) ?: ""
        val isIncome = isCompletedIncome(text)
        return ParsedPayment(amount, if (isIncome) "$bankName(收入)" else bankName, title, content, note, isIncome)
    }

    /**
     * 外卖类处理
     */
    private fun handleTakeout(text: String, title: String, content: String, appName: String): ParsedPayment? {
        val isPayment = TAKEOUT_KEYWORDS.any { text.contains(it) }
        if (!isPayment) return null

        val amount = extractAmount(text) ?: return null
        if (!amount.isFinite() || amount <= 0) return null

        // 提取外卖备注：餐厅名、菜品名等
        val note = extractTakeoutNote(text) ?: extractMerchantName(text) ?: ""
        return ParsedPayment(amount, appName, title, content, note, isCompletedIncome(text))
    }

    /**
     * 购物类处理
     */
    private fun handleShopping(text: String, title: String, content: String, appName: String): ParsedPayment? {
        val isPayment = SHOPPING_KEYWORDS.any { text.contains(it) }
        if (!isPayment) return null

        val amount = extractAmount(text) ?: return null
        if (!amount.isFinite() || amount <= 0) return null

        // 提取购物备注：商品名、店铺名等
        val note = extractShoppingNote(text) ?: extractMerchantName(text) ?: ""
        return ParsedPayment(amount, appName, title, content, note, isCompletedIncome(text))
    }

    /**
     * 打车类处理
     */
    private fun handleTaxi(text: String, title: String, content: String, appName: String): ParsedPayment? {
        val isPayment = TAXI_KEYWORDS.any { text.contains(it) }
        if (!isPayment) return null

        val amount = extractAmount(text) ?: return null
        if (!amount.isFinite() || amount <= 0) return null

        // 提取打车备注：起终点等
        val note = extractTaxiNote(text) ?: ""
        return ParsedPayment(amount, appName, title, content, note, isCompletedIncome(text))
    }

    /**
     * 高德地图处理（可能是打车或导航）
     */
    private fun handleTaxiOrNav(text: String, title: String, content: String, appName: String): ParsedPayment? {
        val isPayment = TAXI_KEYWORDS.any { text.contains(it) }
        if (!isPayment) return null

        val amount = extractAmount(text) ?: return null
        if (!amount.isFinite() || amount <= 0) return null

        val note = extractTaxiNote(text) ?: ""
        return ParsedPayment(amount, "${appName}打车", title, content, note)
    }

    /**
     * 旅行类处理
     */
    private fun handleTravel(text: String, title: String, content: String, appName: String): ParsedPayment? {
        val amount = extractAmount(text) ?: return null
        if (!amount.isFinite() || amount <= 0) return null

        val note = extractTravelNote(text) ?: ""
        return ParsedPayment(amount, appName, title, content, note, isCompletedIncome(text))
    }

    /**
     * NFC 系统服务通知处理
     */
    private fun handleNfc(text: String, title: String, content: String): ParsedPayment? {
        val amount = extractAmount(text) ?: return null
        if (!amount.isFinite() || amount <= 0) return null

        return ParsedPayment(amount, "NFC", title, content, "")
    }

    // ====== 备注提取方法 ======

    /**
     * 提取商户名
     * 匹配模式：向XXX付款、XXX商户、在XXX消费等
     */
    private fun extractMerchantName(text: String): String? {
        // 向XXX付款
        Regex("""向(.+?)付款""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        // XXX商户消费
        Regex("""(.{2,15}?)(?:商户|店铺|商家)""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        // 在XXX消费/支付
        Regex("""在(.{2,20}?)(?:消费|支付|付款|购买)""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        // 商户：XXX / 商家：XXX
        Regex("""(?:商户|商家|店铺)[：:]\s*(.+?)(?:\s|$|,)""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        return null
    }

    /**
     * 提取通用描述
     */
    private fun extractDescription(text: String): String? {
        // 提取引号内的内容
        Regex("""[""「](.+?)[""」]""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        return null
    }

    /**
     * 提取外卖备注
     * 匹配：餐厅名、菜品名、订单号等
     */
    private fun extractTakeoutNote(text: String): String? {
        // 餐厅/店铺名
        Regex("""(.{2,15}?)(?:餐厅|饭店|小吃|奶茶|咖啡|烧烤|火锅)""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        // XXX订单
        Regex("""(.{2,20}?)订单""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        return null
    }

    /**
     * 提取购物备注
     * 匹配：商品名、店铺名等
     */
    private fun extractShoppingNote(text: String): String? {
        // XXX旗舰店 / XXX专卖店
        Regex("""(.{2,15}?)(?:旗舰店|专卖店|专营店|官方)""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        // 商品：XXX / 订单：XXX
        Regex("""(?:商品|订单)[：:]\s*(.+?)(?:\s|$|,)""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        return null
    }

    /**
     * 提取打车备注
     * 匹配：起点->终点、行程信息等
     */
    private fun extractTaxiNote(text: String): String? {
        // XXX -> XXX / XXX至XXX / XXX到XXX
        Regex("""(.{2,10}?)(?:->|→|至|到|去)(.{2,10}?)""").find(text)?.let {
            return "${it.groupValues[1].trim()}→${it.groupValues[2].trim()}"
        }
        // 行程：XXX
        Regex("""行程[：:]\s*(.+?)(?:\s|$|,)""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        return null
    }

    /**
     * 提取旅行备注
     * 匹配：航班号、酒店名、景点等
     */
    private fun extractTravelNote(text: String): String? {
        // 航班号：CA1234 / MU5678
        Regex("""([A-Z]{2}\d{3,4})""").find(text)?.let {
            return "航班${it.groupValues[1]}"
        }
        // XXX酒店 / XXX宾馆
        Regex("""(.{2,15}?)(?:酒店|宾馆|民宿|客栈)""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        // XXX景区 / XXX门票
        Regex("""(.{2,15}?)(?:景区|门票|景点|公园)""").find(text)?.let {
            return it.groupValues[1].trim()
        }
        return null
    }

    /**
     * 从文本中提取金额
     * 只接受明确的金额字段、货币符号或元单位，不猜测裸数字。
     */
    private fun extractAmount(text: String): Double? {
        // 实付字段优先于原价、余额等参考金额。
        Regex("""(?:实际付款|实际支付|实付)(?:金额)?\s*[：:]?\s*[¥￥]?\s*(\d[\d,]*(?:\.\d{1,2})?)(?![\d.])""").find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        // 明确的交易金额字段次之，仍优先于余额/原价中的货币符号。
        Regex("""(?:支付|付款|消费|扣款|扣费|收款|转账|到账|转入|退款)(?:成功)?(?:金额)?[ \t]*[：:]?[ \t]*[¥￥]?[ \t]*(\d[\d,]*(?:\.\d{1,2})?)(?![\d.])""").find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        // 1. 优先匹配 ¥ / ￥ 符号后面的数字
        Regex("""[¥￥]\s*(\d[\d,]*(?:\.\d{1,2})?)(?![\d.])""").find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        // 2. 匹配 "XX元" 格式
        Regex("""(\d[\d,]*(?:\.\d{1,2})?)(?![\d.])\s*元""").find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        // 3. 匹配 "金额XX" / "支付XX" / "扣款XX" 等
        Regex("""(?:金额|支付|付款|消费|扣款|收款|转账|交易)\s*[：:]?\s*(\d[\d,]*(?:\.\d{1,2})?)(?![\d.])""").find(text)?.let {
            return it.groupValues[1].replace(",", "").toDoubleOrNull()
        }

        return null
    }

}
