# OpenLotus

A small Android app for ELK-BLEDOM style BLE LED strips. I built it because the stock
app is closed source and does not let you cycle a set of colors the way I wanted.

## What it does

- scan and connect to ELK, ELK~, CLK-, XSL- and "LED LIGHT STRIP" devices
- power on and off
- brightness and effect speed
- RGB sliders with a live preview
- the 29 built in lighting modes
- color sequence player: pick a few presets, set the hold time, and the app
  alternates them for you, showing which color is up
- remembers your settings and sequence between launches
- clock sync for the strip's timer feature
- a log tab that shows what the app sent, so you can troubleshoot without a cable

No internet permission. The app talks to the strip and nothing else.

## Status

Early, but the main frames are now checked on real hardware. On an ELK-BLEDDM D7
the stock dialect worked for power, color, brightness, modes and speed, and the
legacy dialect worked too, so both are in. Plaintext frames were enough, no
encryption needed. Details in docs/PROTOCOL.md.

Not wired into the UI yet: schedules, mic/music mode, laser controls and channel
order (pin sequence). These have frames but no confirmed hardware test behind
them.

## Build

CI builds on every push, and the debug APK is the artifact from the workflow run.
You can also run the build workflow by hand and pick debug or release. The release
APK is unsigned, so sign it yourself before installing.

Locally you need JDK 17 and the Android SDK:

```
./gradlew :app:assembleDebug
```

The APK lands in app/build/outputs/apk/debug/.

## Notes

The strips have no firmware sequence mode as far as I could find, so the color
sequence is driven from the phone. Keep the app open while it runs.

## Credits

Protocol notes came from these projects:

- TheSylex/ELK-BLEDOM-bluetooth-led-strip-controller
- lorgan3/lotus-lantern-client
- SmartyDolla/Lotus-Lantern-GUI

## License

MIT
