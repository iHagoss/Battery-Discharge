```bash
#!/system/bin/sh

# Battery Discharge Service Script
# Runs at boot with root privileges

MODDIR=${0%/*}
LOG="/data/local/tmp/battery_discharge.log"

log_print() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> $LOG
}

log_print "Battery Discharge Module starting..."

# Wait for system to be ready
sleep 30

# Check if device is Galaxy S10+
DEVICE=$(getprop ro.product.device)
log_print "Device detected: $DEVICE"

# Start battery monitoring service
if [ -f "/data/data/com.batterymod.discharge/battery_service" ]; then
    /data/data/com.batterymod.discharge/battery_service &
    log_print "Battery service started"
else
    log_print "Error: Battery service not found"
fi

# Set proper SELinux contexts
chcon u:object_r:system_file:s0 /system/app/BatteryDischarge/BatteryDischarge.apk
chcon u:object_r:system_lib_file:s0 /system/app/BatteryDischarge/lib/arm64/libbattery.so

log_print "Battery Discharge Module initialization complete"
```
