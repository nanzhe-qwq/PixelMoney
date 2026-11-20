package com.pixel.moneytracker.util

import com.pixel.moneytracker.data.entity.RecordType
import org.junit.Assert.assertEquals
import org.junit.Test

class BillCategoryRulesTest {
    @Test fun expenseKeywordsPickSpecificBucketBeforeGenericShopping() {
        assertEquals("餐饮", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功", "商户消费", "美团外卖", "午餐"))
        assertEquals("咖啡", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功", "商户消费", "星巴克", "拿铁"))
        assertEquals("交通", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功", "商户消费", "滴滴出行", "快车"))
        assertEquals("购物", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功", "商户消费", "淘宝", "日用品"))
        assertEquals("医疗", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功", "商户消费", "市第一医院", "挂号费"))
        assertEquals("通讯", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功", "商户消费", "中国移动", "话费"))
    }

    @Test fun incomeKeywordsFollowTheirOwnTable() {
        assertEquals("工资", BillCategoryRules.infer(RecordType.INCOME, "已收钱", "转账", "某某公司", "8月工资"))
        assertEquals("理财", BillCategoryRules.infer(RecordType.INCOME, "已收钱", "转账", "余额宝", "收益"))
    }

    @Test fun refundStatusAlwaysWinsOverKeywords() {
        assertEquals("退款", BillCategoryRules.infer(RecordType.INCOME, "退款成功", "商户消费", "美团外卖", ""))
        assertEquals("退款", BillCategoryRules.infer(RecordType.EXPENSE, "已退款", "淘宝", "女装", ""))
    }

    @Test fun unknownOrBlankInputFallsBackToOther() {
        assertEquals("其他", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功", "商户消费", "张三", "/"))
        assertEquals("其他", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功"))
        assertEquals("其他", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功", "   ", ""))
    }

    @Test fun matchingIgnoresCaseAndSpacing() {
        assertEquals("数码", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功", "Apple Store", "Mac"))
        assertEquals("咖啡", BillCategoryRules.infer(RecordType.EXPENSE, "支付成功", "MANNER COFFEE", ""))
    }
}
