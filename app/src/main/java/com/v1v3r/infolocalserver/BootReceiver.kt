package com.v1v3r.infolocalserver

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, checking Accessibility Service status")

            // Проверяем, включён ли LocalServerAccessibilityService
            if (isAccessibilityServiceEnabled(context, LocalServerAccessibilityService::class.java)) {
                Log.d("BootReceiver", "Accessibility Service is enabled, starting LocalServerAccessibilityService")
                val serviceIntent = Intent(context, LocalServerAccessibilityService::class.java)
                serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)  // Для работы в фоновом режиме
                context.startService(serviceIntent)
            } else {
                Log.d("BootReceiver", "Accessibility Service is not enabled, starting LocalServerService")
                context.startService(Intent(context, LocalServerService::class.java))
            }
        }
    }

    // Метод для проверки, включен ли Accessibility Service
    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<out AccessibilityService>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (serviceInfo in enabledServices) {
            if (serviceInfo.resolveInfo.serviceInfo.packageName == context.packageName &&
                serviceInfo.resolveInfo.serviceInfo.name == accessibilityService.name) {
                return true
            }
        }

        return false
    }
}
