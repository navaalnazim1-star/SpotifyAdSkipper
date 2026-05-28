package com.adskipper.spotify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import rikka.shizuku.Shizuku

class AdSkipperService : NotificationListenerService() {

    companion object {
        private const val TAG = "AdSkipperService"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
        private const val CHANNEL_ID = "ad_skipper_channel"
        private const val NOTIFICATION_ID = 1
        private const val AD_RESTART_DELAY = 3000L
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Running — listening for Spotify ads..."))
        Log.d(TAG, "AdSkipperService created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName != SPOTIFY_PACKAGE) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val artist = extras.getString(Notification.EXTRA_TEXT) ?: ""

        Log.d(TAG, "Spotify notification — Title: $title | Artist: $artist")

        if (isAd(title, artist)) {
            Log.d(TAG, "Ad detected! Restarting Spotify...")
            updateForegroundNotification("Ad detected! Restarting Spotify...")
            restartSpotify()
        }
    }

    private fun isAd(title: String, artist: String): Boolean {
        val lowerArtist = artist.lowercase().trim()
        val lowerTitle = title.lowercase().trim()

        // Ignore if title is empty
        if (lowerTitle.isEmpty()) return false

        // Only flag explicit ad strings
        val adIndicators = listOf("advertisement", "spotify free", "audio ad", "sponsored")
        for (indicator in adIndicators) {
            if (lowerArtist.contains(indicator) || lowerTitle.contains(indicator)) return true
        }

        return false
    }

    private fun runShizukuCommand(vararg args: String) {
        val process = Shizuku::class.java
            .getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            .apply { isAccessible = true }
            .invoke(null, args, null, null) as Process
        process.waitFor()
    }

    private fun restartSpotify() {
        handler.post {
            try {
                if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    runShizukuCommand("am", "force-stop", SPOTIFY_PACKAGE)
                    Log.d(TAG, "Spotify force stopped via Shizuku")
                } else {
                    updateForegroundNotification("Shizuku not available — please check Shizuku app")
                    return@post
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping Spotify: ${e.message}")
            }
        }

        handler.postDelayed({
            try {
                runShizukuCommand("monkey", "-p", SPOTIFY_PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
                Log.d(TAG, "Spotify relaunched via Shizuku")
                updateForegroundNotification("Running — listening for Spotify ads...")
            } catch (e: Exception) {
                Log.e(TAG, "Error launching Spotify: ${e.message}")
            }
        }, AD_RESTART_DELAY)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spotify Ad Skipper",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the ad skipper running in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spotify Ad Skipper")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateForegroundNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(message))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AdSkipperService destroyed")
    }
}
