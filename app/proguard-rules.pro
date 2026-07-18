# OTheme ProGuard 配置

# 保留行号信息，方便调试
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留注解
-keepattributes *Annotation*

# 保留泛型签名
-keepattributes Signature

# 保留异常信息
-keepattributes Exceptions

#==================== Kotlin ====================
# Kotlin 反射
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

#==================== Jetpack Compose ====================
# Compose Runtime
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**

# Compose UI
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.ui.**

# Compose Foundation
-keep class androidx.compose.foundation.** { *; }
-dontwarn androidx.compose.foundation.**

# Material 3
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.material3.**

# Navigation Compose
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

#==================== AboutLibraries ====================
# 保留 kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# 保留 Libs 及 entity 包下的序列化类（含 $serializer）
-keep,includedescriptorclasses class com.mikepenz.aboutlibraries.**$$serializer { *; }
-keepclassmembers class com.mikepenz.aboutlibraries.** {
    *** Companion;
}
-keepclasseswithmembers class com.mikepenz.aboutlibraries.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.mikepenz.aboutlibraries.entity.** { *; }
-keep class com.mikepenz.aboutlibraries.Libs { *; }
-dontwarn com.mikepenz.aboutlibraries.**

# 保留 kotlinx.collections.immutable（AboutLibraries v11 依赖）
-keep class kotlinx.collections.immutable.** { *; }
-dontwarn kotlinx.collections.immutable.**

#==================== 项目特定类 ====================
# 保留数据类
-keep class com.chuishui.otheme.ThemeInfo { *; }
-keep class com.chuishui.otheme.ThemeMode { *; }

# 保留 Activity
-keep class com.chuishui.otheme.MainActivity { *; }

#==================== Android ====================
# 保留 View 和 ViewGroup 构造方法
-keepclassmembers public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留 Parcelable
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# 保留 Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

#==================== 优化 ====================
# 优化次数
-optimizationpasses 5

# 不跳过非公共库类
-dontskipnonpubliclibraryclasses

# 混淆时不使用大小写混合类名
-dontusemixedcaseclassnames

# 不忽略警告
-dontwarn

# 优化时允许访问并修改有修饰符的类和类的成员
-allowaccessmodification

# 合并接口时，即使两个接口的实现类不相关也进行合并
-mergeinterfacesaggressively

#==================== 移除日志 ====================
# 移除 Log 调用
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

#==================== Apache Commons Compress & optional native libs ====================
# 保留 Commons Compress 运行时类
-keep class org.apache.commons.compress.** { *; }

# Suppress warnings for optional native/compression implementations that may not be present on Android
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.**
-dontwarn org.objectweb.asm.**
-dontwarn org.tukaani.xz.**
-dontwarn org.apache.commons.compress.compressors.zstandard.**
-dontwarn org.apache.commons.compress.compressors.brotli.**
-dontwarn org.apache.commons.compress.harmony.pack200.**
-dontwarn org.apache.commons.compress.compressors.lzma.**
-dontwarn org.apache.commons.compress.archivers.sevenz.**
