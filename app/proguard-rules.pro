# Keep sherpa-onnx JNI classes, native methods, and constructors
-keep class com.k2fsa.sherpa.onnx.** {
    <init>(...);
    native <methods>;
    *;
}
-keepclassmembers class com.k2fsa.sherpa.onnx.** {
    <fields>;
    <methods>;
}

# Keep Google LiteRT-LM JNI classes, native methods, and constructors
-keep class com.google.ai.edge.litertlm.** {
    <init>(...);
    native <methods>;
    *;
}
-keepclassmembers class com.google.ai.edge.litertlm.** {
    <fields>;
    <methods>;
}

# Keep native methods and classes containing native methods across the application
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
