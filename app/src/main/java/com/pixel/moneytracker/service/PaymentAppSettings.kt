package com.pixel.moneytracker.service

import android.content.Context

/** The same allowlist drives screen reading and its user controls. */
internal object PaymentAppSettings {
    val screenApps = linkedMapOf(
        "com.tencent.mm" to "微信",
        "com.eg.android.AlipayGphone" to "支付宝",
        "com.taobao.taobao" to "淘宝",
        "com.xunmeng.pinduoduo" to "拼多多",
        "com.jingdong.app.mall" to "京东",
        "com.sankuai.meituan" to "美团",
        "me.ele" to "饿了么"
    )
    private fun prefs(context: Context) = context.getSharedPreferences("payment_settings", Context.MODE_PRIVATE)
    fun enabled(context: Context, key: String = "enabled") = prefs(context).getBoolean(key, true)
    fun setEnabled(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }
}
