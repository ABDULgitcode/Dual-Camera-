# DualFrame (Dual-Camera)

Android Phase 0 lab prototype for **Approach A**: one sensor stream → dual crop into portrait (9:16) and landscape (16:9) → paired files.

## Status

| Piece | Status |
| --- | --- |
| Kotlin Android app (`:app`, minSdk 29) | Done |
| Camera capability probe (Camera2 + dual MediaCodec gate) | Done |
| Dual preview with 16:9 + 9:16 overlays | Done |
| Capture mode selector (Portrait / Landscape / Dual) | Done |
| Dual / single **1080p** encode + shared AAC audio | Done (Week 2) |
| GPU crop pipeline / thermal matrix | Later lab weeks |

## Requirements

- **Android Studio** (AGP 8.7.x) + **JDK 17**
- **Android SDK** 35 (`compileSdk` / `targetSdk`), `minSdk` 29
- Physical phone strongly preferred (emulator camera + dual encode is not a valid Phase 0 proof)

This cloud agent environment has **no Android SDK**. `./gradlew :app:assembleDebug` fails with `SDK location not found`. That is expected — do not treat missing-SDK failures as a green build.

## Build and run

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Or open in Android Studio → Run `app`.

### Permissions

Grant **Camera** and **Microphone**. Audio is encoded once (AAC 48 kHz) and muxed identically into every output file.

## How to test dual 1080p on a phone

1. Install the debug APK on a Tier 1 or Tier 2 device.
2. Launch **DualFrame Lab** → read the **Dual 1080p encode** card on the probe screen.
   - If Dual is **NOT SUPPORTED**, the reason is listed; Dual mode is disabled in the viewfinder. Portrait/Landscape may still work.
3. Open **capture (preview + record)**.
4. Select **Dual**, frame the subject inside both overlays, tap **Record**, wait ≥10–30 s, tap **Stop**.
5. Files land under app-specific movies, e.g.  
   `Android/data/com.dualframe.prototype/files/Movies/DualFrame/`  
   Named `DF_<yyyyMMdd_HHmmss>_L.mp4` and `DF_<yyyyMMdd_HHmmss>_P.mp4`.
6. Pull with adb:

```bash
adb shell ls -la /sdcard/Android/data/com.dualframe.prototype/files/Movies/DualFrame/
adb pull /sdcard/Android/data/com.dualframe.prototype/files/Movies/DualFrame/ ./captures/
adb logcat -s DualFrameLab:I DualFrameLab:W
```

7. Confirm resolutions (~1920×1080 and ~1080×1920), that both files have audio, and note drop counters from on-screen REC HUD / logcat `STOP` lines.

Single modes write only `_L` or `_P`.

### Lab logging

Filter tag **`DualFrameLab`**: `START`, `DROP`, `STOP`, `GATE` lines include pair id, timestamps, mime, paths, submitted/dropped frames, audio packet counts.

## Architecture (Week 2)

```
CameraX Preview + ImageAnalysis (prefer 4:3 ~1920×1440)
        │
        ├─ centered 16:9 crop/scale → MediaCodec → DF_*_L.mp4
        ├─ centered 9:16 crop/scale → MediaCodec → DF_*_P.mp4
        └─ AudioRecord → one AAC encoder → same packets into both muxers
```

Crop/scale is a **CPU NV12** lab path (nearest-neighbor). Expect higher drop rates than a future GPU/`Surface` pipeline — drops are logged on purpose for Phase 0 measurements.

## Project layout

```
app/src/main/java/com/dualframe/prototype/
  MainActivity.kt
  camera/
    CameraCapabilityProbe.kt
    DualEncodeCapability.kt      # empirical dual MediaCodec gate
    ApproachARecorder.kt         # session + ImageAnalysis → encode
    VideoTrackEncoder.kt
    SharedAacEncoder.kt
    YuvCropScaler.kt
    CropGeometry.kt
    RecordingLabLog.kt
  model/
  ui/
```

## What cannot be verified without a physical device

- Actual dual concurrent encode stability / heat / battery
- Real drop rates and A/V sync skew between `_L` and `_P`
- Color/NV12 layout quirks per OEM encoder
- Concurrent-camera ID behavior beyond the probe dump
- APK assemble in this cloud VM (no SDK)

## Week 3+ (not this PR)

- 10-minute thermal / battery / drop runs; sync audits
- Dual 4K attempts on capable devices
- GPU crop path if CPU drops are unacceptable

## License / notes

Lab prototype for Phase 0 feasibility. Not a product UI.
