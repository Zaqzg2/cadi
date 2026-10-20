package com.inventorysmartai.app.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.Goal
import com.inventorysmartai.app.domain.model.GoalAchievement
import com.inventorysmartai.app.domain.repository.GoalRepository
import com.inventorysmartai.app.domain.usecase.CalculateGoalAchievementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** بطاقة في قائمة الأهداف: الهدف وإنجازه المحسوب من المبيعات الفعلية. */
data class GoalListItem(val goal: Goal, val achievement: GoalAchievement)

/**
 * قائمة الأهداف — نفس نمط قائمة الجرد (بحث + بطاقات + حذف بتأكيد). التبويب يبقى حيًّا وهو مخفي، لذلك الحالة تُجمَع
 * بـ WhileSubscribed: كلما عاد المستخدم إلى الصفحة أُعيد حساب الإنجاز من المبيعات بدل عرض أرقام قديمة.
 */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val calculateAchievement: CalculateGoalAchievementUseCase
) : ViewModel() {

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** كل الصفوف المخزّنة (بما فيها المكرّرة القديمة): حذف الهدف يزيل صفوفه كلها معًا فلا يعود صفّ قديم للظهور. */
    private var allGoals: List<Goal> = emptyList()

    val uiState: StateFlow<UiState<List<GoalListItem>>> =
        combine(goalRepository.observeGoals().onEach { allGoals = it }, query) { goals, q -> goals to q }
            .map { (goals, q) -> toState(goals, q) }
            .catch { e -> emit(UiState.Error(e.message ?: "تعذّر تحميل الأهداف")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private suspend fun toState(goals: List<Goal>, rawQuery: String): UiState<List<GoalListItem>> {
        val needle = rawQuery.trim().lowercase()
        val items = goals.latestPerProductMonth()
            .filter { needle.isEmpty() || it.productName.orEmpty().lowercase().contains(needle) }
            .map { goal -> GoalListItem(goal, calculateAchievement(goal)) }
        return if (items.isEmpty()) UiState.Empty("لا توجد أهداف بعد") else UiState.Success(items)
    }

    fun onQueryChange(value: String) { query.value = value }
    fun onMessageShown() { _message.value = null }

    fun delete(item: GoalListItem) {
        val sameGoal = allGoals.filter {
            it.productId == item.goal.productId && monthKey(it.periodStart) == monthKey(item.goal.periodStart)
        }.ifEmpty { listOf(item.goal) }
        viewModelScope.launch {
            runCatching { sameGoal.forEach { goalRepository.deleteGoal(it.id) } }
                .onSuccess { _message.value = "تم حذف الهدف" }
                .onFailure { _message.value = it.message ?: "تعذّر الحذف" }
        }
    }
}
