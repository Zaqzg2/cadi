// Phase 4 secure backend — plain Kotlin/JVM (Ktor), deliberately its own Gradle module so it
// can never accidentally end up bundled inside the Android APK. Holds every secret the app must
// never see: the Gemini API key and the Google OAuth *client secret* (the app only ever handles
// a one-time authorization code — see auth/GoogleAuthService.kt).
//
// Why a separate module instead of a separate repo: keeps one version catalog (gradle/libs.versions.toml)
// as the single source of truth for every dependency version in the whole project, and lets
// `./gradlew test` at the root exercise both the app's and the backend's unit tests in one CI run
// (see .github/workflows/android-build.yml). It shares nothing else with :app — no compiled code,
// no Android dependency of any kind — so a bug or a dependency bump here can never affect the APK.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.inventorysmartai.backend"
version = "0.1.0"

application {
    mainClass.set("com.inventorysmartai.backend.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android) // -android artifact is fine on a JVM too;
        // reusing the exact version :app already pins (1.10.1) rather than introducing a second,
        // separately-guessed coroutines version. The -core artifact it depends on is what's
        // actually used here (no android.* import exists anywhere in :app either).

    implementation(libs.logback.classic)

    testImplementation(libs.junit)
}

kotlin {
    jvmToolchain(17)
}

