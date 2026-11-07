package com.inventorysmartai.app.presentation.settings.alerts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.data.alerts.StockAlertChecker
import com.inventorysmartai.app.data.alerts.StockAlertScheduler
import com.inventorysmartai.app.data.alerts.StockAlertSettings
import com.inventorysmartai.app.domain.alerts.StockAlertSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockAlertsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: StockAlertSettings,
    private val checker: StockAlertChecker
) : ViewModel() {

    val enabled: StateFlow<Boolean> = settings.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val hour: StateFlow<Int> = settings.hour.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockAlertSchedule.DEFAULT_HOUR)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onMessageShown() { _message.value = null }

    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            settings.setEnabled(value)
            StockAlertScheduler.apply(context, value, hour.value)
        }
    }

    fun setHour(value: Int) {
        viewModelScope.launch {
            settings.setHour(value)
            if (enabled.value) StockAlertScheduler.apply(context, true, value)
        }
    }

    /** يرسل التنبيه الآن (للتأكد من أن الإشعارات تصل). */
    fun sendTestNow() {
        viewModelScope.launch {
            val sent = try {
                checker.checkAndNotify(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _message.value = if (sent != null) "أُرسل التنبيه" else "لا توجد أصناف تستدعي تنبيهًا الآن، أو الإشعارات معطّلة من إعدادات النظام"
        }
    }

    fun onPermissionDenied() {
        _message.value = "لم يُمنح إذن الإشعارات فلن تظهر التنبيهات. يمكنك السماح بها من إعدادات النظام."
    }
}
