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

# 2026-07-03: not sure if we actually need this, but no harm.
# https://github.com/Yalantis/uCrop
-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }

# The below and more is now done with the "@Keep" annotation.
# Keeping for reference for now.

# SearchEngine constructors are called using reflection
#-keepclassmembers public class * extends com.hardbacknutter.nevertoomanybooks.searches.SearchEngine {
#   public <init>(...);
#   }
