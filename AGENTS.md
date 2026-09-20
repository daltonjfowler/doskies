# DOSkies

A private-by-design weather widget for Android. Seven-day forecast — high/low temperature and
precipitation chance — on the home screen, drawn in a DOS/pixel style that matches the phone.

Standalone Android project. Do not modify sibling projects on this machine. Do not deploy any
Worker for this app without Dalton's go-ahead (see PLAN.md, Phase 2).

## Ground rules

- Java 17, Android platform Views/RemoteViews. minSdk 31, compile/target SDK 35. No runtime
  library dependencies. No Google Play services (this is GrapheneOS on a Pixel 7 — Fused Location
  and Play services are not present; use platform `LocationManager` only).
- **Privacy is the whole point.** No analytics, no ad SDKs, no trackers, no cookies, no accounts,
  no API keys. The only network calls are to Open-Meteo (weather) and, in Phase 2, Dalton's own
  update host. If a change would add a tracker or a third-party call, stop and ask.
- **Data source: Open-Meteo.** Free, keyless, no attribution required for use but credited anyway
  in CREDITS.md. Reject an incompatible response and keep the last good snapshot; never turn a
  failed fetch into fake "0%" or a blank forecast silently.
- **Location is least-privilege.** Coarse location only, requested at runtime, and the app must
  work with location denied by falling back to a saved place (default Medford). Never block the
  forecast on a permission the user declined.
- **Visual work is done by hand in the main loop, never delegated to a build agent.** The pixel
  font choice, the weather glyphs, the palette, the widget composition and any Canvas rendering are
  authored by hand (the human-in-the-loop Opus session), not by a Sonnet build agent. A build agent
  wires data, layout scaffolding, jobs, storage, tests and the release pipeline, and leaves clearly
  marked seams for the art pass. See PLAN.md task tags.
- Any bundled font or glyph asset must be openly licensed (SIL OFL, or CC BY / CC BY-SA with
  attribution). Record the license and attribution in CREDITS.md. No "free for personal use only"
  fonts.
- Tests: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1`. That is the gate:
  APK assembleRelease, JUnit parse/format tests, Robolectric widget-render tests, and lint. Add a
  regression test for every real bug fixed.
- Tests or docs may use clearly labeled demo data. Never silently substitute demo data for a failed
  live fetch.
- No em dashes in UI text or cards. Keep PLAN.md's handoff status current, including any
  live-device validation limits (the build machine is Windows; the phone is Dalton's).
- Sign commits with the co-author line the harness specifies. Sign off notes with a plant.
