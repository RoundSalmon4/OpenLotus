# Changelog

## 0.1.0

- first cut
- gradle project, protocol module with frame tests, CI build
- compose app: device scan, power, brightness, speed, color, modes, clock sync
- color sequence player
- debug logging through every layer, plus a log tab in the app
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
