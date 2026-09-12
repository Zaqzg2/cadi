package com.inventorysmartai.app.core.common

/**
 * Small, app-wide defaults. Kept in one place so Phase 2 (Settings-driven overrides,
 * multi-currency, etc.) only has to touch one file.
 */
object Constants {
    /** Placeholder currency symbol — swap this (or wire it to a Settings field) once the
     *  target market/currency is decided. Nothing else in the app assumes a specific currency. */
    const val DEFAULT_CURRENCY_SYMBOL = "ر.س"

    const val DEFAULT_NEAR_EXPIRY_WINDOW_DAYS = 30
    const val DEFAULT_LOW_STOCK_THRESHOLD = 5.0
    const val RECENT_LIST_LIMIT = 5
    const val DEFAULT_GOAL_GROUP_COUNT = 3
}
