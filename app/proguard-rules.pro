# Keep entry points that are reached indirectly by external SDK callbacks.
-keep class com.chen.memorizewords.wxapi.WXEntryActivity { *; }

# Preserve Tencent SDK entry surfaces used for auth/share callbacks.
-keep class com.tencent.mm.opensdk.** { *; }
-keep class com.tencent.tauth.** { *; }

# Keep uCrop public entry points referenced through intents and manifests.
-keep class com.yalantis.ucrop.** { *; }

# Moshi's Kotlin reflection adapter invokes default-argument constructors through Kotlin metadata.
# These DTOs intentionally accept legacy payloads that omit newer floating-pet fields, so R8 must
# retain their synthetic default constructors in release builds.
-keepclassmembers class com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingSettingsSyncRequest {
    <init>(...);
}
-keepclassmembers class com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingSettingsDto {
    <init>(...);
}
-keepclassmembers class com.chen.memorizewords.data.sync.remoteapi.api.learningsync.FloatingDockStateDto {
    <init>(...);
}
