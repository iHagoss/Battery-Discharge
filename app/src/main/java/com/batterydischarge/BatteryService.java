```java
package com.batterymod.discharge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class BatteryService extends Service {
    private static final String TAG = "BatteryDischarge";
    private static final String CHANNEL_ID = "battery_discharge_channel";
    private static final int NOTIFICATION_ID = 12345;
    
    private Handler handler;
    private Runnable batteryRunnable;
    private NotificationManager notificationManager;
    private boolean isCharging = false;
    
    // Galaxy S10+ specific battery paths
    private static final String[] CURRENT_PATHS = {
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/bms/current_now",
        "/sys/class/power_supply/max77705-fuelgauge/current_now"
    };
    
    private static final String[] CAPACITY_PATHS = {
        "/sys/class/power_supply/battery/capacity",
        "/sys/class/power_supply/bms/capacity"
    };
    
    private static final String[] STATUS_PATHS = {
        "/sys/class/power_supply/battery/status",
        "/sys/class/power_supply/ac/online",
        "/sys/class/power_supply/usb/online"
    };
    
    // S10+ battery capacity in mAh
    private static final int BATTERY_CAPACITY_MAH = 4100;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "BatteryService created");
        
        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
        
        // Register for charging state changes
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(chargingReceiver, filter);
        
        startBatteryMonitoring();
    }

    private void createNotificationChannel() {
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "Battery Discharge Time",
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Shows battery discharge time estimation");
        channel.setSound(null, null);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    private void startBatteryMonitoring() {
        batteryRunnable = new Runnable() {
            @Override
            public void run() {
                updateBatteryNotification();
                handler.postDelayed(this, 15000); // Update every 15 seconds
            }
        };
        handler.post(batteryRunnable);
    }

    private void updateBatteryNotification() {
        if (isCharging) {
            // Hide notification when charging (system shows charge time)
            notificationManager.cancel(NOTIFICATION_ID);
            return;
        }

        try {
            int batteryLevel = readBatteryLevel();
            int currentDraw = readCurrentDraw();
            String dischargeTime = calculateDischargeTime(batteryLevel, currentDraw);
            
            showDischargeNotification(batteryLevel, currentDraw, dischargeTime);
            
        } catch (Exception e) {
            Log.e(TAG, "Error updating battery notification", e);
        }
    }

    private int readBatteryLevel() throws IOException {
        for (String path : CAPACITY_PATHS) {
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String line = reader.readLine();
                if (line != null) {
                    return Integer.parseInt(line.trim());
                }
            } catch (Exception e) {
                Log.d(TAG, "Failed to read from " + path);
            }
        }
        throw new IOException("Could not read battery level");
    }

    private int readCurrentDraw() throws IOException {
        // Try root access first
        String rootCurrent = RootUtils.executeRootCommand("cat " + CURRENT_PATHS[0]);
        if (rootCurrent != null && !rootCurrent.isEmpty()) {
            try {
                int microAmps = Integer.parseInt(rootCurrent.trim());
                // Convert microamps to milliamps and make positive (discharge is negative)
                return Math.abs(microAmps / 1000);
            } catch (NumberFormatException e) {
                Log.d(TAG, "Failed to parse root current: " + rootCurrent);
            }
        }
        
        // Fallback to direct file reading
        for (String path : CURRENT_PATHS) {
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String line = reader.readLine();
                if (line != null) {
                    int microAmps = Integer.parseInt(line.trim());
                    return Math.abs(microAmps / 1000); // Convert to mA, make positive
                }
            } catch (Exception e) {
                Log.d(TAG, "Failed to read from " + path);
            }
        }
        
        // If all else fails, return estimated current based on usage
        return estimateCurrentDraw();
    }

    private int estimateCurrentDraw() {
        // Fallback estimation for S10+ (screen on/off, typical usage)
        return 450; // Conservative estimate in mA
    }

    private String calculateDischargeTime(int batteryPercent, int currentMa) {
        if (currentMa <= 0) {
            return "Unknown";
        }
        
        // Calculate remaining capacity in mAh
        float remainingCapacity = (BATTERY_CAPACITY_MAH * batteryPercent) / 100.0f;
        
        // Calculate time in hours
        float hoursRemaining = remainingCapacity / currentMa;
        
        // Convert to hours and minutes
        int hours = (int) hoursRemaining;
        int minutes = (int) ((hoursRemaining - hours) * 60);
        
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    private void showDischargeNotification(int batteryLevel, int currentMa, String timeRemaining) {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Battery")
            .setContentText(String.format("%s remaining (%dmA)", timeRemaining, currentMa))
            .setSubText(String.format("%d%%", batteryLevel))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(Notification.PRIORITY_LOW);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private BroadcastReceiver chargingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                isCharging = true;
                notificationManager.cancel(NOTIFICATION_ID);
                Log.d(TAG, "Charging started - hiding discharge notification");
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                isCharging = false;
                Log.d(TAG, "Charging stopped - showing discharge notification");
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "BatteryService started");
        return START_STICKY; // Restart if killed
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && batteryRunnable != null) {
            handler.removeCallbacks(batteryRunnable);
        }
        unregisterReceiver(chargingReceiver);
        notificationManager.cancel(NOTIFICATION_ID);
        Log.d(TAG, "BatteryService destroyed");
    }
}
```
