# Checkin Keeper R8 / ProGuard Configuration

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Jetpack Compose runtime
-keep class androidx.compose.runtime.** { *; }

# Keep data models used with JSON / SharedPreferences
-keepclassmembers class com.example.keeper.data.** { *; }
-keep class com.example.keeper.data.** { *; }

# Keep Services, BroadcastReceivers, and Core triggers
-keep class com.example.keeper.service.KeepAliveService { *; }
-keep class com.example.keeper.receiver.AlarmReceiver { *; }
-keep class com.example.keeper.receiver.BootReceiver { *; }
-keep class com.example.keeper.trigger.FcmTrigger { *; }

# Keep Android system components & entry points
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application

# Don't warn on optional Google Play Services / vendor packages
-dontwarn com.google.android.gms.**
-dontwarn android.os.PowerManager
-dontwarn android.net.ConnectivityManager

# Optimize aggressive code shrinking while keeping safety
-repackageclasses ''
-allowaccessmodification
