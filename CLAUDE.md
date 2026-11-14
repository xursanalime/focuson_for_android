# Focus On: notes for Claude Code

Personal Android focus app for one user (Xursanali, not a programmer). Works like the Forest app
(a timer grows a plant, leaving to a blocked app is stopped), but with its own name, art and look:
never copy Forest's name, icons, plant art or screens.

## Talking to the user
- Simple Uzbek (Latin), short sentences. Say exactly what to tap. All UI text in Uzbek.
- Build in stages; stop after each and ask before the next.
- No login, accounts, network or telemetry. Data stays on the phone.

## Layout (phone + Ubuntu)
- `shared/src/` — code used by BOTH apps: data models, Periods, GardenTheme/Streaks, WeeklyReport,
  Backup (file format + merge), SoundGenerator, weeklyLines, and the screens: PlantArt/PlantView,
  GardenArt/GardenScreen, HomeScreen, ShopScreen, TagsScreen, Theme, Dial, Icons, Platform.kt
  (FocusActions + LocalFocusActions, LocalBackHandler, settings widgets). Keep it free of android.*:
  shared screens get platform work through `LocalFocusActions` and read state from `Store`.
- `app/` — Android. Adds `../shared/src` via `sourceSets["main"].kotlin.srcDir`. Its own Store,
  FocusController, blocking, limits, widget, Android settings. `AndroidActions` implements FocusActions.
- `desktop/` — Ubuntu app, its own Gradle build (Compose Multiplatform 1.9.0, Kotlin 2.2.21), source
  dirs `src` + `../shared/src`. Own `uz.focuson.data.Store` with the same API (JSON files in
  ~/.local/share/focuson, override with FOCUSON_DATA_DIR / -Dfocuson.dataDir), `DesktopFocus`
  (FocusActions, 1 s loop, reminder/weekly/auto-backup), notify-send, GNOME DND via gsettings
  show-banners, javax.sound ambient sound, tray (`--tray` starts hidden), autostart .desktop.
  No blocking or limits on the computer (the user said there is nothing to block).
  Build/test: `cd desktop && ./gradlew test` (headless screenshots in desktop/build/screens via
  ImageComposeScene). Install: `desktop/install.sh` → ~/.local/share/focuson-app + menu entry.
- Backups merge both ways (phone ↔ computer): `Backup.mergeHistory` dedupes by start/end/plant;
  coins stay per device, bought plants and tags are united.

## Tech
- Native Kotlin + Jetpack Compose. AGP 9.1.0 (built-in Kotlin), Gradle 9.3.1, compose plugin 2.2.21,
  compileSdk/targetSdk 36, minSdk 26. Android SDK at ~/Android/Sdk. No emulator on this machine.
- Build: `./gradlew assembleDebug` → copy `app/build/outputs/apk/debug/app-debug.apk` to `Focus-On.apk`.
- Signing: both build types use `keystore/focuson.keystore` (a copy of the debug key the phone's app was
  first signed with; SHA-256 9dacb385…6c90). Never change the key: the phone could not update without
  uninstalling (data loss). The user was told to keep a copy of that file.
- Tests: `./gradlew testDebugUnitTest`. `LogicTest` = pure logic; `ScreenshotTest` (Robolectric, native
  graphics) writes PNGs to `app/build/screens/` — use them to look at screens without a phone.
- Package `uz.focuson`:
  - `data/` Models + `Store` (SharedPreferences for settings/active session, `sessions.json` for history).
  - `focus/` `FocusController` (start/check/give up, strict-mode warning, exact alarm at end),
    `FocusService` (foreground, specialUse, countdown notification), receivers for alarm and boot.
  - `block/` `BlockerService` (AccessibilityService, only TYPE_WINDOW_STATE_CHANGED, reads no content),
    `BlockRules` (pure), `Blocking` (neutral set + launchers),
    `BlockActivity` (screen shown instead of a blocked app).
  - `ui/` Compose screens; plants in `PlantArt.kt` + `PlantView.kt`, dial in `Dial.kt`, garden in `GardenScreen.kt`.
- Session time is based on timestamps, so it survives the process being killed.

## Product rules
- Dial: 10..120 min, step 5. Stopwatch mode: plant full at 30 min, < 10 min withers, auto-ends at 180 min.
- 16 species (`Catalog`), 5 free; others bought with coins (1 coin per grown minute). Plant art is
  procedural Canvas drawing in `ui/PlantArt.kt` (fixed seed per species), cached as bitmaps by
  `PlantCache` (40 growth steps). Never reuse Forest art.
- Tags are editable (stored in prefs as JSON); a deleted tag shows as "O'chirilgan teg".
- Blocking modes: ALLOWLIST (block every launchable app except chosen) or BLOCKLIST (block chosen).
  Blocking/strict settings are locked while a session runs. Give up is always possible (no lock-out).
- `BlockRules.verdict`: NEUTRAL (system UI, keyboards, dialer, own app, non-launchable overlays),
  LEFT (launcher/recents), BLOCK, ALLOWED.
- Strict mode (Deep Focus): LEFT or BLOCK starts a 10 s countdown (heads-up notification + block
  screen text). Cancelled by MainActivity.onResume, an ALLOWED app, or screen off. Without the
  accessibility service, MainActivity.onStop (screen on) counts as leaving.
