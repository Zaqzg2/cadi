package com.inventorysmartai.app.data.access

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inventorysmartai.app.domain.access.AccessRules
import com.inventorysmartai.app.domain.access.AccessState
import com.inventorysmartai.app.domain.access.AppRole
import com.inventorysmartai.app.domain.access.PinHasher
import com.inventorysmartai.app.domain.access.PinThrottle
import com.inventorysmartai.app.domain.access.ProfileCodec
import com.inventorysmartai.app.domain.access.RecoveryCode
import com.inventorysmartai.app.domain.access.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AccessOutcome<out T> {
    data class Ok<T>(val value: T) : AccessOutcome<T>
    data class Error(val message: String) : AccessOutcome<Nothing>
}

sealed interface SignInResult {
    data object Success : SignInResult
    data class WrongPin(val attemptsLeft: Int) : SignInResult
    data class Locked(val secondsLeft: Long) : SignInResult
}

/**
 * المستخدمون المحليون وقفل التطبيق. مُطفأ افتراضيًا (فيبقى التطبيق مفتوحًا كما كان). عند التفعيل: مستخدم مدير واحد على الأقل،
 * ورمز (4–8 أرقام) لكل مستخدم يُخزَّن مجزَّأً، ورمز استرداد للمدير يُعرض مرة واحدة.
 *
 * الجلسة (من دخل الآن) في الذاكرة فقط: إعادة تشغيل التطبيق تقفله. كل عمليات الكتابة تمرّ عبر [mutex] وتقرأ المخزن مباشرة
 * (لا الحالة المشتقة) كي لا تُفقد كتابة بسبب قراءة متأخرة.
 */
