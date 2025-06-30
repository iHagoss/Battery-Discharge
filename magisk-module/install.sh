#!/sbin/sh
ui_print "🔋 Installing Battery Discharge Calculator..."
ui_print "📱 Optimized for Galaxy S10+ Exynos (beyond2lte)"
ui_print "⚡ Features: Live current readings, Root integration"
ui_print "🎯 Compatible with Extreme Kernel & OneUI 7"

# Debug: Print MODPATH and check APK existence
ui_print "MODPATH is: $MODPATH"
if [ -d "$MODPATH/system/app/BatteryDischarge" ]; then
  ls -l "$MODPATH/system/app/BatteryDischarge/" 2>&1 | while read line; do ui_print "$line"; done
else
  ui_print "❌ ERROR: APK directory not found at $MODPATH/system/app/BatteryDischarge"
fi

APK_SRC="$MODPATH/system/app/BatteryDischarge/BatteryDischarge.apk"
APK_TMP="/data/local/tmp/BatteryDischarge.apk"

if [ -f "$APK_SRC" ]; then
  cp -f "$APK_SRC" "$APK_TMP"
  pm install -r "$APK_TMP"
  rm -f "$APK_TMP"
  ui_print "✅ Installation complete!"
  ui_print "   Launch 'Battery Discharge' from app drawer"
else
  ui_print "❌ ERROR: APK not found at $APK_SRC"
  ls -l "$MODPATH/system/app/BatteryDischarge/" 2>&1 | while read line; do ui_print "$line"; done
fi
