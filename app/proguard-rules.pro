# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.ecodrive.app.data.local.entity.** { *; }
-keep class com.ecodrive.app.domain.model.** { *; }

# ── R8 / Optimization ───────────────────────────────────────────────────────
# CAUTION: -repackageclasses and -mergeinterfacesaggressively break Hilt's
# runtime component lookup and Kotlin reflection. Keep these disabled.
-optimizationpasses 3
-allowaccessmodification

# ── Hilt / Dagger ───────────────────────────────────────────────────────────
# Hilt generates internal classes that are looked up by simple name at runtime.
# Any renaming or repackaging breaks the injection graph and crashes the app.
-keep class dagger.hilt.** { *; }
-keep class dagger.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$ComponentManager { *; }
-keep class dagger.hilt.android.internal.** { *; }
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# ── Kotlin ──────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Lazy { *; }

# ── Kotlin Coroutines & Flow ────────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── Kotlinx Serialization ───────────────────────────────────────────────────
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# ── OkHttp ──────────────────────────────────────────────────────────────────
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Room ────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

# ── Compose ─────────────────────────────────────────────────────────────────
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    void setConfigurationChange(android.content.res.Configuration);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── TFLite ──────────────────────────────────────────────────────────────────
-keep class org.tensorflow.lite.** { *; }

# ── Google Play Services ─────────────────────────────────────────────────────
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── Suppress warnings for optional dependencies ──────────────────────────────
-dontwarn javax.annotation.**

