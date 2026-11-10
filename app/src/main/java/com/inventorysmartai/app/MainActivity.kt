package com.inventorysmartai.app

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.core.designsystem.theme.ColorMath
import com.inventorysmartai.app.core.designsystem.theme.InventorySmartTheme
import com.inventorysmartai.app.data.access.AccessManager
import com.inventorysmartai.app.data.google.GoogleAuthManager
import com.inventorysmartai.app.data.importing.IncomingFileInbox
import com.inventorysmartai.app.data.importing.IncomingFiles
import com.inventorysmartai.app.domain.model.ColorOverrides
import com.inventorysmartai.app.domain.model.ThemeMode
import com.inventorysmartai.app.domain.repository.SettingsRepository
import com.inventorysmartai.app.navigation.AppNavHost
import com.inventorysmartai.app.presentation.access.AccessGateViewModel
import com.inventorysmartai.app.presentation.access.LockScreen
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    @Inject lateinit var incomingFileInbox: IncomingFileInbox

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
        // ملف وصل من «فتح بواسطة» أو «مشاركة» عند تشغيل التطبيق. لا يُعاد التعامل معه بعد تدوير الشاشة أو العودة من السجل الأخير.
        if (savedInstanceState == null) handleIncomingIntent(intent)
        setContent {
            val viewModel: MainActivityViewModel = hiltViewModel()
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val darkTheme = when (appearance.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // أيقونات شريط الحالة والتنقل تتبع وضع التطبيق (لا وضع النظام): بدونه تظهر أيقونات داكنة فوق خلفية داكنة عند اختيار الداكن داخل التطبيق.
            // وإن خصّص المستخدم خلفية فاتحة داخل الوضع الداكن (أو العكس) فالأيقونات تتبع الخلفية الفعلية.
            val barsOnDark = appearance.colors.background?.let { ColorMath.isDark(it) } ?: darkTheme
            DisposableEffect(barsOnDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { barsOnDark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { barsOnDark }
                )
                onDispose {}
            }
            InventorySmartTheme(
                darkTheme = darkTheme,
                accentHue = appearance.accentHue,
                pureBlack = appearance.pureBlackDark,
                overrides = appearance.colors
            ) {
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

    /** التطبيق مفتوح أصلًا (singleTop) ووصله ملف جديد من «فتح بواسطة / مشاركة». */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * ينسخ الملف القادم إلى ذاكرة التطبيق المؤقتة (صلاحية قراءته من التطبيق المرسل مؤقتة) ويضعه في [incomingFileInbox]؛
     * فتح شاشة الاستيراد وتحليل الملف يتمّان من هناك.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val source = IncomingFiles.sourceUri(intent) ?: return
        lifecycleScope.launch {
            val local = withContext(Dispatchers.IO) { IncomingFiles.copyToCache(this@MainActivity, source) }
            if (local != null) {
                incomingFileInbox.offer(local.toString())
            } else {
                Toast.makeText(this@MainActivity, "تعذّر قراءة الملف المشترك", Toast.LENGTH_LONG).show()
            }
        }
    }

    private companion object {
        const val AUTO_LOCK_MILLIS = 60_000L
        const val LIGHT_NAV_SCRIM = 0xE6FFFFFF.toInt()
        const val DARK_NAV_SCRIM = 0x801B1B1B.toInt()
    }
}

/** كل ما يحدّد مظهر التطبيق، يُجمع في تدفق واحد كي لا يُعاد بناء الواجهة مرات عند فتح التطبيق. */
data class AppAppearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentHue: Float? = null,
    val pureBlackDark: Boolean = false,
    val colors: ColorOverrides = ColorOverrides()
)

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {
    val appearance: StateFlow<AppAppearance> = combine(
        settingsRepository.observeThemeMode(),
        settingsRepository.observeAccentHue(),
        settingsRepository.observePureBlackDark(),
        settingsRepository.observeColorOverrides()
    ) { mode, hue, black, colors -> AppAppearance(mode, hue, black, colors) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppAppearance())
}
