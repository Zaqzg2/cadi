package com.inventorysmartai.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.designsystem.theme.InventorySmartTheme
import com.inventorysmartai.app.data.access.AccessManager
import com.inventorysmartai.app.data.google.GoogleAuthManager
import com.inventorysmartai.app.domain.model.ThemeMode
import com.inventorysmartai.app.domain.repository.SettingsRepository
import com.inventorysmartai.app.navigation.AppNavHost
import com.inventorysmartai.app.presentation.access.AccessGateViewModel
import com.inventorysmartai.app.presentation.access.LockScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Phase 4: field-injected (rather than constructor-injected, since ComponentActivity's
    // constructor is framework-owned) so `register(this)` can run in onCreate, before the
    // Activity reaches STARTED — registerForActivityResult requires this. Re-registering on every
    // onCreate (e.g. after a configuration change recreates the Activity) is correct and expected:
    // GoogleAuthManager itself is a singleton that outlives any one Activity instance, but the
    // launcher it holds must always point at the CURRENT Activity.
    @Inject lateinit var googleAuthManager: GoogleAuthManager
    @Inject lateinit var accessManager: AccessManager

    /** لحظة مغادرة التطبيق (0 = لم يُغادَر بعد). للقفل التلقائي عند العودة بعد غياب طويل. */
    private var leftAtElapsed = 0L

    override fun onStop() {
        super.onStop()
        leftAtElapsed = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // عودة بعد أكثر من دقيقة تقفل التطبيق من جديد (لا شيء يحدث إن كان قفل التطبيق مطفأً).
        if (leftAtElapsed != 0L && SystemClock.elapsedRealtime() - leftAtElapsed > AUTO_LOCK_MILLIS) accessManager.lock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        googleAuthManager.register(this)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainActivityViewModel = hiltViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            InventorySmartTheme(darkTheme = darkTheme) {
                // القفل طبقة فوق التطبيق لا بديلًا عنه: يبقى كل ما كان مفتوحًا (ونماذج لم تُحفظ بعد) كما هو خلفها.
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                    val gate: AccessGateViewModel = hiltViewModel()
                    val access by gate.state.collectAsStateWithLifecycle()
                    if (!access.loaded || access.locked) LockScreen()
                }
            }
        }
    }

    private companion object {
        const val AUTO_LOCK_MILLIS = 60_000L
    }
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsRepository.observeThemeMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
}
