package com.inventorysmartai.app

import android.app.Application
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class InventorySmartApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setUpAppCheck()
    }

    /**
     * Firebase AI Logic only serves requests that carry an App Check token, so the provider must be
     * installed before the first AI call. Firebase itself initialises through its own content provider
     * when google-services.json was compiled into the build; without that file there is no default
     * FirebaseApp, `initializeApp` returns null, and this method does nothing — every non-AI feature
     * keeps working and the AI features report "not configured" (see FirebaseAiDocumentImportRepositoryImpl).
     * Which provider gets installed (debug vs Play Integrity) is decided by the build type: see
     * AppCheckInstaller in src/debug and src/release.
     */
    private fun setUpAppCheck() {
        runCatching { FirebaseApp.initializeApp(this) }.getOrNull() ?: return
        // A failure here must never stop the app from starting.
        runCatching { AppCheckInstaller.install() }
    }
}
