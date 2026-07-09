# FreeTuner

FreeTuner is an Android-first instrument tuning app scaffold built with Kotlin and Jetpack Compose.

## Current implementation

- Project structure for a native Android app
- Tuner screen scaffold with note, cents, calibration, and instrument presets
- Metronome screen scaffold with BPM and beat controls
- Settings screen scaffold for future app options
- Pitch math utility for note mapping and cents calculation

## Next build steps

1. Add live microphone capture with AudioRecord
2. Connect a real pitch detection engine
3. Persist settings and recent instruments
4. Add metronome audio playback
5. Test and package for Play Store release

## Local requirements

- JDK 17
- Android SDK
- Gradle or Android Studio / compatible tooling

## Release signing

This project now expects a release keystore to be configured via Gradle properties.

Add the following properties in `gradle.properties` or your local environment:

- `RELEASE_STORE_FILE` = path to the release keystore file
- `RELEASE_STORE_PASSWORD` = keystore password
- `RELEASE_KEY_ALIAS` = key alias inside the keystore
- `RELEASE_KEY_PASSWORD` = key password

Then build a Play Store artifact with:

```bash
./gradlew :app:bundleRelease
```
