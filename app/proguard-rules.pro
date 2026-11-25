  # Add project specific ProGuard rules here.
   -keep class com.bustime.app.models.** { *; }

  # Room Database
  -keep class * extends androidx.room.RoomDatabase
  -dontwarn androidx.room.**
  -keep class com.bustime.app.data.local.** { *; }

   # Gson (used by Supabase sync response parsing and Room type converters)
  -keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
 -keep class kotlin.coroutines.Continuation
 -keepclassmembers,allowobfuscation class * {
   @com.google.gson.annotations.SerializedName <fields>;
 }
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
 -keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
