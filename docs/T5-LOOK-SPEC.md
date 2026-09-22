# T5 look spec — CGA panel window (hand-authored)

The exact target for the DOSkies widget look. Blessed by Dalton via mockup v4
(https://claude.ai/artifact/RKQxKSdPSAgUxy6Y54QFYH). This file is the source of truth; build the
renderer to it. Rendering is **Canvas-to-Bitmap** for true pixel fidelity (RemoteViews text/limits
cannot do the scanlines, double border, and pixel glyphs faithfully).

## Palette (locked: Navy ground)

| Token | Hex | Use |
|---|---|---|
| PANEL | `#101034` | panel fill (navy). Classic-blue alt `#0000AA` kept as a constant, not used. |
| EDGE | `#55FFFF` | bright cyan, the double border + title rule + refresh glyph |
| TITLE | `#FFFF55` | yellow, "DOSkies" title + stat labels (UV/HUM/WIND) + day-of-week |
| VALUE | `#FFFFFF` | white, temperatures + stat values + current temp |
| ACCENT | `#55FFFF` | bright cyan, condition text + precip % + location |
| DIM | `#AAAAAA` | light gray, low temp + secondary text |
| FAINT | `#555555` | dark gray, precip % when < 25 (dry) + the "unknown/--" dashes |
| BAD | `#FF5555` | bright red, error status line |
| UV bands | Low `#55FF55` / Moderate `#FFFF55` / High `#FF5555` / Very High + Extreme `#FF55FF` | |

Scanlines: 1px black lines at alpha ~40/255 every 3px down the whole panel, drawn last.
Border: 2px EDGE outer, 1px PANEL gap, content inset. Title bar: top strip, 1px EDGE rule beneath.

## Font

VT323 (SIL OFL), bundled at `app/src/main/res/font/vt323.ttf`; license at `licenses/VT323-OFL.txt`,
credited in CREDITS.md. Load once via `context.getResources().getFont(R.font.vt323)`; anti-alias on.

## Glyphs — ten 12x12 pixel grids (source of truth, transcribe verbatim)

Chars: `Y`=#FFFF55 yellow, `W`=#FFFFFF white, `G`=#AAAAAA ltgray, `C`=#55FFFF cyan, space=empty.
Bucket names match Wmo.Condition. UNKNOWN uses the "?" glyph (Wmo already keeps the numeric code).

```
CLEAR (sun)          PARTLY               CLOUDY               FOG
     YY               YY                                        
  Y  YY  Y          Y YY Y                                   WWWWWW
   YYYYYY            YYYY                  WWWW              WWWWWWWWWW
  YYYYYYYY          YYYYYY WWW           WWWWWWWW           WWWWWWWWWWWW
 YYYYYYYYYY          YYYY WWWWW         WWWWWWWWWW           GGGGGGGGGG
YY YYYYYY YY          Y WWWWWWWW       WWWWWWWWWWWW         
YY YYYYYY YY           WWWWWWWWW       WWWWWWWWWWWW        GGGGGGGGGGGG
 YYYYYYYYYY          WWWWWWWWWW        WGGGGGGGGGGW        
  YYYYYYYY           WGGGGGGGGW         GGGGGGGGGG          GGGGGGGGGG
   YYYYYY             GGGGGGGG                             
  Y  YY  Y                                                GGGGGGGGGGGG
     YY                                                    
```

Exact rows (12 chars each), authoritative:

CLEAR (pulled in one cell all round so the sun reads a touch smaller; disc cols 3-8):
`            ` / `     YY     ` / `   Y    Y   ` / `    YYYY    ` / `   YYYYYY   ` /
` Y YYYYYY Y ` / ` Y YYYYYY Y ` / `   YYYYYY   ` / `    YYYY    ` / `   Y    Y   ` /
`     YY     ` / `            `

PARTLY:
` YY         ` / `Y YY Y      ` / ` YYYY       ` / `YYYYYY WWW  ` / ` YYYY WWWWW ` /
`  Y WWWWWWWW` / `   WWWWWWWWW` / `  WWWWWWWWWW` / `  WGGGGGGGGW` / `   GGGGGGGG ` /
`            ` / `            `

CLOUDY:
`            ` / `            ` / `    WWWW    ` / `  WWWWWWWW  ` / ` WWWWWWWWWW ` /
`WWWWWWWWWWWW` / `WWWWWWWWWWWW` / `WGGGGGGGGGGW` / ` GGGGGGGGGG ` / `            ` /
`            ` / `            `

FOG:
`            ` / `   WWWWWW   ` / ` WWWWWWWWWW ` / `WWWWWWWWWWWW` / ` GGGGGGGGGG ` /
`            ` / `GGGGGGGGGGGG` / `            ` / ` GGGGGGGGGG ` / `            ` /
`GGGGGGGGGGGG` / `            `

DRIZZLE:
`            ` / `    WWWW    ` / `  WWWWWWWW  ` / ` WWWWWWWWWW ` / `WWWWWWWWWWWW` /
`WGGGGGGGGGGW` / ` GGGGGGGGGG ` / `   C   C    ` / `            ` / `  C   C     ` /
`            ` / `            `

RAIN:
`            ` / `    WWWW    ` / `  WWWWWWWW  ` / ` WWWWWWWWWW ` / `WWWWWWWWWWWW` /
`WGGGGGGGGGGW` / ` GGGGGGGGGG ` / `  C   C   C ` / ` C   C   C  ` / `  C   C   C ` /
` C   C   C  ` / `            `

SHOWERS:
`YY          ` / ` Y   WWWW   ` / `Y   WWWWWWW ` / `   WWWWWWWWW` / `  WWWWWWWWWW` /
`  WGGGGGGGGW` / `   GGGGGGGG ` / `  C  C  C   ` / ` C  C  C    ` / `  C  C  C   ` /
` C  C  C    ` / `            `

SNOW:
`            ` / `    WWWW    ` / `  WWWWWWWW  ` / ` WWWWWWWWWW ` / `WWWWWWWWWWWW` /
`WGGGGGGGGGGW` / ` GGGGGGGGGG ` / `  W   W   W ` / ` W   W   W  ` / `  W   W   W ` /
` W   W   W  ` / `            `

THUNDER:
`            ` / `    WWWW    ` / `  WWWWWWWW  ` / ` WWWWWWWWWW ` / `WWWWWWWWWWWW` /
`WGGGGGGGGGGW` / ` GGGGGGGGGG ` / `     YY     ` / `    YY      ` / `   YYYY     ` /
`     YY     ` / `    YY      `

UNKNOWN (?):
`            ` / `   WWWWW    ` / `  W     W   ` / `  W     W   ` / `        W   ` /
`       W    ` / `     WW     ` / `     W      ` / `            ` / `     W      ` /
`     W      ` / `            `

## Layouts — one adaptive widget, picks by shape

Read the widget's current size (AppWidgetManager options min/max width+height dp). Pick:
- **WIDE ROW** when wide and short (width >= ~250dp and height < ~110dp): current block on the left
  (glyph + big temp + condition + UV/HUM/WIND stacked small), a 1px dashed vertical rule, then 7 day
  columns across, each: DOW (yellow) / small glyph / hi (white) / lo (gray) / precip % (cyan, or
  FAINT when < 25).
- **LARGE** when tall (height >= ~200dp): current block on top (glyph + temp + condition, then a
  UV/HUM/WIND line), a dashed rule, then 7 day rows stacked: DOW | glyph | hi | lo | precip %.
- **MEDIUM** in between: current block + UV/HUM/WIND line + first 3 day rows.
- **STRIP** when very short (height < ~60dp): one line — glyph, temp, condition, refresh glyph.

Title bar on all but STRIP: `DOSkies` (yellow) left; `MEDFORD` (white) + `↻` (cyan) right; 1px cyan
rule beneath. UV shown as `N/11` + band word in the band color; `--` (FAINT) for any unknown (-1)
stat. Precip band: `>=25` cyan, else FAINT.

## States
- No snapshot: current temp `--`, condition `Waiting`, day rows hidden, status `Tap refresh to load`.
- Error + snapshot present: render the full last forecast; status line becomes the error text in BAD.
- Demo on (Store.demo()): render Weather.demo() with a `DEMO` marker in the title bar; never on failure.

## Wiring
- FrameLayout: an ImageView (fills, `setImageViewBitmap`, tap = open MainActivity) + a transparent
  View pinned top-right ~44dp square (tap = broadcast REFRESH -> RefreshJob.now). Bitmap redrawn on
  onUpdate and onAppWidgetOptionsChanged, sized to the widget px, recycled sensibly.
- Keep all existing behavior (keep-last-snapshot, demo rules) intact.
