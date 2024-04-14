# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault
-dontwarn javax.annotation.processing.AbstractProcessor
-dontwarn javax.annotation.processing.Processor
-dontwarn javax.annotation.processing.SupportedOptions
# used by acra
-dontwarn com.google.auto.service.AutoService


# The below and more is now done with the "@Keep" annotation.
# Keeping for reference for now.

# fragments only referenced from xml and started by
# androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
#-keep public class * extends androidx.preference.PreferenceFragmentCompat

# SearchEngine constructors are called using reflection
#-keepclassmembers public class * extends com.hardbacknutter.nevertoomanybooks.searches.SearchEngine {
#   public <init>(...);
#   }
