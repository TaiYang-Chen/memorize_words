# Keep entry points that are reached indirectly by external SDK callbacks.
-keep class com.chen.memorizewords.wxapi.WXEntryActivity { *; }

# Preserve Tencent SDK entry surfaces used for auth/share callbacks.
-keep class com.tencent.mm.opensdk.** { *; }
-keep class com.tencent.tauth.** { *; }

# Keep uCrop public entry points referenced through intents and manifests.
-keep class com.yalantis.ucrop.** { *; }
