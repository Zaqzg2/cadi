package com.inventorysmartai.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds attest with Play Integrity: Firebase asks Google Play whether this is the genuine,
 * unmodified app installed from Play on a genuine device. It needs the Play Integrity provider enabled
 * for the app in Firebase Console > App Check, with the release signing certificate's SHA-256 registered.
 *
 * The same object exists in src/debug and src/release with the same name; the build type picks one.
 */
internal object AppCheckInstaller {
    fun install() {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
