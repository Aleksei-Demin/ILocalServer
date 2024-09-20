package com.v1v3r.ilocalserver

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.BatteryManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import android.app.ActivityManager
import java.net.Inet4Address
import java.net.NetworkInterface

class LocalServerAccessibilityService : AccessibilityService() {

    private lateinit var server: LocalServer
    private var serverStartTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("LocalServerAccessibilityService", "onServiceConnected called")

        serverStartTime = System.currentTimeMillis()  // Запоминаем время запуска сервера

        server = LocalServer(8080, this)
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d("LocalServerAccessibilityService", "Server started successfully")
            updateStatus("Local server is running\nvia Accessibility service")
        } catch (e: IOException) {
            Log.e("LocalServerAccessibilityService", "Could not start server", e)
            updateStatus("Local server is not working\nvia Accessibility service")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocalServerAccessibilityService", "onDestroy called")
        server.stop()
        updateStatus("Local server is not working\nvia Accessibility service")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Необходимо для реализации AccessibilityService
    }

    override fun onInterrupt() {
        // Необходимо для реализации AccessibilityService
    }

    private fun updateStatus(status: String) {
        val intent = Intent("com.v1v3r.ilocalserver.STATUS_UPDATE")
        intent.putExtra("status", status)
        sendBroadcast(intent)
    }

    private inner class LocalServer(port: Int, private val context: Context) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val cpuTemp = getCpuTemperature()
            val memoryUsage = getMemoryUsage()
            val batteryLevel = getBatteryLevel()

            val batteryHtml = if (batteryLevel != "N/A") {
                "<div class=\"value\">Battery: $batteryLevel</div>"
            } else {
                "" // Не отображать строку, если значение N/A
            }

            val response = """
                <html>
                <head>
                    <title>ILocalServer</title>
                    <style>
                        body {
                            display: flex;
                            flex-direction: column;
                            justify-content: center;
                            align-items: center;
                            min-height: 100vh;
                            margin: 0;
                            text-align: center;
                            background-color: black;
                            color: white;
                            font-size: 4em;
                        }
                        .value {
                            margin: 20px 0;
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
                            z-index: 1000;
                        }
                        .restart-btn {
                            margin-top: 20px;
                            padding: 20px 30px;
                            font-size: 0.8em;
                            background-color: grey;
                            color: white;
                            border: none;
                            cursor: pointer;
                        }
                        .address-info {
                            position: fixed;
                            bottom: 0;
                            width: 100vw;
                            background-color: black;
                            color: white;
                            padding: 20px 0;
                            text-align: center;
                            border-top: 3px solid lightgrey;
                            z-index: 1000;
                        }
                    </style>
                    <script>
                        function confirmRestart() {
                            if (confirm('Are you sure you want to reboot the device?')) {
                                fetch('/restart')
                                    .then(() => alert('Rebooting the device...'))
                                    .catch(() => alert('Failed to reboot the device'));
                            }
                        }
                    </script>
                </head>
                <body>
                    <div class="status-bar">
                        Server uptime:<br>${getServerUptime()}
                    </div>
                    <div>
                        <div class="value">CPU: $cpuTemp</div>
                        <div class="value">$batteryHtml</div>
                        <div class="value">RAM: $memoryUsage</div>
                        <br><button class="restart-btn" onclick="confirmRestart()">Reboot device</button>
                    </div>
                    <div class="address-info">${getLocalIpAddress() + ":8080<br>Accessibility service is on"}</div>
                </body>
                </html>
            """.trimIndent()

            // Handle device restart request
            if (session.uri == "/restart") {
                rebootDevice()
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "Device is rebooting...")
            }

            return newFixedLengthResponse(response)
        }

        private fun getServerUptime(): String {
            val currentTime = System.currentTimeMillis()
            val uptimeMillis = currentTime - serverStartTime

            val seconds = (uptimeMillis / 1000) % 60
            val minutes = (uptimeMillis / (1000 * 60)) % 60
            val hours = (uptimeMillis / (1000 * 60 * 60)) % 24
            val days = uptimeMillis / (1000 * 60 * 60 * 24)

            return when {
                days > 0 -> "$days days $hours hours"
                hours > 0 -> "$hours hours $minutes minutes"
                else -> "$minutes minutes $seconds seconds"
            }
        }

        private fun rebootDevice() {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
                process.waitFor()
            } catch (e: Exception) {
                Log.e("LocalServerAccessibilityService", "Reboot failed", e)
            }
        }

        private fun getCpuTemperature(): String {
            return try {
                val reader = BufferedReader(FileReader("/sys/class/thermal/thermal_zone0/temp"))
                val temp = reader.readLine().toDouble() / 1000
                reader.close()
                String.format("%.1f°C", temp)
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

            return String.format("%.2f Gb / %.2f Gb<br>(used %.0f%%)", usedMemoryGb, totalMemoryGb, memoryUsagePercent)
        }

        private fun getBatteryLevel(): String {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            return if (batteryLevel != Integer.MIN_VALUE) {
                "$batteryLevel%"
            } else {
                "N/A"
            }
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
