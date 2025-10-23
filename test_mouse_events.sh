#!/bin/bash

echo "UltraView Mouse Events Test"
echo "=========================="
echo ""
echo "Choose test type:"
echo "1. Simple Mouse Test (Basic event detection)"
echo "2. Single Machine Test (With Robot simulation)"
echo "3. Exit"
echo ""
read -p "Enter your choice (1-3): " choice

case $choice in
    1)
        echo "Starting Simple Mouse Test..."
        java -cp target/classes com.example.ultraviewdemo.SimpleMouseTest
        ;;
    2)
        echo "Starting Single Machine Test..."
        java -cp target/classes com.example.ultraviewdemo.SingleMachineTest
        ;;
    3)
        echo "Goodbye!"
        exit 0
        ;;
    *)
        echo "Invalid choice. Please run the script again."
        ;;
esac
