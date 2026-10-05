# Spec rev 31: release builds are shrunk with R8.

# Crashlytics: keep file names and line numbers so stack traces map back to source
# (the mapping file is uploaded by the Crashlytics Gradle plugin), and keep custom exceptions.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep public class * extends java.lang.Exception

# Enum names are stored as text (entry.type, theme_mode, ...) and read back with valueOf / entries.
-keepclassmembernames enum com.mitenko.repkit.** { *; }
