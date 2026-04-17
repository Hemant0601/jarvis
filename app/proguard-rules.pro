# Keep generics / annotations used by Moshi / Retrofit
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper

# MediaPipe GenAI + native
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Whisper JNI
-keep class io.github.givimad.whisperjni.** { *; }

# Google API client
-dontwarn com.google.api.**
-dontwarn com.google.auth.**
-dontwarn org.apache.http.**
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.** { *; }
