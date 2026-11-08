# kotlinx.serialization ships its own consumer rules; keep our @Serializable models explicit anyway.
-keepclassmembers @kotlinx.serialization.Serializable class com.alphasim.brain.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.alphasim.brain.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
