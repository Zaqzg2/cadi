package com.inventorysmartai.app.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.Constants
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.core.common.toDecimalOrNull
import com.inventorysmartai.app.domain.model.CommissionGroup
import com.inventorysmartai.app.domain.model.CommissionType
import com.inventorysmartai.app.domain.model.Goal
import com.inventorysmartai.app.domain.model.GoalAchievement
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.repository.GoalRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.usecase.CalculateGoalAchievementUseCase
import com.inventorysmartai.app.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One editable row in the form — a "group" in the spec's Group 1/2/3 language. */
data class GroupRow(
    val localId: String,
    val groupOrder: Int,
    val targetQuantity: String,
    val commissionType: CommissionType,
    val commissionValue: String
)

data class GoalFormData(
    val isNew: Boolean,
    val products: List<Product>,
    val selectedProductId: Long?,
    val groups: List<GroupRow>,
    val achievement: GoalAchievement?,
    /** صنف جديد له هدف قائم لهذا الشهر: الحفظ يحدّثه بدل أن يضيف هدفًا مكرّرًا. */
    val updatesExisting: Boolean,
    val isSaved: Boolean = false
) {
    /** لا حفظ بلا صنف، ولا هدف فارغ: لا بدّ من مجموعة واحدة على الأقل بكمية مستهدفة صحيحة. */
    val canSave: Boolean get() = selectedProductId != null && groups.any { it.targetQuantity.toDecimalOrNull() != null }
}

/**
 * نموذج هدف واحد. مع goalId يعدّل ذلك الهدف (الصنف ثابت)، وبدونه ينشئ هدفًا جديدًا: اختيار صنف له هدف في الشهر الحالي
 * يحمّل ذلك الهدف للتعديل، فلا يتكرر الصف نفسه في كل حفظ كما كان يحدث.
 */
@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val productRepository: ProductRepository,
    private val goalRepository: GoalRepository,
    private val calculateAchievement: CalculateGoalAchievementUseCase
) : ViewModel() {

    private val goalId: Long = savedStateHandle[Destination.GoalDetail.ARG_GOAL_ID] ?: Destination.GoalDetail.NEW_GOAL
    private val isNew = goalId <= 0L

    private val selectedProductId = MutableStateFlow<Long?>(null)
    private val groups = MutableStateFlow(defaultGroups())
    private val achievement = MutableStateFlow<GoalAchievement?>(null)
    private val products = MutableStateFlow<List<Product>>(emptyList())
    private val isSaved = MutableStateFlow(false)

    /** الهدف المخزَّن الذي يعرضه النموذج الآن (null = لا شيء محفوظ لهذا الصنف بعد). */
    private var currentGoal: Goal? = null

    /** لا يُعرض النموذج قبل وصول قائمة الأصناف، كي لا يومض «لا توجد أصناف» لحظة التحميل. */
    private var productsLoaded = false

    /** يمنع ضغطتَين متتاليتين على «حفظ» من إدخال هدفين قبل أن يعكس النموذج الهدف المحفوظ. */
    private var saving = false

    private val _uiState = MutableStateFlow<UiState<GoalFormData>>(UiState.Loading)
    val uiState: StateFlow<UiState<GoalFormData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            productRepository.observeProducts()
                .catch { _uiState.value = UiState.Error("تعذّر تحميل الأصناف") }
                .collect { list ->
                    products.value = list
                    productsLoaded = true
                    publish()
                }
        }

        val goalFlow: Flow<Goal?> =
            if (isNew) {
                selectedProductId.flatMapLatest { productId ->
                    if (productId == null) flowOf(null) else goalRepository.observeGoalForProduct(productId)
                }
            } else {
                goalRepository.observeGoal(goalId)
            }

        goalFlow
            .onEach { goal ->
                currentGoal = goal
                if (!isNew && goal != null) selectedProductId.value = goal.productId
                groups.value = goal?.groups
                    ?.sortedBy { it.groupOrder }
                    ?.map { GroupRow(it.id.toString(), it.groupOrder, plain(it.targetQuantity), it.commissionType, plain(it.commissionValue)) }
                    ?.ifEmpty { defaultGroups() }
                    ?: defaultGroups()
                achievement.value = goal?.let { calculateAchievement(it) }
                publish()
            }
            .launchIn(viewModelScope)
    }

    private fun publish() {
        if (!productsLoaded) return
        val goal = currentGoal
        val updatesExisting = isNew && goal != null && monthKey(goal.periodStart) == monthKey(System.currentTimeMillis())
        _uiState.value = UiState.Success(
            GoalFormData(isNew, products.value, selectedProductId.value, groups.value, achievement.value, updatesExisting, isSaved.value)
        )
    }

    fun onProductSelected(productId: Long) {
        selectedProductId.value = productId
        isSaved.value = false
        publish()
    }

    fun onAddGroup() {
        val next = (groups.value.maxOfOrNull { it.groupOrder } ?: 0) + 1
        groups.value = groups.value + GroupRow(localId = "new_$next", groupOrder = next, targetQuantity = "", commissionType = CommissionType.FIXED, commissionValue = "")
        publish()
    }

    fun onRemoveGroup(localId: String) {
        groups.value = groups.value.filterNot { it.localId == localId }
        publish()
    }

    fun onTargetChange(localId: String, value: String) {
        groups.value = groups.value.map { if (it.localId == localId) it.copy(targetQuantity = value) else it }
        publish()
    }

    fun onCommissionValueChange(localId: String, value: String) {
        groups.value = groups.value.map { if (it.localId == localId) it.copy(commissionValue = value) else it }
        publish()
    }

    fun onCommissionTypeChange(localId: String, type: CommissionType) {
        groups.value = groups.value.map { if (it.localId == localId) it.copy(commissionType = type) else it }
        publish()
    }

    fun onSave() {
        val productId = selectedProductId.value ?: return
        val domainGroups = groups.value
            .mapNotNull { row ->
                val target = row.targetQuantity.toDecimalOrNull() ?: return@mapNotNull null
                Triple(target, row.commissionType, row.commissionValue.toDecimalOrNull() ?: 0.0)
            }
            .mapIndexed { index, (target, type, commission) ->
                CommissionGroup(groupOrder = index + 1, targetQuantity = target, commissionType = type, commissionValue = commission)
            }
        if (domainGroups.isEmpty() || saving) return
        saving = true

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // يُحدَّث الهدف القائم في مكانه (تعديل، أو صنف جديد له هدف في الشهر نفسه)؛ وإلا يُنشأ هدف لهذا الشهر.
            val existing = currentGoal?.takeIf { !isNew || monthKey(it.periodStart) == monthKey(now) }
            // تعديل الأهداف لا يغيّر فترة الهدف القائم؛ أما الهدف الجديد فيأخذ الشهر الحالي بحدود دقيقة (مثل الاستيراد).
            val (periodStart, periodEnd) = existing?.let { it.periodStart to it.periodEnd } ?: monthBounds(now)
            goalRepository.saveGoal(
                Goal(
                    id = existing?.id ?: 0L,
                    productId = productId,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    notes = existing?.notes,
                    groups = domainGroups
                )
            )
            isSaved.value = true
            publish()
        }
    }

    private fun defaultGroups(): List<GroupRow> =
        (1..Constants.DEFAULT_GOAL_GROUP_COUNT).map {
            GroupRow(localId = "default_$it", groupOrder = it, targetQuantity = "", commissionType = CommissionType.FIXED, commissionValue = "")
        }

    /** بلا فاصلة آلاف ولا «.0» زائدة: النص في الحقل يجب أن يُقرأ رقمًا كما هو. */
    private fun plain(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
