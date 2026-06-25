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

# Replicate API DTOs are deserialized by Moshi-Kotlin codegen, which generates
# @JsonClass adapters at compile time. The generated adapters reference these
# classes' constructors and properties, so keep those members. Scoping to
# @JsonClass (rather than the whole package) lets ReplicateApi — covered by
# Retrofit's own consumer rules — and NetworkModule shrink and obfuscate.
-keepclassmembers @com.squareup.moshi.JsonClass class com.rsilverst.mememeupscotty.data.network.** {
    <init>(...);
    <fields>;
}

# History persistence DTOs (CaptionSnapshot, CaptionData, HistoryEntryDto) are
# deserialized by Moshi-codegen; the generated adapters reference their
# constructors and properties. Scope the keep to the @JsonClass models so the
# rest of the package (MainViewModel, state classes) can still shrink.
-keepclassmembers @com.squareup.moshi.JsonClass class com.rsilverst.mememeupscotty.ui.viewmodel.** {
    <init>(...);
    <fields>;
}
