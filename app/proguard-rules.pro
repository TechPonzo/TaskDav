# TaskDav — release shrink / obfuscation rules
# R8 already ships consumer rules for AndroidX; these cover reflection-heavy libs.

# --- Keep line numbers for crash reports ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- Kotlin / coroutines ---
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# --- WorkManager ---
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keepclassmembers class * extends androidx.work.Worker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# --- Glance app widgets ---
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
-keep class * extends androidx.glance.appwidget.action.ActionCallback

# --- EncryptedSharedPreferences / Tink (security-crypto) ---
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- dav4jvm ---
-keep class at.bitfire.dav4jvm.** { *; }
-dontwarn at.bitfire.dav4jvm.**

# --- ical4j (heavy reflection / ServiceLoader) ---
-keep class net.fortuna.ical4j.** { *; }
-keep class net.fortuna.ical4j.validate.** { *; }
-dontwarn net.fortuna.ical4j.**
-dontwarn groovy.**
-dontwarn org.codehaus.groovy.**
-dontwarn org.apache.commons.logging.**
-dontwarn org.apache.commons.lang.**
-dontwarn org.apache.commons.codec.**
-dontwarn edu.umd.cs.findbugs.annotations.**

# Keep META-INF services referenced by ical4j
-keep class * implements net.fortuna.ical4j.model.ParameterFactory
-keep class * implements net.fortuna.ical4j.model.PropertyFactory
-keep class * implements net.fortuna.ical4j.model.ComponentFactory

# --- App enum / preference IDs used from SharedPreferences / DataStore ---
-keepclassmembers enum app.taskdav.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
