# Third-Party Notices and Attributions

Dualis for Android (`com.z3itt.dualis`) is Copyright (C) 2026 z3itt and licensed
under the [GNU General Public License v3.0 or later](LICENSE).

This file lists major third-party components used by Dualis and their licenses.
Dependency versions match `app/build.gradle.kts` at release time.

## Runtime libraries

| Component | Version | License | Notes |
|-----------|---------|---------|-------|
| [AndroidX Core Splashscreen](https://developer.android.com/jetpack/androidx) | 1.0.1 | Apache-2.0 | Launch splash |
| [AndroidX Activity Compose](https://developer.android.com/jetpack/androidx) | 1.9.3 | Apache-2.0 | Android Jetpack |
| [AndroidX Lifecycle](https://developer.android.com/jetpack/androidx) | 2.8.7 | Apache-2.0 | ViewModel, service, compose |
| [Jetpack Compose BOM](https://developer.android.com/jetpack/compose) | 2024.12.01 | Apache-2.0 | UI toolkit |
| [Material 3 for Compose](https://developer.android.com/jetpack/androidx/releases/compose-material3) | via BOM | Apache-2.0 | UI components |
| [Material Icons Extended](https://developer.android.com/jetpack/androidx/releases/compose-material) | via BOM | Apache-2.0 | Icons |
| [AndroidX Room](https://developer.android.com/jetpack/androidx) | 2.6.1 | Apache-2.0 | Local library database |
| [AndroidX WorkManager](https://developer.android.com/jetpack/androidx) | 2.10.0 | Apache-2.0 | Background job helpers |
| [AndroidX DataStore Preferences](https://developer.android.com/jetpack/androidx) | 1.1.1 | Apache-2.0 | Local preferences |
| [AndroidX DocumentFile](https://developer.android.com/jetpack/androidx) | 1.0.1 | Apache-2.0 | SAF file access |
| [AndroidX Media3 ExoPlayer](https://developer.android.com/media/media3) | 1.5.1 | Apache-2.0 | Dual-stem playback |
| [AndroidX Media3 Session](https://developer.android.com/media/media3) | 1.5.1 | Apache-2.0 | System mini player |
| [ONNX Runtime Android](https://github.com/microsoft/onnxruntime) | 1.22.0 | MIT | On-device inference |
| [OkHttp](https://square.github.io/okhttp/) | 4.12.0 | Apache-2.0 | HTTP downloads |
| [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.9.0 | Apache-2.0 | Async flows |
| [Jsoup](https://jsoup.org/) | 1.18.3 | MIT | Spotify page scrape |
| [JTransforms](https://github.com/wendykierp/JTransforms) | 3.1 | BSD-2-Clause / GPL-2.0-with-classpath-exception | STFT / FFT |
| [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) | v0.26.5 | GPL-3.0-or-later | YouTube audio extract |

## Build-time tools

| Component | Version | License |
|-----------|---------|---------|
| [Kotlin](https://kotlinlang.org/) | 2.0.21 | Apache-2.0 |
| [Android Gradle Plugin](https://developer.android.com/studio/releases/gradle-plugin) | 8.7.3 | Apache-2.0 |
| [Gradle](https://gradle.org/) | 8.11.1 | Apache-2.0 |
| [KSP](https://github.com/google/ksp) | 2.0.21-1.0.28 | Apache-2.0 |
| [JUnit](https://junit.org/) | 4.13.2 | EPL-1.0 |

## Models (downloaded at runtime)

ONNX weights are **not** bundled in the APK. The first job fetches the selected
model into app storage. Hosts and filenames live in `ModelCatalog`.

| Model | Typical source | Notes |
|-------|----------------|-------|
| Kim Vocal 2 | UVR / community ONNX mirrors | Default MDX vocal model |
| Karaoke 2 | UVR / community ONNX mirrors | Faster MDX; primary stem is instrumental |
| UVR MDX Voc FT | UVR / community ONNX mirrors | Alternate MDX timbre |
| BS-Roformer | UVR / community ONNX mirrors | Waveform ONNX; shape is read from the file |

Those model files remain under their upstream licenses. Dualis does not
redistribute them unless you add them yourself.

## Fonts and visual assets

- UI typography uses the Android system sans-serif stack (Roboto on most devices).
  No bundled font files ship with Dualis.
- Launcher icon, splash artwork, brand marks, and notification icons are original
  Dualis assets created for this project unless noted otherwise in the repository.

## Transitive permissions

WorkManager and Media3 may merge standard Android permissions such as
`WAKE_LOCK` and `ACCESS_NETWORK_STATE`. Dualis also declares `INTERNET` for
link ingest and model download. Stems are not uploaded to a Dualis server.

## License texts

- GNU GPL v3: [LICENSE](LICENSE) and [COPYING](COPYING)
- Apache-2.0: https://www.apache.org/licenses/LICENSE-2.0
- MIT: https://opensource.org/licenses/MIT
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor/blob/dev/LICENSE

For questions about licensing or attribution, contact info@z3itt.com.
