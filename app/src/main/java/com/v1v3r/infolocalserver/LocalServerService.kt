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
import java.net.Inet4Address
import java.net.NetworkInterface

class LocalServerService : Service() {

    private lateinit var server: LocalServer

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocalServerService", "onStartCommand called")
        server = LocalServer(8080, this) // Pass context to LocalServer
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d("LocalServerService", "Server started successfully")
            updateStatus("Local server is running")
        } catch (e: IOException) {
            Log.e("LocalServerService", "Could not start server", e)
            updateStatus("Local server is not working")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("LocalServerService", "onDestroy called")
        server.stop()
        updateStatus("Local server is not working")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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

            // Format the output
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
                            padding-top: 60px; /* Space for content below status bar */
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
                            z-index: 1000; /* Ensures the status bar is on top */
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
                String.format("%.1f°C", temp) // One decimal place
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