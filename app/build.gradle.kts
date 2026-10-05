import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firebase (AI Logic + App Check). google-services.json is downloaded from YOUR Firebase project and is
// not in the repo, so the plugin is applied only when the file is present. Without it the app still
// builds and runs — everything except the AI features works, and those say "not configured" — which
// is also what keeps CI green before the file is added. NOTE: the debug build type has
// applicationIdSuffix ".debug", so the file must list BOTH com.inventorysmartai.app and
// com.inventorysmartai.app.debug (register both Android apps in the Firebase console), otherwise the
// plugin fails with "No matching client found for package name".
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Values that differ per developer/deployment. Resolution order: -P flag / gradle.properties, then an
// environment variable of the same name, then the old placeholder (the app detects the placeholder and
// tells the user exactly what is missing instead of failing with a generic error).
fun configValue(name: String, fallback: String): String =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: fallback

val googleServerClientId = configValue("GOOGLE_BACKEND_SERVER_CLIENT_ID", "870544200288-g3tnk26k4kmi8ajqq7i56kp2rt29a17n.apps.googleusercontent.com")
val backendUrlDebug = configValue("BACKEND_BASE_URL_DEBUG", "http://10.0.2.2:8080/")
val backendUrlRelease = configValue("BACKEND_BASE_URL", "https://CHANGE-ME.example.com/")

android {
    namespace = "com.inventorysmartai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.inventorysmartai.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.0" // Phase 4: AI + Google ecosystem + Smart Assistant.

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Phase 4: the BACKEND's "Web application" OAuth client id (see backend/README.md's "Why
        // a separate OAuth client" section) — this is a public identifier, not a secret, safe to
        // ship in the APK; it is what tells Google's consent screen which backend is asking for
        // offline access. Placeholder until a real Cloud Console project exists.
        // Override without editing code: -PGOOGLE_BACKEND_SERVER_CLIENT_ID=... , gradle.properties, or the
        // same-named environment variable (e.g. a GitHub Actions secret).
        buildConfigField("String", "GOOGLE_BACKEND_SERVER_CLIENT_ID", "\"$googleServerClientId\"")
    }

    // A FIXED debug signing key. Without it every CI run signs the debug APK with a freshly generated key,
    // so a new APK can never be installed over the previous one — you must uninstall first, which wipes
    // the app's data (the local database) and its Firebase App Check debug token, forcing a new token to
    // be registered after every build. This is the conventional shared debug key (alias "androiddebugkey",
    // password "android"), not a secret, and it signs debug builds only. If the file is missing the build
    // falls back to Android's per-machine default, exactly as before. The first build with it must be
    // installed after uninstalling the old one once (the signatures differ); after that, updates keep data.
    signingConfigs {
        getByName("debug") {
            val sharedDebugKey = file("debug.keystore")
            if (sharedDebugKey.exists()) {
                storeFile = sharedDebugKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Phase 4: MUST be overridden with a real deployed backend URL (e.g. via a
            // gradle.properties value injected here, or a CI secret) before a release build is
            // ever distributed — this placeholder exists only so the app fails obviously
            // (network calls simply fail against localhost) rather than compiling in some
            // guessed-at "probably production" URL. See backend/README.md for what to deploy.
            buildConfigField("String", "BACKEND_BASE_URL", "\"${backendUrlRelease}\"")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            // 10.0.2.2 is the Android emulator's alias for the host machine's localhost — run
            // `./gradlew :backend:run` on the same machine the emulator runs on. A physical
            // device needs the host's real LAN IP instead.
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://cadi-9qmu.onrender.com/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// No room.schemaLocation on purpose: with schema export on, the debug and release KSP tasks race to write
// and read the same JSON file and CI fails intermittently (README, fix log item 6). Re-enable it with the
// Room Gradle Plugin (androidx.room) once real migrations exist.
ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Phase 3: Excel (.xlsx) reading — see libs.versions.toml for why this specific artifact.
    implementation(libs.fastexcel.reader)
    // CSV is hand-parsed (see data/importing/parser/CsvImportParser.kt) — no dependency needed.

    // --- Phase 4: networking to this app's own backend (paused for now; see the backend module's
    // README). AI document extraction does NOT go through it — see Firebase AI Logic below. ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor) // activated only when BuildConfig.DEBUG (see di/NetworkModule.kt) — needs to be on every variant's classpath, not just debug's, since it's referenced from main-source-set code
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    // Google account connection: AuthorizationClient (play-services-auth) — the current,
    // non-deprecated API for requesting incremental OAuth scopes on Android and, via
    // requestOfflineAccess(...), a one-time server auth code for this app's backend. (Only
    // GoogleSignInClient/GoogleSignInOptions were deprecated in favor of Credential Manager;
    // AuthorizationClient remains the standing mechanism for authorization/scopes, which is all
    // this app needs — its own account-picker UI is part of the same consent flow, so a separate
    // Credential Manager sign-in step would only add a second screen with no real benefit here.)
    implementation(libs.play.services.auth)

    // Barcode scanner (Data Center "الباركود" tile + Inventory scan button).
    implementation(libs.play.services.code.scanner)

    // Firebase AI Logic: the app calls Gemini itself, and App Check (Play Integrity in release, the debug
    // provider in debug — see src/debug and src/release AppCheckInstaller) proves the call comes from this
    // app, so no API key ships in the APK. The debug provider is debugImplementation so it cannot reach a
    // release build. Versions come from the BoM.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    testImplementation(libs.junit)
    testImplementation(libs.fastexcel.writer) // builds real .xlsx fixtures for ExcelImportParser tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
