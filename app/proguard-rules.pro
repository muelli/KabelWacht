# WireGuard tunnel library uses JNI (wireguard-go). Keep its classes/native methods.
-keep class com.wireguard.** { *; }
-keepclasseswithmembernames class com.wireguard.** {
    native <methods>;
}

# ZXing embedded scanner reflects on some capture classes.
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# The WireGuard library carries JSR-305 (@NonNullForAll) annotations that are
# compile-time only and absent at runtime. They are safe to ignore.
-dontwarn javax.annotation.**
