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
    &current=temperature_2m,weather_code,precipitation,relative_humidity_2m,wind_speed_10m
    &daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max
    &temperature_unit=fahrenheit&precipitation_unit=inch&wind_speed_unit=mph
    &timezone=auto&forecast_days=7
```
`daily.*` are parallel arrays indexed by `daily.time[i]` (ISO date). Model:
`Forecast { current{temp, code, precip, humidity, uvMax, wind}, days[7]{ date, hi, lo, precipChancePct, code } }`.
`humidity` = current `relative_humidity_2m` (%); `uvMax` = today's `daily.uv_index_max[0]` rounded,
shown as `N/11` + band (Low 0-2 / Moderate 3-5 / High 6-7 / Very High 8-10 / Extreme 11+); `wind` =
current `wind_speed_10m`, and `wind_speed_unit` follows the temp unit (mph for F, km/h for C). All
three shown in the current block only, not per day (Dalton's request 2026-09-20; needs a small T1
data-layer patch before the T5 look can show live values).
Tides: NOT available from Open-Meteo (would require NOAA Tides & Currents, US-only + coastal) — out
of scope; Medford NJ is inland. Field customization (user picks which stats show): future enhancement,
not built yet.

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
  saved-place fallback, fixed mode via city search, label handling (auto -> "Current location", no
  platform Geocoder; fixed -> the searched city's stored label). Unit tests for the mode/fallback
  decision logic. **Gate:** `build.ps1` green.

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

### T5 look direction (Dalton, 2026-09-20)
CHOSEN: **CGA panel window** — a DOS window (title bar + double border), authentic CGA-16 palette,
pixel font (VT323, SIL OFL, to be bundled + credited), small colorful pixel weather glyphs. Chosen
to sit with the home screen's neon pixel-DOS icons.
LOCKED SETTINGS (Dalton, blessed mockup v3): ground = **Navy** (#101034 panel, not classic blue);
**scanlines ON**; keep the **precip %** in each day column; add **UV** + **Humidity** to the current
block. LAYOUTS: one adaptive widget that picks by shape — WIDE ROW (current left + all 7 days
across, like his old widget) when wide/short, plus the stacked LARGE/MEDIUM/STRIP when taller.
Mockup: https://claude.ai/artifact/RKQxKSdPSAgUxy6Y54QFYH
TABLED (kept for possible later, not discarded): Amber CRT terminal (mono amber-on-black +
scanlines); Pixel-art scene (larger Game Boy-ish glyphs, art over terminal).

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
- 2026-09-20: T2 done. Widget scaffolding added under `app/src/main/java/app/doskies/`:
  `ForecastWidget` (AppWidgetProvider; STRIP/MEDIUM/LARGE size variants via a RemoteViews SizeF
  map, mirroring the Cloudflare widget's `UsageWidget`), `RefreshJob` (JobService: periodic ~3h/30m
  flex only when a widget is placed, immediate `now()`, `cancel()`, runs `Repository.refresh` on
  Repository's own executor via FutureTask, repaints, then `jobFinished`), `BootReceiver`
  (reschedules on BOOT_COMPLETED/MY_PACKAGE_REPLACED). Placeholder layouts (plain LinearLayout/
  TextView, no pixel art; T5 replaces them) are `forecast_widget_strip.xml` and
  `forecast_widget_card.xml`, both clearly commented as placeholder art. Manifest registers the
  receiver/service/boot receiver and adds RECEIVE_BOOT_COMPLETED. Renders only from
  `Store.snapshot()` and never fetches itself; the three states are: no snapshot -> "Waiting"
  (current_temp "--", no fake forecast); snapshot present + `Store.error()` set -> the last
  snapshot still renders in full, with the error surfacing on the freshness line instead of the
  "Updated ..." text (never blanked); snapshot present + no error -> normal render with the
  freshness line showing an age ("Updated Xm/h/d ago"). Tapping the widget body opens
  `MainActivity`; tapping `refresh` broadcasts an action `ForecastWidget.onReceive` handles by
  calling `RefreshJob.now`. 7 new offline Robolectric tests in `ForecastWidgetTest` (per-variant
  render smoke tests plus the three states; no network, every snapshot is either empty or a
  labeled fixture matching Weather.demo()'s values serialized to the real response shape) bring
  the suite to 52 offline test runs total. `build.ps1` green: assembleRelease, testDebugUnitTest,
  lintRelease. Not yet installable as a widget that *looks* right (placeholder art only, that is
  T5); no location UI yet (T3); no settings screen yet (T4). Next up is T3 (location).
- 2026-09-20: T3 done. Location added under `app/src/main/java/app/doskies/`: `Locator`
  (coarse-only, `LocationManager` alone, no Play services) with `hasLocationPermission(Context)`;
  `getCoarseFix(Context)` checks the enabled NETWORK/PASSIVE/GPS/FUSED providers for the
  freshest cached fix within 30 minutes, else one bounded (8s) `requestSingleUpdate`, off the
  main thread, null on any miss; `reverseLabel(Context, lat, lon)` returns a "City" or
  "City, State" label only when `Geocoder.isPresent()`, else null, guarded so it never throws;
  and two pure, Android-free decision functions, `resolveCoordinates(mode, hasPermission, fix,
  storedLat, storedLon)` (fixed always stored; auto uses the fix only with permission and a fix
  in hand, else stored) and `resolveLabel(geocoderPresent, reverseLabel, storedLabel)` (falls
  back to the stored label unless the geocoder is present and actually returned something).
  `Geocoding` adds Open-Meteo city search: `search(query)` (`HttpURLConnection`, timeouts, 1MB
  cap, no redirects) and a network-free `parse(json)` returning `Result{lat, lon, name, admin1,
  countryCode}`; a response with no "results" key parses as an empty list (Open-Meteo's real
  shape for zero matches), malformed JSON throws. `Repository.refresh` now best-effort updates
  Store's coordinates (and label) from a coarse fix in auto mode before fetching, wrapped so any
  location failure falls through to fetching with whatever coordinates Store already has; fixed
  mode is untouched. `MainActivity` requests `ACCESS_COARSE_LOCATION` on launch only in auto mode
  without it yet, and kicks a refresh on either outcome of the request, never blocking on denial.
  20 new offline JUnit/Robolectric test runs (9 `LocatorTest` covering every resolveCoordinates
  and resolveLabel branch, no Robolectric needed since both are pure; 2 `LocatorPermissionTest`
  for `hasLocationPermission`'s default-denied/granted-after-shadow-grant against a real
  Robolectric `PackageManager`; 9 `GeocodingTest` for the fixture parse, the no-"results"-key and
  empty-array cases, an admin1-absent default, a partial-entry skip, and two malformed-JSON
  throws) bring the suite to 72 offline test runs total. `build.ps1` green: assembleRelease,
  testDebugUnitTest, lintRelease. No Google Play services or Fused Location anywhere; only
  `ACCESS_COARSE_LOCATION` is requested, never fine. No settings UI yet to change location mode
  or run a city search by hand (that is T4); live-device validation (the permission prompt, a
  real fix, GrapheneOS's Geocoder absence) is still Dalton's on the Pixel 7. Next up is T4
  (settings app).
- 2026-09-20: T4 done. `MainActivity` is now a plain, functional settings/host screen (framework
  Activity + framework Views only, matching the Cloudflare Usage Widget's style; no androidx, no
  Material Components, no view binding, no new runtime dependency): a ScrollView of cards for
  Status (place label plus last-updated/error, and a manual "Refresh now" button with a brief
  "Refreshing..." acknowledgement), Units (a Switch, F/C, wired to `Store.setUnits` + immediate
  `RefreshJob.now` since Open-Meteo returns temperatures already converted), Location (a RadioGroup
  auto/fixed wired to `Store.setMode` + `RefreshJob.now`; auto requests `ACCESS_COARSE_LOCATION` if
  not yet granted; fixed reveals a city search: an EditText + button call `Geocoding.search` off
  the main thread on `Repository.IO`, list the candidates as simple result buttons, and picking one
  calls `Store.setLocation` + `Store.setPlaceLabel` + `RefreshJob.now`; empty query, zero results,
  and a search failure each show a clear message and never crash), Demo mode (a Switch wired to a
  new `Store.demo()`/`setDemo(boolean)`, explicit and off by default), a commented WIDGET
  APPEARANCE seam for the T5 art pass (nothing to configure yet), and About (app name +
  `BuildConfig.VERSION_NAME`, a one-line privacy statement, and Open-Meteo attribution referencing
  the new root `CREDITS.md`). `ForecastWidget.render` now checks `Store.demo()` first: when on, it
  renders `Weather.demo()` regardless of any real snapshot, with a "DEMO" marker (prefixed on
  `title` for MEDIUM/LARGE, on `current_cond` for STRIP since it has no title row) and a "Demo
  data, not live" freshness line; `Repository.refresh` short-circuits to a no-op success in demo
  mode, so no network call is made and Store's snapshot/checked/error are left untouched. Demo is
  never a fallback for a failed live fetch: it is only ever set by this explicit toggle, and a
  failed fetch with demo off still keeps the last snapshot (or the Waiting state) plus the error,
  exactly as before T4. 17 new offline JUnit/Robolectric test runs (10 `MainActivitySettingsTest`
  covering every control's exact Store-key wiring, the RadioGroup revealing/hiding city search, a
  geocoding-result selection and a full parsed-fixture-to-click flow with no network, empty-query
  and zero-result messaging, the refresh acknowledgement, and the status/freshness text; 5 new
  `ForecastWidgetTest` cases for the demo marker on both non-STRIP and STRIP variants, demo
  overriding a stale real snapshot/error, and demo-off never substituting demo data whether or not
  a snapshot exists; 2 new `RepositoryTest` cases for the demo no-op) bring the suite to 89 offline
  test runs total. `build.ps1` green: assembleRelease, testDebugUnitTest, lintRelease. Not yet the
  final DOS/pixel look (placeholder layouts and a plain settings screen only; that is T5);
  live-device validation (the permission prompt, a real city search, GrapheneOS's Geocoder
  absence, actually resizing and reading the widget) is still Dalton's on the Pixel 7. Next up is
  T5 (the look, done by hand).
- 2026-09-20: T5a done (the small data-layer patch T5's look needs). `Repository.fetch` now also
  requests `relative_humidity_2m` and `wind_speed_10m` under `current=`, and `uv_index_max` under
  `daily=`; `wind_speed_unit` follows the temperature unit exactly like `precipitation_unit`
  already did (mph for Fahrenheit, kmh for Celsius), via a new pure `Repository.buildUrl` extracted
  for string-only testing. `Forecast.Current` gains `humidity` (%), `uvMax` (rounded from today's
  `daily.uv_index_max[0]`), `wind` (rounded), and `windUnit` ("mph"/"km/h"); a new `Forecast.UNKNOWN`
  sentinel (-1) and `Forecast.Current.display(int)` helper (renders UNKNOWN as "--") back the three
  new fields. `Weather.parse` reads all three as optional/best-effort: absent or null (not merely
  0, which is itself a valid reading) becomes UNKNOWN and never throws, while every existing
  required field keeps throwing exactly as before. `Weather.demo()` carries realistic values
  (humidity 78, uvMax 5, wind 8, windUnit "mph"). `ForecastWidget` was not touched (still reads
  only `current.temp`/`current.code`) and still compiles and renders; showing the new fields is
  T5's job. 7 new offline JUnit/Robolectric test runs (4 in `WeatherTest`: the three new fields
  parsed and rounded correctly plus wind unit under Celsius, a fixture missing all three optional
  fields parsing clean to UNKNOWN, a fixture with them explicitly `null` doing the same, and
  demo()'s realistic values; 3 in `RepositoryTest`: `buildUrl` string assertions for
  mph/inch/fahrenheit vs kmh/mm/celsius and that the new fields are requested) bring the suite to
  96 offline test runs total, all green, no network in any test. `build.ps1` green: assembleRelease,
  testDebugUnitTest, lintRelease. Next up is T5b: wire these fields into the actual CGA-panel
  layouts (done by hand).
- 2026-09-21: T5d done (data-layer correctness fixes from `docs/ADVERSARIAL-REVIEW.md`'s adversarial
  review, following T5b/T5c's hand-authored CGA-panel look and error markers). Plumbing/tests/docs
  only; `CgaRenderer.java` and `Glyphs.java` were not touched. Two P1s fixed:
  - **Snapshot provenance.** `Store.setSnapshot(String)` is now
    `setSnapshot(String raw, char unit, String place, double lat, double lon)`, recording a
    successful fetch as one coherent record (`snapshot_unit`, `snapshot_place`,
    `snapshot_lat`/`lon` alongside the existing `snapshot`/`checked`, same clear-error behavior).
    New `Store.snapshotUnit()`/`snapshotPlace()` read that record back (falling back to the
    requested `units()`/`placeLabel()` only when no snapshot has ever been stored);
    `units()`/`placeLabel()`/`lat()`/`lon()` still mean the live requested settings, unchanged.
    `Repository.refresh` now calls `setSnapshot` with the unit/place/coordinates the fetch actually
    used. `ForecastWidget.screen()` parses a present snapshot with `snapshotUnit()` and labels it
    with `snapshotPlace()` (the waiting/demo states still use the requested `placeLabel()`, since
    they have no fetched data to be faithful to). A unit change or a city change followed by a
    failed refresh can no longer relabel or reconvert stale data.
  - **Platform Geocoder removed.** `Locator.reverseLabel`/`resolveLabel` and the
    `android.location.Geocoder` import are gone; `Repository.updateLocationIfAuto` only updates
    Store's coordinates from a coarse fix now. The place label for a fetched snapshot is decided by
    a new pure `Repository.snapshotLabel(mode, requestedPlaceLabel)`: `"Current location"` in auto
    mode (never an invented or stale city name), the requested place label in fixed mode. The only
    network calls this app makes are now, literally, Open-Meteo (plus, in Phase 2, Dalton's own
    update host) -- no more conditional platform-Geocoder call.

  Also restored the P2 assembled-widget integration coverage that fell during the T5 bitmap
  conversion: `ForecastWidget.build` is now a thin wrapper around a new package-visible
  `static RemoteViews buildForSize(Context, int wDp, int hDp)` (still no drawing logic; that stays
  entirely in `CgaRenderer`), and a new `ForecastWidgetIntegrationTest` applies the real
  `forecast_widget_frame` RemoteViews at one representative dp size per `CgaRenderer.Variant`
  (STRIP/WIDE/MEDIUM/LARGE), checking the bitmap `ImageView` binds a real bitmap and that tapping
  the canvas starts `MainActivity` and tapping refresh broadcasts `app.doskies.REFRESH`.

  16 new offline JUnit/Robolectric test runs (4 new `StoreTest` cases for the snapshot-provenance
  getters/defaults/replacement, doubled under `@Config(sdk={31,35})` to 8 runs; 3 new
  `ForecastWidgetTest` regressions matching the review's exact scenarios -- a cached 71°F snapshot
  stays 'F'/71 after switching to Celsius and failing a refresh, a cached "Alpha" snapshot stays
  ALPHA after switching the requested place to "Beta" and failing a refresh, and an auto-mode
  snapshot is labeled CURRENT LOCATION even with a stale "Medford" sitting in the requested
  settings; 2 new `RepositoryTest` cases for `snapshotLabel`; 3 new `ForecastWidgetIntegrationTest`
  cases), less 4 `LocatorTest` `resolveLabel` cases removed since that method no longer exists,
  bring the suite (115 offline test runs before this pass, across every suite T4/T5b/T5c had
  already grown it to) to 127 offline test runs total, all green, no network in any test. `build.ps1` green:
  assembleRelease, testDebugUnitTest, lintRelease (`releases/DOSkies.apk` rebuilt). The review's
  remaining P2 (a compact error indicator inside `drawStrip`/`drawWide` themselves) was already
  covered by T5c's hand-authored error-marker pass; this T5d entry only backfills the handoff log
  for T5b/T5c and adds the assembled-widget test coverage that P2 also called for. Live-device
  validation (a real failed refresh, a real city change, GrapheneOS's Geocoder-less behavior,
  resizing through every variant) is still Dalton's on the Pixel 7.
