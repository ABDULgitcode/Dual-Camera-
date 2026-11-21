# DualFrame (Dual-Camera)

Android Phase 0 lab prototype for **Approach A**: one tall/wide sensor stream, dual crop into portrait (9:16) and landscape (16:9) outputs.

Working name from the DualFrame PRD. This repo currently holds the **Week 1 scaffold** only.

## Week 1 status

| Piece | Status |
| --- | --- |
| Kotlin Android app module (`:app`) | Done |
| Camera capability probe (Camera2 sizes / FPS / concurrent IDs) | Done |
| Dual preview stub with 16:9 + 9:16 crop overlays | Done |
| Capture mode selector UI (Portrait / Landscape / Dual) | Done |
| Dual hardware encode → two files | **Week 2** |

Record is intentionally disabled in the viewfinder until Week 2.

## Requirements

- **Android Studio** Ladybug / Koala or newer (AGP 8.7.x)
- **JDK 17**
- **Android SDK** with:
  - `compileSdk` / `targetSdk` **35**
  - `minSdk` **29** (Android 10+)
  - Platform tools + a device or emulator with a camera
- Gradle Wrapper (`./gradlew`) — included in this repo

This cloud agent environment did **not** have an Android SDK installed, so APKs were **not** built or claimed green here. Build on a machine with the SDK, or CI with Android build images.

## Build and run

```bash
# From repo root
./gradlew :app:assembleDebug

# Install on a connected device / emulator
./gradlew :app:installDebug
```

Or open the project in Android Studio → **Run** `app`.

On first launch:

1. Grant **Camera** permission.
2. **Capability probe** lists cameras, hardware level, AE FPS ranges, output sizes (MediaRecorder / YUV / PRIVATE / JPEG / high-speed), and concurrent-camera notes when API 30+ exposes them.
3. Tap **Open dual preview stub** for the CameraX viewfinder with crop overlays and the mode selector.

## Project layout

```
app/src/main/java/com/dualframe/prototype/
  MainActivity.kt                 # Permission gate + screen routing
  camera/CameraCapabilityProbe.kt # Camera2 probe
  model/                          # CaptureMode + capability models
  ui/
    CapabilityProbeScreen.kt
    DualPreviewScreen.kt
    DualCropOverlay.kt            # Composition-only frames
    CaptureModeSelector.kt
    theme/Theme.kt
```

## What Week 2 will add

- Select a tall usable readout (prefer 4:3 / 1:1 over 16:9-primary when available)
- Two crop/scale paths from one stream → portrait + landscape
- Dual **1080p** hardware encode (HEVC preferred, H.264 fallback) + shared audio → two muxed files
- Start/stop wiring from the viewfinder; basic on-device stats (drops / resolution)
- First device matrix entries for Tier 1 and one Tier 2 phone

Out of Week 1–2 scope: polished gallery, independent framing, Approach B dual-lens, iOS.

## License / notes

Lab prototype for Phase 0 feasibility. Not a product UI.
