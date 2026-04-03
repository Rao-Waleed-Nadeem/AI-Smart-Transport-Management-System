package com.example.ai_smarttransportsystem.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.ai_smarttransportsystem.R
import com.example.ai_smarttransportsystem.data.repository.TrackingRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private val repository  = TrackingRepository()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP  = "ACTION_STOP_TRACKING"
        const val CHANNEL_ID   = "location_tracking_channel"
        const val NOTIF_ID     = 1001

        // GPS update interval — 10 seconds is accurate without draining battery
        private const val UPDATE_INTERVAL_MS   = 10_000L
        private const val FASTEST_INTERVAL_MS  = 5_000L
    }

    // ── Location callback — fires every UPDATE_INTERVAL_MS ───────────────────

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            serviceScope.launch {
                repository.updateMyLocation(
                    latitude  = loc.latitude,
                    longitude = loc.longitude,
                    speed     = if (loc.hasSpeed()) loc.speed else null
                )
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP  -> stopTracking()
            // null intent means system restarted the service after kill — resume tracking
            null         -> startTracking()
        }
        // START_STICKY: if killed by system, OS restarts with a null intent
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when the user swipes the app from Recents (task removed).
     * Re-deliver ACTION_START to ourselves so the service restarts automatically.
     * This keeps GPS sharing alive even after the supervisor closes the app.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Schedule an immediate restart via AlarmManager
        val restartIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_START
        }
        val pendingIntent = android.app.PendingIntent.getService(
            this, 1, restartIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(android.app.AlarmManager::class.java)
        alarmManager.set(
            android.app.AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000L,
            pendingIntent
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
        // DO NOT call clearMyLocation() here — onDestroy fires on both explicit stop
        // AND system kill. Only clear when supervisor explicitly taps "Stop Sharing"
        // (handled in stopTracking() below).
        serviceScope.cancel()
    }

    // ── Start GPS tracking ────────────────────────────────────────────────────

    private fun startTracking() {
        startForeground(NOTIF_ID, buildNotification())

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Permission was revoked — stop gracefully
            stopSelf()
        }
    }

    // ── Stop tracking (explicit — supervisor tapped "Stop Sharing") ──────────
    // This is the ONLY place we clear the Firestore location doc.
    // System kills / onDestroy do NOT clear it so students keep seeing the last position.

    private fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        serviceScope.launch { repository.clearMyLocation() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Persistent notification ───────────────────────────────────────────────

    private fun buildNotification(): Notification {
        // Tap notification → open supervisor dashboard
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Stop action inside notification
        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Sharing Active")
            .setContentText("Your bus location is being shared with students")
            .setSmallIcon(R.drawable.baseline_directions_bus_24)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.cancel, "Stop Sharing", stopPending)
            .setOngoing(true)          // can't be dismissed by swipe
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while supervisor is sharing bus location"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}