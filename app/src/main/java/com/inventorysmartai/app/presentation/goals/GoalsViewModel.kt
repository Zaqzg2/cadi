package com.inventorysmartai.app.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.common.Constants
import com.inventorysmartai.app.core.common.UiState
import com.inventorysmartai.app.domain.model.CommissionGroup
import com.inventorysmartai.app.domain.model.CommissionType
import com.inventorysmartai.app.domain.model.Goal
import com.inventorysmartai.app.domain.model.GoalAchievement
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.repository.GoalRepository
import com.inventorysmartai.app.domain.repository.ProductRepository
import com.inventorysmartai.app.domain.usecase.CalculateGoalAchievementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** One editable row in the UI — a "group" in the spec's Group 1/2/3 language. */
data class GroupRow(
    val localId: String,
    val groupOrder: Int,
    val targetQuantity: String,
    val commissionType: CommissionType,
    val commissionValue: String
)

data class GoalsScreenData(
    val products: List<Product>,
    val selectedProductId: Long?,
    val groups: List<GroupRow>,
    val achievement: GoalAchievement?,
    val isSaved: Boolean = false
)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val goalRepository: GoalRepository,
    private val calculateAchievement: CalculateGoalAchievementUseCase
) : ViewModel() {

    private val selectedProductId = MutableStateFlow<Long?>(null)
    private val groups = MutableStateFlow(defaultGroups())
    private val achievement = MutableStateFlow<GoalAchievement?>(null)
    private val products = MutableStateFlow<List<Product>>(emptyList())
    private val isSaved = MutableStateFlow(false)

    private val _uiState = MutableStateFlow<UiState<GoalsScreenData>>(UiState.Loading)
    val uiState: StateFlow<UiState<GoalsScreenData>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            productRepository.observeProducts()
                .catch { _uiState.value = UiState.Error("تعذّر تحميل الأصناف") }
                .collect { list ->
                    products.value = list
                    if (selectedProductId.value == null && list.isNotEmpty()) {
                        onProductSelected(list.first().id)
                    } else {
                        publish()
                    }
                }
        }

        selectedProductId
            .distinctUntilChanged()
            .flatMapLatest { productId ->
                if (productId == null) flowOf(null)
                else goalRepository.observeGoalForProduct(productId)
            }
            .onEach { goal ->
                groups.value = goal?.groups
                    ?.sortedBy { it.groupOrder }
                    ?.map { GroupRow(it.id.toString(), it.groupOrder, it.targetQuantity.toString(), it.commissionType, it.commissionValue.toString()) }
                    ?.ifEmpty { defaultGroups() }
                    ?: defaultGroups()
                achievement.value = goal?.let { calculateAchievement(it) }
                publish()
            }
            .launchIn(viewModelScope)
    }

    private fun publish() {
        _uiState.value = UiState.Success(
            GoalsScreenData(products.value, selectedProductId.value, groups.value, achievement.value, isSaved.value)
        )
    }

    fun onProductSelected(productId: Long) {
        selectedProductId.value = productId
        isSaved.value = false
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
        viewModelScope.launch {
            val now = Calendar.getInstance()
            val periodStart = now.apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            val periodEnd = now.apply { add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1) }.timeInMillis

            val domainGroups = groups.value.mapIndexedNotNull { index, row ->
                val target = row.targetQuantity.toDoubleOrNull() ?: return@mapIndexedNotNull null
                val commission = row.commissionValue.toDoubleOrNull() ?: 0.0
                CommissionGroup(
                    groupOrder = index + 1,
                    targetQuantity = target,
                    commissionType = row.commissionType,
                    commissionValue = commission
                )
            }
            goalRepository.saveGoal(
                Goal(
                    productId = productId,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
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
}
