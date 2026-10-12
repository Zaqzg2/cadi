# R8 rules for the "optimized" build (isMinifyEnabled = true; see app/build.gradle.kts and docs/OPTIMIZED_BUILD.md).
# The debug build does not shrink, so none of this is exercised there. Most libraries (Retrofit, OkHttp, Moshi, Room, Hilt/Dagger,
# Compose, coroutines, Play Services) ship their own "consumer" rules, so only what is specific to this app is listed here.
# If an optimized build ever fails with "Missing classes detected", the exact -dontwarn lines R8 wants are in
# app/build/outputs/mapping/optimized/missing_rules.txt (CI uploads it with the failure logs).

# ---- Readable crash reports --------------------------------------------------------------------------------------------------------------
# Keep line numbers; mapping.txt (uploaded by CI next to the optimized APK) turns an obfuscated stack trace back into real names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generic signatures, annotations and inner-class info are read at runtime by Retrofit/Moshi (and Kotlin suspend functions).
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# ---- Room --------------------------------------------------------------------------------------------------------------------------------
-keep class com.inventorysmartai.app.data.local.database.entity.** { *; }

# ---- Hilt --------------------------------------------------------------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# ---- JSON models -------------------------------------------------------------------------------------------------------------------------
# Every DTO uses @JsonClass(generateAdapter = true); Moshi finds the generated "<Name>JsonAdapter" by NAME at runtime. Moshi's codegen already
# emits per-class keep rules, this is the belt to those braces: keep the model classes themselves (they are small).
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# ---- Excel reading (fastexcel-reader -> aalto-xml / stax2 / commons-compress) -----------------------------------------------------------
# These reference JVM-only classes (javax.xml.stream and optional compression codecs) that do not exist on Android. The app already copes at
# runtime — ExcelImportParser catches the LinkageError and uses its own SAX reader — so R8 must simply not treat them as errors.
-dontwarn javax.xml.stream.**
-dontwarn org.codehaus.stax2.**
-dontwarn com.fasterxml.aalto.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**

# ---- Annotation-only / optional references used by common libraries ----------------------------------------------------------------------
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
