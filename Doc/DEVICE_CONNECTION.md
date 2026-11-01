# onn. Google TV 4K Device & Hardware Connection Info

## 1. Physical Device Profile
- **Device Model**: onn. Google TV 4K Streaming Box (`onn_4K_Streaming_Device`)
- **System Architecture**: ARMv8-A (Amlogic S905Y4, Mali-G31 MP2)
- **Display Resolution & Density**: 1920x1080 @ 320dpi (Logical Compose viewport: `960dp x 540dp`)
- **OS Version**: Android TV 12+ (Google TV Leanback Interface)
- **Input Hardware**: G10 Reference Bluetooth Voice Remote

## 2. Wireless ADB Connection Parameters
- **Configured IP Address**: `192.168.7.125`
- **Default ADB Port**: `5555`
- **Target Device String**: `192.168.7.125:5555`
- **Physical USB / Direct Link**: Available via host PC USB connection when box is in proximity

### Quick Connect Command
```bash
# Connect over Local Wi-Fi
adb connect 192.168.7.125:5555

# Verify connection
adb devices -l
```

> **Note on Power / Sleep State**:
> When the onn. box enters deep sleep (`mWakefulness=Asleep`), Wi-Fi power-saving may close port 5555 (`No route to host`). To reconnect, press the Power or Home button on the physical remote (or issue `adb connect` while on the same 192.168.7.x subnet once awake).

## 3. Package & Launcher Entry Points
- **Application Package**: `com.tvfs.app`
- **Home Hub Activity**: `com.tvfs.app/.HubActivity` (Silver TV)
- **Direct Live TV**: `com.tvfs.app/.LivePlayerActivity`
- **Recorded Shows**: `com.tvfs.app/.SeriesBrowserActivity`
- **Movies**: `com.tvfs.app/.MovieBrowserActivity`
- **Search**: `com.tvfs.app/.SearchActivity`
