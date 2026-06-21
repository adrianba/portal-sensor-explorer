# Portal Sensor Explorer

A native Android app that probes and displays all available hardware on a Meta Portal (1st generation) device — sensors, cameras, microphones, Bluetooth, network interfaces, and user presence detection.

## Features

- **Device Info** — Model, SoC (Snapdragon 835), CPU cores, RAM, display specs
- **Sensor List** — Enumerates all hardware sensors with live value monitoring
- **Camera Explorer** — Camera2 characteristics and CameraX live preview
- **Microphone Explorer** — Far-field mic array details, audio device info, live audio level meter
- **Audio Recorder** — Record and playback audio (requires stopping Hey Portal first)
- **Bluetooth** — Adapter info, classic discovery, BLE scan (with firmware limitation detection)
- **Network** — WiFi details, network interface enumeration
- **Weather** — Reads Portal's weather data from aloha.WeatherFetcher service
- **Platform State** — Device state bundle (presence, privacy, shutter, call state) via dumpsys
- **User Presence Monitor** — Reads Portal's built-in presence detection via logcat (requires `READ_LOGS`)

## Getting Started

### Prerequisites

- Meta Portal (1st gen) with ADB enabled, connected via USB-C
- JDK 21 or later
- Android SDK

### Build & Deploy

```powershell
# Build
.\gradlew.bat assembleDebug

# Install
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Launch
adb shell am start -n net.adrianba.portal.sensorexplorer/net.adrianba.portal.sensorexplorer.MainActivity

# Grant READ_LOGS for presence monitoring
adb shell pm grant net.adrianba.portal.sensorexplorer android.permission.READ_LOGS

# Stop Hey Portal before audio recording (it holds the mic)
adb shell am force-stop com.millennium

# Read app logs
adb logcat -d -s SensorExplorer:I
```

## Project Structure

- `app/src/main/java/net/adrianba/portal/sensorexplorer/`
  - `MainActivity.kt` — Entry point, wires all sections, logs hardware info on startup
  - `DeviceInfoSection.kt` — Device model, SoC, CPU, memory, display, light sensor details
  - `SensorListSection.kt` — Hardware sensor enumeration with live monitoring
  - `CameraExplorerSection.kt` — Camera2 characteristics + CameraX preview
  - `MicrophoneExplorerSection.kt` — Audio devices, MicrophoneInfo, audio level meter
  - `AudioRecorderSection.kt` — Audio recording and playback (AAC/M4A format)
  - `BluetoothSection.kt` — BT adapter, classic + BLE scanning, device discovery
  - `NetworkSection.kt` — WiFi, connectivity, network interfaces
  - `WeatherSection.kt` — Weather data from aloha.WeatherFetcher logcat
  - `PlatformStateSection.kt` — Platform state bundle via dumpsys
  - `PresenceMonitorSection.kt` — User presence state via logcat monitoring
  - `PermissionsSection.kt` — Runtime permission requests
  - `ui/theme/` — Compose theme (dark theme for Portal, bundled Inter font)
- `app/src/main/AndroidManifest.xml` — Permissions and activity declaration
- `AGENTS.md` — Comprehensive development guide with hardware specs and known issues

## Target Device

Meta Portal (1st gen) — codename "aloha", Qualcomm Snapdragon 835, Android 9 (SDK 28), 1280×800 display, single front camera, 4-mic far-field array, ambient light sensor only (no accelerometer/gyroscope/GPS).

See [AGENTS.md](AGENTS.md) for full hardware specifications and platform constraints.

## References

- [Portal development documentation](https://developers.meta.com/horizon/documentation/android-apps/portal-development/)
- [Portal design requirements](https://developers.meta.com/horizon/documentation/android-apps/portal-design-requirements)
- [Original portal-samples repo](https://github.com/meta-quest/portal-samples)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
