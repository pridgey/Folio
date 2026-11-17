# Keep line numbers so crash reports stay readable. Names stay too: extensions are loaded at
# runtime and link against these classes by name, so nothing they can reach may be renamed.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*,Exceptions
-renamesourcefileattribute SourceFile
-dontobfuscate

# ---- Extension host API (eu.kanade.tachiyomi.*), implemented by Folio.
-keep class eu.kanade.tachiyomi.** { *; }

# ---- Everything an extension is compiled against and expects the host to provide.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.squareup.zstd.** { *; }
-keep class org.brotli.** { *; }
-keep class org.jsoup.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keep class androidx.preference.** { *; }
-keep class androidx.webkit.** { *; }
-keep class org.chromium.support_lib_boundary.** { *; }
-keep class org.jspecify.** { *; }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.jspecify.**
-dontwarn javax.annotation.**
-dontwarn com.google.re2j.**
-dontwarn kotlinx.serialization.**
