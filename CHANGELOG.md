# Changelog

Dates are based on UTC.

## v0.1.0 (2026-10-19)

- first cut
- gradle project, protocol module with frame tests, CI build
- compose app: device scan, power, brightness, speed, color, modes, clock sync
- color sequence player
- debug logging through every layer, plus a log tab in the app that keeps the
  verbose lines too, so a dropped write or a bad saved step shows up there
- ci can also build a release apk on demand (unsigned, needs your own keystore)
- unit tests for the sequence player, run in ci
- dropped a little dead code and marked the parts that wait on hardware testing
- settings and the color sequence persist between launches, and the sequence shows
  which color is playing
- protocol notes record a hardware check on an ELK-BLEDDM D7: both dialects work,
  no encryption needed, no write queue needed
- each color in a sequence can hold for its own time
- a single power toggle that remembers its state
- a launcher icon, and a banner to fix a denied bluetooth permission
- remembers the last strip and reconnects to it, including after a dropped link
- the sequence takes any color from the RGB sliders, plus off steps, so an
  orange and black alternation is just two steps
- fixed the layout drawing under the status bar, which hid the tabs behind the
  camera cutout on a pixel 7 (edge to edge insets)
- one scan line per device per scan, so a busy room does not flood the log tab
- a color wheel on the swatch, so you can pick a color by eye instead of
  knowing its RGB values
- a timer tab that sets the strip's own turn on and turn off times, with a
  weekday repeat, so the lights can switch while the phone is away
- the clock sync now sends the weekday the way the strip expects (Sunday is 0),
  which the timer relies on
- keep the link alive with a periodic read, ask for a faster connection
  interval, and retry the first reconnect sooner
- the color wheel now drives the strip while you drag it, not only on confirm,
  and picking a plain color or a mode stops the sequence that was running
- the hold time slider sets every step when nothing is selected, so changing it
  during a run actually changes the pace instead of only the next color added
- tap a step in the sequence and pick its color on the wheel, and the steps row
  scrolls instead of squashing when there are a lot of them
- presets are editable now: add the current color, replace or remove any swatch,
  and they persist
- a calibrate tab runs a short white then orange check and stores per channel
  gains and a gamma, applied to every color sent and every preview, because this
  strip's green runs hot and needed about R255 G34 for what should read orange
- gatt writes are queued and sent one at a time with a retry on a busy reject,
  which is what the status 201 lines in the log were
- the calibrate tab is a real wizard now: three guided steps (balance the white
  with cast buttons, match the dim level, then walk nine reference colors), each
  cue is a tap instead of a number to guess, and the manual sliders moved behind
  a toggle with a plain explanation of what every one does
- the tabs scroll, so the calibrate label stopped wrapping on a narrow screen
- one loader now feeds the saved calibration into the whole app, so the
  correction reaches the controls and the sequence from one place
- subscribed to the fff4 notify side and log the keepalive read bytes, so the
  next capture can say whether the strip reports any state worth reading back
