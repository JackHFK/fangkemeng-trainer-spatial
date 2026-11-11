# Spatial 的原生回调、反射注册和 ECS 系统必须保留。
-keep class com.pico.spatial.** { *; }
-keep class * extends com.pico.spatial.core.ecs.System { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# 发布构建移除依赖库的详细调试日志，保留警告和错误。
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
