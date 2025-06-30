package com.batterydischarge.calculator

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.batterydischarge.calculator.databinding.ActivityMainBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var batteryManager: BatteryHardwareManager
    
    private val currentReadings = mutableListOf<Entry>()
    private val timeReadings = mutableListOf<Entry>()
    private var startTime = System.currentTimeMillis()
    private var updateJob: Job? = null
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startBatteryService()
        } else {
            showPermissionDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setSupportActionBar(binding.toolbar)
        
        batteryManager = BatteryHardwareManager(this)
        
        setupUI()
        checkPermissions()
        setupChart()
    }
    
    override fun onResume() {
        super.onResume()
        startDataUpdates()
    }
    
    override fun onPause() {
        super.onPause()
        stopDataUpdates()
    }
    
    private fun setupUI() {
        // Device info
        binding.deviceModel.text = "Galaxy S10+ (SM-G975F)"
        binding.deviceCodename.text = "beyond2lte • Exynos"
        binding.romInfo.text = "Nexus ROM OneUI 7 • Extreme Kernel"
        
        // Button listeners
        binding.btnStartService.setOnClickListener {
            if (checkAllPermissions()) {
                startBatteryService()
            } else {
                requestPermissions()
            }
        }
        
        binding.btnStopService.setOnClickListener {
            stopBatteryService()
        }
        
        binding.btnRefresh.setOnClickListener {
            refreshBatteryData()
        }
        
        binding.btnDebugInfo.setOnClickListener {
            showDebugInfo()
        }
        
        // Root check
        lifecycleScope.launch(Dispatchers.IO) {
            val hasRoot = Shell.SU.available()
            withContext(Dispatchers.Main) {
                updateRootStatus(hasRoot)
            }
        }
    }
    
    private fun setupChart() {
        with(binding.batteryChart) {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
            }
            
            axisRight.isEnabled = false
            legend.isEnabled = true
        }
    }
    
    private fun updateRootStatus(hasRoot: Boolean) {
        binding.rootStatus.text = if (hasRoot) "✓ Root Access" else "✗ No Root Access"
        binding.rootStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (hasRoot) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
        
        if (!hasRoot) {
            binding.rootWarning.text = "⚠️ Root access required for accurate hardware readings"
        } else {
            binding.rootWarning.text = "Hardware-level battery monitoring enabled"
        }
    }
    
    private fun checkAllPermissions(): Boolean {
        val requiredPermissions = mutableListOf<String>()
        
        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Battery optimization whitelist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // Will handle this separately
            }
        }
        
        return requiredPermissions.isEmpty()
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    showPermissionDialog()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        
        // Request battery optimization exemption
        requestBatteryOptimizationExemption()
    }
    
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not request battery optimization exemption", e)
                }
            }
        }
    }
    
    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs notification permission to display battery discharge time in the status bar, similar to how charging time is shown.")
            .setPositiveButton("Grant") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                Toast.makeText(this, "App will work but notifications won't be shown", Toast.LENGTH_LONG).show()
            }
            .show()
    }
    
    private fun startBatteryService() {
        val intent = Intent(this, BatteryMonitorService::class.java).apply {
            action = BatteryMonitorService.ACTION_START_MONITORING
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        binding.serviceStatus.text = "✓ Service Running"
        binding.serviceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        
        Toast.makeText(this, "Battery monitoring started", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopBatteryService() {
        val intent = Intent(this, BatteryMonitorService::class.java).apply {
            action = BatteryMonitorService.ACTION_STOP_MONITORING
        }
        startService(intent)
        
        binding.serviceStatus.text = "✗ Service Stopped"
        binding.serviceStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        
        Toast.makeText(this, "Battery monitoring stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun startDataUpdates() {
        updateJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    updateBatteryDisplay()
                    delay(5000) // Update every 5 seconds in UI
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating battery display", e)
                    delay(10000) // Wait longer on error
                }
            }
        }
    }
    
    private fun stopDataUpdates() {
        updateJob?.cancel()
        updateJob = null
    }
    
    private suspend fun updateBatteryDisplay() {
        val batteryData = batteryManager.getCurrentBatteryData()
        
        withContext(Dispatchers.Main) {
            // Update main display
            binding.batteryLevel.text = "${batteryData.capacityPercent}%"
            binding.currentDraw.text = String.format("%.1f mA", batteryData.currentMilliAmps)
            binding.remainingCapacity.text = String.format("%.0f mAh", batteryData.remainingCapacityMah)
            binding.designCapacity.text = "${batteryData.designCapacityMah} mAh"
            binding.voltage.text = String.format("%.2f V", batteryData.voltage / 1000.0)
            binding.temperature.text = String.format("%.1f°C", batteryData.temperature / 10.0)
            binding.powerDraw.text = String.format("%.2f W", batteryData.powerDrawWatts)
            
            // Time remaining
            if (batteryData.isCharging) {
                binding.timeRemaining.text = "Charging"
                binding.timeRemaining.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
            } else {
                binding.timeRemaining.text = batteryData.estimatedTimeRemaining
                binding.timeRemaining.setTextColor(
                    when {
                        batteryData.capacityPercent > 50 -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark)
                        batteryData.capacityPercent > 20 -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark)
                        else -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark)
                    }
                )
            }
            
            // Update chart
            updateChart(batteryData)
        }
    }
    
    private fun updateChart(data: BatteryHardwareManager.BatteryData) {
        val timeElapsed = (System.currentTimeMillis() - startTime) / 1000f / 60f // minutes
        
        currentReadings.add(Entry(timeElapsed, data.currentMilliAmps.toFloat()))
        
        // Keep only last 60 readings (5 minutes at 5-second intervals)
        if (currentReadings.size > 60) {
            currentReadings.removeAt(0)
        }
        
        val dataSet = LineDataSet(currentReadings, "Current Draw (mA)").apply {
            color = ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark)
            setCircleColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))
            lineWidth = 2f
            circleRadius = 3f
            setDrawCircleHole(false)
            valueTextSize = 9f
            setDrawFilled(true)
            fillAlpha = 50
        }
        
        binding.batteryChart.data = LineData(dataSet)
        binding.batteryChart.notifyDataSetChanged()
        binding.batteryChart.invalidate()
    }
    
    private fun refreshBatteryData() {
        lifecycleScope.launch {
            try {
                updateBatteryDisplay()
                Toast.makeText(this@MainActivity, "Battery data refreshed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error refreshing data", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showDebugInfo() {
        lifecycleScope.launch(Dispatchers.IO) {
            val debugInfo = batteryManager.getDebugInfo()
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Debug Information")
                    .setMessage(debugInfo)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Copy") { _, _ ->
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Debug Info", debugInfo)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@MainActivity, "Debug info copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Open settings
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Battery Discharge Calculator")
            .setMessage("""
                Version: 1.0
                Device: Galaxy S10+ Exynos (beyond2lte)
                ROM: Nexus ROM OneUI 7
                Kernel: Extreme Kernel
                
                Features:
                • Live hardware current readings
                • Root-level battery access
                • Real-time discharge calculations
                • System notification integration
                
                Optimized for Samsung Galaxy S10+ with Exynos processor running rooted Android 15.
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }
}
