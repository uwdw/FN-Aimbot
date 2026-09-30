# FortniteAimbot

Android aimbot — YOLOv8n inference + BT HID gamepad output

## Setup

1. Get a YOLOv8n Fortnite player detection model exported to TFLite:
   - search roboflow universe: "fortnite player detection"
   - export as TFLite FP16 or INT8
   - rename to `fn_model.tflite`
   - drop into: `app/src/main/assets/fn_model.tflite`

2. Open project in Android Studio

3. Build > Generate Signed APK (or just Run for debug)

4. Sideload APK to Galaxy A32
   - Settings > Apps > Special Access > Install Unknown Apps

5. Pair A32 to Xbox via Bluetooth
   - Xbox Settings > Devices & Connections > Bluetooth
   - Phone pairs as "Que Gamepad"

6. Launch app on phone

7. Start Discord screenshare of FN stream on phone

8. Hit START — grant screen capture permission

## Tuning (AimController.kt)

| param | default | effect |
|-------|---------|--------|
| sensitivity | 0.35 | higher = faster tracking |
| smoothing | 0.6 | lower = snappier, higher = smoother |
| headOffsetRatio | 0.15 | aim point within bbox, 0 = top, 0.5 = center |
| deadZone | 0.02 | min delta before stick moves |
| confidenceThreshold | 0.45 | lower = more detections, more false positives |

## Notes

- A32 runs inference at ~8-15fps on Mali-G52 via GPU delegate
- Lower discord stream quality to 480p for better inference speed
- Path A setup: phone handles right stick (aim) only, real controller handles everything else
- Xbox will show two controllers connected — this is expected
