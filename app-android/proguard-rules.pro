# R8 keep rules for the release build (isMinifyEnabled in build.gradle.kts). The libraries below ship most of their own
# consumer rules; these cover what they leave to reflection or to generated code. Checked by release.yml's smoke test.

# Koin and SQLDelight: kept whole until the first releases prove narrower rules safe (the smoke test is the check).
-keepnames class kotlin.Metadata
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# kotlinx-serialization: generated serializers are found through the `Companion.serializer()` of @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# SQLDelight (Android driver): the schema and generated queries are plain classes, the driver uses AndroidX SQLite.
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**
