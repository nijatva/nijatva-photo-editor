# Nijatva Editor — v1.3.0

Fully offline photo editor for Android. Kotlin + Jetpack Compose + Material 3,
pure AMOLED black (`#000000`). No internet permission, no analytics, no cloud.

* Package: `com.nijatva.photo.editor`
* minSdk 26 (Android 8.0) · targetSdk / compileSdk 35 (Android 15)
* APK: `Nijatva-Editor-1.3.0.apk` (~6.2 MB, signed, installable, updates over 1.0/1.1)
* Signing key: `nijatva-release.keystore` (alias `nijatva`, store & key password `nijatva`).
  Keep this file — future versions must be signed with the same key to update over the top.

## Install

Copy the APK to the phone and open it (allow "Install unknown apps" for your file
manager / browser when asked). Or over USB:

    adb install -r Nijatva-Editor-1.3.0.apk

The app also appears in the system "Open with" / "Edit with" sheet for images
(`ACTION_EDIT`, `ACTION_SEND`, `ACTION_VIEW`).

## What is new in 1.3.0

* **App renamed to "Nijatva Editor"** (Hindi: निजत्व एडिटर).
* **Local adjustments with masks** — the Lightroom-style brush: pick exposure /
  saturation / warmth, then paint. Only the painted, feathered area changes.
  Every mask is a layer in the edit state, so it is undoable and replayed at full
  resolution on export. New "Local" tab.
* **Split compare** — tap "Split" in the top bar and drag the divider to wipe
  between the original and the edited photo (alongside press-and-hold compare).
* **Export resolution scaling** (100% / 75% / 50%) with a live estimated file size.
* **Tap any slider label to reset just that value** (photo-editor convention).
* **Six more presets**: Sepia, Chrome, Vintage, Matte, Sunset, Forest (13 total).
* Home screen now carries quick tips; About shows 1.3.0.

## What is new in 1.2.0

* Local mask engine groundwork, split-compare scaffolding, export scaling and the
  extra filter presets were introduced here.

## What is new in 1.1.0

* **Crop with handles**: drag the four corners (or the frame) on a live crop
  overlay with rule-of-thirds grid, plus a straighten slider (±45°) that
  auto-zooms so no black edges appear. Aspect presets now set a real crop window.
* **Full tone control**: highlights and shadows (luminance-masked), vibrance and
  tint next to the existing exposure / contrast / saturation / warmth.
* **Detail tab**: clarity (local contrast), sharpening (unsharp mask), vignette
  and film grain — real per-pixel work, not fake sliders.
* **Curves tab**: five-point RGB curve with Catmull-Rom interpolation drawn live
  and baked into a 256-entry LUT.
* **HSL tab**: eight colour ranges (red, orange, yellow, green, aqua, blue,
  purple, magenta) with hue / saturation / luminance per range.
* **Markup tab**: pen, highlighter, pixelate, blur, blackout and text layers.
  Redaction is real — the pixelate/blur patches are computed on the pixels under
  the stroke, not a translucent overlay. Layers live in the edit state, so they
  are undoable, reorderable by undo/redo and replayed at full resolution on export.
* **Live RGB histogram** overlay (toggle in the top bar).
* Everything above stays non-destructive: it is all parameters in `EditState`
  replayed by `Pipeline.render()`, and the original file is never touched.

## What is still missing

* On-device AI: subject cutout / background removal and OCR. These need ML Kit
  (bundled models) or ONNX Runtime + U2Net, which add 25-40 MB and a much larger
  build. Planned as v1.2.
* `.cube` LUT import, perspective (4-point) correction, batch processing with
  WorkManager, and an EXIF *editor* (currently view + strip).

## What works in this build

* Home screen with the aperture mark, photo picker (`PickMultipleVisualMedia`-style
  system Photo Picker — no storage permission needed on Android 13+).
* Editor workspace: pinch-zoom (1×–8×), two-finger pan, double-tap to fit,
  press-and-hold "Original" to compare against the untouched source.
* Non-destructive pipeline: every edit is a parameter set (`EditState`) replayed on
  the bitmap — the source file is never modified. Full undo / redo stack.
* Crop tab: rotate 90° left/right, flip horizontal/vertical, draggable crop
  window, straighten, aspect presets (Free, 1:1, 4:5, 16:9, 3:2), reset.
