package com.v1v3r.infolocalserver

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import android.app.ActivityManager
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
val feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK



class MainActivity : AppCompatActivity() {

    private lateinit var serverStatusTextView: TextView
    private val statusUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateServerStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val serverAddressTextView: TextView = findViewById(R.id.serverAddressTextView)
        serverStatusTextView = findViewById(R.id.serverStatusTextView)
        val copyAddressButton: Button = findViewById(R.id.copyAddressButton)
        val restartServerButton: Button = findViewById(R.id.restartServerButton)

        // Получение IP-адреса сервера и установка в TextView
        val serverAddress = getLocalIpAddress() + ":8080"
        serverAddressTextView.text = serverAddress
        Log.d("MainActivity", "Server address: $serverAddress")

        // Обработка нажатия на кнопку "Copy address"
        copyAddressButton.setOnClickListener {
            copyToClipboard(serverAddress)
            Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
        }

        // Запуск LocalServerService при создании MainActivity
        startService(Intent(this, LocalServerService::class.java))

        // Регистрируем BroadcastReceiver для получения обновлений статуса сервера
        registerReceiver(statusUpdateReceiver, IntentFilter("com.v1v3r.infolocalserver.STATUS_UPDATE"))

        // Обработка нажатия на кнопку "Restart server"
        restartServerButton.setOnClickListener {
            restartServer()
        }

        // Инициализация статуса сервера
        updateServerStatus()

    }

    override fun onStart() {
        super.onStart()
        // Обновляем статус сервера каждый раз при открытии окна
        updateServerStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Отменяем регистрацию BroadcastReceiver
        unregisterReceiver(statusUpdateReceiver)
    }

    // Метод для обновления статуса сервера
    private fun updateServerStatus() {
        when {
            isAccessibilityServiceRunning(LocalServerAccessibilityService::class.java) -> {
                serverStatusTextView.text = "Local server is working\nvia accessibility service"
            }
            isServiceRunning(LocalServerService::class.java) -> {
                serverStatusTextView.text = "Local server is working"
            }
            else -> {
                serverStatusTextView.text = "Local server is not working"
            }
        }
    }

    private fun restartServer() {
        if (isAccessibilityServiceRunning(LocalServerAccessibilityService::class.java)) {
            // Если сервис доступен, останавливаем и перезапускаем его
            stopService(Intent(this, LocalServerAccessibilityService::class.java))
            startService(Intent(this, LocalServerAccessibilityService::class.java))
        } else {
            // Иначе перезапускаем LocalServerService
            stopService(Intent(this, LocalServerService::class.java))
            startService(Intent(this, LocalServerService::class.java))
        }
        updateServerStatus() // Обновляем статус после перезапуска сервера
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

    private fun copyToClipboard(text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("Server Address", text)
        clipboardManager.setPrimaryClip(clipData)
    }

    private fun isAccessibilityServiceRunning(serviceClass: Class<*>): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (serviceInfo in enabledServices) {
            if (serviceInfo.resolveInfo.serviceInfo.packageName == packageName &&
                serviceInfo.resolveInfo.serviceInfo.name == serviceClass.name) {
                return true
            }
        }
        return false
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = activityManager.getRunningServices(Int.MAX_VALUE)

        for (service in services) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}