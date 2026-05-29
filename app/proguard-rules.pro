# Project-level ProGuard / R8 rules for Meme Me Up Scotty.
#
# Most of what we need is covered by the AGP-supplied "default" file
# (proguard-android-optimize.txt) and the consumer rules shipped by the
# libraries we depend on:
#   * Retrofit, OkHttp, Moshi (codegen) — all ship their own keep rules.
#   * Coil — ships its own.
#   * Jetpack Compose + AndroidX — covered by default rules.
#
# We only add what isn't already covered.

# Keep Kotlin metadata (used by Moshi reflective fallbacks and stack traces).
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Replicate API DTOs are deserialized by Moshi-Kotlin codegen, which
# generates @JsonClass adapters at compile time. The generated adapters
# reference the constructors and properties of these classes by name,
# so they must survive R8.
-keep class com.rsilverst.mememeupscotty.data.network.** { *; }
-keepclassmembers class com.rsilverst.mememeupscotty.data.network.** {
    <init>(...);
    <fields>;
}
