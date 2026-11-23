# Add project specific ProGuard rules here.
-keep class com.bustime.app.models.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Gson (used by Supabase sync response parsing)
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class kotlin.coroutines.Continuation
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
