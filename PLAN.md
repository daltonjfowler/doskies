# DOSkies — plan

A weather widget for the home screen that does not spy on you. Seven days, high and low
temperature, and the chance of rain, drawn in a DOS/pixel style so it belongs next to the pixel
sunset wallpaper and the DOS-style app icons. Built to replace an ad-and-tracker-ridden weather
widget that wanted a subscription.

Target device: GrapheneOS on a Pixel 7. Build machine: Windows.

## What it is (and is not)

| | |
|---|---|
| Data | Open-Meteo. Free, keyless, no account, no tracking. The only weather call. |
| Secrets | None. There is no token, no login, no Keystore. This app stores nothing sensitive. |
| Trackers/ads | None, ever. That is the reason this project exists. |
| Location | Coarse only, asked at runtime, optional. Denied -> falls back to a saved place. |
| Surface | A resizable Android home-screen widget, plus a small settings/host app. |
| Look | DOS/pixel. Bitmap pixel font + hand-drawn pixel weather glyphs. Hand-authored. |
| Distribution | Self-hosted APK + in-app updater (Phase 2, Dalton-gated). No Play Store. |

## Architecture (mirrors the Cloudflare Usage Widget, minus all the secret-handling)

- Java 17, Android platform Views/RemoteViews. minSdk 31, compile/target SDK 35.
- No runtime dependencies. No Google Play services. Location via platform `LocationManager`.
- `DOSkies` (AppWidgetProvider) renders size variants from one snapshot; taps open the app and
  trigger a refresh. `RefreshJob` (JobService) refreshes periodically and on demand; `BootReceiver`
  reschedules after boot/update. `Repository` fetches + parses + stores; `Store` is SharedPreferences.
- Keep the last good snapshot on any fetch failure. Never render a fake forecast.
- Local toolchain in `.tools/` (JDK + Android SDK), reused from the Cloudflare widget's `.tools`
  (same SDK). `scripts/build.ps1` is the gate. `scripts/ship.ps1` builds + gates + signs + (Phase 2)
  publishes.

## Data contract (Open-Meteo)

Forecast (units swap `fahrenheit`/`celsius` per the setting):
```
GET https://api.open-meteo.com/v1/forecast
    ?latitude={lat}&longitude={lon}
    &current=temperature_2m,weather_code,precipitation
    &daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max
    &temperature_unit=fahrenheit&precipitation_unit=inch&wind_speed_unit=mph
    &timezone=auto&forecast_days=7
```
`daily.*` are parallel arrays indexed by `daily.time[i]` (ISO date). Model:
`Forecast { current{tempF, code, precip}, days[7]{ date, hi, lo, precipChancePct, code } }`.

City search (fixed-location mode only):
```
GET https://geocoding-api.open-meteo.com/v1/search?name={q}&count=5&language=en&format=json
```
Returns `results[]{ latitude, longitude, name, admin1, country_code }`. Use it to set both the
coordinates and the display label so "Medford" ambiguity is resolved by the user's pick. Default
fallback is Medford NJ 08055 (39.9007, -74.8235); auto-locate overrides it whenever allowed.

### WMO weather_code -> condition (author glyphs for these ten buckets)

| Bucket | Codes | Short label |
|---|---|---|
| CLEAR | 0 | Clear |
| PARTLY | 1, 2 | Partly cloudy |
| CLOUDY | 3 | Cloudy |
| FOG | 45, 48 | Fog |
| DRIZZLE | 51, 53, 55, 56, 57 | Drizzle |
| RAIN | 61, 63, 65, 66, 67 | Rain |
| SHOWERS | 80, 81, 82 | Showers |
| SNOW | 71, 73, 75, 77, 85, 86 | Snow |
| THUNDER | 95, 96, 99 | Storm |
| UNKNOWN | anything else | (fall back to CLOUDY glyph, keep the numeric code in logs) |

## Task order (sequential; one build agent at a time; the local gate is free)

Tags: **[Sonnet]** mechanical plumbing/tests/pipeline, run as workflow build agents (1-2 at a time).
**[Hand]** visual/art, authored by hand in the main loop (Opus), never delegated to a build agent.
Each task ends green on `scripts/build.ps1` before the next begins.

- **T0 [Sonnet] Skeleton + toolchain.** Provision `.tools/` (copy the JDK+SDK from the Cloudflare
  widget), gradle wrapper, `settings.gradle`, root + `app/build.gradle` (namespace `app.doskies`,
  test-only deps: junit, org.json, robolectric), manifest, empty `MainActivity`, theme, strings,
  placeholder launcher icon. **Gate:** `build.ps1` assembles a release APK and passes lint.

