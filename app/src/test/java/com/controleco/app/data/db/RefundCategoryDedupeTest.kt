package com.controleco.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「退款」收入分类去重的判定规则（纯逻辑，不碰 Room）。
 *
 * 真实 bug：Application 里异步建种子（含「退款」）与 ViewModel.init 里
 * 「不存在就补回」并发执行，撞车后库里出现两条「退款」，且两条都被
 * [CategoryRules.deleteError] 锁死删不掉。
 *
 * 修复时把两件事收进 DefaultData.seedAndRepair 串行执行，并在启动时去重。
 * 这里锁住去重时"选谁留下"的判定：
 * - 只认**收入**侧、名字 trim 后等于「退款」的分类；
 * - 保留 **id 最小**的那条（最早创建，历史流水大多挂在它上面）。
 */
class RefundCategoryDedupeTest {

    private fun category(id: Long, name: String, type: TxType) =
        CategoryEntity(id = id, name = name, type = type)

    /** 与 DefaultData.dedupeRefundCategory 内部同一套筛选/排序规则 */
    private fun pickKeep(all: List<CategoryEntity>): CategoryEntity? =
        all.filter { it.type == TxType.INCOME && it.name.trim() == RefundCategory.NAME }
            .minByOrNull { it.id }

    @Test
    fun `两条退款_保留id最小的那条`() {
        val keep = pickKeep(
            listOf(
                category(7, RefundCategory.NAME, TxType.INCOME),
                category(3, RefundCategory.NAME, TxType.INCOME),
            ),
        )
        assertEquals(3L, keep?.id)
    }

    @Test
    fun `带空格的退款也算同一条_按trim判定`() {
        val keep = pickKeep(
            listOf(
                category(9, " 退款 ", TxType.INCOME),
                category(5, RefundCategory.NAME, TxType.INCOME),
            ),
        )
        assertEquals(5L, keep?.id)
    }

    @Test
    fun `支出侧同名分类不参与去重`() {
        // 支出里叫「退款」不是保留名，不该被当成重复项删掉
        val keep = pickKeep(
            listOf(
                category(2, RefundCategory.NAME, TxType.EXPENSE),
                category(4, RefundCategory.NAME, TxType.INCOME),
            ),
        )
        assertEquals(4L, keep?.id)
    }

    @Test
    fun `没有退款分类时返回null_调用方会补一条`() {
        assertNull(pickKeep(listOf(category(1, "工资", TxType.INCOME))))
    }

    @Test
    fun `只有一条时就是它自己`() {
        val keep = pickKeep(listOf(category(6, RefundCategory.NAME, TxType.INCOME)))
        assertEquals(6L, keep?.id)
    }

    @Test
    fun `退款分类仍然不可删除_去重后不会被用户误删`() {
        val refund = category(1, RefundCategory.NAME, TxType.INCOME)
        assertEquals("「退款」不能删除", CategoryRules.deleteError(refund))
    }
}
