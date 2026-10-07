# Project-specific ProGuard rules.

# ── Room ──────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.**

# ── Google Play Billing ───────────────────────────────────────────
-keep class com.android.billingclient.** { *; }
-keep class com.android.vending.billing.** { *; }
-dontwarn com.android.billingclient.**

# ── Google AdMob / Play Services ──────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**

# ── MPAndroidChart ────────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }
-dontwarn io.github.inflationx.**

# ── Lottie ────────────────────────────────────────────────────────
-dontwarn com.airbnb.lottie.**
-keep class com.airbnb.lottie.** { *; }

# ── Coil ──────────────────────────────────────────────────────────
-dontwarn coil.**
-keep class coil.** { *; }

# ── OkHttp (AI Worker client) ─────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.json.** { *; }

# ── Kotlin Serialization / Coroutines ─────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**

# ── DataStore ─────────────────────────────────────────────────────
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ── General ───────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# ── R8 Optimizations & Performance ──────────────────────────────
-repackageclasses ''
-allowaccessmodification
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontwarn android.graphics.BitmapFactory**

