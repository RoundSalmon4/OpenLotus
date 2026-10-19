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
