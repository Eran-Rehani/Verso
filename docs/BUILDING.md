# Building Verso

Use JDK 21, Android command-line tools and the checked-in Gradle 9.3.1 wrapper.
The wrapper verifies the official distribution SHA-256. Java bytecode remains
compatible with Android API 26; the build JVM is a separate requirement.

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager "platforms;android-36.1" "build-tools;36.0.0" "platform-tools"
```

Accept the Android SDK licenses interactively with `sdkmanager --licenses`.
`ANDROID_HOME` or an ignored `local.properties` containing `sdk.dir=...` must
point to the installed SDK. A missing SDK/platform is a setup error; install the
exact component above instead of lowering compileSdk.

The preserved debug signing configuration uses `debug.keystore` at the project
root. If you have the original signing key, place it there. Otherwise generate
a development-only key (only when that file is absent):

```sh
test -f debug.keystore || keytool -genkeypair -keystore debug.keystore \
  -storepass android -keypass android -alias androiddebugkey \
  -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 \
  -validity 10000
```

A newly generated key cannot update an APK signed with a different certificate.
Compare certificates with Android build-tools `apksigner verify --print-certs`.
Installed-APK compatibility has not been verified. Never commit signing keys.
Release signing retains the existing `KEYSTORE_PATH`, `STORE_PASSWORD`, and
`KEY_PASSWORD` environment-variable interface and `upload` alias. Release
signing credentials are unnecessary for the debug checks.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

The second command needs an authorized API 26+ device/emulator visible in
`adb devices`. No connected device means instrumentation is unavailable, not a
pass. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. CI uploads it
as a short-lived Actions artifact. Dependency downloads happen during building;
reading must remain offline. No Firebase credentials are required.

If `/tmp` is mounted `noexec`, native build tools cannot run or load libraries
from it. Keep Gradle's cache on an executable filesystem (the default
`~/.gradle` normally works), and set a writable executable Java temporary
directory before building:

```sh
mkdir -p .gradle/tmp
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PWD/.gradle/tmp"
```

Do not set `GRADLE_USER_HOME` to a `noexec` filesystem: AAPT2 runs from that
cache. `failed to map segment from shared object` from SQLite during Room
processing and `AAPT2 ... Daemon startup failed` can indicate this setup issue.