@Singleton
class AccessManager @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val currentUserId = MutableStateFlow<String?>(null)
    private val throttle = PinThrottle()
    private val mutex = Mutex()

    val state: StateFlow<AccessState> =
        combine(dataStore.data.catch { emit(emptyPreferences()) }, currentUserId) { prefs, userId ->
            val profiles = ProfileCodec.decode(prefs[PROFILES])
            // علامة التفعيل بلا مدير صالح تُقفل الجميع إلى الأبد (ملف تالف مثلًا): تُعامَل كأنها مطفأة.
            val usable = (prefs[ENABLED] ?: false) && profiles.any { it.role == AppRole.OWNER }
            AccessState(loaded = true, enabled = usable, profiles = profiles, currentUserId = userId)
        }.stateIn(scope, SharingStarted.Eagerly, AccessState())

    fun lock() {
        currentUserId.value = null
    }

    /** يفعّل الحماية بمدير أول ويدخل به. يعيد رمز الاسترداد منسَّقًا ليُعرض مرة واحدة. */
    suspend fun enable(ownerName: String, pin: String): AccessOutcome<String> = mutex.withLock {
        AccessRules.nameError(ownerName, emptyList())?.let { return@withLock AccessOutcome.Error(it) }
        AccessRules.pinError(pin)?.let { return@withLock AccessOutcome.Error(it) }
        val owner = newProfile(ownerName, AppRole.OWNER, pin)
        val code = RecoveryCode.generate()
        val recoverySalt = PinHasher.newSalt()
        val recoveryHash = withContext(Dispatchers.Default) { PinHasher.hash(code, recoverySalt) }
        dataStore.edit {
            it[PROFILES] = ProfileCodec.encode(listOf(owner))
            it[RECOVERY_SALT] = PinHasher.encode(recoverySalt)
            it[RECOVERY_HASH] = PinHasher.encode(recoveryHash)
            it[ENABLED] = true
        }
        currentUserId.value = owner.id
        AccessOutcome.Ok(RecoveryCode.display(code))
    }

    /** يطفئ الحماية ويمسح المستخدمين. للمدير المسجَّل دخوله فقط، ويتطلب رمزه. */
    suspend fun disable(pin: String): AccessOutcome<Unit> = mutex.withLock {
        val user = state.value.currentUser
        if (user == null || user.role != AppRole.OWNER) return@withLock AccessOutcome.Error("إيقاف الحماية للمدير فقط")
        if (!verify(user, pin)) return@withLock AccessOutcome.Error("الرمز غير صحيح")
        dataStore.edit {
            it.remove(PROFILES)
            it.remove(RECOVERY_SALT)
            it.remove(RECOVERY_HASH)
            it[ENABLED] = false
        }
        currentUserId.value = null
        AccessOutcome.Ok(Unit)
    }

    suspend fun signIn(userId: String, pin: String): SignInResult = mutex.withLock {
        val before = System.currentTimeMillis()
        val remaining = throttle.remainingLockMillis(before)
        if (remaining > 0) return@withLock SignInResult.Locked(secondsOf(remaining))

        val user = state.value.profiles.firstOrNull { it.id == userId }
        if (user != null && verify(user, pin)) {
            throttle.recordSuccess()
            currentUserId.value = user.id
            return@withLock SignInResult.Success
        }
        val now = System.currentTimeMillis()
        throttle.recordFailure(now)
        val lockLeft = throttle.remainingLockMillis(now)
        if (lockLeft > 0) SignInResult.Locked(secondsOf(lockLeft)) else SignInResult.WrongPin(throttle.attemptsLeft())
    }

    suspend fun addUser(name: String, role: AppRole, pin: String): AccessOutcome<Unit> = mutex.withLock {
        val profiles = storedProfiles()
        AccessRules.nameError(name, profiles)?.let { return@withLock AccessOutcome.Error(it) }
        AccessRules.pinError(pin)?.let { return@withLock AccessOutcome.Error(it) }
        val profile = newProfile(name, role, pin)
        dataStore.edit { it[PROFILES] = ProfileCodec.encode(profiles + profile) }
        AccessOutcome.Ok(Unit)
    }

    /** [newPin] null = إبقاء الرمز الحالي. */
    suspend fun updateUser(id: String, name: String, role: AppRole, newPin: String?): AccessOutcome<Unit> = mutex.withLock {
        val profiles = storedProfiles()
        val target = profiles.firstOrNull { it.id == id } ?: return@withLock AccessOutcome.Error("المستخدم غير موجود")
        AccessRules.nameError(name, profiles, ignoreId = id)?.let { return@withLock AccessOutcome.Error(it) }
        AccessRules.roleChangeError(profiles, id, role)?.let { return@withLock AccessOutcome.Error(it) }
        if (newPin != null) AccessRules.pinError(newPin)?.let { return@withLock AccessOutcome.Error(it) }

        val renewed = if (newPin != null) newProfile(name, role, newPin).copy(id = id) else target.copy(name = name.trim(), role = role)
        dataStore.edit { prefs -> prefs[PROFILES] = ProfileCodec.encode(profiles.map { if (it.id == id) renewed else it }) }
        AccessOutcome.Ok(Unit)
    }

    suspend fun deleteUser(id: String): AccessOutcome<Unit> = mutex.withLock {
        val profiles = storedProfiles()
        AccessRules.removeError(profiles, id, state.value.currentUserId)?.let { return@withLock AccessOutcome.Error(it) }
        dataStore.edit { it[PROFILES] = ProfileCodec.encode(profiles.filterNot { p -> p.id == id }) }
        AccessOutcome.Ok(Unit)
    }

    /** «نسيت الرمز»: برمز الاسترداد يُعاد ضبط رمز أول مدير ويُدخَل به. التخمين يخضع للتباطؤ نفسه. */
    suspend fun recoverOwner(code: String, newPin: String): AccessOutcome<Unit> = mutex.withLock {
        AccessRules.pinError(newPin)?.let { return@withLock AccessOutcome.Error(it) }
        val remaining = throttle.remainingLockMillis(System.currentTimeMillis())
        if (remaining > 0) return@withLock AccessOutcome.Error("محاولات كثيرة، انتظر ${secondsOf(remaining)} ثانية")

        val prefs = dataStore.data.first()
        val salt = prefs[RECOVERY_SALT]
        val hash = prefs[RECOVERY_HASH]
        if (salt == null || hash == null) return@withLock AccessOutcome.Error("لا يوجد رمز استرداد محفوظ")
        val matches = withContext(Dispatchers.Default) {
            try {
                PinHasher.verify(RecoveryCode.normalize(code), PinHasher.decode(salt), PinHasher.decode(hash))
            } catch (e: IllegalArgumentException) {
                false
            }
        }
        if (!matches) {
            throttle.recordFailure(System.currentTimeMillis())
            return@withLock AccessOutcome.Error("رمز الاسترداد غير صحيح")
        }
        val profiles = ProfileCodec.decode(prefs[PROFILES])
        val owner = profiles.firstOrNull { it.role == AppRole.OWNER } ?: return@withLock AccessOutcome.Error("لا يوجد مدير")
        val renewed = newProfile(owner.name, AppRole.OWNER, newPin).copy(id = owner.id)
        dataStore.edit { it[PROFILES] = ProfileCodec.encode(profiles.map { p -> if (p.id == owner.id) renewed else p }) }
        throttle.recordSuccess()
        currentUserId.value = owner.id
        AccessOutcome.Ok(Unit)
    }

    /** رمز استرداد جديد (يبطل القديم). للمدير المسجَّل دخوله ويتطلب رمزه. */
    suspend fun newRecoveryCode(pin: String): AccessOutcome<String> = mutex.withLock {
        val user = state.value.currentUser
        if (user == null || user.role != AppRole.OWNER) return@withLock AccessOutcome.Error("للمدير فقط")
        if (!verify(user, pin)) return@withLock AccessOutcome.Error("الرمز غير صحيح")
        val code = RecoveryCode.generate()
        val salt = PinHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(code, salt) }
        dataStore.edit {
            it[RECOVERY_SALT] = PinHasher.encode(salt)
            it[RECOVERY_HASH] = PinHasher.encode(hash)
        }
        AccessOutcome.Ok(RecoveryCode.display(code))
    }

    // ------------------------------------------------------------------ internals

    private suspend fun storedProfiles(): List<UserProfile> = ProfileCodec.decode(dataStore.data.first()[PROFILES])

    private suspend fun newProfile(name: String, role: AppRole, pin: String): UserProfile = withContext(Dispatchers.Default) {
        val salt = PinHasher.newSalt()
        UserProfile(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            role = role,
            pinSalt = PinHasher.encode(salt),
            pinHash = PinHasher.encode(PinHasher.hash(pin, salt))
        )
    }

    /** التجزئة بطيئة عمدًا (نحو مئات الملّي ثانية): خارج الخيط الرئيسي. ملف تالف = رفض لا انهيار. */
    private suspend fun verify(user: UserProfile, pin: String): Boolean = withContext(Dispatchers.Default) {
        try {
            PinHasher.verify(pin, PinHasher.decode(user.pinSalt), PinHasher.decode(user.pinHash))
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun secondsOf(millis: Long): Long = (millis + 999) / 1000

    private companion object {
        val ENABLED = booleanPreferencesKey("access_enabled")
        val PROFILES = stringPreferencesKey("access_profiles")
        val RECOVERY_SALT = stringPreferencesKey("access_recovery_salt")
        val RECOVERY_HASH = stringPreferencesKey("access_recovery_hash")
    }
}
