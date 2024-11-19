#!/bin/bash

emulator1="Pixel_9_API_34"
emulator2="Pixel_9_Pro_XL_API_35"
# Launch the emulators (optional, you can skip this if already running)
echo "Launching $emulator1..."
flutter emulators --launch "$emulator1" &

echo "Launching $emulator2..."
flutter emulators --launch "$emulator2" &

# Wait for both emulators to launch
wait

emulator1_pid="emulator-5554"
emulator2_pid="emulator-5556"

# Run the app on both emulators in parallel
echo "Running app on $emulator1_pid..."
flutter run -d "$emulator1_pid" &

echo "Running app on $emulator2_pid..."
flutter run -d "$emulator2_pid" &

# Wait for both flutter run processes to complete
wait

echo "App successfully run on $emulator1 and $emulator2."
