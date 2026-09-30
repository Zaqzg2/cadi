package com.inventorysmartai.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use App Check's DEBUG provider (firebase-appcheck-debug is a debugImplementation
 * dependency, so this class does not exist in release builds). On first launch it logs a line like
 * "Enter this debug secret into the allow list in the Firebase Console..." under the tag
 * DebugAppCheckProvider: register that token once in Firebase Console > App Check > Apps > Manage
 * debug tokens. Keep the token private.
 *
 * The same object exists in src/debug and src/release with the same name; the build type picks one.
 */
internal object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
