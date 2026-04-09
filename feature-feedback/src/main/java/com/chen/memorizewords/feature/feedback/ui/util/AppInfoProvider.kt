package com.chen.memorizewords.feature.feedback.ui.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 应用信息提供器
 * 用于安全、稳定地获取版本号等信息
 */
object AppInfoProvider {

    /**
     * 获取 versionName（如 2.4.0）
     */
    fun getVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            val info = pm.getPackageInfo(pkg, 0)
            info.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * 获取 versionCode（兼容 Android P+）
     */
    fun getVersionCode(context: Context): Long {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            val info = pm.getPackageInfo(pkg, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }
}