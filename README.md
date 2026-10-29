# TVfS - TV for Seniors

TVfS is an ultra-simplified, resilient, zero-friction Android TV / Google TV client designed explicitly for seniors (80+ years old) and calibrated for the **onn. Google TV 4K / 4K Pro** streaming box with the **G10 Reference Bluetooth Voice Remote**.

---

## Implemented Architecture (`Doc/SDS_TVfS.md`)

### 1. Multi-Activity Google TV Launcher Model (SDS §2.1)
To eliminate complex sub-menus, `AndroidManifest.xml` declares **4 distinct `LEANBACK_LAUNCHER` entry points** that appear directly on the native Google TV home screen:
1. **`HubActivity` (`TV Hub`)**: 4 large high-contrast tiles (`Live TV`, `Favorite TV Shows`, `Movies`, `Last Viewed - Instant Resume`) plus a dedicated **Voice / Text Search** card.
2. **`LivePlayerActivity` (`Live TV`)**: Skips the hub and initializes full-screen playback on the last-viewed live channel.
3. **`MovieBrowserActivity` (`Movies`)**: Launches directly into the curated film catalogue with single-click playback.
4. **`SeriesBrowserActivity` (`TV Shows`)**: Launches directly into the curated series catalogue with a one-click **`[ PLAY NEXT EPISODE ]`** mechanism (`S01E01` -> `S01E02` -> ...).

### 2. G10 Remote Control Matrix & Accidental Exit Protection (SDS §3.1 & §3.2)
| Key / Input | In-Player: Live TV | In-Player: VOD / TV Shows |
| :--- | :--- | :--- |
| **D-Pad Up / Ch +** | Channel Up (Sequential) | Previous Episode in Queue |
| **D-Pad Down / Ch -** | Channel Down (Sequential) | Next Episode in Queue |
| **D-Pad Left** | Mini EPG Quick Peek (Now & Next) | Rewind 15 seconds |
| **D-Pad Right** | Mini EPG Quick Peek (Now & Next) | Fast Forward 15 seconds |
| **Center / OK** | Toggle Top Status Banner | Pause / Play Toggle |
| **Back (Single Tap)** | Open / Close Bottom 40% EPG Drawer | Open / Close Bottom Drawer |
| **Back (Double Tap ≤ 1.5s)** | Prompt High-Contrast Exit Modal (`[ KEEP WATCHING ]` focused by default, auto-dismisses after 10s) | Prompt High-Contrast Exit Modal |

### 3. Playback Resilience & Error Recovery (SDS §4.1 & §4.2)
* Monitors Media3 `ExoPlayer` for stream drops or buffering exceeding `3500ms`.
* Displays non-blocking banner: `"Re-connecting to video stream... (Attempt X of 3)"` across 3 attempts (`4000ms` timeout per attempt, `2000ms` backoff on Attempts 2 & 3).
* On terminal failure after 3 attempts, halts the decoder pipeline and presents the senior-friendly recovery dialog with **`[ OPEN CHANNEL LIST ]`** (default focused) and **`[ TRY AGAIN ]`**.

### 4. Ergonomic Standards & Bottom Drawer EPG (SDS §5.1 & §5.2)
* **Typography**: `38sp` Bold headings/channels, `30sp` Semi-Bold program titles, `24sp` Regular schedule metadata, `22sp` minimum text size across the entire app.
* **High-Contrast Focus**: `4dp` solid Solar Yellow (`#FFD54F`) border with a `1.05x` scale factor on focus; `ARGB(210, 16, 20, 24)` dark slate overlay.
* **Bottom EPG Drawer**: Covers the lower 40% of the screen while live video continues playing in the top 60%.

### 5. Unified Voice / QWERTY Search & Remote Provisioning (SDS §6.1, §7.1 & §7.2)
* **`SearchActivity`**: Launches the Android TV System Voice Dialog (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`) and smoothly falls back to an on-screen QWERTY keyboard if unavailable, grouping results with explicit `[LIVE CHANNEL]`, `[MOVIE]`, and `[SERIES]` badges.
* **Remote JSON Bootstrap & Storage Guardrails**: Parses the SDS §7.1 remote JSON bootstrap schema (`bootstrap-server/bootstrap.json`), caches locally for offline resilience, restricts live channels to curated IDs/categories, and purges expired or >24h EPG schedules.

---

## Building & Testing

### Run Unit Tests & Build Debug APK
```bash
./gradlew testDebugUnitTest assembleDebug
```
The compiled debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Remote Bootstrap Provisioning on Render
This repository includes `render.yaml` and `bootstrap-server/bootstrap.json` to host the SDS §7.1 remote configuration endpoint as a Static Site on Render.
