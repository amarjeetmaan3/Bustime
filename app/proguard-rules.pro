1   # Add project specific ProGuard rules here.
2   -keep class com.bustime.app.models.** { *; }
3
4   # Room Database
5   -keep class * extends androidx.room.RoomDatabase
6   -dontwarn androidx.room.**
7   -keep class com.bustime.app.data.local.** { *; }
8
9   # Gson (used by Supabase sync response parsing and Room type converters)
10  -keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
11  -keep class kotlin.coroutines.Continuation
12  -keepclassmembers,allowobfuscation class * {
13      @com.google.gson.annotations.SerializedName <fields>;
14  }
15  -keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
16  -keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
