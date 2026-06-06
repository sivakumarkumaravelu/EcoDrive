# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.ecodrive.app.data.local.entity.** { *; }

# R8 Full Mode optimizations
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-mergeinterfacesaggressively

# Compose specific optimizations
-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    void setConfigurationChange(android.content.res.Configuration);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Hilt/Dagger rules (usually handled by AAR but good to have)
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$ComponentManager { *; }

# TFLite
-keep class org.tensorflow.lite.** { *; }
