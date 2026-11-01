# HyperFragz — notes for Claude sessions

Personal Android app (Kotlin, Jetpack Compose, Material 3, minSdk 26, compile/target 37) that
gives fragrantica.com a native UI. Sideloaded via GitHub Releases, not the Play Store.
Owner: Hosni (GitHub `hystericmoon`). Public repo: https://github.com/hystericmoon/hyperfragz

## How it works

- A **hidden WebView** (`SiteController.kt`) loads fragrantica.com and is the only data source.
  It stays composed full-size under the opaque Compose screens (`ui/App.kt`) so the site's
  lazy loading keeps working.
- Document-start scripts (injected with `androidx.webkit` `addDocumentStartJavaScript`):
  - `assets/bridge.js` — search page: reads Algolia InstantSearch (Vue 3) state, applies
    filters/sort/query, posts `PageState` JSON to `VitrineNative.onState`.
  - `assets/extract.js` — perfume page: extracts every section into JSON, posts to
    `VitrineNative.onDetail` (`detail-partial` while streaming, `detail` when complete).
    Selectors live in the `X` object at the top; patch them there when the site changes.
  - `assets/skin.css` — styling for the visible fallback ("Open on Fragrantica") and hides ads;
    `html.hf-hidden` turns off all animations while the page is hidden.
- `assets/adhosts.txt` — blocked hosts (suffix match), `host/path` prefixes, and `*/path`
  (any non-fragrantica host). Checked in `SiteController.isAd`.
- Native screens: `ui/IndexScreen.kt` (The Index, design/1+3), `ui/RefineSheet.kt` (design/2),
  `ui/DetailScreen.kt` (design/4), `ui/SavedScreen.kt`, `ui/UpdateUi.kt`. Tokens in `ui/Theme.kt`
  ("Night Vitrine"; fonts Instrument Serif italic + Geist). The PNGs in `design/` are the spec.
- `Detail.kt` models; `Detail.mergedOver` keeps sections the site briefly drops while re-rendering.
- `Favorites.kt` — saved perfumes in SharedPreferences. `Updater.kt` — GitHub Releases update
  check/download/install (FileProvider + REQUEST_INSTALL_PACKAGES).
- Links: `MainActivity` handles VIEW intents for fragrantica.com `/perfume/` + `/search` and
  SEND text containing a link → `SiteController.openLink`.

## Hard-won lessons (don't undo these)

- **Eager sections** (`extract.js`, top): on perfume pages, `IntersectionObserver` reports every
  non-ad target visible immediately, and `requestIdleCallback` fires at once. The site wraps
  sections in `lazy-section-new` (IntersectionObserver) and Vue lazy hydration (idle). Without
  this, sections took 6–7 s; with it, ~2–3 s. `loadAll` only scrolls as a fallback after 2.5 s.
- `extract.js` must never declare a function named `performance` (it shadowed
  `performance.now()` and silently broke the eager trigger). The longevity/sillage reader is `perfCounts`.
- **Ad-shield** (anti-adblock) loads from an inline loader with rotating domains
  (butcornyyet.com, neverveto.com, html-load.cc…). It's blocked by its path
  `*/script/d3d3lmzyywdyyw50awnhlmnvbq` (base64 "www.fragrantica.com"). When it ran it cost
  ~60% CPU continuously. If CPU climbs again, profile first (`tools/cdp.py --profile`).
- The hidden page's animations made the app redraw at 120 fps → `html.hf-hidden` CSS.
- `MainActivity.onPause/onResume` pause the WebView (timers + rendering) in the background.
- Renderer death (memory pressure) → `onRenderProcessGone` recreates the WebView; the UI is
  keyed on `site.webView`. Hidden-page drift is corrected by `resyncIfDrifted`.
- Refine sheet: fixed height + `sheetGesturesEnabled = false` (otherwise it jiggles when scrolled).

## Build, run, release

- JDK: Android Studio's JBR (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`) on the Mac;
  cloud sessions have OpenJDK 21; CI uses Temurin 21. Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21. SDK platform package is
  `platforms;android-37.0` (not `android-37`).
- Debug: `./gradlew :app:assembleDebug` → id `com.hosni.hyperfragz.debug`, label "HyperFragz Debug"
  (installs beside the release build). `./run.sh` builds, installs and launches on a device.
- Debug-only: intent extras `--es open <url>` and `--ez crashPage true`; logcat tag `Vitrine`
  logs `detail +Nms complete=… sections` for timing; WebView DevTools enabled.
