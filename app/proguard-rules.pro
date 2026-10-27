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

# No Moshi DTO rules on purpose. Every DTO is @JsonClass(generateAdapter = true)
# and no Moshi instance registers KotlinJsonAdapterFactory, so all JSON goes
# through codegen adapters: they hard-code the JSON names as string literals and
# access properties directly, and codegen emits its own keep rules for the
# adapter lookup and synthetic constructors. R8 may rename DTO fields freely.
# A DTO that relies on reflection instead (no codegen, a reflective factory, or
# an enum-typed field via Moshi's EnumJsonAdapter) needs its own rule.
