# Contributing to Verso

## Bug Reports
Report issues via [GitHub Issues](https://github.com/Eran-Rehani/Verso/issues). For reading or import problems, provide:

- Exact source document format.

- Device model and Android version.

- Steps to reproduce.

- Minimal reproduction documents that are shareable. Do not attach private books, sensitive documents, or secrets.

## Development Guidelines
Verso is an offline reader. All contributions must adhere to these constraints:

- Maintain full offline functionality.

- No network requests or WebView dependencies.

- Maintain support for Android API 26+.

- Ensure backward compatibility for existing user data.

- Preserve existing signing identity.

## Technical Stack

- Language: Kotlin / Jetpack Compose

- Database: Room

- JDK: 21

- Gradle: 9.3.1

- SDK: 36.1

## Build and Verification
Refer to [the build guide](docs/BUILDING.md) for setup and signing instructions.

Run these checks before submitting a pull request:
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

If you have a device or emulator available, run:
```bash
./gradlew :app:connectedDebugAndroidTest
```
Note: If you lack a device for connected tests, state this clearly in your PR; do not mark them as passed.

## Pull Request Process

- Submit small, focused pull requests with a clear description of the reading problem and the resulting behavior.

- Include actual check results and any unavailable device scenarios. For UI changes, include screenshots captured from the app.

- Keep signing keys, private documents, credentials, and generated APK binaries out of Git.

- Describe known limitations and any migration impact.