- **Release = push a tag.** Bump `versionCode` + `versionName` in `app/build.gradle.kts`, commit,
  `git tag -a vX.Y.Z -m "notes"` (tag must equal versionName), `git push origin main vX.Y.Z`.
  `.github/workflows/release.yml` builds, signs with repo secrets (`KEYSTORE_BASE64`,
  `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and publishes `HyperFragz-X.Y.Z.apk`
  (~3.5 min). Apps on 1.1.3+ show the Update banner on next launch.
  If a tag push doesn't trigger the workflow, delete and re-push the tag.
- The signing key is **not in the repo** (`keystore/`, `keystore.properties` are gitignored);
  it exists on Hosni's Mac, in Hosni's backup, and as the GitHub secrets above. Never commit it.
  All releases so far share cert SHA-256 `fa727de5…11ccc6`; a different key breaks updates.

## Cloud sessions (the default way to work)

Development happens in Claude Code cloud sessions. There is no phone, no emulator
(no `/dev/kvm`) and no access to fragrantica.com (it answers cloud IPs with HTTP 429; don't
try to get around that). What replaces each device check:
- **Build:** `./gradlew :app:assembleDebug` works as on the Mac. The environment's setup script
  (copy of `tools/cloud-setup.sh`) installs the Android SDK at `/opt/android-sdk` and a Gradle init
  script that reads Maven Central through Google's mirror. Without the mirror, builds stall for
  minutes in 429 retries. If `ANDROID_HOME` is unset or the init script is missing, run
  `bash tools/cloud-setup.sh` once. First build in a fresh session: ~2–3 min; rebuilds ~15 s.
- **UI:** screenshot tests (below). Look at the images yourself and compare with `design/`.
- **On the phone:** push a test build (below); Hosni installs it from the Debug app's banner.
- **Before a release:** Hosni does the on-device smoke test on a test build of the release commit.

## Test builds (to Hosni's phone)

- `git tag -a test-<short-name> -m "What to check, in one line"` on the commit, then
  `git push origin test-<short-name>`. `.github/workflows/test-build.yml` (~3–4 min) builds the debug
  APK as versionName `X.Y.Z-test.<run number>`, signed with the release key, and publishes it as a
  pre-release titled `Test build N · <tag message>`. Only the newest 5 test builds are kept.
- The Debug app (`com.hosni.hyperfragz.debug`) checks the test channel on every launch and shows
  "Test build N is ready · Install", with the tag message under it. Release apps never see them
  (`/releases/latest` skips pre-releases).
- Locally: `-PtestBuild=N` sets the suffix, `-PsignDebug=true` signs with keystore.properties.
- A Debug app built on the Mac has a different signature: Android refuses to update it from a test
  build. Uninstall it once; from then on test builds update each other.

## Screenshot tests (`app/src/test`)

Roborazzi + Robolectric render each screen on the JVM from sample data (`Samples.kt`, shaped
like bridge.js/extract.js output and mirroring the `design/` PNGs), at 390×844 dp ×2.
Golden images: `app/src/test/screenshots/` (`4-detail-full.png` is one tall image like design/4).
- `./gradlew :app:verifyRoborazziDebug` — fails if any screen changed.
- `./gradlew :app:compareRoborazziDebug` — writes `*_compare.png` (reference | diff | new) to
  `app/build/outputs/roborazzi/`. Read these to check your own UI work.
- `./gradlew :app:recordRoborazziDebug` — accept changes; commit the updated PNGs with the code.
- The **Screenshots** workflow runs verify on every PR and uploads the compare images on failure.
- Tests set `site.state` directly (`internal set`); the WebView exists but loads nothing.
  Images are null, so cards show the bottle outline. Sample reviews use made-up names and text.
- Not covered: the WebView itself, live data, Coil images, real-device fonts/insets.

## Testing on a device (`tools/`)

Needs adb access to a phone or emulator, so only from a local session on Hosni's Mac.
- `tools/ui.sh tap|list|shot|state`, `tools/open.sh <url>`, `tools/fwd.sh` (DevTools on :9222),
  `tools/cdp.py` (evaluate JS / `--profile` / `--reload`), `tools/timing.sh` (section timings),
  `tools/cpu.sh`, `tools/frames.sh`. Hosni's phone: Samsung A55 (serial `RRCX5049JQF`);
  emulator AVD `Vitrine_Pixel`. Set `ANDROID_SERIAL` when more than one device is attached.
- Baselines (1.2.0, phone): sections ≤ ~3 s; idle CPU ~1–2% for app and renderer; 0 idle frames.
- Before a release, smoke-test search, a filter, opening a perfume + Back, Saved, and
  "Open on Fragrantica" — R8 minification only runs in release builds.

## Working with Hosni

- Prefers testing on the real phone; explain in plain language with measured numbers.
- Wants the whole loop to run from cloud sessions; don't make a step depend on his Mac.
- Original constraints: personal use; no prefetching or automated/bulk requests (ad blocking was
  later explicitly requested). Keep the "unofficial, not affiliated with Fragrantica" disclaimer.
- Don't publish Fragrantica members' usernames or review text (e.g. in README screenshots).
- Ideas not done yet: none queued. Past suggestions were links, favourites, problem reports and
  CI releases — all shipped in 1.2.0.
