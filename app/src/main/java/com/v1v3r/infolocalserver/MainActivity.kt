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

        // Запуск LocalServerService при создании MainActivity
        startService(Intent(this, LocalServerService::class.java))

        val serverAddressTextView: TextView = findViewById(R.id.serverAddressTextView)
        serverStatusTextView = findViewById(R.id.serverStatusTextView)
        val copyAddressButton: Button = findViewById(R.id.copyAddressButton)

        val serverAddress = getLocalIpAddress() + ":8080"
        serverAddressTextView.text = serverAddress

        Log.d("MainActivity", "Server address: $serverAddress")

        // Обработка нажатия на кнопку "Copy address"
        copyAddressButton.setOnClickListener {
            copyToClipboard(serverAddress)
            Toast.makeText(this, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
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
}