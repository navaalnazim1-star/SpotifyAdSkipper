package com.adskipper.spotify

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var permissionButton: Button
    private lateinit var toggleButton: Button

    private val shizukuRequestCode = 1001

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        permissionButton = findViewById(R.id.permissionButton)
        toggleButton = findViewById(R.id.toggleButton)

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        permissionButton.setOnClickListener {
            when {
                !isNotificationServiceEnabled() -> {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                !isShizukuGranted() -> {
                    if (Shizuku.pingBinder()) {
                        Shizuku.requestPermission(shizukuRequestCode)
                    } else {
                        statusText.text = "⚠️ Shizuku is not running. Please open the Shizuku app and start it."
                    }
                }
            }
        }

        toggleButton.setOnClickListener {
            if (isNotificationServiceEnabled() && isShizukuGranted()) {
                stopService(Intent(this, AdSkipperService::class.java))
            }
            updateUI()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        if (isNotificationServiceEnabled() && isShizukuGranted()) {
            startService(Intent(this, AdSkipperService::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    private fun updateUI() {
        val notifGranted = isNotificationServiceEnabled()
        val shizukuGranted = isShizukuGranted()
        val shizukuRunning = Shizuku.pingBinder()

        when {
            !notifGranted -> {
                statusText.text = "⚠️ Step 1: Grant notification access"
                permissionButton.text = "Grant Notification Access"
                permissionButton.isEnabled = true
            }
            !shizukuRunning -> {
                statusText.text = "⚠️ Step 2: Open Shizuku app and start it"
                permissionButton.text = "Waiting for Shizuku..."
                permissionButton.isEnabled = false
            }
            !shizukuGranted -> {
                statusText.text = "⚠️ Step 2: Grant Shizuku permission"
                permissionButton.text = "Grant Shizuku Permission"
                permissionButton.isEnabled = true
            }
            else -> {
                statusText.text = "✅ Active — Listening for Spotify ads"
                permissionButton.text = "All Permissions Granted ✓"
                permissionButton.isEnabled = false
                toggleButton.text = "Stop Service"
            }
        }
    }

    private fun isShizukuGranted(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == packageName) return true
            }
        }
        return false
    }
}
