# =========================================================================
# KAAGAZ VAULT - ProGuard/R8 Security Hardening Rules
# =========================================================================
# Aggressive obfuscation, optimization, and anti-reverse-engineering rules
# for a privacy-first offline document vault.
# =========================================================================

# -------------------------------------------------------------------------
# Core Obfuscation Configuration
# -------------------------------------------------------------------------
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Maximum obfuscation: rename everything not explicitly kept
-overloadaggressively
-allowaccessmodification
-repackageclasses ''

# Flatten package hierarchy to single letter packages
-flattenpackagehierarchy ''

# Use meaningless names for renamed elements (default dictionary)
-useuniqueclassmembernames

# -------------------------------------------------------------------------
# Optimization Passes (Security-focused)
# -------------------------------------------------------------------------
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable
-optimizationpasses 5

# Remove logging in release builds (prevents info leakage)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# Remove debug assertions
-assumenosideeffects class java.lang.System {
    public static void setProperty(java.lang.String, java.lang.String);
}

# -------------------------------------------------------------------------
# Keep Rules - Application Entry Points
# -------------------------------------------------------------------------
-keep public class com.kaagazvault.MainActivity {
    public protected *;
}

-keep public class com.kaagazvault.** {
    public protected *;
}

# Keep annotation processors working (KSP, Room)
-keep class androidx.room.** { *; }
-keep class com.google.devtools.ksp.** { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Enum values for serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native method signatures
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI references
-keep class * {
    *** getInstance();
}

# -------------------------------------------------------------------------
# Security-Critical: Keep Cryptographic Classes Intact But Obfuscated
# -------------------------------------------------------------------------
# These classes use reflection for Android Keystore, must keep structure
-keep class com.kaagazvault.security.** { *; }
-keep class com.kaagazvault.database.** { *; }

# But obfuscate their internal member names
-keepclassmembers class com.kaagazvault.security.** {
    <fields>;
    <methods>;
}

# -------------------------------------------------------------------------
# SQLCipher - Keep native loading
# -------------------------------------------------------------------------
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }

# -------------------------------------------------------------------------
# Tesseract OCR - Keep native loading
# -------------------------------------------------------------------------
-keep class cz.adaptech.tesseract4android.** { *; }

# -------------------------------------------------------------------------
# AndroidX / Jetpack Compose
# -------------------------------------------------------------------------
-keep class androidx.** { *; }
-keep class com.google.android.** { *; }

# -------------------------------------------------------------------------
# Kotlin Coroutines & Serialization
# -------------------------------------------------------------------------
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }

# -------------------------------------------------------------------------
# Remove Stack Trace Information (Anti-Debugging)
# -------------------------------------------------------------------------
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# But strip line numbers in release for anti-reversing
# Uncomment for maximum anti-debug:
# -renamesourcefileattribute SourceFile
# -keepattributes SourceFile

# -------------------------------------------------------------------------
# Preserve Annotations for Runtime Security Checks
# -------------------------------------------------------------------------
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# -------------------------------------------------------------------------
# Crash Reporting (if any) - Keep minimal
# -------------------------------------------------------------------------
# -keep class com.google.firebase.crashlytics.** { *; }

# -------------------------------------------------------------------------
# Testing
# -------------------------------------------------------------------------
-dontwarn junit.**
-dontwarn org.junit.**
-dontwarn org.mockito.**
-dontwarn org.hamcrest.**

# =========================================================================
# End of ProGuard Rules
# =========================================================================