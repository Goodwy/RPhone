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


# Room persistence library
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep the generated Room implementation classes
-keep class **._RillDatabase_Impl { *; }
-keep class **.RillDatabase_Impl { *; }

# Keep constructors for Room generated classes
-keepclassmembers class **.RillDatabase_Impl {
    public <init>(...);
}

# Keep all classes in the modal.db package
-keep class dev.goodwy.rphone.modal.db.** { *; }

# Keep Room's generated code
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}