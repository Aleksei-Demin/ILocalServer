package com.v1v3r.infolocalserver

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import android.app.ActivityManager
import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface

class LocalServerAccessibilityService : AccessibilityService() {

    private lateinit var server: LocalServer
    private var isServerRunning: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("LocalServerAccessibilityService", "onServiceConnected called")

        server = LocalServer(8080, this)
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            isServerRunning = true
            updateStatus("Local Server is running")
            Log.d("LocalServerAccessibilityService", "Local server is running")
        } catch (e: IOException) {
            Log.e("LocalServerAccessibilityService", "Could not start server", e)
            updateStatus("Local server is not working")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocalServerAccessibilityService", "onDestroy called")
        server.stop()
        isServerRunning = false
        updateStatus("Local server is not working")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Необходимо для реализации AccessibilityService
    }

    override fun onInterrupt() {
        // Необходимо для реализации AccessibilityService
    }

    private fun updateStatus(status: String) {
        val intent = Intent("com.v1v3r.infolocalserver.STATUS_UPDATE")
        intent.putExtra("status", status)
        sendBroadcast(intent)
    }

    private class LocalServer(port: Int, private val context: Context) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val cpuTemp = getCpuTemperature()
            val memoryUsage = getMemoryUsage()

            val response = """
                <html>
                <head>
                    <title>InfoLocalServer</title>
                    <style>
                        body {
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            font-size: 4em;
                            text-align: center;
                            background-color: black;
                            color: white;
                            margin: 0;
                            padding-top: 60px; /* Отступ для контента под статус-баром */
                        }
                        .value {
                            margin: 40px 0;
                        }
                        .status-bar {
                            position: fixed;
                            top: 0;
                            width: 100vw;
                            background-color: black;
                            color: white;
                            padding: 20px 0;
                            text-align: center;
                            border-bottom: 3px solid lightgrey;
                            z-index: 1000; /* Чтобы статус-бар был поверх другого контента */
                        }
                    </style>
                </head>
                <body>
                    <div class="status-bar">${getLocalIpAddress() + ":8080"}</div>
                    <div>
                        <div class="value">CPU: $cpuTemp</div>
                        <div class="value">RAM: $memoryUsage</div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            return newFixedLengthResponse(response)
        }

        private fun getCpuTemperature(): String {
            return try {
                val reader = BufferedReader(FileReader("/sys/class/thermal/thermal_zone0/temp"))
                val temp = reader.readLine().toDouble() / 1000
                reader.close()
                String.format("%.1f°C", temp) // Один знак после точки
            } catch (e: Exception) {
                Log.e("LocalServerAccessibilityService", "Could not read CPU temperature", e)
                "N/A"
            }
        }

        private fun getMemoryUsage(): String {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val totalMemory = memoryInfo.totalMem
            val availableMemory = memoryInfo.availMem
            val usedMemory = totalMemory - availableMemory

            val memoryUsagePercent = (usedMemory.toDouble() / totalMemory) * 100

            val usedMemoryGb = usedMemory / (1024.0 * 1024.0 * 1024.0)
            val totalMemoryGb = totalMemory / (1024.0 * 1024.0 * 1024.0)

            return String.format("%.2f GB / %.2f GB<br>(used %.0f%%)", usedMemoryGb, totalMemoryGb, memoryUsagePercent)
        }

        private fun getLocalIpAddress(): String {
            try {
                val en = NetworkInterface.getNetworkInterfaces()
                while (en.hasMoreElements()) {
                    val intf = en.nextElement()
                    val enumIpAddr = intf.inetAddresses
                    while (enumIpAddr.hasMoreElements()) {
                        val inetAddress = enumIpAddr.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                            return inetAddress.hostAddress
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return "127.0.0.1"
        }
    }
}
