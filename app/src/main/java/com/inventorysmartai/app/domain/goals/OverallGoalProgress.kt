package com.inventorysmartai.app.domain.goals

/** هدف مختزل إلى ما تحتاجه النسبة العامة: صنفه وفترته ومجموع كمياته المستهدفة (مجموع كل المجموعات). */
data class GoalTargetSnapshot(
    val id: Long,
    val productId: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val targetQuantity: Double
)

/**
 * منطق «نسبة تحقيق الأهداف» في الرئيسية (دوال صرفة قابلة للاختبار).
 *
 * كانت النسبة تجمع أهداف **كل** الأشهر وكل صفوف الهدف المكرَّرة (النموذج القديم كان يضيف صفًّا جديدًا في كل حفظ)،
 * فتختلط أرقام شهر مضى بالشهر الحالي. الآن: أهداف الفترة التي تقع فيها اللحظة الحالية فقط، وصفّ واحد لكل صنف.
 */
object OverallGoalProgress {

    /** الأهداف الجارية في [now]، صفّ واحد لكل صنف (الأحدث = الأكبر id). */
    fun currentGoals(goals: List<GoalTargetSnapshot>, now: Long): List<GoalTargetSnapshot> =
        goals
            .filter { now in it.periodStart..it.periodEnd }
            .groupBy { it.productId }
            .values
            .map { rows -> rows.maxBy { it.id } }

    /** المحقق ÷ المستهدف من 0 إلى 100، و0 حين لا يوجد مستهدف (لا قسمة على صفر). */
    fun percent(totalTarget: Double, totalAchieved: Double): Double =
        if (totalTarget <= 0.0) 0.0 else (totalAchieved / totalTarget * 100).coerceIn(0.0, 100.0)
}
