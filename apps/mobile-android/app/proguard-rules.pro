# kotlinx.serialization keeps its generated serializers on the companion; R8's
# default rules cover the annotation but not the reflective lookup Retrofit's
# converter does for a @Serializable return type.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.omaykan.storefront.core.network.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.omaykan.storefront.core.network.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit reads generic return types off the interface at runtime.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
