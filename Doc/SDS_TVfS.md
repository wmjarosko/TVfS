1. Executive Summary & Hardware Target
1.1 ObjectiveDeliver an ultra-simplified, resilient, zero-friction IPTV interface tailored specifically for elderly non-technical users (80+ years old). The interface prioritizes low cognitive load, large typography, mistake-tolerant remote control ergonomics, and a traditional "cable box" direct-viewing experience.
1.2 Target Hardware ProfileTarget Hardware: onn. Google TV 4K / 4K Pro Streaming BoxSoC: Amlogic S905Y4 (Quad-core ARM Cortex-A35)GPU: ARM Mali-G31 MP2Memory: 2 GB DDR4 RAMStorage: 8 GB eMMC (usable internal storage approx. 4.5 GB)OS: Android TV 12+ (Google TV Interface)Input Device: onn. / Google G10 Reference Bluetooth Voice Remote
2. System Architecture & Entry Points2.1 Multi-Activity Launcher ModelTo eliminate complex sub-menus, the application package declares multiple distinct launcher activities (android.intent.action.MAIN + android.intent.category.LEANBACK_LAUNCHER). This places separate, direct-access application icons directly on the native Google TV home screen.

+-------------------------------------------------------------------------+
|                        Google TV Home Screen                            |
|                                                                         |
|  [ TV Hub Icon ]   [ Live TV Icon ]   [ Movies Icon ]   [ TV Shows Icon ] |
+--------+------------------+------------------+-----------------+--------+
         |                  |                  |                 |
         v                  v                  v                 v
+-----------------+  +--------------+  +---------------+  +---------------+
|  HubActivity    |  | LivePlayer-  |  | MovieBrowser- |  | SeriesBrowser-|
|  4 Large Tiles  |  |   Activity   |  |   Activity    |  |   Activity    |
+--------+--------+  +-------+------+  +-------+-------+  +-------+-------+
         |                   |                 |                  |
         +-------------------+-----------------+------------------+
                             |
         +-------------------------------------------+
         |       Shared Android Application Core     |
         |  - Media3 / ExoPlayer Singleton Engine    |
         |  - Room Local DB (Curated Streams & EPG)  |
         |  - Remote JSON Config Synchronizer        |
         +-------------------------------------------+

Icon 1: TV Hub (Main): Launches the 4-tile simplified home screen:Live TV (News & Favorites)Favorite TV ShowsMoviesLast Viewed (Instant Resume)
Icon 2: Live TV: Skips the hub entirely and initializes full-screen playback on the last-viewed live channel.
Icon 3: Movies: Launches directly into the curated film catalogue.
Icon 4: TV Shows: Launches directly into the curated series catalogue with a single-click "Play Next Episode" mechanism.2.2 Core 

Tech StackLanguage: Kotlin 1.9+UI Framework: Jetpack Compose for TV (using TvLazyRow, TvLazyColumn, custom high-contrast focus modifiers).

Media Engine: AndroidX Media3 (ExoPlayer 1.3+) supporting HLS (.m3u8), MPEG-TS (.ts), and MP4/MKV containers.Data Persistence: Android Room DB + Jetpack DataStore (for user state, last channel played, playback resume timestamps).Network Client: OkHttp3 + Retrofit with Kotlinx Serialization.

3. Remote Control Interaction & State Engine3.1 G10 Remote Mapping MatrixKey / InputIn-Player: Live TVIn-Player: VOD / TV ShowsIn-Guide / Browse ModeD-Pad UpChannel Up (Sequential)Previous Episode / Title in QueueNavigate UpD-Pad DownChannel Down (Sequential)Next Episode / Title in QueueNavigate DownD-Pad LeftMini EPG Quick Peek (Now & Next)Rewind 15 secondsNavigate LeftD-Pad RightMini EPG Quick Peek (Now & Next)Fast Forward 15 secondsNavigate RightDedicated Ch +/-Channel Up / Channel DownNext / Previous EpisodeScroll Guide Page Up / DownCenter / OKToggle Overlay ControlsPause / Play ToggleSelect / Confirm PlaybackBack (Single Tap)Open/Close Bottom EPG DrawerOpen/Close Bottom Details DrawerClose Overlay / Step BackBack (Double Tap)Prompt Exit Confirmation ModalPrompt Exit Confirmation ModalReturn to Hub Activity3.2 Accidental Exit Protection (Back Button)Single Tap: Toggles the semi-transparent Bottom Drawer guide without interrupting playback.Double Tap (within 1500ms): Pauses background input and displays a full-screen, high-contrast modal:Prompt: "Are you sure you want to turn off the TV app?"Options: [ KEEP WATCHING ] (Default Focus) vs [ EXIT APP ].If no action is taken within 10 seconds, the modal automatically dismisses and video resumes.
4. Playback Resilience & Error Recovery
4.1 Reconnect & Failover State MachineIPTV streams are prone to packet drops, server timeouts, and socket disconnects. The application manages errors without displaying technical jargon or crash dialogs.

                        +----------------------+
                        |  Active Stream Play  |
                        +----------+-----------+
                                   |
                         (Stream Drops / Errors)
                                   |
                                   v
                        +----------------------+
                        | Attempt Connection 1 |
                        | (Display Banner UI)  |
                        +----------+-----------+
                                   |
                         (Fails after 4000ms)
                                   |
                                   v
                        +----------------------+
                        | Attempt Connection 2 |
                        | (Display Banner UI)  |
                        +----------+-----------+
                                   |
                         (Fails after 4000ms)
                                   |
                                   v
                        +----------------------+
                        | Attempt Connection 3 |
                        | (Display Banner UI)  |
                        +----------+-----------+
                                   |
                         (Fails after 4000ms)
                                   |
                                   v
                        +----------------------+
                        | Recovery Modal Dialog|
                        | "Try different ch?"  |
                        +----------------------+

