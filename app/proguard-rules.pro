# Add project specific ProGuard rules here.
-keep class com.bustime.app.models.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Retrofit
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Gson / Retrofit (release में minify चालू है, इसके बिना API जवाब पार्स नहीं होगा)
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep interface com.bustime.app.data.remote.** { *; }
-keep class kotlin.coroutines.Continuation
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
