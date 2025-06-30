```java
package com.batterymod.discharge;

import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class RootUtils {
    private static final String TAG = "BatteryDischarge";

    public static String executeRootCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            String result = reader.readLine();
            process.waitFor();

            Log.d(TAG, "Root command executed: " + command + " -> " + result);
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Failed to execute root command: " + command, e);
            return null;
        }
    }

    public static boolean isRootAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("echo test\n");
            os.writeBytes("exit\n");
            os.flush();
            
            int exitValue = process.waitFor();
            return exitValue == 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Root check failed", e);
            return false;
        }
    }
}
```

### 8. `/build.gradle`
```gradle
android {
    compileSdkVersion 34
    buildToolsVersion "34.0.0"

    defaultConfig {
        applicationId "com.batterymod.discharge"
        minSdkVersion 21
        targetSdkVersion 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt')
        }
    }
}

dependencies {
    implementation 'androidx.core:core:1.8.0'
}
```
