# Equerry ProGuard / R8 rules.
# Keep kotlinx.serialization generated serializers for provider request/response models.
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers,allowobfuscation class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit / OkHttp keep rules are bundled with the libraries (consumer rules); nothing
# extra is needed here for v1. Add app-specific keeps as drivers are implemented.
