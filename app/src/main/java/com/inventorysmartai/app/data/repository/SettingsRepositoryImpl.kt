package com.inventorysmartai.app.data.repository

import com.inventorysmartai.app.data.demo.DemoDataSeeder
import com.inventorysmartai.app.data.local.datastore.SettingsLocalDataSource
import com.inventorysmartai.app.domain.model.ColorOverrides
import com.inventorysmartai.app.domain.model.ThemeMode
import com.inventorysmartai.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val local: SettingsLocalDataSource,
    private val demoDataSeeder: DemoDataSeeder
) : SettingsRepository {

    override fun observeThemeMode(): Flow<ThemeMode> =
        local.themeMode.map { runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM) }

    override suspend fun setThemeMode(mode: ThemeMode) = local.setThemeMode(mode.name)

    override fun observeAccentHue(): Flow<Float?> = local.accentHue
    override suspend fun setAccentHue(hue: Float?) = local.setAccentHue(hue)

    override fun observePureBlackDark(): Flow<Boolean> = local.pureBlackDark
    override suspend fun setPureBlackDark(value: Boolean) = local.setPureBlackDark(value)

    override fun observeColorOverrides(): Flow<ColorOverrides> = local.colorOverrides
    override suspend fun setBackgroundColor(argb: Int?) = local.setBackgroundColor(argb)
    override suspend fun setTextColor(argb: Int?) = local.setTextColor(argb)
    override suspend fun setCardColor(argb: Int?) = local.setCardColor(argb)
    override suspend fun resetColorOverrides() = local.resetColorOverrides()

    override fun observeUseArabicIndicDigits(): Flow<Boolean> = local.useArabicIndicDigits
    override suspend fun setUseArabicIndicDigits(value: Boolean) = local.setUseArabicIndicDigits(value)

    override fun observeNearExpiryWindowDays(): Flow<Int> = local.nearExpiryWindowDays
    override suspend fun setNearExpiryWindowDays(days: Int) = local.setNearExpiryWindowDays(days)

    override fun observeDefaultLowStockThreshold(): Flow<Double> = local.defaultLowStockThreshold
    override suspend fun setDefaultLowStockThreshold(value: Double) = local.setDefaultLowStockThreshold(value)

    override fun observeIsDemoDataLoaded(): Flow<Boolean> = local.isDemoDataLoaded

    /** The ONLY path that writes demo rows into Room — always an explicit user tap from
     *  Settings, never called from app startup. See DemoDataSeeder for why. */
    override suspend fun loadDemoData() {
        demoDataSeeder.seed()
        local.setDemoDataLoaded(true)
    }
}
