# T-mA24 Keyboard ProGuard rules

# Keep the InputMethodService and all keyboard classes
-keep class com.tma24.keyboard.** { *; }

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep GIF drawable library
-keep class pl.droidsonroids.gif.** { *; }

# Keep AndroidX preference classes for SettingsActivity
-keep class androidx.preference.** { *; }

# Keep EmojiCompat
-keep class androidx.emoji2.** { *; }

# Suppress warnings from ML Kit internal classes
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# Keep Kotlin metadata (required for reflection-free coroutines)
-keepattributes *Annotation*, Signature, Exception

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile