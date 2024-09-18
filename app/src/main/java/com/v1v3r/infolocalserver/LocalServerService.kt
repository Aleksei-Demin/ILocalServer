package com.v1v3r.infolocalserver

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import android.app.ActivityManager
import android.content.Context

class LocalServerService : Service() {

    private lateinit var server: LocalServer

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocalServerService", "onStartCommand called")
        server = LocalServer(8080, this) // Передаем контекст в LocalServer
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d("LocalServerService", "Server started successfully")
        } catch (e: IOException) {
            Log.e("LocalServerService", "Could not start server", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocalServerService", "onDestroy called")
        server.stop()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private class LocalServer(port: Int, private val context: Context) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val cpuTemp = getCpuTemperature()
            val memoryUsage = getMemoryUsage()

            // Форматируем вывод
            val response = """
                <html>
                <head>
                    <style>
                        body {
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            height: 100vh;
                            font-size: 3em;
                            text-align: center;
                        }
                        .value {
                            margin: 20px 0;
                        }
                    </style>
                </head>
                <body>
                    <div>
                        <div class="value">CPU Temperature: $cpuTemp</div>
                        <div class="value">Memory Usage: $memoryUsage%</div>
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
                Log.e("LocalServerService", "Could not read CPU temperature", e)
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
    }
}