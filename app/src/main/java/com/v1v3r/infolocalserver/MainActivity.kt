package com.v1v3r.infolocalserver

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.text.TextUtils
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var serverStatusTextView: TextView
    private val statusUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: "Local server is not working"
            serverStatusTextView.text = status
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
            Toast.makeText(this, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        // Запуск LocalServerService при создании MainActivity
        startService(Intent(this, LocalServerService::class.java))

        // Обработка нажатия на кнопку "Restart server"
        restartServerButton.setOnClickListener {
            restartServer()
        }

        // Регистрируем BroadcastReceiver для получения обновлений статуса сервера
        registerReceiver(statusUpdateReceiver, IntentFilter("com.v1v3r.infolocalserver.STATUS_UPDATE"))
    }

    override fun onDestroy() {
        super.onDestroy()
        // Отменяем регистрацию BroadcastReceiver
        unregisterReceiver(statusUpdateReceiver)
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

    private fun restartServer() {
        // Остановка LocalServerAccessibilityService, если он запущен
        try {
            val accessibilityServiceIntent = Intent(this, LocalServerAccessibilityService::class.java)
            stopService(accessibilityServiceIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "LocalServerAccessibilityService is not running")
        }

        // Запуск LocalServerAccessibilityService
        if (isAccessibilityServiceRunning(LocalServerAccessibilityService::class.java)) {
            startService(Intent(this, LocalServerAccessibilityService::class.java))
            Toast.makeText(this, "LocalServerAccessibilityService restarted", Toast.LENGTH_SHORT).show()
        } else {
            // Если LocalServerAccessibilityService не запущен, то запускаем LocalServerService
            startService(Intent(this, LocalServerService::class.java))
            Toast.makeText(this, "LocalServerService restarted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAccessibilityServiceRunning(serviceClass: Class<out AccessibilityService>): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        val colonSplitter = enabledServices.split(":").toList()

        for (componentName in colonSplitter) {
            if (componentName.equals(serviceClass.name, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
