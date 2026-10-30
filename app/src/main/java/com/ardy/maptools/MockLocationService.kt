package com.ardy.maptools

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat

class MockLocationService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"

        const val CHANNEL_ID = "mock_location_channel"
        const val NOTIF_ID = 1001

        var isRunning = false
        var currentLat = 0.0
        var currentLng = 0.0
    }

    private lateinit var locationManager: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    private val mockRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                pushMockLocation(currentLat, currentLng)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopMocking()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        currentLat = intent?.getDoubleExtra(EXTRA_LAT, -7.568220) ?: -7.568220
        currentLng = intent?.getDoubleExtra(EXTRA_LNG, 110.260163) ?: 110.260163

        startForeground(NOTIF_ID, createNotification(currentLat, currentLng))
        startMocking()

        return START_STICKY
    }

    private fun startMocking() {
        isRunning = true
        setupProviders()
        handler.removeCallbacks(mockRunnable)
        handler.post(mockRunnable)
    }

    private fun stopMocking() {
        isRunning = false
        handler.removeCallbacks(mockRunnable)
        removeProviders()
    }

    private fun setupProviders() {
        for (provider in providers) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager.addTestProvider(
                        provider,
                        false, false, false, false, true, true, true,
                        ProviderProperties.POWER_USAGE_LOW,
                        ProviderProperties.ACCURACY_FINE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.addTestProvider(
                        provider,
                        false, false, false, false, true, true, true,
                        1, 1
                    )
                }
                locationManager.setTestProviderEnabled(provider, true)
            } catch (e: Exception) {
                // Provider might already exist or mock location not enabled in developer options
            }
        }
    }

    private fun removeProviders() {
        for (provider in providers) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.clearTestProviderLocation(provider)
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }

    private fun pushMockLocation(lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()

        for (provider in providers) {
            try {
                val loc = Location(provider).apply {
                    latitude = lat
                    longitude = lng
                    altitude = 55.0
                    time = now
                    elapsedRealtimeNanos = elapsed
                    accuracy = 1.5f
                    bearing = 0.0f
                    speed = 0.0f

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        bearingAccuracyDegrees = 0.1f
                        verticalAccuracyMeters = 1.0f
                        speedAccuracyMetersPerSecond = 0.1f
                    }
                }
                locationManager.setTestProviderLocation(provider, loc)
            } catch (e: Exception) {
                // Ignore per-tick errors
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mock Location Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(lat: Double, lng: Double): Notification {
        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock GPS JNE Aktif")
            .setContentText("Lokasi palsu: %.5f, %.5f".format(lat, lng))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "HENTIKAN", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopMocking()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
