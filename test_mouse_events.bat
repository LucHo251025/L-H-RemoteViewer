@echo off
echo UltraView Mouse Events Test
echo ==========================
echo.
echo Choose test type:
echo 1. Simple Mouse Test (Basic event detection)
echo 2. Single Machine Test (With Robot simulation)
echo 3. Exit
echo.
set /p choice="Enter your choice (1-3): "

if "%choice%"=="1" (
    echo Starting Simple Mouse Test...
    java -cp target/classes com.example.ultraviewdemo.SimpleMouseTest
) else if "%choice%"=="2" (
    echo Starting Single Machine Test...
    java -cp target/classes com.example.ultraviewdemo.SingleMachineTest
) else if "%choice%"=="3" (
    echo Goodbye!
    exit
) else (
    echo Invalid choice. Please run the script again.
)

pause
