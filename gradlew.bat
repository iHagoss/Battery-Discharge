```batch
@rem Battery Discharge Module - Windows Gradle Wrapper
@if "%DEBUG%" == "" @echo off
@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd_ return code. This is useful for continuous integration
rem systems.
if not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
```

## **proguard-rules.pro** (Place in app/ directory):
```pro
# Battery Discharge Module - ProGuard Rules
-keep class com.batterydischarge.** { *; }
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

# Keep root helper methods
-keep class com.batterydischarge.RootHelper {
    public static *;
}

# Keep notification methods
-keep class com.batterydischarge.NotificationHelper {
    public *;
}
```

---

# 🚀 **COMPLETE AUTOMATED SETUP SCRIPT**

## **Create this as: `setup_battery_module.sh`**
```bash
#!/bin/bash

echo "🔋 Battery Discharge Module - Automated Setup"
echo "============================================="

# Create directory structure
echo "📁 Creating directory structure..."
mkdir -p Battery-Discharge/{app/src/main/{java/com/batterydischarge,res/{layout,values,drawable}},magisk_module/{META-INF/com/google/android,system/app/BatteryDischarge},gradle/wrapper}

cd Battery-Discharge

# Download Gradle Wrapper JAR
echo "⬇️  Downloading Gradle Wrapper..."
curl -L "https://github.com/gradle/gradle/raw/v8.2.0/gradle/wrapper/gradle-wrapper.jar" -o gradle/wrapper/gradle-wrapper.jar

# Create all necessary files
echo "📝 Creating project files..."

# Root build.gradle
cat > build.gradle << 'EOF'
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.0'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}

task packageMagiskModule(type: Zip) {
    from 'magisk_module'
    include '**/*'
    archiveFileName = 'BatteryDischargeMagisk.zip'
    destinationDirectory = file('build/outputs')
    dependsOn 'app:assembleDebug'
    
    doLast {
        copy {
            from 'app/build/outputs/apk/debug/app-debug.apk'
            into 'magisk_module/system/app/BatteryDischarge'
            rename 'app-debug.apk', 'BatteryDischarge.apk'
        }
    }
}
EOF