* Adjust tab: exposure, contrast, highlights, shadows, saturation, vibrance,
  warmth, tint (all -100…+100, tap Reset).
* Filters tab: Original, Mono, Noir, Warm, Cool, Vivid, Fade (colour-matrix presets).
* Export tab: JPEG / PNG / WebP, quality 10–100, "Strip EXIF & GPS" (on by default),
  saved through `MediaStore` into `Pictures/Nijatva/`. On Android 9 and below the app
  asks for the legacy storage permission first.
* EXIF sheet: make, model, date, aperture, shutter, ISO, focal length, size, GPS.
* Localisation: English + Hindi (`values-hi`); the rest of India's languages drop into
  `res/values-xx/strings.xml` without code changes.
* Adaptive launcher icon (teal→blue→violet→pink aperture on black), round variant,
  and an Android 13+ monochrome/themed layer.

## Not in this build (next phases)

These need extra dependencies / time and are deliberately left out, so nothing here
is a stub that pretends to work:

* On-device ML Kit subject cutout / background removal and OCR (adds ~40 MB of
  bundled models and the `com.google.mlkit` dependencies).
* Tone curve editor, 8-channel HSL mixer, `.cube` LUT import, clarity/sharpen/denoise,
  vignette and grain.
* Markup brush / text overlay / pixelate-blur redaction, spot healing.
* Perspective (4-point) correction, batch processing, foreground-service WorkManager jobs.

The pipeline is a single `EditState` → `Pipeline.render()` function, so each of the
above slots in as one more parameter plus one more UI panel.

## Build from source

    # JDK 17 + Android SDK 35 (platforms;android-35, build-tools;35.0.0)
    ./gradlew :app:assembleRelease      # or: gradle :app:assembleRelease

`local.properties` must point at your SDK (`sdk.dir=/path/to/Android/sdk`).

Release signing reads `nijatva-release.keystore` from the project root (alias
`nijatva`). The keystore is **not** in this repository — bring your own, or point
`signingConfigs.release` in `app/build.gradle.kts` at your key. For a quick local
build use `./gradlew :app:assembleDebug`.

## Attribution

APK always made by Vasudev Dangaya.

## Web app

`web/` is the source of the browser editor (plain HTML/CSS/JS, no build step).
The published copy lives in the suite repository, so its URL is the short one:

    source      web/                                  (this repo)
    published   nijatva/nijatva.github.io → editor/    → https://nijatva.github.io/editor/

Copy `web/*` into that repository's `editor/` folder (and bump the cache name in
`sw.js`) whenever the shell changes. `docs/app/` and `docs/app.html` here are only
redirects to the suite URL now.

```
web/                docs/
  index.html    ->    app/index.html      UI markup
  styles.css    ->    app/styles.css      design tokens, AMOLED theme
  app.js        ->    app/app.js          canvas pipeline, tools, export
  manifest.webmanifest, sw.js, icons      PWA shell (offline)
docs/index.html                          landing page (APK + web app)

Web app panels: Crop, Adjust (exposure, contrast, highlights, shadows,
saturation, warmth), **Curves** (5-point tone curve baked to a 256-step LUT),
**HSL** (8 colour ranges with hue / saturation / luminance), Filters (9 presets),
Markup (pen, highlighter, pixelate, blackout) and Export (JPEG/PNG/WebP, quality).
docs/app.html                            redirect -> app/
```

## CI

`.github/workflows/build.yml` builds an APK on every push to `main`, on a `v*` tag
and on demand (Actions → build → Run workflow). The APK is attached to the run as
the `nijatva-editor-apk` artefact.

* With the repository secret **`KEYSTORE_BASE64`** set, it builds the **signed
  release** APK (the keystore at the project root; alias and passwords are in
  `app/build.gradle.kts`). Create the secret with
  `base64 -w0 nijatva-release.keystore`.
* Without that secret it falls back to a **debug** APK (package id gets the
  `.debug` suffix), which is enough to test a build.

`GITHUB_TOKEN` is passed through as `GITHUB_TOKEN` because `com.nijatva:signature`
resolves from GitHub Packages; the job needs `packages: read`.

The repository now also carries the Gradle wrapper, so `./gradlew` works without a
local Gradle install.
