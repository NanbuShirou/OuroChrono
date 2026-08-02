@echo off
setlocal
cd /d "%~dp0"
if not exist "dist" mkdir "dist"
call gradlew.bat clean assembleDebug
if errorlevel 1 (
  echo.
  echo Build failed. Open Android Studio Build Output for the actual Gradle error.
  exit /b 1
)
copy /Y "app\build\outputs\apk\debug\app-debug.apk" "dist\OuroChrono-debug.apk" >nul
if errorlevel 1 (
  echo Build succeeded, but the APK could not be copied to dist.
  exit /b 1
)
echo.
echo APK created: %~dp0dist\OuroChrono-debug.apk
endlocal
