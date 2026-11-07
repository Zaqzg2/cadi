package com.inventorysmartai.app.presentation.access

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.data.access.AccessManager
import com.inventorysmartai.app.data.access.AccessOutcome
import com.inventorysmartai.app.data.access.SignInResult
import com.inventorysmartai.app.domain.access.AccessState
import com.inventorysmartai.app.domain.access.AppRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** بوابة الوصول التي تقرأها الشاشات (شريط التنقل، المزيد، حجب الشاشات غير المسموحة). */
@HiltViewModel
class AccessGateViewModel @Inject constructor(private val manager: AccessManager) : ViewModel() {
    val state: StateFlow<AccessState> = manager.state
    fun lock() = manager.lock()
}

data class LockUiState(val selectedUserId: String? = null, val error: String? = null, val busy: Boolean = false)

@HiltViewModel
class LockViewModel @Inject constructor(private val manager: AccessManager) : ViewModel() {
    val access: StateFlow<AccessState> = manager.state

    private val _ui = MutableStateFlow(LockUiState())
    val ui: StateFlow<LockUiState> = _ui.asStateFlow()

    fun select(userId: String) { _ui.update { it.copy(selectedUserId = userId, error = null) } }

    /** الدخول بالمستخدم المختار (أو الأول إن لم يُختر أحد). عند النجاح يزول القفل من تلقاء نفسه. */
    fun signIn(pin: String) {
        val userId = _ui.value.selectedUserId ?: manager.state.value.profiles.firstOrNull()?.id ?: return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            val result = manager.signIn(userId, pin)
            _ui.update { state ->
                state.copy(
                    busy = false,
                    error = when (result) {
                        is SignInResult.Success -> null
                        is SignInResult.WrongPin -> "الرمز غير صحيح. تبقّى ${result.attemptsLeft} محاولات"
                        is SignInResult.Locked -> "محاولات كثيرة. انتظر ${result.secondsLeft} ثانية ثم أعد المحاولة"
                    }
                )
            }
        }
    }

    fun recover(code: String, newPin: String, onResult: (AccessOutcome<Unit>) -> Unit) {
        viewModelScope.launch { onResult(manager.recoverOwner(code, newPin)) }
    }
}

@HiltViewModel
class PermissionsViewModel @Inject constructor(private val manager: AccessManager) : ViewModel() {
    val access: StateFlow<AccessState> = manager.state

    fun enable(name: String, pin: String, onResult: (AccessOutcome<String>) -> Unit) {
        viewModelScope.launch { onResult(manager.enable(name, pin)) }
    }

    fun disable(pin: String, onResult: (AccessOutcome<Unit>) -> Unit) {
        viewModelScope.launch { onResult(manager.disable(pin)) }
    }

    fun addUser(name: String, role: AppRole, pin: String, onResult: (AccessOutcome<Unit>) -> Unit) {
        viewModelScope.launch { onResult(manager.addUser(name, role, pin)) }
    }

    fun updateUser(id: String, name: String, role: AppRole, newPin: String?, onResult: (AccessOutcome<Unit>) -> Unit) {
        viewModelScope.launch { onResult(manager.updateUser(id, name, role, newPin)) }
    }

    fun deleteUser(id: String, onResult: (AccessOutcome<Unit>) -> Unit) {
        viewModelScope.launch { onResult(manager.deleteUser(id)) }
    }

    fun newRecoveryCode(pin: String, onResult: (AccessOutcome<String>) -> Unit) {
        viewModelScope.launch { onResult(manager.newRecoveryCode(pin)) }
    }

    fun lock() = manager.lock()
}
