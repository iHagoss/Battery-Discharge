#!/sbin/sh
ui_print "🔋 Installing Battery Discharge Calculator..."
ui_print "📱 Optimized for Galaxy S10+ Exynos (beyond2lte)"
ui_print "⚡ Features: Live current readings, Root integration"
ui_print "🎯 Compatible with Extreme Kernel & OneUI 7"

# Install APK as system app
cp -f $MODPATH/system/app/BatteryDischarge/BatteryDischarge.apk /data/local/tmp/
pm install -r /data/local/tmp/BatteryDischarge.apk
rm -f /data/local/tmp/BatteryDischarge.apk

ui_print "✅ Installation complete!"
ui_print "   Launch 'Battery Discharge' from app drawer"