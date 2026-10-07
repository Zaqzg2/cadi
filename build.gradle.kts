// Top-level build file — per-module configuration lives in app/build.gradle.kts and
// backend/build.gradle.kts.
//
// Every Kotlin-family plugin used ANYWHERE in this build — by :app or by :backend — must be
// declared here, once, with apply false, even the ones :app itself never applies directly
// (kotlin.jvm, kotlin.serialization). This is not stylistic: org.jetbrains.kotlin.jvm and
// org.jetbrains.kotlin.android are different plugin IDs backed by the SAME underlying Kotlin
// Gradle Plugin implementation, so if kotlin.android gets resolved here first and :backend's
// build.gradle.kts then requests kotlin.jvm fresh (with its own version), Gradle cannot verify
// the two are compatible and fails with "the plugin is already on the classpath with an unknown
// version, so compatibility cannot be checked" — confirmed against real Gradle/Kotlin issue
// reports (gradle/gradle#20084, JetBrains Slack) after this exact failure showed up in CI; the
// fix there is uniformly "declare it in the root with apply false", not a version bump. Every
// Kotlin-family entry below shares one version.ref ("kotlin" in the catalog) for the same reason
// — two catalog keys that happen to resolve to an equal string are still, to Gradle's plugin
// resolver, two separate declarations.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
