# Add project specific ProGuard rules here.
# Phase 1 ships with isMinifyEnabled = false, so this file is not yet exercised,
# but it is kept ready for when release shrinking is turned on.

# Room
-keep class com.inventorysmartai.app.data.local.database.entity.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
