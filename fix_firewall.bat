@echo off
echo 🔧 Adding Windows Firewall rule for ShiftSync Backend...
echo.

REM Check if running as administrator
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo ❌ Please run this script as Administrator!
    echo Right-click the file and select "Run as administrator"
    pause
    exit /b
)

echo ✅ Running as Administrator - Good!

REM Add firewall rule for Node.js backend on port 3000
echo Adding firewall rule for port 3000...
netsh advfirewall firewall add rule name="ShiftSync Backend Port 3000" dir=in action=allow protocol=TCP localport=3000

if %errorLevel% equ 0 (
    echo ✅ Firewall rule added successfully!
) else (
    echo ❌ Failed to add firewall rule
)

echo.
echo 🌐 Testing connection to backend...
curl -s http://localhost:3000/api >nul 2>&1
if %errorLevel% equ 0 (
    echo ✅ Backend is accessible from localhost
) else (
    echo ❌ Backend not responding on localhost:3000
)

echo.
echo 📱 Now try logging in from your mobile device!
echo The app will automatically test different IP addresses.
echo.
pause
