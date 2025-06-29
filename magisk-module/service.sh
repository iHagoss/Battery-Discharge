#!/system/bin/sh
# Grant root permissions to battery discharge app
pm grant com.batterydischarge.calculator android.permission.WRITE_SECURE_SETTINGS 2>/dev/null
pm grant com.batterydischarge.calculator android.permission.DUMP 2>/dev/null

# Set SELinux context for battery access
chcon u:object_r:sysfs_batteryinfo:s0 /sys/class/power_supply/battery/* 2>/dev/null
chmod 644 /sys/class/power_supply/battery/current_now 2>/dev/null
chmod 644 /sys/class/power_supply/battery/charge_full_design 2>/dev/null
chmod 644 /sys/class/power_supply/battery/capacity 2>/dev/null
