package com.pixel.moneytracker.util

import com.pixel.moneytracker.data.entity.RecordType

/**
 * Best-effort category for imported bills.
 *
 * Amount, direction and time always come from the exported file; only the label is guessed so a
 * freshly imported history is not a wall of "其他". Rules are ordered: the first keyword hit wins,
 * which lets specific buckets (咖啡 / 零食 / 服饰) take precedence over the broad 购物 fallback.
 */
internal object BillCategoryRules {
    private data class Rule(val category: String, val keywords: List<String>)

    private val expenseRules = listOf(
        Rule("咖啡", listOf("咖啡", "星巴克", "瑞幸", "库迪", "manner", "奶茶", "喜茶", "茶百道", "蜜雪", "奈雪")),
        Rule("零食", listOf("零食", "良品铺子", "三只松鼠", "百草味", "来伊份")),
        Rule("餐饮", listOf("餐饮", "美团", "饿了么", "外卖", "餐厅", "饭店", "小吃", "快餐", "食堂", "火锅",
            "烧烤", "面馆", "肯德基", "麦当劳", "汉堡", "必胜客", "海底捞", "早餐", "午餐", "晚餐")),
        Rule("交通", listOf("滴滴", "打车", "出租车", "地铁", "公交", "12306", "铁路", "火车", "高铁", "加油",
            "石化", "石油", "停车", "高速", "单车", "哈啰", "青桔", "曹操")),
        Rule("旅行", listOf("酒店", "民宿", "携程", "飞猪", "去哪儿", "景区", "门票", "旅行", "旅游", "机票",
            "航空", "airbnb")),
        Rule("居住", listOf("房租", "物业", "水费", "电费", "燃气", "暖气", "水电", "租金", "公寓")),
        Rule("通讯", listOf("话费", "流量", "宽带", "中国移动", "中国联通", "中国电信", "广电")),
        Rule("医疗", listOf("医院", "药房", "药店", "诊所", "门诊", "挂号", "体检", "口腔", "牙科")),
        Rule("教育", listOf("学费", "培训", "课程", "书店", "图书", "网校", "教育", "考试", "文具")),
        Rule("娱乐", listOf("电影", "影院", "游戏", "steam", "腾讯视频", "爱奇艺", "优酷", "哔哩", "bilibili",
            "会员", "ktv", "演出", "剧本杀", "网吧")),
        Rule("运动", listOf("健身", "运动", "瑜伽", "游泳", "滑雪", "球场", "球馆")),
        Rule("宠物", listOf("宠物", "猫粮", "狗粮", "猫砂", "宠物医院")),
        Rule("美妆", listOf("化妆", "美妆", "护肤", "屈臣氏", "丝芙兰", "口红", "面膜")),
        Rule("服饰", listOf("服饰", "服装", "优衣库", "内衣", "鞋", "袜", "帽子", "zara", "衬衫", "裤")),
        Rule("数码", listOf("数码", "电脑", "手机", "耳机", "相机", "小米之家", "apple", "华为", "显示器", "键盘")),
        Rule("社交", listOf("红包", "群收款", "聚会", "聚餐", "份子")),
        Rule("购物", listOf("淘宝", "天猫", "京东", "拼多多", "唯品会", "苏宁", "商城", "超市", "便利店",
            "沃尔玛", "永辉", "盒马", "山姆", "名创优品")),
    )

    private val incomeRules = listOf(
        Rule("工资", listOf("工资", "薪资", "薪酬", "代发")),
        Rule("奖金", listOf("奖金", "绩效", "年终", "补贴")),
        Rule("兼职", listOf("兼职", "劳务", "稿费", "佣金")),
        Rule("理财", listOf("理财", "利息", "基金", "分红", "收益", "余额宝", "零钱通")),
        Rule("红包", listOf("红包")),
        Rule("中奖", listOf("中奖", "抽奖")),
    )

    private fun normalize(text: String): String = text.lowercase().replace(" ", "").replace("　", "")

    /**
     * @param status the file's transaction status, e.g. 支付成功 / 退款成功
     * @param texts transaction type, counterparty, product, note — any order
     */
    fun infer(type: RecordType, status: String, vararg texts: String): String {
        if ("退款" in status) return "退款"
        val haystack = texts.joinToString("|") { normalize(it) }
        if (haystack.isBlank()) return "其他"
        val rules = if (type == RecordType.INCOME) incomeRules + expenseRules else expenseRules
        return rules.firstOrNull { rule -> rule.keywords.any { haystack.contains(it) } }?.category ?: "其他"
    }
}
