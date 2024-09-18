package com.v1v3r.infolocalserver

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class LocalServerService : Service() {

    private lateinit var server: LocalServer

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocalServerService", "onStartCommand called")
        server = LocalServer(8080)
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

    private class LocalServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val cpuTemp = getCpuTemperature()
            val memoryUsage = getMemoryUsage()
            val response = "CPU Temperature: $cpuTemp\nMemory Usage: $memoryUsage%"
            return newFixedLengthResponse(response)
        }

        private fun getCpuTemperature(): String {
            // Здесь должен быть код для получения температуры процессора
            // В данном примере возвращаем фиктивное значение
            return "50°C"
        }

        private fun getMemoryUsage(): String {
            // Здесь должен быть код для получения загрузки оперативной памяти
            // В данном примере возвращаем фиктивное значение
            return "60"
        }
    }
}