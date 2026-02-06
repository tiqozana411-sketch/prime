# PRIME ProGuard Rules

#==================== Kotlin ====================
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# Kotlin反射
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

#==================== 注解和反射 ====================
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# 保留行号信息（用于调试）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

#==================== PRIME核心类 ====================
-keep class com.prime.core.** { *; }
-keep class com.prime.ai.** { *; }
-keep class com.prime.swarm.** { *; }
-keep class com.prime.vision.** { *; }
-keep class com.prime.tools.** { *; }
-keep class com.prime.remote.** { *; }
-keep class com.prime.speech.** { *; }
-keep class com.prime.skill.** { *; }
-keep class com.prime.distillation.** { *; }
-keep class com.prime.updater.** { *; }
-keep class com.prime.memory.** { *; }
-keep class com.prime.cache.** { *; }

#==================== Android服务 ====================
# 保留AccessibilityService
-keep class * extends android.accessibilityservice.AccessibilityService {
    public <methods>;
}

# 保留InCallService
-keep class * extends android.telecom.InCallService {
    public <methods>;
}

#==================== 第三方库 ====================
# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Timber
-dontwarn org.jetbrains.annotations.**
-keep class timber.log.** { *; }

# libsu
-keep class com.topjohnwu.superuser.** { *; }
-keepclassmembers class com.topjohnwu.superuser.** { *; }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# RapidOCR
-keep class io.github.mymonstercat.** { *; }
-dontwarn io.github.mymonstercat.**

#==================== 枚举 ====================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

#==================== Serializable ====================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

#==================== Parcelable ====================
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

#==================== 移除日志（Release版本） ====================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}