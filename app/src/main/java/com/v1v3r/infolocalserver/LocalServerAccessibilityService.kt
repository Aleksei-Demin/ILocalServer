package com.v1v3r.infolocalserver

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("LocalServerAccessibilityService", "onServiceConnected called")

        server = LocalServer(8080, this)
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d("LocalServerAccessibilityService", "Server started successfully")
        } catch (e: IOException) {
            Log.e("LocalServerAccessibilityService", "Could not start server", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocalServerAccessibilityService", "onDestroy called")
        server.stop()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Необходимо для реализации AccessibilityService
    }

    override fun onInterrupt() {
        // Необходимо для реализации AccessibilityService
    }

    private class LocalServer(port: Int, private val context: Context) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val cpuTemp = getCpuTemperature()
            val memoryUsage = getMemoryUsage()

            // Форматируем вывод
            val response = """
                <html>
                <head>
                    <title>InfoLocalServer</title>
                    <style>
                        body {
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            height: 90vh;
                            font-size: 7em;
                            text-align: center;
                            background-color: black;
                            color: white;
                        }
                        .value {
                            margin: 40px 0;
                        }
                    </style>
                </head>
                <body>
                    <div>
                        <div class="value">${getLocalIpAddress() + ":8080"}</div>
                        <div class="value">CPU: $cpuTemp</div>
                        <div class="value">Memory: $memoryUsage%</div>
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
            return String.format("%.0f", memoryUsagePercent) // Без знаков после точки
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