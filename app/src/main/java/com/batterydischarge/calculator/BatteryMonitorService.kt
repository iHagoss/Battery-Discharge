package com.batterydischarge.calculator

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*

class BatteryMonitorService : Service() {
    
    companion object {
        private const val TAG = "BatteryMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "battery_discharge_channel"
        private const val CHANNEL_NAME = "Battery Discharge Calculator"
        private const val UPDATE_INTERVAL = 15000L // 15 seconds
        
        // Intent Actions
        const val ACTION_START_MONITORING = "com.batterydischarge.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.batterydischarge.STOP_MONITORING"
        const val ACTION_UPDATE_NOTIFICATION = "com.batterydischarge.UPDATE_NOTIFICATION"
    }
    
    private lateinit var batteryManager: BatteryHardwareManager
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var wakeLock: PowerManager.WakeLock
    
    private var serviceJob: Job? = null
    private var isMonitoring = false
    private var lastBatteryData: BatteryHardwareManager.BatteryData? = null
    
    private val batteryReceiver = BatteryStateReceiver()
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        batteryManager = BatteryHardwareManager(this)
        notificationManager = NotificationManagerCompat.from(this)
        
        // Create wake lock for background operation
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG::BatteryMonitorWakeLock"
        )
        
        createNotificationChannel()
        registerBatteryReceiver()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_UPDATE_NOTIFICATION -> updateNotification()
            else -> startMonitoring() // Default action
        }
        
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        stopMonitoring()
        unregisterBatteryReceiver()
        
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows battery discharge time remaining"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun registerBatteryReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(batteryReceiver, filter)
    }
    
    private fun unregisterBatteryReceiver() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering battery receiver", e)
        }
    }
    
    private fun startMonitoring() {
        if (isMonitoring) return
        
        Log.i(TAG, "Starting battery monitoring")
        isMonitoring = true
        
        // Acquire wake lock
        if (!wakeLock.isHeld) {
            wakeLock.acquire(10*60*1000L /*10 minutes*/)
        }
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createInitialNotification())
        
        // Start monitoring coroutine
        serviceJob = CoroutineScope(Dispatchers.Default).launch {
            while (isMonitoring) {
                try {
                    updateBatteryData()
                    delay(UPDATE_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop", e)
                    delay(5000) // Wait 5 seconds before retry
                }
            }
        }
    }
    
    private fun stopMonitoring() {
        if (!isMonitoring) return
        
        Log.i(TAG, "Stopping battery monitoring")
        isMonitoring = false
        
        serviceJob?.cancel()
        serviceJob = null
        
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        
        stopForeground(true)
        stopSelf()
    }
    
    private suspend fun updateBatteryData() {
        try {
            val batteryData = batteryManager.getCurrentBatteryData()
            lastBatteryData = batteryData
            
            // Only show notification when not charging
            if (!batteryData.isCharging) {
                updateDischargeNotification(batteryData)
            } else {
                // Hide notification when charging (system will show charge time)
                hideNotification()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating battery data", e)
        }
    }
    
    private fun updateNotification() {
        lastBatteryData?.let { data ->
            if (!data.isCharging) {
                updateDischargeNotification(data)
            }
        }
    }
    
    private fun createInitialNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Monitor")
            .setContentText("Initializing...")
            .setSmallIcon(R.drawable.ic_battery_discharge)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateDischargeNotification(data: BatteryHardwareManager.BatteryData) {
        val title = "Battery"
        val content = if (data.currentMilliAmps > 10) {
            "${data.estimatedTimeRemaining} remaining (${String.format("%.0f", data.currentMilliAmps)}mA)"
        } else {
            "Calculating discharge time..."
        }
        val subText = "${data.capacityPercent}%"
        
        // Create tap intent to open main activity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(subText)
            .setSmallIcon(getBatteryIcon(data.capacityPercent))
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(getBatteryColor(data.capacityPercent))
            .build()
        
        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for notification", e)
        }
    }
    
    private fun hideNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
    
    private fun getBatteryIcon(percentage: Int): Int {
        return when {
            percentage >= 90 -> R.drawable.ic_battery_90
            percentage >= 80 -> R.drawable.ic_battery_80
            percentage >= 60 -> R.drawable.ic_battery_60
            percentage >= 50 -> R.drawable.ic_battery_50
            percentage >= 30 -> R.drawable.ic_battery_30
            percentage >= 20 -> R.drawable.ic_battery_20
            else -> R.drawable.ic_battery_alert
        }
    }
    
    private fun getBatteryColor(percentage: Int): Int {
        return when {
            percentage >= 50 -> android.graphics.Color.GREEN
            percentage >= 20 -> android.graphics.Color.YELLOW
            else -> android.graphics.Color.RED
        }
    }
}
