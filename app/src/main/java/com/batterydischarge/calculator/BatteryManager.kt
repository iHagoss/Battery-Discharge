package com.batterydischarge.calculator

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import kotlin.math.abs

class BatteryHardwareManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BatteryHardwareManager"
        
        // Galaxy S10+ Exynos (beyond2lte) Hardware Paths
        private val BATTERY_PATHS = arrayOf(
            "/sys/class/power_supply/battery",
            "/sys/class/power_supply/sec-fuelgauge",
            "/sys/class/power_supply/max77705-fuelgauge",
            "/sys/class/power_supply/s2mu004-fuelgauge"
        )
        
        private val CURRENT_PATHS = arrayOf(
            "current_now",
            "current_avg", 
            "present_current",
            "batt_current"
        )
        
        private val CAPACITY_PATHS = arrayOf(
            "charge_full_design",
            "charge_full",
            "energy_full_design",
            "batt_capacity"
        )
        
        // Galaxy S10+ SM-G975F Specifications
        private const val DESIGN_CAPACITY_MAH = 4100
        private const val DESIGN_CAPACITY_UAH = DESIGN_CAPACITY_MAH * 1000
        private const val UPDATE_INTERVAL_MS = 15000L // 15 seconds
    }
    
    data class BatteryData(
        val currentMicroAmps: Int,
        val currentMilliAmps: Double,
        val capacityPercent: Int,
        val remainingCapacityMah: Double,
        val designCapacityMah: Int,
        val isCharging: Boolean,
        val voltage: Int,
        val temperature: Int,
        val estimatedTimeRemaining: String,
        val powerDrawWatts: Double,
        val lastUpdated: Long = System.currentTimeMillis()
    )
    
    private var isRootAvailable = false
    private var optimalBatteryPath: String? = null
    private var optimalCurrentPath: String? = null
    private var optimalCapacityPath: String? = null
    
    init {
        checkRootAccess()
        findOptimalPaths()
    }
    
    private fun checkRootAccess() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                isRootAvailable = Shell.SU.available()
                Log.i(TAG, "Root access: ${if (isRootAvailable) "Available" else "Not available"}")
                
                if (isRootAvailable) {
                    // Grant additional permissions via root
                    Shell.SU.run(arrayOf(
                        "chmod 644 /sys/class/power_supply/battery/*",
                        "chown system:system /sys/class/power_supply/battery/*"
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking root access", e)
                isRootAvailable = false
            }
        }
    }
    
    private fun findOptimalPaths() {
        GlobalScope.launch(Dispatchers.IO) {
            // Find the best battery path
            for (basePath in BATTERY_PATHS) {
                if (File(basePath).exists()) {
                    optimalBatteryPath = basePath
                    Log.i(TAG, "Found battery path: $basePath")
                    break
                }
            }
            
            optimalBatteryPath?.let { basePath ->
                // Find current path
                for (currentFile in CURRENT_PATHS) {
                    val fullPath = "$basePath/$currentFile"
                    if (File(fullPath).exists()) {
                        optimalCurrentPath = fullPath
                        Log.i(TAG, "Found current path: $fullPath")
                        break
                    }
                }
                
                // Find capacity path
                for (capacityFile in CAPACITY_PATHS) {
                    val fullPath = "$basePath/$capacityFile"
                    if (File(fullPath).exists()) {
                        optimalCapacityPath = fullPath
                        Log.i(TAG, "Found capacity path: $fullPath")
                        break
                    }
                }
            }
        }
    }
    
    suspend fun getCurrentBatteryData(): BatteryData = withContext(Dispatchers.IO) {
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            
            // Get basic battery info
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            
            val batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
            
            // Get hardware current reading
            val currentMicroAmps = getHardwareCurrentReading()
            val currentMilliAmps = abs(currentMicroAmps) / 1000.0
            
            // Calculate remaining capacity
            val designCapacity = getDesignCapacity()
            val remainingCapacity = (designCapacity * batteryPercent / 100.0)
            
            // Calculate time remaining
            val timeRemaining = if (!isCharging && currentMilliAmps > 10) {
                calculateTimeRemaining(remainingCapacity, currentMilliAmps)
            } else "N/A"
            
            // Calculate power draw
            val powerDrawWatts = (currentMilliAmps * voltage / 1000.0) / 1000.0
            
            BatteryData(
                currentMicroAmps = currentMicroAmps,
                currentMilliAmps = currentMilliAmps,
                capacityPercent = batteryPercent,
                remainingCapacityMah = remainingCapacity,
                designCapacityMah = designCapacity,
                isCharging = isCharging,
                voltage = voltage,
                temperature = temperature,
                estimatedTimeRemaining = timeRemaining,
                powerDrawWatts = powerDrawWatts
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery data", e)
            // Return fallback data
            BatteryData(
                currentMicroAmps = 0,
                currentMilliAmps = 0.0,
                capacityPercent = 0,
                remainingCapacityMah = 0.0,
                designCapacityMah = DESIGN_CAPACITY_MAH,
                isCharging = false,
                voltage = 0,
                temperature = 0,
                estimatedTimeRemaining = "Error",
                powerDrawWatts = 0.0
            )
        }
    }
    
    private fun getHardwareCurrentReading(): Int {
        // Try multiple methods to get current reading
        
        // Method 1: Direct file reading with root
        optimalCurrentPath?.let { path ->
            try {
                if (isRootAvailable) {
                    val result = Shell.SU.run("cat $path")
                    if (result.isNotEmpty()) {
                        return result[0].trim().toIntOrNull() ?: 0
                    }
                } else {
                    // Try direct file reading
                    val value = File(path).readText().trim()
                    return value.toIntOrNull() ?: 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read from $path", e)
            }
        }
        
        // Method 2: Android BatteryManager (API 21+)
        try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } catch (e: Exception) {
            Log.w(TAG, "BatteryManager current reading failed", e)
        }
        
        // Method 3: Root fallback with alternative paths
        if (isRootAvailable) {
            for (basePath in BATTERY_PATHS) {
                for (currentFile in CURRENT_PATHS) {
                    try {
                        val result = Shell.SU.run("cat $basePath/$currentFile")
                        if (result.isNotEmpty()) {
                            val value = result[0].trim().toIntOrNull()
                            if (value != null && value != 0) {
                                return value
                            }
                        }
                    } catch (e: Exception) {
                        // Continue to next path
                    }
                }
            }
        }
        
        Log.w(TAG, "All current reading methods failed, returning 0")
        return 0
    }
    
    private fun getDesignCapacity(): Int {
        // Try to get actual design capacity
        optimalCapacityPath?.let { path ->
            try {
                if (isRootAvailable) {
                    val result = Shell.SU.run("cat $path")
                    if (result.isNotEmpty()) {
                        val value = result[0].trim().toIntOrNull()
                        if (value != null && value > 1000) {
                            // Convert from microAh to mAh if needed
                            return if (value > 100000) value / 1000 else value
                        }
                    }
                } else {
                    val value = File(path).readText().trim().toIntOrNull()
                    if (value != null && value > 1000) {
                        return if (value > 100000) value / 1000 else value
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read capacity from $path", e)
            }
        }
        
        // Fallback to known Galaxy S10+ capacity
        Log.i(TAG, "Using hardcoded Galaxy S10+ capacity: ${DESIGN_CAPACITY_MAH}mAh")
        return DESIGN_CAPACITY_MAH
    }
    
    private fun calculateTimeRemaining(remainingCapacity: Double, currentDraw: Double): String {
        if (currentDraw <= 0) return "N/A"
        
        val hoursRemaining = remainingCapacity / currentDraw
        val hours = hoursRemaining.toInt()
        val minutes = ((hoursRemaining - hours) * 60).toInt()
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }
    
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Battery Hardware Debug Info ===")
            appendLine("Root Available: $isRootAvailable")
            appendLine("Optimal Battery Path: $optimalBatteryPath")
            appendLine("Optimal Current Path: $optimalCurrentPath")
            appendLine("Optimal Capacity Path: $optimalCapacityPath")
            appendLine("Design Capacity: ${DESIGN_CAPACITY_
