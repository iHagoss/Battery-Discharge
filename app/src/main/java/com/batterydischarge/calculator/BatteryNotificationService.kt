package com.batterydischarge.calculator

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

class BatteryNotificationService : Service() {
    private val CHANNEL_ID = "battery_discharge_channel"
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification("Calculating..."))
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val info = readBatteryInfo()
                val notif = buildNotification(info)
                startForeground(1, notif)
                delay(15000) // update every 15 seconds
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Battery Discharge",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
    }

    private fun buildNotification(info: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery")
            .setContentText(info)
            .setSmallIcon(R.drawable.ic_battery)
            .setOngoing(true)
            .build()
    }

    private fun readBatteryInfo(): String {
        // Try to read current_now and charge_full_design using root
        val currentNow = tryReadFile("/sys/class/power_supply/battery/current_now")
        val chargeFull = tryReadFile("/sys/class/power_supply/battery/charge_full_design")
        val percent = getBatteryPercent()

        val currentMa = (currentNow?.toFloatOrNull() ?: 0f) / 1000f // Convert uA to mA

        val capacityMah = (chargeFull?.toFloatOrNull() ?: 4100000f) / 1000f // uAh to mAh, fallback to 4100mAh
        val remainingMah = capacityMah * percent / 100f
        val hours = if (currentMa > 0) remainingMah / currentMa else 0f

        val h = hours.toInt()
        val m = ((hours - h) * 60).toInt()
        return if (h > 0 || m > 0)
            "%dh %dm remaining (%.0fmA) — %d%%".format(h, m, currentMa, percent)
        else
            "Calculating... %d%%".format(percent)
    }

    private fun tryReadFile(path: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $path"))
            process.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun getBatteryPercent(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
