# TVfS - TV for Seniors

TVfS is a simplified, highly accessible Android TV client designed explicitly for seniors. It strips away complex navigation, standardizes input controls, and offers a curated selection of Live TV, Movies, and Shows.

## Key Features
* **Simplified Interface**: Uses Jetpack Compose for TV to present large, high-contrast text and prominent focus states.
* **Ergonomic Controls**: Remote D-Pad inputs map natively to intuitive channel-surfing functions.
* **Accidental Exit Protection**: Overrides the standard Android back button behavior to require a deliberate double-tap confirmation to exit the app.
* **Resilient Playback**: Employs AndroidX Media3 (ExoPlayer) with automatic failovers for seamless video streaming (HLS/MPEG-TS).

## Project Setup & Creation
The project is initialized using Android Gradle Plugin 8.1+ and targets Android SDK version 34.

### Prerequisites
* JDK 17 or higher
* Android Studio (Giraffe | 2022.3.1 or newer recommended)
* Android SDK Platform 34
* Android SDK Build-Tools 33.0.1+

### Creating a Build
To scaffold or build the project from scratch, simply fetch the codebase and run Gradle:
```bash
./gradlew assembleDebug
```

This command will output the compiled debug APK to `app/build/outputs/apk/debug/`.

## Installation Instructions

### Method 1: Sideload via adb (Android Debug Bridge)
1. Ensure your Android TV device has **Developer Options** enabled and **Network Debugging / USB Debugging** turned on.
2. Connect to the TV device:
   ```bash
   adb connect <TV_IP_ADDRESS>
   ```
3. Install the APK:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Method 2: Android Studio
1. Open the project folder in Android Studio.
2. Ensure your TV (physical or Android TV emulator) is selected in the Target Device drop-down.
3. Click the **Run** button (Shift + F10) to automatically build and push the application to the TV.

## Usage Guide
Upon launching TVfS, the user will be presented with a full-screen, immersive media player.

### Remote Control Navigation
* **D-Pad Up / Channel Up**: Changes to the next available channel in the lineup.
* **D-Pad Down / Channel Down**: Changes to the previous channel in the lineup.
* **Back Button (Single Tap)**: Arms the exit mechanism.
* **Back Button (Double Tap)**: Triggers the exit sequence, ensuring the user deliberately intended to close the video playback and leave the app.

For further architectural and design details, see the software design spec located in `Doc/SDS_TVfS.md`.
