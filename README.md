# Zipline Upload

Minimal Android image uploader for [Zipline](https://zipline.diced.sh) servers. Pick an image or share from any app.

## Stack

Kotlin, Jetpack Compose, Material 3, OkHttp, Coil, GitHub Actions CI/CD.

## Building

### Prerequisites
- JDK 17+
- Android SDK (compileSdk 34)

### From Android Studio
Open the project in Android Studio, sync Gradle, and click Run.

### From command line
```bash
# Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### From GitHub Actions
**Debug** — Push to `main` or open a PR. Download from Actions > Artifacts.

**Release** — Actions > Build & Release APK > Run workflow > enter version tag (e.g. `v1.0.0`).

## License

MIT
