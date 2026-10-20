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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- API models ----
# Gson fills these classes by field name through reflection. If minification is ever turned on, renaming or removing their fields would
# make every server response parse as empty, so the request/response models keep their names and members.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.example.velocitysuites.network.dto.** { *; }
-keepclassmembers class com.example.velocitysuites.network.dto.** { <fields>; }
# The models Gson / Intent extras serialize directly.
-keep class com.example.velocitysuites.Notification { *; }
-keep class com.example.velocitysuites.Booking { *; }
-keep class com.example.velocitysuites.Booking$* { *; }
-keep class com.example.velocitysuites.BookingRoom { *; }
-keep class com.example.velocitysuites.BookingAmenity { *; }
-keep class com.example.velocitysuites.AmenityRecord { *; }
# Retrofit interface + generic response types
-keep interface com.example.velocitysuites.network.ApiService { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

