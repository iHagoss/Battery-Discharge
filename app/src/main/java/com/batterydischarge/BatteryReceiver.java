java
package com.batterydischarge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BatteryReceiver extends BroadcastReceiver {
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            // Start monitoring when charger disconnected
            Intent serviceIntent = new Intent(context, BatteryService.class);
            context.startForegroundService(serviceIntent);
            
        } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            // Stop discharge monitoring when charger connected
            NotificationHelper helper = new NotificationHelper(context);
            helper.hideNotification();
        }
    }
}
