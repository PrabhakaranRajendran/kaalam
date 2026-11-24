# Keep stack traces readable and keep the app's own classes (WorkManager creates workers by name).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class com.prabhu.kaalam.** { *; }
