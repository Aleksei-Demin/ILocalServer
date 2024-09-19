package com.v1v3r.infolocalserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, starting LocalServerAccessibilityService")
            JobIntentService.enqueueWork(context, LocalServerAccessibilityService::class.java, 0, Intent(context, LocalServerAccessibilityService::class.java))
        }
    }
}
