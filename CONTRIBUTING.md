# Contributing to Dualis for Android

Dualis is free software under [GPL-3.0-or-later](LICENSE). Contributions must
be compatible with that license. Open an issue or pull request on GitHub.

## Development setup

### Requirements

- Android Studio Ladybug or newer (or compatible IDE)
- **JDK 17** for Gradle (Gradle 8.11.1 does not run on JDK 25)
- Android SDK with API 35 platform tools
- Device or emulator on API 26+

### Clone and run (debug)

```bash
git clone https://github.com/z3itt/Dualis-for-Android.git
cd Dualis-for-Android
```

Open the project in Android Studio, set **Gradle JDK** to 17, sync, then Run.

Or from the terminal:

```bash
export JAVA_HOME="$HOME/.jdks/temurin-17.0.20.1"   # or your JDK 17 path
./gradlew :app:installDebug
```

### Tests

```bash
./gradlew testDebugUnitTest
```

## Release APK (signed)

Signing reads environment variables only. Never commit the keystore or passwords.

```bash
export DUALIS_STORE_FILE="$HOME/keys/dualis-release.jks"
export DUALIS_STORE_PASSWORD="your-store-password"
export DUALIS_KEY_ALIAS="dualis"
export DUALIS_KEY_PASSWORD="your-key-password"

./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Without those variables, `assembleRelease` still builds an unsigned APK.

Attach that APK to a GitHub Release tagged with the matching version (for example
`v1.0.0`).

## Distribution

| Channel | Notes |
|---------|-------|
| GitHub Releases | Signed APK from the maintainer keystore |
| F-Droid | Builds from source and signs with the F-Droid key |
| Google Play | Not published |

F-Droid does not use your keystore. Listing text lives under
[`metadata/`](metadata/).

## Pull requests

- Keep the change focused. Do not mix refactors with bug fixes.
- Run unit tests before opening a PR.
- Do not commit `local.properties`, keystores, `.onnx` models, or APKs.
- Spotify ingest must search YouTube as `title artist`, not the Spotify URL.
- Retry must use the stored `ytdlpQuery`, never the original Spotify URL.
- Keep one job on the queue at a time. Do not add a parallel download/infer pool
  without an explicit design change.
- Playback uses two ExoPlayer instances plus a Media3 session. Do not drop the
  mediaPlayback foreground service; lock-screen audio depends on it.

## Contact

z3itt · info@z3itt.com · https://z3itt.com
