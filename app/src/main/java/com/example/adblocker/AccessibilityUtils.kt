package com.example.adblocker

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

fun isAdBlockerAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponent = ComponentName(context, AdBlockerAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()

    return enabledServices
        .split(':')
        .mapNotNull(ComponentName::unflattenFromString)
        .any { it == expectedComponent }
}
