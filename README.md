# AlphaSim — WorldQuant BRAIN alpha simulator for Android

A native Android app (Kotlin + Jetpack Compose) that takes lists of FASTEXPR alpha expressions,
simulates them on your WorldQuant BRAIN account through the BRAIN REST API, and lets you review,
filter, export and submit the results from your phone.

Your three 200-alpha lists (`brain_alphas_GLB_200.txt`, `brain_alphas_USA_200.txt`,
`brain_alphas_GLB_200_v2.txt`) are bundled, so you can import them with one tap.

## Features

**Account**
- Sign in with your BRAIN email/password (HTTP basic → session cookie, same as the platform).
- Biometric (Persona) verification flow: open the link, finish the face check, tap *I've finished*.
- Optional "keep me signed in": the password is encrypted with an AES-GCM key in the Android
  Keystore, so long runs renew the 4-hour session token on their own.

**Lists**
- Import from the bundled files, any `.txt` file on the phone, or the clipboard
  (one expression per line; `#` / `//` comments and blank lines are skipped; duplicates removed).
- Basic syntax checks on import (unbalanced parentheses / quotes, truncated lines).
- Region auto-detected from the file name or content (GLB vs USA).
- Per-list simulation settings: region, universe, delay, decay, neutralization, truncation,
  pasteurization, NaN handling, unit handling, test period, max trade, language — with
  per-alpha overrides.
- Rename, duplicate, delete, add more expressions, reset results.

**Simulation engine**
- Persistent queue stored in Room — survives app restarts; interrupted simulations resume
  polling instead of being re-simulated (the progress URL is saved as soon as BRAIN accepts one).
- Configurable concurrency (default 3, BRAIN's usual per-user limit).
- Optional multi-simulation mode (up to 10 alphas per request, needs the BRAIN permission).
- Honors `Retry-After`; rate limits and the concurrent-simulation limit wait without burning
  retries; network/server errors retry with back-off; expression errors fail fast with BRAIN's message.
- Runs as a foreground service with a progress notification (Pause / Stop actions) and a
  "run finished" notification. Pause lets in-flight simulations finish; Stop parks them for later.
- Automatic session renewal on 401; if BRAIN demands biometrics mid-run, the run pauses and asks you to sign in.

**Results**
- List view with status chips, Sharpe / Fitness / Turnover / Returns / Drawdown, colour-coded
  against BRAIN's thresholds (delay-aware).
- Filters (not run, queued, running, done, passing, failing checks, errors, starred),
  sort by any metric, search by expression or alpha id, multi-select (run / reset / delete).
- Alpha detail: full stats, interactive cumulative PnL chart, yearly stats table, all IS checks,
  self-correlation and production-correlation, full submission check, **Submit** (with confirmation),
  name & tags (synced to BRAIN), star, open on the BRAIN website, copy/share, edit & re-simulate.
- Export a list to CSV (save to a file or share), copy passing alpha IDs, copy all expressions.

## Build it

The project uses Gradle 8.11 (wrapper included), Android Gradle Plugin 8.7, Kotlin 2.0.21,
compileSdk/targetSdk 35, minSdk 26 (Android 8.0+). JDK 17 is required.

**Option A — Android Studio (easiest)**
1. Unzip, then *File → Open* the `AlphaSim` folder in Android Studio (Ladybug or newer).
2. Let Gradle sync (it downloads dependencies the first time).
3. Plug in your phone with USB debugging on and press **Run** — or *Build → Build APK(s)*.

**Option B — GitHub, no local setup**
1. Push the folder to a new GitHub repository.
2. The included workflow `.github/workflows/build-apk.yml` builds on every push.
3. Open the run under the *Actions* tab and download the `AlphaSim-debug-apk` artifact,
   then install the APK on your phone (allow "install unknown apps" for your browser/file manager).

**Option C — command line** (with the Android SDK installed and `ANDROID_HOME` set)
```
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # minified build, signed with the debug key for side-loading
```

## Using it

1. Open the app → **Sign in** with your BRAIN credentials (complete the biometric check if asked).
2. **Import alphas** → tap one of the bundled lists (or open a file / paste) → check the settings → **Import & run**.
3. Watch progress on the home screen or in the notification. Leave the phone; the run continues with the screen off.
4. Open a list to filter and sort results; tap an alpha for its PnL chart, checks and actions.
5. For long lists: *Settings → Battery settings* and exempt AlphaSim from battery optimisation.

## Notes

- Endpoints used: `POST /authentication`, `POST /simulations`, polling the returned `Location`,
  `GET /simulations/{id}`, `GET /alphas/{id}`, `/alphas/{id}/recordsets/{pnl,yearly-stats}`,
  `/alphas/{id}/correlations/{self,prod}`, `/alphas/{id}/check`, `/alphas/{id}/submit`,
  `PATCH /alphas/{id}` — all on `https://api.worldquantbrain.com`.
- This is an unofficial client. Simulations count against your account's limits; use it within
  BRAIN's terms of use. The settings lists (regions, universes, neutralizations) are editable
  text fields, because what's available depends on your account.
- Android 15 caps "data sync" foreground services at about 6 hours per day. If a run hits that,
  AlphaSim stops cleanly and posts a notification — open the app and tap **Start** to continue;
  nothing is lost.
- Everything (lists, results, cached charts) is stored only on the phone.

## Project layout

```
app/src/main/java/com/alphasim/brain/
├── AlphaSimApp.kt            Application + dependency container
├── MainActivity.kt
├── data/
│   ├── api/BrainApi.kt       BRAIN REST client (auth, persona, simulate, poll, alphas, checks, submit)
│   ├── api/PersistentCookieJar.kt
│   ├── db/                   Room entities + DAOs (lists, alphas, recordset cache)
│   ├── model/                SimSettings + catalog, results, recordsets
│   ├── prefs/                DataStore settings, Keystore-encrypted credentials
│   ├── AlphaRepository.kt
│   ├── SessionManager.kt
│   ├── ExpressionTools.kt    import parsing, syntax checks, operator/field extraction
│   └── CsvExporter.kt
├── sim/
│   ├── SimulationEngine.kt   queue, concurrency, multi-sim, retries, resume
│   ├── SimulationService.kt  foreground service, wake lock, notifications
│   └── Notifications.kt
└── ui/                       Compose screens: home, import, list, alpha detail, login, settings
app/src/main/assets/alphas/   the three bundled alpha lists
```
