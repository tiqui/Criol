# Modelos salvos em JSON (kotlinx.serialization)
-keep,includedescriptorclasses class app.criol.**$$serializer { *; }
-keepclassmembers class app.criol.** {
    *** Companion;
}
-keepclasseswithmembers class app.criol.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Serviço de reprodução referenciado pelo manifesto
-keep class app.criol.playback.PlaybackService { *; }
