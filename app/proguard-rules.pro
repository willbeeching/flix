# Flix ProGuard Rules

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep crash reporting information
-keepattributes *Annotation*

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Moshi (JSON parsing)
-keepclassmembers class ** {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonQualifier interface *
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
# Keep data classes for Moshi
-keep class com.willbeeching.flix.plex.** { *; }
-keepclassmembers class com.willbeeching.flix.plex.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Coil (Image Loading)
-keep class coil.** { *; }
-dontwarn coil.**

# NanoHTTPD (Local web server)
-keep class fi.iki.elonen.** { *; }
-keepclassmembers class fi.iki.elonen.** { *; }

# ZXing (QR codes)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.service.dreams.DreamService

# Preserve Compose @Preview functions
-keep @androidx.compose.ui.tooling.preview.Preview class * { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}