4.2 State Machine SpecificationsOn Stream Failure / Buffering Exceeding 3500ms:A non-blocking, high-contrast overlay card slides in:"Re-connecting to video stream... (Attempt 1 of 3)"Exponential Backoff Retries:Retry 1: Immediate reconnect attempt (timeout 4s).Retry 2: 2-second delay before reconnect attempt (timeout 4s).Retry 3: 2-second delay before reconnect attempt (timeout 4s).Terminal Failure State (After 3 Failed Attempts):Stop decoder pipeline.Display high-contrast alert dialog:Title: Connection FailedMessage: "We couldn't connect to this channel right now. Would you like to try a different channel?"Buttons:[ OPEN CHANNEL LIST ] (Default Focused - opens Bottom Drawer)[ TRY AGAIN ] (Restarts the 3-attempt cycle)

5. UI Ergonomics & Visual Specification5.1 Ergonomic StandardsViewing Distance: Calibrated for 7 to 10 feet.Base Typography:Primary Headings / Channel Names: 38sp, Bold.Program Titles: 30sp, Semi-Bold.EPG Schedule Times / Secondary Metadata: 24sp, Regular.Minimum Text Size across entire app: 22sp.Contrast & Color Tokens:Background Overlay: ARGB(210, 16, 20, 24) (82% opacity dark slate blue/charcoal).Active Focused Border: 4dp solid outline in high-visibility Solar Yellow (#FFD54F) with a 1.05x scale factor.Text Color: Pure White (#FFFFFF) on dark cards; Light Gray (#CCCCCC) for secondary timestamps.5.2 Bottom Drawer EPG Overlay LayoutScreen Coverage: Lower 40% of the display.Background: Semi-transparent dark wash with hardware-accelerated blur. Video playback remains visible in the top 60% of the screen.Component Arrangement:Horizontal carousel (TvLazyRow) of large channel cards.Each card displays: Channel Number, Channel Name / Logo, and Current Program Title (with progress bar).Below the card carousel, a two-line summary displays the program description and upcoming program ("Next: [Title] at [Time]").

+-------------------------------------------------------------------+
|                                                                   |
|              VIDEO CONTINUES PLAYING IN TOP 60%                   |
|                                                                   |
+-------------------------------------------------------------------+
|  [CHANNEL 04]        [CHANNEL 05]        [CHANNEL 06]             |
|  CBS Local           NBC News            ABC World News           |
|  Morning News (LIVE) Today Show          Good Morning America     |
|  [========.....]     [===..........]     [======.......]          |
|-------------------------------------------------------------------|
|  Now: CBS Morning News (7:00 AM - 9:00 AM)                        |
|  Next: The Price is Right at 9:00 AM                              |
+-------------------------------------------------------------------+

6. Search Architecture6.1 Unified Search FlowIntegrated directly as a large, dedicated search card in the Hub and in the Movie/Show rows.Primary Input: Launches the Android TV System Voice Dialog (RecognizerIntent.ACTION_RECOGNIZE_SPEECH).Fallback Input: If the remote has no microphone access or voice recognition fails, the system smoothly falls back to the native Android Leanback on-screen QWERTY keyboard.Search Execution: Queries the local Room DB across:Live Channel Names / CategoriesMovie Titles and GenresTV Show TitlesResults Display: Grouped into simple, flat horizontal rows marked with explicit badges: [LIVE CHANNEL], [MOVIE], [SERIES].
7. Data Provisioning & Backend Integration7.1 Remote JSON Bootstrap SchemaTo prevent on-screen credential entry on the device, the app fetches a remote bootstrap configuration on boot (via a hosted JSON endpoint or private GitHub Gist). If network is absent, it loads the last-cached local DB profile.JSON

{
  "version": 1,
  "last_updated": "2026-10-28T10:00:00Z",
  "provider": {
    "server_url": "http://provider-domain.xyz:8080",
    "username": "client_user_id",
    "password": "client_password"
  },
  "curation": {
    "enable_full_provider_catalog": false,
    "allowed_live_categories": ["Local News", "Sports Networks", "Classic TV"],
    "allowed_channel_ids": [101, 102, 103, 205, 301],
    "favorite_series_ids": [12, 45, 88],
    "favorite_movie_ids": [8901, 9022]
  },
  "behavior": {
    "boot_mode": "hub",
    "retry_attempts": 3,
    "auto_play_last_channel": true
  }
}

7.2 Database Ingestion & Storage GuardrailsStorage Footprint Limitation: The onn. 4K has limited internal flash memory. The app restricts local Room DB storage by:Only fetching EPG data for allowed_channel_ids or designated favorites (saving over 100MB of XMLTV parsing overhead).Setting EPG retention limits to Current Day + 24 Hours ahead.Running an automatic purge routine at midnight to drop past program schedules.
8. Development Implementation RoadmapSprint 1: Core Player & Remote MechanicsSetup Jetpack Compose for TV project scaffold.Implement Media3 ExoPlayer singleton with HLS/MPEG-TS decoders.Bind G10 remote keys: channel flipping (Up/Down) and double-tap Back exit protection.Sprint 2: Multi-Activity Manifest & Hub UIRegister separate Leanback launcher activities for Hub, Live, Movies, and Series.Implement the 4-tile Hub view with high-contrast accessibility themes.Sprint 3: Bottom Drawer EPG & Stream ResilienceBuild the semi-transparent bottom overlay for channel flipping and guide inspection.Implement the 3-step reconnect state machine with custom senior-friendly alert dialogs.Sprint 4: Remote Provisioning & Voice/QWERTY SearchIntegrate remote JSON configuration parser and SQLite/Room caching layers.Hook system voice search with QWERTY fallback.