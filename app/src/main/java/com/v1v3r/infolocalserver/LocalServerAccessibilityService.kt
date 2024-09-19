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

        // Запуск локального сервера
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
        // Обработка событий сервиса доступности (если требуется)
    }

    override fun onInterrupt() {
        // Реализация прерывания сервиса
    }

    // Метод для отправки статуса
    private fun updateStatus(status: String) {
        val intent = Intent("com.v1v3r.infolocalserver.STATUS_UPDATE")
        intent.putExtra("status", status)
        sendBroadcast(intent)
    }

    // Класс локального сервера
    private class LocalServer(port: Int, private val context: Context) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val cpuTemp = getCpuTemperature()
            val memoryUsage = getMemoryUsage()

            // Форматирование HTML ответа
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
                            padding-top: 60px; /* Оставляем место для панели статуса */
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
                            z-index: 1000; /* Панель статуса поверх контента */
                        }
                    </style>
                </head>
                <body>
                    <div class="status-bar">${getLocalIpAddress() + ":8080 (Accessibility service)"}</div> 
                    <div>
                        <div class="value">CPU: $cpuTemp</div>
                        <div class="value">RAM: $memoryUsage</div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            return newFixedLengthResponse(response)
        }

        // Метод для получения температуры CPU
        private fun getCpuTemperature(): String {
            return try {
                val reader = BufferedReader(FileReader("/sys/class/thermal/thermal_zone0/temp"))
                val temp = reader.readLine().toDouble() / 1000
                reader.close()
                String.format("%.1f°C", temp) // Округляем до десятых
            } catch (e: Exception) {
                Log.e("LocalServerAccessibilityService", "Could not read CPU temperature", e)
                "N/A"
            }
        }

        // Метод для получения информации о памяти
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

        // Метод для получения локального IP-адреса
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
                Log.e("LocalServerAccessibilityService", "Could not get local IP address", ex)
            }
            return "127.0.0.1"
        }
    }
}
