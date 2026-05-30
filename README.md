# Zipline Uploader

Minimal Android image uploader for [Zipline](https://zipline.diced.sh) servers. Pick an image or share from any app.

## Stack

Kotlin, Jetpack Compose, Material 3, OkHttp, Coil, GitHub Actions CI/CD.

## Building

### Prerequisites
- JDK 17+
- Android SDK (compileSdk 34)

### Build
```bash
# Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

## License

[MIT](https://github.com/vloeibaarglas/Zipline-Uploader/blob/main/LICENSE)
