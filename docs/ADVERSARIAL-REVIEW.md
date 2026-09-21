# DOSkies adversarial review

Review date: 2026-09-20. Scope: PLAN.md and the current Android implementation, including the uncommitted T5 bitmap renderer. This is a proposal and handoff only; no implementation files were changed.

## Findings, in priority order

### P1: Cached forecast can be presented with the wrong units or city

`Store.setSnapshot` saves only the raw API response and fetch time. `ForecastWidget.screen` later parses that response using the *current* unit preference and draws the *current* place label. A unit change followed by a failed refresh can present Fahrenheit numbers as Celsius. Selecting a different city followed by a failed refresh can present the previous city's forecast under the new city's name. Updating auto-location coordinates before the fetch creates the same mismatch.

Evidence: `app/src/main/java/app/doskies/Store.java` (`setSnapshot`), `ForecastWidget.java` (`screen`), `MainActivity.java` (`onUnitsChanged`, `selectResult`), and `Repository.java` (`updateLocationIfAuto`).

Proposed change: Store the units and location label/coordinates with each successful snapshot as one coherent record. Render cached data with that record's metadata. Keep the requested settings separate until a successful fetch produces a replacement snapshot. On a failed fetch, show the old forecast with its original location and units plus the error.

Regression: Save a 71°F Medford snapshot, switch to Celsius and force a fetch failure; the widget must never show 71°C. Repeat after selecting another city and after an auto-location change.

### P1: Auto-location can retain a false city label

`Repository.updateLocationIfAuto` stores a successful coarse fix before reverse geocoding. When Android has no Geocoder backend or the lookup misses, it retains the previous place label, even though the next successful weather fetch uses the new coordinates. This is especially relevant to the planned GrapheneOS device.

Evidence: `app/src/main/java/app/doskies/Repository.java` (`updateLocationIfAuto`) and `Locator.java` (`resolveLabel`).

Proposed change: When a new fix lacks a trustworthy city name, label its forecast `Current location` or show coordinates. Do not retain an unrelated city name. Tie the chosen label to the fetched snapshot as above.

Regression: Start with a fixed Medford label, switch to auto, return a distant fix with no Geocoder result, and verify the successful forecast is not labeled Medford.

### P1: Platform Geocoder weakens the stated network privacy guarantee

`AGENTS.md` and `PLAN.md` say the only network calls are to Open-Meteo (plus a future update host). `Locator.reverseLabel` calls the platform Geocoder when present. [Android's API documentation](https://developer.android.com/reference/android/location/Geocoder) says this method may hit the network. The app cannot guarantee which backend a different device uses.

Proposed change: Remove platform reverse geocoding if the Open-Meteo-only promise is intended to be strict. A neutral current-location label is sufficient for auto mode. If the Geocoder remains, narrow the privacy claim and document the conditional backend call.

Regression: Check the source and network behavior on a device with a Geocoder backend; the privacy statement must match actual behavior.

### P2: Error and freshness status are absent from STRIP and WIDE

`ForecastWidget.screen` sets a status for failed refreshes and stale data. `CgaRenderer.drawStacked` displays it, while `drawStrip` and `drawWide` never use it. These shapes can show the last good forecast without any sign that refresh failed, contrary to the plan's error-state contract.

Evidence: `app/src/main/java/app/doskies/CgaRenderer.java` (`drawStrip`, `drawWide`, `drawStacked`).

Proposed change: Reserve a compact status indicator in both shapes, with an error marker visible even where the full message cannot fit. Tapping the widget can open the complete error in the app.

Regression: Render every variant with an error and a cached snapshot; assert a visible error indicator in the produced bitmap or verify it in a saved preview.

### P2: Widget integration coverage fell during the T5 conversion

The old `ForecastWidgetTest` applied `RemoteViews` and checked rendered fields and tap targets. It now tests only the `Screen` state. `CgaRendererTest` checks bitmap dimensions and that drawing does not throw. Neither test exercises the assembled `forecast_widget_frame` RemoteViews, its bitmap binding, its overlay tap region, or real target sizes. The bitmap tests render all variants at 180×220 pixels rather than their intended shapes.

Proposed change: Restore a small integration test that builds/applies the actual RemoteViews at representative STRIP, WIDE, MEDIUM, and LARGE dimensions. Check the bitmap ImageView, open-app tap, and refresh tap. Export previews at those sizes for visual inspection, especially the minimum WIDE size where `drawStatsStacked` has no height-fit check and its `maxW` argument is unused.

## Handoff and verification

1. Fix snapshot provenance and auto-location labeling first. These are correctness issues when network access fails.
2. Decide whether the Open-Meteo-only privacy promise excludes Android Geocoder, then align code and documentation.
3. Add error indicators to STRIP and WIDE, and restore assembled-widget coverage.
4. Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1`, then inspect the widget on the Pixel 7 across resize shapes and with Network access denied.
5. Update `PLAN.md` and `README.md` after validation. The current handoff ends at T5a/T5b and README still says the app is not installable, while the T5 renderer is already in progress.

Validation limit: This review was static. The existing `.tools/build.log` shows a successful build, but its timestamp precedes the current renderer and test edits. No new build or device check was run for this review.

🌱
