@echo off
chcp 65001 >nul
echo =========================================
echo   Counter App - Build and Run
echo =========================================
echo.

REM Change to project directory
cd /d "%~dp0"

REM Build debug APK
echo [1/3] Building debug APK...
CALL gradlew.bat :app:assembleDebug
if errorlevel 1 (
    echo [ERROR] Build failed!
    pause
    exit /b 1
)
echo [OK] Build successful.
echo.

REM Check if device is connected
echo [2/3] Checking device...
adb devices | findstr "device$" >nul
if errorlevel 1 (
    echo [WARNING] No device found. Please connect your phone and enable USB debugging.
    pause
    exit /b 1
)
echo [OK] Device connected.
echo.

REM Install APK
echo [3/3] Installing APK...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
if errorlevel 1 (
    echo [ERROR] Install failed!
    pause
    exit /b 1
)
echo [OK] Install successful.
echo.

REM Launch app
echo Launching app...
adb shell am start -n com.snuabar.counter/.MainActivity
echo.
echo =========================================
echo   Done!
echo =========================================
