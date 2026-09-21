# DOSkies

A weather widget for Android that minds its own business.

Seven days on your home screen: high and low temperature and the chance of rain, in a DOS/pixel
style. No ads, no trackers, no cookies, no account, no API key, no subscription. It talks to exactly
one weather service ([Open-Meteo](https://open-meteo.com)) and, if you turn it on, your own update
host. That is the entire list of things it phones.

Built for GrapheneOS on a Pixel 7. No Google Play services required.

## Status

The DOS/pixel look (a CGA panel window, hand-rendered to a bitmap) has shipped, so the widget is
installable and usable. On-device validation on the target Pixel 7/GrapheneOS hardware is still
pending. See [PLAN.md](PLAN.md) for the build order and current handoff status, and
[AGENTS.md](AGENTS.md) for the house rules.

## Building

```
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
```

Builds a release APK, runs the unit and widget-render tests, and lints. The JDK and Android SDK live
in a local `.tools/` directory (git-ignored); nothing is installed system-wide.

## Privacy

DOSkies stores no secrets and holds no account. Location is coarse, optional, and asked for at
runtime; deny it and the widget falls back to a saved place. If a fetch fails, the last good
forecast stays on screen rather than being replaced by zeros.

## Credits

Weather data by Open-Meteo (CC BY 4.0). Bundled fonts and glyph assets and their licenses are listed
in [CREDITS.md](CREDITS.md).