- **T1 [Sonnet] Data layer.** `Weather`/`Repository` (HttpURLConnection fetch, size cap, timeouts,
  clear error strings, keep-last-snapshot), `Forecast` model, WMO bucket mapping, `Store`
  (units, mode, lat/lon, place label, snapshot, checked, error). Fixtures + JUnit parse/format
  tests + WMO mapping tests. **Gate:** `build.ps1` green with tests.

- **T2 [Sonnet] Widget scaffolding.** `DOSkies` AppWidgetProvider, `usage`-style provider xml,
  `RefreshJob`, `BootReceiver`, size-variant layouts with **placeholder standard views** and stable
  IDs, tap-to-open + tap-refresh, Waiting/Error/Connected states. Robolectric render test per
  variant. **Gate:** `build.ps1` green; widget renders without crashing.

- **T3 [Sonnet] Location.** Coarse `LocationManager` fix, runtime permission flow, auto mode with
  saved-place fallback, fixed mode via city search, label handling (Geocoder if present, else stored
  label). Unit tests for the mode/fallback decision logic. **Gate:** `build.ps1` green.

- **T4 [Sonnet] Settings app.** `MainActivity`: units toggle, location mode + city search, manual
  refresh, labeled demo toggle, about/credits, and a clearly marked seam for widget appearance the
  art pass will own. **Gate:** `build.ps1` green.

- **T5 [Hand] The look.** Bundled openly-licensed pixel/DOS font, ten hand-drawn pixel weather
  glyphs, palette, widget composition. Likely a Canvas-to-Bitmap render of the forecast "screen"
  for true pixel fidelity, with invisible tap regions overlaid for open/refresh. Replace the
  placeholder layouts. Update CREDITS.md with font + any asset licenses. **Done by hand, not delegated.**

- **T6 [Sonnet] Release pipeline + docs.** `ensure-signing.ps1`, `build.ps1`, `ship.ps1`,
  `verify-apk.ps1`; README with real screenshots; final icon (hand). **Gate:** signed APK in
  `releases/`, `build.ps1` green.

- **Phase 2 [Dalton-gated] Updater + host.** `updates/` Worker serving `latest.json` + the APK,
  `Updater` + daily check job + one notification (nothing auto-installs), `publish.ps1`. Mirrors
  Tally and the Cloudflare widget. Dalton runs the deploy; the agent prepares everything up to it.

## Open decisions (Dalton)

- Repo visibility: starting **private**. Say the word to make it public.
- Units default **Fahrenheit** with a Celsius toggle. Change the default if you prefer.
- Widget appearance knobs (accent/opacity) or a single fixed DOS palette: decided in the T5 art pass.

## Handoff status

- 2026-09-20: Repo scaffolded (this plan, AGENTS.md, .gitignore, README). No app code yet. Next up
  is T0. Build machine is Windows; physical-device validation (adding the widget, the location
  prompt, real APK install) is Dalton's on the Pixel 7.
- 2026-09-20: T0 done. Skeleton builds and gates green (`scripts/build.ps1`: assembleRelease,
  testDebugUnitTest, lintRelease all pass; `releases/DOSkies.apk` staged). `.tools/` toolchain
  (JDK, Android SDK, warmed Gradle cache) copied locally from the Cloudflare widget's, self-contained,
  no network needed. Placeholder launcher icon and empty MainActivity only; no widget, no data layer,
  no location yet (that is T1-T3). Not yet installable as a real widget. Next up is T1 (data layer).
  Physical-device validation is still Dalton's on the Pixel 7.
- 2026-09-20: T1 done. Data layer added under `app/src/main/java/app/doskies/`: `Forecast`
  (current + days[7], unit marker), `Wmo` (code -> Condition, PLAN table labels, UNKNOWN falls
  back to the CLOUDY glyph but keeps the numeric code), `Weather` (org.json parser + `demo()`),
  `Repository` (HttpURLConnection fetch with timeouts/size cap/no redirects, `refresh(Context)`
  keeps the last snapshot on failure), `Store` (SharedPreferences `doskies`: units, mode, lat/lon,
  placeLabel, snapshot/checked/error; no Keystore, nothing secret). 45 offline JUnit/Robolectric
  test runs (33 methods; Store's run twice under `@Config(sdk={31,35})`) cover the parse fixture,
  all ten WMO buckets plus unknown, rounding/clamping, malformed-response rejection, and Store
  round-trips. `build.ps1` green: assembleRelease, testDebugUnitTest, lintRelease. Not yet wired
  into a widget or UI (that is T2-T4); no location resolution yet (T3), so lat/lon default to a
  placeholder (Medford, MA). Next up is T2 (widget scaffolding).