- The block screen must NOT send the user home (GLOBAL_ACTION_HOME used to hide it on Samsung).
- Garden (`GardenScreen`): isometric island per day/week/month/year, stats, tag donut, bars, history.
  `GardenSize`: island width and grid grow with the period (day 4x4 … year 13x13), and further with many plants.
- Garden looks (`data/GardenTheme`, art in `ui/GardenArt.kt`): MEADOW 0, FLOWERS 3, LAKE 7, MOUNTAIN 15,
  ZEN 30, NIGHT 90, SKY 180, GOLDEN 365 days in a row with a grown plant. `Store.bestStreak` (persisted,
  never goes down) opens them; `Streaks.current` counts today or, if nothing grown yet today, from yesterday.
  Each look blocks some squares (`GardenLayout.blocked`) for water/paths/sand; the grid grows to fit plants.
  Chosen look is `Settings.gardenTheme` (empty = newest open). Picker: tap the look's name in Bog'.
- Sounds are synthesized (`AmbientPlayer`), played by `FocusService` during a session.
- Daily reminder (`Reminder`), JSON backup export/import in Settings.
- Focus extras: DND during focus (`focus/Rest.kt` `Dnd`, needs notification-policy access; the phone's
  filter is saved in prefs and restored in `FocusController.finish`), break timer (`BreakTimer`, alarm +
  countdown notification, optional auto start), daily goal bar on the focus tab.
- Weekly auto backup to Download/FocusOn via MediaStore (`data/AutoBackup.kt`, keeps 5).
- Home-screen widget `widget/FocusWidget` (RemoteViews), refreshed on session start/finish and app resume.
- Garden zoom dialog (+/−, two-finger pinch; one finger scrolls) and per-tag stats dialog ("Batafsil").
- Dark mode: `C` colours are getters over `C.dark` (snapshot state) set by `FocusTheme(mode)`;
  `Settings.themeMode` = system / light / dark. Never hard-code light colours in screens: use `C.*`.
- App limits ("Limitlar" tab, `limits/`): per-app daily minutes in `Store.limits`. Usage comes from
  UsageStatsManager events (`UsageTracker`, needs usage access; pure math in `UsageMath`): each activity
  (pkg+class) counts from RESUMED to STOPPED, not PAUSED, so overlays/PiP don't cut it. Checked against
  Samsung Screen time on 2026-11-13 (Chess 65 m, YouTube 50 m: match). `BlockerService`
  passes every real foreground app to `AppLimits.onForeground`, which sets a Handler timer for when time
  runs out and then shows `BlockActivity` with MODE_LIMIT. After the limit: `Settings.graceCount` passes
  per app per day (default 1), each 5 minutes (`Store.grant`, reset at midnight). Works with or without a
  focus session; focus blocking wins when both apply. `adb logcat | grep FocusOnLimits` shows decisions.
- Limits are hard to cheat: `Store.requestLimit` applies new/stricter limits now; raising or removing
  waits until tomorrow (`Store.pendingLimits`, applied by `applyDueLimits`). Rule in `LimitChange`.
- Weekly report (`focus/Weekly.kt`): Sunday 20:00 notification (`WeeklyAlarm`, `Settings.weeklyReport`),
  numbers from `data/WeeklyReport`; tapping it opens Bog' on the week, which also shows the report card.
- Git: repo https://github.com/xursanalime/focuson_for_android (PUBLIC). The keystore is gitignored and
  must never be committed. Commit author uses the GitHub noreply email (repo-local git config).
  Push with `git -c 'credential.helper=!gh auth git-credential' push`.
- Withered plants can be cleared from the garden for `Store.CLEAR_PRICE` (20) coins each (removed from history too).

## Testing on the phone
- Phone: Samsung Galaxy A16 (SM-A165F), Android 16, connected over USB (`adb devices`).
  Install: `~/Android/Sdk/platform-tools/adb install -r Focus-On.apk`.
- The phone is PIN-locked: never try to unlock it; wait for the user (poll `isKeyguardShowing`).
- `adb logcat | grep FocusOnBlocker` shows each foreground app and its verdict.
- NEVER `am force-stop uz.focuson`: Android then turns the accessibility (blocking) permission off
  and the user has to grant it again. `adb install -r` keeps it.
- Editing prefs on the phone (e.g. giving coins): the app keeps prefs in memory and rewrites the
  file, so first end the process with `adb shell run-as uz.focuson kill <pid>` (not force-stop),
  check `pidof` is empty, then pull/edit/push the XML with `exec-out`/`exec-in run-as ... cat`.
- The focus tab must fit ~650dp of height (phone minus bars and bottom menu): the dial takes the
  space that is left (`BoxWithConstraints`). Screenshot test `home_phone` checks this.

## Stages
1. Done 2026-11-13: timer, growing plant, foreground service, blocking, permissions.
2. Done 2026-11-13: strict mode fixed (home screen counts as leaving), realistic plants (16 species),
   garden + statistics, editable tags, coins + shop, stopwatch, sounds, reminder, backup.
   Strict mode and blocking verified on the phone.
