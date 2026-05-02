# Flutter-specific rules.
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# App specific rules
-keep class com.example.visa_form_app.** { *; }
-keep class com.example.assistant_hb.** { *; }


# OkHttp3 and Okio
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx.coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepclassmembernames class kotlinx.coroutines.flow.internal.AbstractSharedFlow {
    kotlinx.coroutines.flow.internal.AbstractSharedFlowSlot[] susbscribers;
}
-keepclassmembernames class kotlinx.coroutines.internal.Segment {
    java.lang.Object[] data;
}

# BouncyCastle/Conscrypt/OpenJSSE (avoid missing class errors)
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
