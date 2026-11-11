# Verso Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build "Verso", a native Android reflow reader (Kotlin + Jetpack Compose) that turns PDF, EPUB, Markdown, HTML, plain text and shared LLM output into one fast, scrollable, fully adjustable reading column, built and delivered to the phone entirely through GitHub Actions as a signed APK.

**Architecture:** Two Gradle modules. `:core` is pure Kotlin/JVM (document model, every extractor, PDF layout heuristics, import pipeline, JSON persistence), so every non-UI rule is unit-tested on a plain JVM in CI. `:app` is the Android shell (Compose reader, settings panel, home/recents, share-sheet intake, PdfBox-Android adapter). Every format converts to one `Document` of flat `Block`s, and one virtualized `LazyColumn` renders it.

**Tech Stack:** Kotlin 2.2.21, AGP 8.13.2, Gradle 8.14.3, JDK 17, Jetpack Compose (BOM 2025.12.00) + Material 3, CommonMark-java, jsoup, kotlinx-serialization-json, kotlinx-coroutines, PdfBox-Android 2.0.27.0, JUnit 4, Robolectric (Compose UI tests), GitHub Actions.

**Spec:** the "Product Spec" section of this document (below). Executors read both.

## Global Constraints

Every task's requirements implicitly include everything in this section. Controllers paste it into every implementer and reviewer prompt.

- **Name and ids:** working name `Verso`. `:app` `applicationId` and `namespace` = `app.verso.reader`. `:core` root package = `app.verso.core`. Display name string `app_name` = `Verso`. Renaming the app touches only those three places.
- **Toolchain (pinned, do not upgrade):** Gradle wrapper 8.14.3; Android Gradle Plugin 8.13.2 with the `org.jetbrains.kotlin.android` plugin; Kotlin 2.2.21 with `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization` at the same version; JDK 17. **Do NOT adopt AGP 9.x, "built-in Kotlin", the new AGP DSL, or compileSdk 37.** Compose 1.12 (BOM 2026.08.00 and newer) requires them; this plan deliberately stays on Compose BOM 2025.12.00.
- **Android levels:** `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`. Java/Kotlin target 17.
- **Dependency allow-list:** only the coordinates declared in `gradle/libs.versions.toml` by Task 1. Anything else needs controller approval and an `AGENTS.md` note.
- **Forbidden:** any `uses-permission` (especially `INTERNET`), WebView, Room/SQLite, Hilt/Dagger/Koin, Retrofit/OkHttp, Coil/Glide, the Navigation library, `material-icons-extended` (use `material-icons-core` only), analytics or crash-reporting SDKs, reflection-based JSON.
- **Reading rules:** vertical scroll only; no zoom and no horizontal scroll anywhere (code, math and tables soft-wrap). One `LazyColumn` item per `Block`, with `key = index` and `contentType = block::class`. Text blocks use `TextDirection.Content` so RTL paragraphs (Hebrew, Arabic) align correctly.
- **Limits:** `MAX_SOURCE_BYTES = 64 MiB (67_108_864)`; `MAX_BLOCK_CHARS = 2500`; `RECENTS_MAX = 20`; release APK size gate `12 MiB (12_582_912 bytes)`.
- **Settings ranges** (field / default / min / max / step): `fontSizeSp` 20 / 12 / 48 / 1; `lineHeight` 1.5 / 1.0 / 2.4 / 0.05; `paragraphSpacingEm` 0.8 / 0.0 / 2.0 / 0.1; `marginDp` 20 / 0 / 48 / 4; `theme` = System; `font` = Serif; `align` = Start; `keepScreenOn` = false.
- **Palettes (exact hex):**

  | Theme | background | text | muted | link | codeBackground | rule |
  |---|---|---|---|---|---|---|
  | Light | #FAFAF7 | #1A1A1A | #6B6B6B | #1A5FB4 | #EFEFEA | #D9D9D4 |
  | Sepia | #F4ECD8 | #5B4636 | #6F5E4D | #8A4B08 | #EADFC4 | #D8CBAA |
  | Dark | #1B1B1D | #D6D6D6 | #9A9A9A | #8AB4F8 | #2A2B2E | #3A3B3F |
  | Black | #000000 | #C8C8C8 | #8A8A8A | #7AA2F7 | #141414 | #2A2A2A |

  `System` resolves to Dark when the device is in dark mode, otherwise Light.
- **Typography scale:** headings H1/H2/H3/H4-H6 = 1.6x / 1.4x / 1.2x / 1.0x body size, all bold. Code and math text = 0.92x body size, monospace. Fonts: `Sans` = `FontFamily.SansSerif`, `Serif` = `FontFamily.Serif`, `Mono` = `FontFamily.Monospace`. No bundled fonts.
- **Exact UI strings** (English only): Home title `Verso`; buttons `Open file`, `Paste`; empty state `Share text, a PDF or an EPUB to Verso, or tap Open file.`; importing `Preparing…`; error title `Can't open this`; error button `OK`; settings labels `Theme`, `Font`, `Size`, `Line spacing`, `Paragraph spacing`, `Margins`, `Align`, `Keep screen on`, `Reset`; align chips `Left`, `Justified`; theme chips `System`, `Light`, `Sepia`, `Dark`, `Black`; font chips `Sans`, `Serif`, `Mono`; content descriptions `Back`, `Reading settings`, `Remove from recents`; image placeholder `[Image: <alt>]` (`[Image]` when alt is blank); clipboard empty `Clipboard is empty.`; empty share `Nothing to read in the shared content.`; unreadable share `Couldn't open the shared file.`; missing recent `That document is no longer available.`
- **Tests:** JUnit 4 (`org.junit.Test`) with `kotlin.test` assertions. Robolectric only in `:app`. Test names are backticked sentences. The tests in each task are the specification: if one cannot pass as written, ask the controller; do not weaken it.
- **Verification ("Run" steps):** run locally when the Verification Mode in `AGENTS.md` allows; otherwise commit locally, push, and read the GitHub Actions result (`gh run watch --exit-status`, or the Actions page). CI pass/fail is then the "Expected" result. In `CI-ONLY` mode the "run to see it fail" step is skipped and a push happens only after the implementation is written.
- **Git:** all work on `main`, one commit per task (conventional message given in each task), never force-push. The repository must stay **private** (it contains the signing keystore).
- **Code style:** official Kotlin style; no TODO comments; no commented-out code; pure functions where possible; extractors never throw for merely odd input (only the `ImportError` cases named in the tasks).

## Review Focus

The five input classes or failure modes the spec implies but a straightforward implementation would not test, most likely first. Each has a test in the task that owns the code (marked "Review Focus #n" there).

1. **One enormous paragraph** (minified text, a PDF with no line breaks, a 1 MB paste on one line) freezes `Text` layout. Expected: it is split into blocks of at most 2500 characters and imports in seconds. (Tasks 3, 9)
2. **Unusable share intents** (empty `EXTRA_TEXT`, an `EXTRA_STREAM` URI that throws `SecurityException` or `FileNotFoundException`, a file over 64 MiB). Expected: a readable error screen, never a crash or a blank reader. (Task 14)
3. **Corrupt or truncated persisted JSON** (`settings.json`, `recents.json`, a document file cut off by a kill during write). Expected: defaults / empty list / entry silently dropped; the app still starts. (Task 10)
4. **Losing the reading position:** font size changed while reading, app killed right after a scroll, the same file imported twice. Expected: the position is stored as block index plus pixel offset and survives all three; a duplicate import opens the existing entry. (Tasks 10, 13)
5. **Scanned or password-protected PDF.** Expected: a clear error message, not an empty document and not a crash. (Tasks 8, 15)

---

## Product Spec

**Purpose.** A personal, sideloaded Android app (single user, no accounts) that makes any text comfortable to read on a phone: large text that never needs zooming or sideways panning, one scrollable column, full control over typography, instant start, tiny APK.

**Inputs.** Everything is converted to one internal `Document`:

| Source | How it arrives | Notes |
|---|---|---|
| Plain text | Share sheet text, `.txt`, Paste button | Hard-wrapped prose is un-wrapped |
| Markdown / LLM output | Share sheet text from any chat app, `.md`, Paste | CommonMark + GFM tables + strikethrough; `$...$`, `$$...$$`, `\(...\)`, `\[...\]` math kept as monospace source |
| HTML | `.html` files | jsoup |
| EPUB | Open with / Open file / Share | DRM-protected books are refused with a message |
| PDF | Open with / Open file / Share | Text layer only (PdfBox-Android + column/header/hyphen heuristics); scanned PDFs refused with a message |

**Screens.**
1. **Home:** title, `Open file` and `Paste` buttons, list of up to 20 recent documents (title, format, percent read, remove button). Empty state text from Global Constraints.
2. **Importing:** a spinner with `Preparing…`; Back cancels.
3. **Reader:** full-screen `LazyColumn`. A single tap toggles the chrome (top bar with Back, title, settings button; system bars hide when chrome is hidden). A 2dp progress bar sits at the top edge. The settings panel is a non-modal bottom panel (max 45% of screen height) so changes are visible live behind it.
4. **Error:** title `Can't open this`, the error message, an `OK` button back to Home.

**Reader behavior.** Settings (theme, font, size, line spacing, paragraph spacing, margins, alignment, keep-screen-on) apply instantly and persist. Justified alignment enables automatic hyphenation. Reading position (block index + pixel offset) is saved per document, restored on reopen, and survives font changes and process death. Progress = `anchorBlock / (blockCount - 1)`. Links are tappable (they open the external browser; the app itself needs no network permission).

**Block rendering rules.** Paragraph = body style. Heading = scale above. ListItem = marker column of 1.5em, indent `depth x 1.25em`. Quote = 3dp bar in `muted`, indent `12dp x (depth + 1)`. CodeBlock and MathBlock = monospace on `codeBackground`, 8dp padding, soft-wrapped. TableBlock = stacked records: for each row one text with a bold `Header: ` prefix per cell, one cell per line, thin `rule` line between rows. ImageBlock = muted italic placeholder text. Divider = 1px `rule` line. Gaps between blocks (in em of body size): first block 0; ListItem after ListItem 0.25; any block after a Heading 0.4; a Heading after a non-heading `paragraphSpacingEm + 0.6`; otherwise `paragraphSpacingEm`. Content padding: top 24dp, bottom 96dp, horizontal = `marginDp`.

**Non-goals for v1.** Annotations, cloud sync, a real library or collections, text-to-speech, OCR, native LaTeX rendering (math is shown as source), displaying images (EPUB and Markdown images show a placeholder line), text selection, pinch-zoom, landscape/tablet tuning, Play Store publishing, launcher-icon artwork.

**Known limitations (accepted).** PDF equations, tables and figures come out as plain extracted text; hyphen-ending lines are always joined; unusual PDF layouts may reorder columns; fixed-layout EPUBs are read as their text only.

---

## Controller Notes (read before dispatching)

- **Sequential only.** Tasks 1 to 15 depend on each other's interfaces; never run two implementers at once. Paste into each implementer prompt: the full task text, the Global Constraints, `AGENTS.md`, and the Product Spec section "Block rendering rules" for UI tasks. Do not make the implementer read this file.
- **Reviewers.** Spec reviewer checks the task text and its tests; code-quality reviewer additionally checks the Global Constraints (forbidden dependencies, no horizontal scrolling, performance rules).
- **Model choice.** Tasks 7 and 8 (PDF heuristics) deserve the most capable model; everything else can run on a mid-tier model.
- **Human checkpoints** (controller pauses and asks the human): after Task 1 (install the hello APK), after Task 14 (full app minus PDF), after Task 15 (PDF on real files). The human installs from the GitHub Release `latest` and reports back; failures become fix tasks inserted before continuing.
- **Failure policy.** If a task fails review twice, stop and ask the human. A Robolectric or R8 problem gets one honest fix attempt; the plan names the fallback where it applies.
- **Finish.** Run the final whole-branch review against the Spec Coverage table at the end of this document, then tag `v0.1.0` (the finishing-a-development-branch merge/PR options do not apply: all work is on `main`).
- **Before Task 1 (human, once):** create an empty **private** GitHub repository, authorize the cloud agent on it, and commit this file at `docs/superpowers/plans/2026-11-11-verso-reader.md`.

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradle/wrapper/*, gradlew, gradlew.bat
AGENTS.md, README.md, .github/workflows/build.yml
docs/superpowers/plans/2026-11-11-verso-reader.md
core/build.gradle.kts
core/src/main/kotlin/app/verso/core/
  model/    Document.kt (Span, SpanKind, InlineText, Block + subclasses, Document, plainText), ImportError.kt, DocumentCodec.kt
  text/     TextCleaner.kt, BlockSplitter.kt, PlainTextExtractor.kt
  html/     HtmlExtractor.kt
  markdown/ MathProtector.kt, MarkdownExtractor.kt
  epub/     EpubExtractor.kt
  pdf/      PdfModels.kt (TextRun, PdfPage, PdfContent, PdfPageSource, PdfLine), PdfLayout.kt, PdfAssembler.kt, PdfExtractor.kt
  importer/ DocumentSource.kt, FormatDetector.kt, TextDecoding.kt, Importer.kt (Importer, ImportResult, DocumentImporter)
  store/    JsonFile.kt, ReaderSettings.kt, SettingsStore.kt, LibraryStore.kt (RecentEntry, LibraryStore)
core/src/test/kotlin/app/verso/core/...   (mirrors main)
app/build.gradle.kts, app/proguard-rules.pro, app/verso.keystore
app/src/main/AndroidManifest.xml, res/values/strings.xml
app/src/main/kotlin/app/verso/reader/
  VersoApp.kt, MainActivity.kt, AppViewModel.kt (AppEnv, Screen, AppViewModel)
  ui/reader/   ReaderPalette.kt, ReaderTextStyles.kt, InlineRender.kt, Spacing.kt, BlockViews.kt, ReaderScreen.kt
  ui/settings/ SettingsPanel.kt
  ui/home/     HomeScreen.kt
  intake/      IntentParser.kt (IntakeResult), SourceReader.kt
  pdf/         PdfBoxPageSource.kt
app/src/test/kotlin/app/verso/reader/...  (mirrors main)
```

---

### Task 1: Project skeleton, signing, CI and hello APK

**Files:**
- Create: everything in the top two lines of File Structure, `core/build.gradle.kts`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/verso.keystore`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/kotlin/app/verso/reader/MainActivity.kt`, `core/src/main/kotlin/app/verso/core/Version.kt`
- Test: `core/src/test/kotlin/app/verso/core/VersionTest.kt`, `app/src/test/kotlin/app/verso/reader/ManifestGuardTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: Gradle modules `:core` (Kotlin JVM, plugins `kotlin("jvm")` + serialization) and `:app` (Android application); version catalog (below) used by every later task; `object Version { const val NAME = "Verso" }` in `app.verso.core`; `AGENTS.md`; CI workflow that tests, builds a signed release APK, size-gates it, and publishes it as GitHub prerelease tag `latest` (asset `verso.apk`).

- [ ] **Step 1: Probe the toolchain and record the Verification Mode**

Run: `java -version; which gradle; echo "ANDROID_HOME=$ANDROID_HOME"; curl -sI https://services.gradle.org | head -1; curl -sI https://dl.google.com/android/maven2/ | head -1; curl -sI https://repo.maven.apache.org/maven2/ | head -1`
Expected: you learn which mode applies. Write it into `AGENTS.md` under "Verification mode": `FULL` (JDK 17 + Android SDK + all hosts reachable: run `:core` and `:app` tasks locally), `JVM-ONLY` (JDK 17 + Gradle/Maven Central reachable but no Android SDK or no dl.google.com: run only `:core:test` locally; `:app` verified by CI), or `CI-ONLY` (nothing runs locally; everything verified by CI).

- [ ] **Step 2: Write the two smoke tests**

```kotlin
// core/src/test/kotlin/app/verso/core/VersionTest.kt
class VersionTest { @Test fun `name is Verso`() = assertEquals("Verso", Version.NAME) }

// app/src/test/kotlin/app/verso/reader/ManifestGuardTest.kt  (runs with the module dir as working dir)
class ManifestGuardTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    @Test fun `manifest declares no permissions`() = assertFalse(manifest.contains("uses-permission"))
    @Test fun `manifest does not reference webview or internet`() {
        assertFalse(manifest.contains("INTERNET")); assertFalse(manifest.contains("WebView"))
    }
}
```

- [ ] **Step 3: Create the Gradle skeleton**

Create these files exactly as specified (the implementer writes standard bodies; only the pinned decisions are listed):
- **Wrapper:** `gradle/wrapper/gradle-wrapper.properties` pointing at `https://services.gradle.org/distributions/gradle-8.14.3-bin.zip`. If `gradle` is not installed, fetch `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` from `https://raw.githubusercontent.com/gradle/gradle/v8.14.3/` (same relative paths) and `chmod +x gradlew`.
- **`settings.gradle.kts`:** `rootProject.name = "verso"`, `include(":core", ":app")`, repositories `google()` (content-filtered to `com.android.*`, `com.google.*`, `androidx.*`) and `mavenCentral()` in both `pluginManagement` and `dependencyResolutionManagement` (`FAIL_ON_PROJECT_REPOS`), plus `gradlePluginPortal()` in `pluginManagement`.
- **`gradle.properties`:** `org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8`, `org.gradle.parallel=true`, `org.gradle.caching=true`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official`.
- **Root `build.gradle.kts`:** declares plugins from the catalog with `apply false`.
- **`gradle/libs.versions.toml`:** exactly these entries. Versions marked `✔` were verified on 2026-11-11; the others are known-good conservative releases: if one does not resolve, use the newest stable that resolves **and** does not require compileSdk above 36 or AGP above 8.13, and note it in `AGENTS.md`.

```toml
[versions]
kotlin = "2.2.21"            # ✔
agp = "8.13.2"               # ✔
composeBom = "2025.12.00"    # ✔
pdfbox = "2.0.27.0"          # ✔
coroutines = "1.10.2"
serialization = "1.9.0"
commonmark = "0.24.0"
jsoup = "1.21.2"
activityCompose = "1.10.1"
lifecycle = "2.9.1"
coreKtx = "1.16.0"
junit = "4.13.2"
robolectric = "4.15.1"
androidxTest = "1.6.1"
androidxTestExt = "1.2.1"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
commonmark = { module = "org.commonmark:commonmark", version.ref = "commonmark" }
commonmark-ext-gfm-tables = { module = "org.commonmark:commonmark-ext-gfm-tables", version.ref = "commonmark" }
commonmark-ext-gfm-strikethrough = { module = "org.commonmark:commonmark-ext-gfm-strikethrough", version.ref = "commonmark" }
jsoup = { module = "org.jsoup:jsoup", version.ref = "jsoup" }
pdfbox-android = { module = "com.tom-roush:pdfbox-android", version.ref = "pdfbox" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-core = { module = "androidx.compose.material:material-icons-core" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTest" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- **`core/build.gradle.kts`:** plugins `kotlin-jvm`, `kotlin-serialization`; Kotlin `compilerOptions.jvmTarget = JVM_17`; `tasks.test { useJUnit() }`; `api` dependencies: coroutines-core, serialization-json, commonmark, both commonmark extensions, jsoup; `testImplementation`: `kotlin("test")`, junit, coroutines-test.
- **`app/build.gradle.kts`:** plugins `android-application`, `kotlin-android`, `kotlin-compose`; `namespace`/`applicationId` `app.verso.reader`; compile/target/min SDK from Global Constraints; `versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1`; `versionName = "0.1.$versionCode"`; Java/Kotlin 17; `buildFeatures.compose = true`; `testOptions.unitTests.isIncludeAndroidResources = true`; signing config `verso` (file `verso.keystore`, store password and key password `verso-personal`, alias `verso`) used by **both** `debug` and `release`; `release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }`; `packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"`; dependencies: `implementation(project(":core"))`, compose-bom (platform, also on `testImplementation`), compose-ui, compose-foundation, compose-material3, compose-material-icons-core, androidx-core-ktx, androidx-activity-compose, both lifecycle-compose libs; `testImplementation`: junit, kotlin("test"), robolectric, androidx-test-core, androidx-test-ext-junit, compose-ui-test-junit4, coroutines-test; `debugImplementation(compose-ui-test-manifest)`. (`pdfbox-android` is added in Task 15, not now.)
- **Keystore:** `keytool -genkeypair -keystore app/verso.keystore -alias verso -keyalg RSA -keysize 2048 -validity 36500 -storepass verso-personal -keypass verso-personal -dname "CN=Verso"`. A fixed key means every build has the same signature, so updates install over the previous version.
- **Manifest:** `<application android:label="@string/app_name" android:allowBackup="false" android:enableOnBackInvokedCallback="true" android:theme="@android:style/Theme.Material.NoActionBar">` with one exported `MainActivity` (MAIN/LAUNCHER). No permissions.
- **`MainActivity`:** `ComponentActivity` calling `enableEdgeToEdge()` and `setContent { Text("Verso") }`.
- **`Version.kt`:** `object Version { const val NAME = "Verso" }`.
- **`AGENTS.md`** (exact content, fill in the mode from Step 1):

```
# Verso: agent rules
Native Android reflow reader. Kotlin + Jetpack Compose. Two modules: :core (pure JVM) and :app (Android).
Plan: docs/superpowers/plans/2026-11-11-verso-reader.md (Global Constraints there are binding).

## Hard rules
- No permissions in the manifest (no INTERNET). No WebView. No Room, Hilt, Retrofit, OkHttp, Coil, Navigation, material-icons-extended.
- Stay on AGP 8.13.2, Kotlin 2.2.21, Gradle 8.14.3, Compose BOM 2025.12.00, compileSdk 36. Do not move to AGP 9 or compileSdk 37.
- Reader: one LazyColumn item per Block; never lay out a whole document at once; no zoom; no horizontal scroll.
- Parse and import work runs off the main thread. Block text is capped at 2500 chars (BlockSplitter).
- Pin every dependency version in gradle/libs.versions.toml; confirm it resolves before writing code against it.
- Repository must stay private (signing keystore is committed).

## Commands
- Core tests: ./gradlew :core:test
- App tests: ./gradlew :app:testDebugUnitTest
- Release APK: ./gradlew :app:assembleRelease   (app/build/outputs/apk/release/)
- CI (.github/workflows/build.yml) runs tests, builds the APK, enforces the 12 MiB size gate, publishes prerelease tag "latest".

## Verification mode
<FULL | JVM-ONLY | CI-ONLY>: <one-line evidence from the probe>

## Notes
(append version substitutions, R8 rules added, and other deviations here)
```

- [ ] **Step 4: Write the CI workflow** `.github/workflows/build.yml`

```yaml
name: build
on:
  push: { branches: [main] }
  pull_request:
  workflow_dispatch:
concurrency: { group: "build-${{ github.ref }}", cancel-in-progress: true }
permissions: { contents: write }
jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "17" }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew --no-daemon :core:test :app:testDebugUnitTest
      - run: ./gradlew --no-daemon :app:assembleRelease
      - name: APK size gate (12 MiB)
        run: |
          APK=$(ls app/build/outputs/apk/release/*.apk | head -n1)
          SIZE=$(stat -c%s "$APK"); echo "APK bytes: $SIZE"
          test "$SIZE" -le 12582912
      - name: Keep reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with: { name: reports, path: "**/build/reports/tests/**" }
      - name: Publish rolling release
        if: github.ref == 'refs/heads/main' && github.event_name != 'pull_request'
        env: { GH_TOKEN: "${{ github.token }}" }
        run: |
          cp "$(ls app/build/outputs/apk/release/*.apk | head -n1)" verso.apk
          gh release delete latest --cleanup-tag --yes || true
          gh release create latest verso.apk --prerelease --title "Verso build ${{ github.run_number }}" --notes "Commit ${{ github.sha }}"
```

Also create `README.md` (5 lines: what it is, how to install: open the repo's Releases page on the phone, tap `verso.apk`, allow "Install unknown apps"; optional: Obtainium can watch the `latest` release).

- [ ] **Step 5: Run tests and build**

Run (per Verification Mode): `./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease`
Expected: PASS; `app/build/outputs/apk/release/app-release.apk` exists and is signed (`apksigner verify` if available, otherwise the filename lacks `-unsigned`).

- [ ] **Step 6: Commit and verify CI**

```bash
git add -A && git commit -m "chore: project skeleton, signing, CI and hello APK"
git push origin main
```
Expected: the `build` workflow is green and a prerelease `latest` containing `verso.apk` exists.

- [ ] **Step 7 (HUMAN CHECKPOINT):** the controller asks the human to download `verso.apk` from the `latest` release on the phone, install it, and confirm it opens and shows "Verso". Do not continue until confirmed.

---

### Task 2: Document model, codec and import errors

**Files:**
- Create: `core/src/main/kotlin/app/verso/core/model/Document.kt`, `ImportError.kt`, `DocumentCodec.kt`
- Test: `core/src/test/kotlin/app/verso/core/model/DocumentCodecTest.kt`

**Interfaces:**
- Consumes: kotlinx-serialization (catalog).
- Produces (package `app.verso.core.model`):

```kotlin
@Serializable enum class SpanKind { Bold, Italic, Code, Strike, Link, Math }
@Serializable data class Span(val start: Int, val end: Int, val kind: SpanKind, val href: String? = null)
@Serializable data class InlineText(val text: String, val spans: List<Span> = emptyList()) { companion object { fun plain(text: String) = InlineText(text) } }
@Serializable sealed interface Block
@Serializable @SerialName("heading")   data class Heading(val level: Int, val text: InlineText) : Block
@Serializable @SerialName("paragraph") data class Paragraph(val text: InlineText) : Block
@Serializable @SerialName("list_item") data class ListItem(val depth: Int, val marker: String, val text: InlineText) : Block
@Serializable @SerialName("quote")     data class Quote(val depth: Int, val text: InlineText) : Block
@Serializable @SerialName("code")      data class CodeBlock(val language: String?, val code: String) : Block
@Serializable @SerialName("math")      data class MathBlock(val latex: String) : Block
@Serializable @SerialName("table")     data class TableBlock(val header: List<InlineText>, val rows: List<List<InlineText>>) : Block
@Serializable @SerialName("image")     data class ImageBlock(val alt: String) : Block
@Serializable @SerialName("divider")   data object Divider : Block
@Serializable data class Document(val title: String, val blocks: List<Block>)
fun Block.plainText(): String

sealed class ImportError(message: String, val userMessage: String, cause: Throwable? = null) : Exception(message, cause) {
    class Empty : ImportError("empty", "No readable text was found in this file.")
    class Drm : ImportError("drm", "This book is DRM-protected and can't be opened.")
    class NoTextLayer : ImportError("no text layer", "This PDF has no selectable text (it looks scanned). OCR isn't supported.")
    class TooLarge : ImportError("too large", "This file is larger than 64 MB.")
    class Unsupported(reason: String) : ImportError("unsupported: $reason", "Unsupported file: $reason")
    class Corrupt(detail: String, cause: Throwable? = null) : ImportError("corrupt: $detail", "This file couldn't be read.", cause)
}
object DocumentCodec { fun encode(doc: Document): String; fun decode(json: String): Document }
```

- [ ] **Step 1: Write the failing tests** in `DocumentCodecTest`:

```kotlin
@Test fun `round trips every block type`() {
    val doc = Document("T", listOf(
        Heading(2, InlineText("H", listOf(Span(0, 1, SpanKind.Bold)))),
        Paragraph(InlineText("a link", listOf(Span(2, 6, SpanKind.Link, "https://x.y")))),
        ListItem(1, "•", InlineText.plain("item")), Quote(0, InlineText.plain("q")),
        CodeBlock("kotlin", "val x = 1"), MathBlock("E=mc^2"),
        TableBlock(listOf(InlineText.plain("h")), listOf(listOf(InlineText.plain("c")))),
        ImageBlock("alt"), Divider))
    assertEquals(doc, DocumentCodec.decode(DocumentCodec.encode(doc)))
}
@Test fun `decode of truncated json throws SerializationException`() {
    val json = DocumentCodec.encode(Document("T", listOf(Paragraph(InlineText.plain("x")))))
    assertFailsWith<kotlinx.serialization.SerializationException> { DocumentCodec.decode(json.dropLast(5)) }
}
@Test fun `plainText covers every block kind`() {
    assertEquals("a", Paragraph(InlineText.plain("a")).plainText())
    assertEquals("x = 1", CodeBlock(null, "x = 1").plainText())
    assertEquals("E", MathBlock("E").plainText())
    assertEquals("alt", ImageBlock("alt").plainText())
    assertEquals("", Divider.plainText())
    assertEquals("h c", TableBlock(listOf(InlineText.plain("h")), listOf(listOf(InlineText.plain("c")))).plainText())
}
@Test fun `import errors carry exact user messages`() {
    assertEquals("No readable text was found in this file.", ImportError.Empty().userMessage)
    assertEquals("This book is DRM-protected and can't be opened.", ImportError.Drm().userMessage)
    assertEquals("This PDF has no selectable text (it looks scanned). OCR isn't supported.", ImportError.NoTextLayer().userMessage)
    assertEquals("This file is larger than 64 MB.", ImportError.TooLarge().userMessage)
    assertEquals("Unsupported file: ZIP archive", ImportError.Unsupported("ZIP archive").userMessage)
    assertEquals("This file couldn't be read.", ImportError.Corrupt("x").userMessage)
}
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :core:test --tests "app.verso.core.model.DocumentCodecTest"` Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement the model, `plainText`, `ImportError` and `DocumentCodec` exactly as in Interfaces.** `DocumentCodec` uses a single `Json { encodeDefaults = false; ignoreUnknownKeys = true }` instance; `decode` lets `SerializationException` propagate. `Table.plainText()` joins header texts then every cell text with single spaces.
- [ ] **Step 4: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add core && git commit -m "feat(core): document model, codec and import errors"
```

---

### Task 3: Text cleaning, long-block splitting and plain-text extraction

**Files:**
- Create: `core/src/main/kotlin/app/verso/core/text/TextCleaner.kt`, `BlockSplitter.kt`, `PlainTextExtractor.kt`
- Test: `core/src/test/kotlin/app/verso/core/text/TextCleanerTest.kt`, `BlockSplitterTest.kt`, `PlainTextExtractorTest.kt`

**Interfaces:**
- Consumes: `Block`, `Paragraph`, `Quote`, `ListItem`, `CodeBlock`, `InlineText`, `Span`, `Document` from Task 2.
- Produces:

```kotlin
object TextCleaner { fun clean(raw: String): String }
object BlockSplitter { const val MAX_BLOCK_CHARS = 2500; fun splitLong(blocks: List<Block>, maxChars: Int = MAX_BLOCK_CHARS): List<Block> }
object PlainTextExtractor { fun extract(text: String): Document }   // title is always ""
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// TextCleanerTest
@Test fun `clean normalizes line endings and strips invisibles`() {
    assertEquals("a\nb\nc", TextCleaner.clean("\uFEFFa\r\nb\rc"))
    assertEquals("a b", TextCleaner.clean("a\u00A0b"))
    assertEquals("coopx", TextCleaner.clean("co\u00ADop\u200Bx"))     // soft hyphen and zero-width space removed
    assertEquals("a\u200Cb", TextCleaner.clean("a\u200Cb"))            // ZWNJ kept (Persian)
    assertEquals("ab", TextCleaner.clean("a\u0007b"))                  // control char removed
    assertEquals("a b", TextCleaner.clean("a\tb"))
}

// BlockSplitterTest
@Test fun `long paragraph splits after sentence ends and loses nothing`() {   // Review Focus #1
    val text = (1..1000).joinToString("") { "Sentence $it. " }
    val out = BlockSplitter.splitLong(listOf(Paragraph(InlineText.plain(text))), 2500).map { (it as Paragraph).text.text }
    assertTrue(out.size > 1)
    assertTrue(out.all { it.length <= 2500 })
    assertEquals(text, out.joinToString(""))
    assertTrue(out.dropLast(1).all { it.endsWith(". ") })
}
@Test fun `text without whitespace hard-splits at the limit`() {
    val out = BlockSplitter.splitLong(listOf(Paragraph(InlineText.plain("x".repeat(6000)))), 2500).map { (it as Paragraph).text.text.length }
    assertEquals(listOf(2500, 2500, 1000), out)
}
@Test fun `spans are clipped across chunks`() {
    val p = Paragraph(InlineText("a".repeat(3000), listOf(Span(2400, 2600, SpanKind.Bold))))
    val out = BlockSplitter.splitLong(listOf(p), 2500).map { (it as Paragraph).text }
    assertEquals(listOf(Span(2400, 2500, SpanKind.Bold)), out[0].spans)
    assertEquals(listOf(Span(0, 100, SpanKind.Bold)), out[1].spans)
}
@Test fun `code splits only at newlines`() {
    val code = (1..1000).joinToString("\n") { "line $it" }
    val out = BlockSplitter.splitLong(listOf(CodeBlock("py", code)), 2500).map { (it as CodeBlock).code }
    assertTrue(out.all { it.length <= 2500 }); assertEquals(code, out.joinToString(""))
    assertTrue(out.dropLast(1).all { it.endsWith("\n") })
}
@Test fun `short blocks and other kinds pass through unchanged`() {
    val blocks = listOf(Paragraph(InlineText.plain("short")), Heading(1, InlineText.plain("h")), Divider)
    assertEquals(blocks, BlockSplitter.splitLong(blocks))
}

// PlainTextExtractorTest
@Test fun `short lines keep their line breaks inside one paragraph`() {
    val doc = PlainTextExtractor.extract("First line\nsecond line\n\nSecond para")
    assertEquals(listOf("First line\nsecond line", "Second para"), doc.blocks.map { (it as Paragraph).text.text })
}
@Test fun `hard-wrapped prose is unwrapped`() {
    val line = "word ".repeat(14).trim()                      // 69 chars
    val doc = PlainTextExtractor.extract("$line\n$line\n$line")
    assertEquals("$line $line $line", (doc.blocks.single() as Paragraph).text.text)
}
@Test fun `blank runs and CRLF produce no empty paragraphs`() {
    assertEquals(2, PlainTextExtractor.extract("a\r\n\r\n\r\n\r\nb").blocks.size)
    assertTrue(PlainTextExtractor.extract("  \n \n").blocks.isEmpty())
    assertEquals("", PlainTextExtractor.extract("a").title)
}
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :core:test --tests "app.verso.core.text.*"` Expected: FAIL.
- [ ] **Step 3: Implement `TextCleaner.clean`.** Remove U+FEFF, U+00AD, U+200B and control characters except `\n`; convert `\r\n` and `\r` to `\n`; U+00A0 and `\t` become a single space. Keep U+200C and U+200D.
- [ ] **Step 4: Implement `BlockSplitter.splitLong`.** Applies to `Paragraph`, `Quote`, `ListItem` (split into several blocks of the same kind and depth; the marker stays only on the first chunk, later chunks use marker `""`) and `CodeBlock` (split only after `\n`, falling back to a hard split at `maxChars` for a single line longer than the limit). For prose, the split point is the last position at or before `maxChars` that directly follows `". "`, `"? "`, `"! "` or `"\n"`; else the last position after any whitespace; else a hard split at `maxChars`. Chunks are contiguous substrings (concatenation equals the original); spans are clipped and shifted per chunk, empty spans dropped. Other block kinds pass through.
- [ ] **Step 5: Implement `PlainTextExtractor.extract`.** `TextCleaner.clean`, split on blank lines (`\n[ \t]*\n+`), trim line ends. A chunk with at least 2 lines where at least 60% of lines have length >= 45 is hard-wrapped prose: join lines with one space. Any other multi-line chunk keeps its `\n` separators. Each non-blank chunk becomes `Paragraph(InlineText.plain(...))`. Title `""`.
- [ ] **Step 6: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 7: Commit**

```bash
git add core && git commit -m "feat(core): text cleaning, long-block splitting, plain-text extractor"
```

---

### Task 4: HTML extractor

**Files:**
- Create: `core/src/main/kotlin/app/verso/core/html/HtmlExtractor.kt`
- Test: `core/src/test/kotlin/app/verso/core/html/HtmlExtractorTest.kt`

**Interfaces:**
- Consumes: model from Task 2, `TextCleaner.clean(String)` from Task 3, jsoup.
- Produces:

```kotlin
object HtmlExtractor {
    fun extract(html: String): Document                       // title = <title> text or ""
    fun blocksOf(root: org.jsoup.nodes.Element): List<Block>  // used by EpubExtractor and MarkdownExtractor
}
```

- [ ] **Step 1: Write the failing tests** (each builds HTML, calls `extract` or `blocksOf(Jsoup.parseBodyFragment(h).body())`):

```kotlin
@Test fun `headings paragraphs and inline styles`() {
    val doc = HtmlExtractor.extract("<html><head><title>T</title></head><body><h2>Hi</h2><p>a <b>b</b> <i>c</i> <a href=\"u\">d</a><br>e</p></body></html>")
    assertEquals("T", doc.title)
    assertEquals(Heading(2, InlineText.plain("Hi")), doc.blocks[0])
    assertEquals(Paragraph(InlineText("a b c d\ne", listOf(Span(2, 3, SpanKind.Bold), Span(4, 5, SpanKind.Italic), Span(6, 7, SpanKind.Link, "u")))), doc.blocks[1])
}
@Test fun `whitespace collapses and nbsp counts as a space`() {
    assertEquals(Paragraph(InlineText.plain("x y z")), blocks("<p>  x \n  y&nbsp; z </p>").single())
}
@Test fun `soft hyphen entity is removed`() = assertEquals("cooperate", (blocks("<p>co&shy;operate</p>").single() as Paragraph).text.text)
@Test fun `lists carry depth markers and start`() {
    assertEquals(listOf(ListItem(0, "•", InlineText.plain("a")), ListItem(1, "•", InlineText.plain("b")),
        ListItem(0, "3.", InlineText.plain("c")), ListItem(0, "4.", InlineText.plain("d"))),
        blocks("<ul><li>a<ul><li>b</li></ul></li></ul><ol start=\"3\"><li>c</li><li>d</li></ol>"))
}
@Test fun `pre becomes code with language and keeps whitespace`() =
    assertEquals(CodeBlock("py", "x = 1\n  y"), blocks("<pre><code class=\"language-py\">x = 1\n  y</code></pre>").single())
@Test fun `blockquote becomes quotes with nesting depth`() {
    assertEquals(listOf(Quote(0, InlineText.plain("q")), Quote(1, InlineText.plain("deep"))),
        blocks("<blockquote><p>q</p><blockquote><p>deep</p></blockquote></blockquote>"))
}
@Test fun `table with th header`() {
    val t = blocks("<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>").single() as TableBlock
    assertEquals(listOf("A", "B"), t.header.map { it.text }); assertEquals(listOf(listOf("1", "2")), t.rows.map { r -> r.map { it.text } })
}
@Test fun `noise is dropped and loose text and images are handled`() {
    assertEquals(listOf<Block>(Paragraph(InlineText.plain("loose")), ImageBlock("fig"), Divider),
        blocks("<script>x()</script><style>p{}</style><nav>menu</nav><div>loose</div><img alt=\"fig\"><hr>"))
}
@Test fun `empty input gives no blocks`() = assertTrue(HtmlExtractor.extract("").blocks.isEmpty())
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :core:test --tests "app.verso.core.html.HtmlExtractorTest"` Expected: FAIL.
- [ ] **Step 3: Implement `HtmlExtractor`.** Rules: skip `script, style, nav, head, noscript, svg, template, iframe`; `h1`-`h6` -> `Heading`; `p` -> `Paragraph`; `li` -> `ListItem` (depth = number of ancestor `ul/ol` minus 1; marker `•` for `ul`, `N.` for `ol` counting from the `start` attribute, default 1; nested lists emit after the parent item's own text); `blockquote` -> its text blocks as `Quote` with depth = number of ancestor blockquotes; `pre` -> `CodeBlock` (language from a `language-xyz` class on `pre` or its `code` child; keep whitespace, strip one trailing newline); `table` -> `TableBlock` (header = the cells of the first row if they are `th`, else first row; rows = the rest; cell text via the inline builder); `img` -> `ImageBlock(alt)`; `hr` -> `Divider`; `div/section/article/main/body/figure/etc.` -> recurse, and text nodes or inline elements that sit directly inside such a container become a `Paragraph`. Inline mapping: `b/strong` Bold, `i/em/cite/dfn` Italic, `code/kbd/samp/tt` Code, `s/del/strike` Strike, `a[href]` Link, `br` -> `\n`. Whitespace: collapse `[ \t\n\r\f\u00A0]+` to one space (outside `pre`), trim each block's edges, remove U+00AD. Blocks whose text is blank are dropped. `extract` cleans the input with `TextCleaner.clean`, then parses with `Jsoup.parse`.
- [ ] **Step 4: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add core && git commit -m "feat(core): HTML extractor"
```

---

### Task 5: Markdown extractor with math protection

**Files:**
- Create: `core/src/main/kotlin/app/verso/core/markdown/MathProtector.kt`, `MarkdownExtractor.kt`
- Test: `core/src/test/kotlin/app/verso/core/markdown/MathProtectorTest.kt`, `MarkdownExtractorTest.kt`

**Interfaces:**
- Consumes: model (Task 2), `TextCleaner` (Task 3), `HtmlExtractor.blocksOf` (Task 4), commonmark-java.
- Produces:

```kotlin
object MathProtector {
    const val OPEN = '\uE000'; const val CLOSE = '\uE001'          // placeholder = OPEN + index + CLOSE
    data class MathSpan(val latex: String, val display: Boolean)
    data class Result(val text: String, val math: List<MathSpan>)
    fun protect(markdown: String): Result
}
object MarkdownExtractor { fun extract(markdown: String): Document }    // title is always ""
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// MathProtectorTest: helper `fun ph(i: Int) = "${MathProtector.OPEN}$i${MathProtector.CLOSE}"`
@Test fun `inline dollars`() { val r = MathProtector.protect("a \$x\$ b"); assertEquals("a ${ph(0)} b", r.text); assertEquals(listOf(MathSpan("x", false)), r.math) }
@Test fun `prices are not math`() {
    for (s in listOf("costs \$5 and \$10 total", "\$ x\$", "\$x \$", "\$5.00", "\$x\$5")) assertEquals(s, MathProtector.protect(s).text, s)
}
@Test fun `display delimiters`() {
    assertEquals(MathSpan("a_b + c_d", true), MathProtector.protect("\\[ a_b + c_d \\]").math.single())
    assertEquals(MathSpan("x", false), MathProtector.protect("inline \\(x\\) done").math.single())
    assertEquals(MathSpan("a", true), MathProtector.protect("see \$\$a\$\$ here").math.single())
}
@Test fun `display math on its own lines is surrounded by blank lines`() {
    assertEquals("p1\n\n${ph(0)}\n\np2", MathProtector.protect("p1\n\$\$\nx\n\$\$\np2").text)
}
@Test fun `code and escapes are left alone`() {
    for (s in listOf("`\$a\$ and \$b\$`", "```\n\$\$x\$\$\n```", "\\\$5 and \\\$6")) assertTrue(MathProtector.protect(s).math.isEmpty(), s)
}

// MarkdownExtractorTest (helper b(md) = MarkdownExtractor.extract(md).blocks)
@Test fun `inline styles and exact span offsets`() {
    assertEquals(listOf(Heading(1, InlineText.plain("Title")), Paragraph(InlineText("Hello bold and it and code and link gone", listOf(
        Span(6, 10, SpanKind.Bold), Span(15, 17, SpanKind.Italic), Span(22, 26, SpanKind.Code), Span(31, 35, SpanKind.Link, "https://a.b"), Span(36, 40, SpanKind.Strike))))),
        b("# Title\n\nHello **bold** and *it* and `code` and [link](https://a.b) ~~gone~~"))
}
@Test fun `nested emphasis`() {
    val p = b("**bold *both***").single() as Paragraph
    assertEquals("bold both", p.text.text); assertTrue(Span(0, 9, SpanKind.Bold) in p.text.spans); assertTrue(Span(5, 9, SpanKind.Italic) in p.text.spans)
}
@Test fun `soft break is a space and hard break is a newline`() {
    assertEquals("a b", (b("a\nb").single() as Paragraph).text.text); assertEquals("a\nb", (b("a  \nb").single() as Paragraph).text.text)
}
@Test fun `lists`() {
    assertEquals(listOf(ListItem(0, "•", InlineText.plain("a")), ListItem(1, "•", InlineText.plain("b")), ListItem(0, "1.", InlineText.plain("x")), ListItem(0, "2.", InlineText.plain("y"))),
        b("- a\n  - b\n\n1. x\n2. y"))
    assertEquals(listOf(ListItem(0, "•", InlineText.plain("a")), ListItem(0, "", InlineText.plain("more"))), b("- a\n\n  more"))
}
@Test fun `quotes`() = assertEquals(listOf(Quote(0, InlineText.plain("q1")), Quote(0, InlineText.plain("q2")), Quote(1, InlineText.plain("deep"))), b("> q1\n>\n> q2\n\n> > deep"))
@Test fun `fenced code`() = assertEquals(CodeBlock("kotlin", "val x = 1"), b("```kotlin\nval x = 1\n```").single())
@Test fun `gfm table`() {
    val t = b("| A | B |\n|---|---|\n| 1 | 2 |").single() as TableBlock
    assertEquals(listOf("A", "B"), t.header.map { it.text }); assertEquals(listOf(listOf("1", "2")), t.rows.map { r -> r.map { it.text } })
}
@Test fun `divider and images`() {
    assertEquals(listOf<Block>(Paragraph(InlineText.plain("a")), Divider, Paragraph(InlineText.plain("b"))), b("a\n\n---\n\nb"))
    assertEquals(ImageBlock("diagram"), b("![diagram](x.png)").single())
    assertEquals("see [d] here", ((b("see ![d](x.png) here").single()) as Paragraph).text.text)
}
@Test fun `math becomes spans and blocks and survives underscores`() {
    assertEquals(Paragraph(InlineText("Energy E=mc^2 here", listOf(Span(7, 13, SpanKind.Math)))), b("Energy \$E=mc^2\$ here").single())
    assertEquals(MathBlock("\\int_0^1 x\\,dx"), b("\$\$\n\\int_0^1 x\\,dx\n\$\$").single())
    assertEquals(MathBlock("a_b + c_d"), b("\\[ a_b + c_d \\]").single())
    assertEquals(Paragraph(InlineText.plain("costs \$5 and \$10 total")), b("costs \$5 and \$10 total").single())
    assertEquals(Paragraph(InlineText("inline x_1 done", listOf(Span(7, 10, SpanKind.Math)))), b("inline \\(x_1\\) done").single())
}
@Test fun `html blocks are extracted not dropped`() = assertEquals(listOf<Block>(Paragraph(InlineText.plain("x")), Paragraph(InlineText.plain("para"))), b("<div>x</div>\n\npara"))
@Test fun `empty input`() = assertTrue(b("").isEmpty())
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :core:test --tests "app.verso.core.markdown.*"` Expected: FAIL.
- [ ] **Step 3: Implement `MathProtector.protect`.** A line-by-line scanner that tracks fenced-code state (``` or ~~~ lines) and skips inline code spans (matching backtick runs). Recognized delimiters: `$$...$$` (display, may span lines), `\[...\]` (display), `\(...\)` (inline), `$...$` (inline). Single-dollar rule (pandoc): the opening `$` must not be followed by whitespace; the closing `$` must not be preceded by whitespace and must not be followed by a digit; `\$` is never a delimiter. Each match is replaced by `OPEN + index + CLOSE` and its trimmed content appended to `math`. A display match whose opening delimiter starts a line (ignoring indentation) and whose closing delimiter ends a line is replaced by `"\n\n" + placeholder + "\n\n"` (the test above fixes the exact blank-line behavior).
- [ ] **Step 4: Implement `MarkdownExtractor.extract`.** `TextCleaner.clean` -> `MathProtector.protect` -> commonmark `Parser` with `TablesExtension` and `StrikethroughExtension` -> walk the AST. The commonmark node names collide with the model's (`Heading`, `ListItem`, `Paragraph`, `Image`): import commonmark as `org.commonmark.node as md` and the table extension's `TableBlock` with an alias. Mapping: Heading, Paragraph, FencedCodeBlock and IndentedCodeBlock (strip the trailing newline), ThematicBreak -> Divider, BlockQuote (depth = nesting; each inner paragraph -> `Quote`), BulletList/OrderedList (marker `•` or `N.` from the list's start number; depth = list nesting; the first paragraph of an item gets the marker, further paragraphs in the same item become `ListItem` with marker `""`), tables -> `TableBlock`, `HtmlBlock` -> `HtmlExtractor.blocksOf(Jsoup.parseBodyFragment(literal).body())`, inline `HtmlInline` tags are dropped but their text stays. A paragraph consisting only of an image -> `ImageBlock(alt)`; an inline image -> `[alt]` text. Inline builder: one `StringBuilder` plus spans (Emphasis Italic, StrongEmphasis Bold, Code, Link with href, Strikethrough); soft break -> `" "`, hard break -> `"\n"`. Placeholders inside text are expanded to the math source with a `Math` span; a paragraph that is exactly one display placeholder becomes `MathBlock`. Unsupported nesting is flattened in document order; text is never silently dropped.
- [ ] **Step 5: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 6: Commit**

```bash
git add core && git commit -m "feat(core): Markdown extractor with math protection"
```

---

### Task 6: EPUB extractor

**Files:**
- Create: `core/src/main/kotlin/app/verso/core/epub/EpubExtractor.kt`
- Test: `core/src/test/kotlin/app/verso/core/epub/EpubExtractorTest.kt`

**Interfaces:**
- Consumes: `HtmlExtractor.blocksOf`, `TextCleaner.clean`, `Document`, `ImportError.Drm/Corrupt`, `TextDecoding` is NOT available yet (decode entry bytes as UTF-8 with a BOM check).
- Produces:

```kotlin
object EpubExtractor {
    fun extract(bytes: ByteArray, maxUncompressedBytes: Long = 256L * 1024 * 1024, onChapter: () -> Unit = {}): Document   // title = dc:title or ""
}
```

- [ ] **Step 1: Write the failing tests.** In the test file add a helper `epub(files: Map<String, String>): ByteArray` using `ZipOutputStream` (first entry `mimetype` = `application/epub+zip`, stored). Standard files: `META-INF/container.xml` pointing at `OEBPS/content.opf`; an OPF with `dc:title`, manifest and spine.

```kotlin
@Test fun `chapters follow spine order not manifest order and title comes from dc title`()   // two chapters, spine lists ch2 before ch1; blocks come out ch2 then ch1; doc.title == "My Book"
@Test fun `hrefs are percent-decoded and resolved relative to the opf`()                      // OPF at OEBPS/content.opf, href "Text/ch%201.xhtml", entry "OEBPS/Text/ch 1.xhtml"
@Test fun `non-linear items the nav document and non-html items are skipped`()                // linear="no" item, manifest item properties="nav", image/css items: none contribute blocks
@Test fun `a missing spine file is skipped without failing the book`()                        // other chapters still extracted
@Test fun `aes encrypted content is DRM but font obfuscation is fine`() {
    assertFailsWith<ImportError.Drm> { EpubExtractor.extract(epub(base + ("META-INF/encryption.xml" to enc("http://www.w3.org/2001/04/xmlenc#aes128-cbc")))) }
    assertTrue(EpubExtractor.extract(epub(base + ("META-INF/encryption.xml" to enc("http://www.idpf.org/2008/embedding")))).blocks.isNotEmpty())
}
@Test fun `garbage bytes and a missing container are Corrupt`() {
    assertFailsWith<ImportError.Corrupt> { EpubExtractor.extract(ByteArray(100) { 7 }) }
    assertFailsWith<ImportError.Corrupt> { EpubExtractor.extract(epub(mapOf("mimetype" to "application/epub+zip"))) }
}
@Test fun `uncompressed size cap is enforced`() = assertFailsWith<ImportError.Corrupt> { EpubExtractor.extract(epub(base), maxUncompressedBytes = 50) }
@Test fun `onChapter fires once per extracted chapter`()
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :core:test --tests "app.verso.core.epub.EpubExtractorTest"` Expected: FAIL.
- [ ] **Step 3: Implement `EpubExtractor.extract`.** Read the ZIP with `ZipInputStream`, retaining only entries whose names end in `.xml`, `.opf`, `.xhtml`, `.html`, `.htm`, `.ncx` (images and fonts are skipped); keep a running total of uncompressed bytes and throw `Corrupt` above the cap; any `IOException`/`ZipException` or a missing container -> `ImportError.Corrupt`. `META-INF/encryption.xml`: any `EncryptionMethod/@Algorithm` other than `http://www.idpf.org/2008/embedding` and `http://ns.adobe.com/pdf/enc#RC` -> `ImportError.Drm`. Parse `container.xml` and the OPF with `Jsoup.parse(xml, "", Parser.xmlParser())`. Spine order from `spine > itemref[idref]` (skip `linear="no"`), looked up in `manifest > item[id]`; skip items whose `media-type` is not `application/xhtml+xml` or `text/html`, or whose `properties` contains `nav`. Resolve `href` relative to the OPF's directory after dropping any `#fragment`, percent-decoding with `java.net.URI(href).path` (not `URLDecoder`, because `+` is literal), and normalizing `..`. For each chapter: decode as UTF-8 (BOM-aware), `TextCleaner.clean`, `Jsoup.parse`, `HtmlExtractor.blocksOf(body)`, then `onChapter()`. Title from `metadata > dc|title` (first), trimmed, else `""`.
- [ ] **Step 4: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add core && git commit -m "feat(core): EPUB extractor"
```

---

### Task 7: PDF layout analysis (runs to ordered lines)

**Files:**
- Create: `core/src/main/kotlin/app/verso/core/pdf/PdfModels.kt`, `PdfLayout.kt`
- Test: `core/src/test/kotlin/app/verso/core/pdf/PdfLayoutTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces (package `app.verso.core.pdf`; coordinates are in points with the origin at the **top-left** and `baseline` growing downward):

```kotlin
data class TextRun(val text: String, val x: Float, val baseline: Float, val width: Float, val fontSize: Float, val bold: Boolean = false)
data class PdfPage(val index: Int, val width: Float, val height: Float, val runs: List<TextRun>)
data class PdfContent(val title: String?, val pages: List<PdfPage>)
interface PdfPageSource { fun read(pdf: ByteArray, onPage: () -> Unit = {}): PdfContent }   // throws ImportError (Unsupported for password-protected, Corrupt for broken files)
data class PdfLine(val page: Int, val column: Int, val text: String, val x0: Float, val x1: Float, val baseline: Float, val fontSize: Float, val bold: Boolean)
object PdfLayout {
    const val LINE_TOLERANCE_EM = 0.35f      // runs share a line if |baseline diff| <= 0.35 x the larger fontSize
    const val SPACE_GAP_EM = 0.15f           // insert a space if the gap between runs > 0.15 x fontSize and neither side is whitespace
    const val HEADER_FOOTER_BAND = 0.08f     // top / bottom 8% of the page height
    const val SPANNING_FRACTION = 0.6f       // a run wider than 60% of the page width spans columns
    const val MIN_GUTTER_PT = 8f; const val MIN_SIDE_FRACTION = 0.25f
    fun orderedLines(pages: List<PdfPage>): List<PdfLine>   // reading order: pages ascending; per page top-to-bottom bands, left column then right column in each band
}
```

- [ ] **Step 1: Write the failing tests.** Pages are 600 x 800, body fontSize 10; helper `r(text, x, baseline, width = text.length * 5f, size = 10f, bold = false)`.

```kotlin
@Test fun `single column lines come out top to bottom even from shuffled runs`()           // baselines 100/112/124 at x=72 shuffled -> in order, all column 0
@Test fun `runs on one baseline join with a space only when the gap is wide enough`() {
    // "Hello"(x72,w25) + "world"(x102): gap 5pt > 1.5pt -> "Hello world";  "foo"(x72,w15) + "bar"(x87.5): gap 0.5pt -> "foobar"
}
@Test fun `a superscript joins its line`()                                                   // "E = mc"(x72,w30,b100) + "2"(x102.2,w3.5,size7,b97) -> "E = mc2"
@Test fun `two columns are read left column fully then right column`() {
    // left runs x=72 w=218, right runs x=320 w=218 (30pt gutter), 26 baselines 100..400 step 12 -> 26 left lines (column 0) then 26 right lines (column 1)
}
@Test fun `a full-width title precedes the columns and a full-width run splits them into bands`() {
    // title x72 w466 b60 size16; columns below; a spanning run at b250 -> order: title, L-above, R-above, spanning, L-below, R-below
}
@Test fun `repeated headers and footers and page numbers are removed`() {
    // 6 pages, header "Running title" at b30 on all, footer "Journal of X 12".."17" at b770 (numbers vary), plus a lone "3" at b770 on one page, body lines at b100.. ->
    // only body lines remain. A one-off line at b40 on page 0 only ("Title of this paper") is kept.
}
@Test fun `in a two page document a footer repeated on both pages is removed`()
@Test fun `line geometry is aggregated`()                                                    // x0 = min x, x1 = max(x+width), bold only if every run is bold, fontSize = the larger size
@Test fun `empty input and empty pages give no lines`()
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :core:test --tests "app.verso.core.pdf.PdfLayoutTest"` Expected: FAIL.
- [ ] **Step 3: Implement `PdfModels.kt` exactly as in Interfaces.**
- [ ] **Step 4: Implement `PdfLayout.orderedLines`.** Per page: (a) **Spanning runs** = runs wider than `SPANNING_FRACTION x page width`. (b) **Gutter detection** on the remaining runs: build the occupied x-intervals; find the widest empty interval whose midpoint lies in 30% to 70% of the page width; it is a gutter when it is at least `MIN_GUTTER_PT` wide and each side holds at least `MIN_SIDE_FRACTION` of those runs; otherwise the page is single-column. A non-spanning run that straddles the gutter counts as spanning. (c) **Bands:** sort spanning runs by baseline; they cut the page into vertical bands; within a band order runs left column first, then right column; spanning runs form their own lines with `column = 0`. (d) **Lines:** within each (band, column) group runs by baseline using `LINE_TOLERANCE_EM`, sort by `x`, join text inserting a space per `SPACE_GAP_EM`. (e) **Header/footer removal:** a line is a candidate when its baseline is in the top or bottom `HEADER_FOOTER_BAND` of the page height; its key is `text.lowercase().replace(Regex("\\d+"), "#").trim()`; a candidate is removed when its key occurs in the bands of at least `max(2, ceil(0.5 x pageCount))` distinct pages, or when it matches `^(page\s+)?([0-9]{1,4}|[ivxlcdm]{1,6})(\s+of\s+[0-9]+)?$` (case-insensitive). Output pages ascending, preserving the order above.
- [ ] **Step 5: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 6: Commit**

```bash
git add core && git commit -m "feat(core): PDF layout analysis"
```

---

### Task 8: PDF assembly (lines to blocks) and `PdfExtractor`

**Files:**
- Create: `core/src/main/kotlin/app/verso/core/pdf/PdfAssembler.kt`, `PdfExtractor.kt`
- Test: `core/src/test/kotlin/app/verso/core/pdf/PdfAssemblerTest.kt`, `PdfExtractorTest.kt`

**Interfaces:**
- Consumes: `PdfLine`, `PdfPageSource`, `PdfContent`, `PdfLayout.orderedLines` (Task 7); `Heading`, `Paragraph`, `ListItem`, `InlineText`, `Document`, `ImportError.NoTextLayer` (Task 2); `TextCleaner.clean` (Task 3).
- Produces:

```kotlin
object PdfAssembler { fun assemble(lines: List<PdfLine>): List<Block> }
class PdfExtractor(private val source: PdfPageSource) { fun extract(pdf: ByteArray, onPage: () -> Unit = {}): Document }   // title = metadata title or ""
```

- [ ] **Step 1: Write the failing tests** (helper `ln(page, text, baseline, x0 = 72f, size = 10f, bold = false, column = 0)` builds a `PdfLine` with `x1 = x0 + text.length * 5f`; body size is the dominant 10pt):

```kotlin
@Test fun `wrapped lines join into one paragraph`()                       // "The quick brown fox" b100 + "jumps over the dog." b112 -> "The quick brown fox jumps over the dog."
@Test fun `a large vertical gap starts a new paragraph`()                  // b100, b112, b140 -> two paragraphs (gap 28 > 1.45 x pitch 12)
@Test fun `a first-line indent after a sentence end starts a new paragraph`()   // prev ends with ".", next x0=82 (>= 0.8 x body above the margin 72) at normal pitch -> new paragraph; same indent after a line that does NOT end a sentence -> joined
@Test fun `hyphenated line ends are dehyphenated`() {
    // "exam-" + "ple text" -> "example text";  "well-" + "Known" -> "well- Known" (next starts uppercase: keep hyphen and add a space);  "A-" + "1" keeps the hyphen
}
@Test fun `ligatures are expanded`()                                       // "ﬁnal ﬂow ﬀ ﬃ ﬄ" -> "final flow ff ffi ffl"
@Test fun `headings by size and by bold-only short lines`() {
    // sizes 16 / 13 / 11.5 -> Heading levels 1 / 2 / 3; bold body-size line "Abstract" -> Heading(3); bold line ending "." stays a paragraph
    // two consecutive 16pt lines at b60 and b80 (gap 20 <= 1.6 x 16) merge into ONE heading "Line one Line two"
}
@Test fun `paragraphs continue across page and column boundaries unless a sentence ended`() {
    // p0 last line "This sentence continues" + p1 first line "on the next page." -> one paragraph;  "...done." + "New paragraph" -> two;  same rule for column 0 -> column 1
}
@Test fun `bullet lines become list items and indented continuations join them`() {
    // "• first item" (x72) + "continues here" (x82) + "• second" -> ListItem(0,"•","first item continues here"), ListItem(0,"•","second")
}
@Test fun `no lines gives no blocks`()

// PdfExtractorTest (fake PdfPageSource returning a PdfContent)
@Test fun `title comes from metadata and text becomes blocks`()
@Test fun `pages without runs are a scanned pdf`() = assertFailsWith<ImportError.NoTextLayer> { ... }     // Review Focus #5
@Test fun `fewer than 25 characters in total is also a scanned pdf`()
@Test fun `onPage is passed through to the source`()
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :core:test --tests "app.verso.core.pdf.PdfAssemblerTest" --tests "app.verso.core.pdf.PdfExtractorTest"` Expected: FAIL.
- [ ] **Step 3: Implement `PdfAssembler.assemble`.** Definitions: **body size** = the font size (rounded to 0.5pt) with the greatest total character count; **pitch** = median of the positive baseline deltas (< 3 x body size) between consecutive lines of the same page and column, fallback `1.2 x body`; **left margin** of a (page, column) = most frequent `x0` rounded to 1pt. Line text goes through `TextCleaner.clean` and the ligature map (`ﬁ fl ﬀ ﬃ ﬄ` -> `fi fl ff ffi ffl`). **Heading:** `fontSize >= 1.15 x body` -> level 1 when `>= 1.6x`, 2 when `>= 1.3x`, else 3; or an all-bold line at body size, shorter than 100 chars, not ending in `. , ;` -> level 3; consecutive heading lines of equal size on one page with baseline gap `<= 1.6 x size` merge into one heading (joined by a space). **Bullets:** a line starting with one of `• ▪ ◦ ●` followed by a space starts a `ListItem(0, "•", ...)`; following lines with `x0` greater than the bullet's `x0` and no bullet join that item. **Paragraph start** when: first line, or after a heading or list item; or same page and column and baseline gap `> 1.45 x pitch`; or `x0 - leftMargin >= 0.8 x body` and the previous line ends a sentence; or a page or column boundary where the previous text ends a sentence. **A sentence end** = text matching `[.!?:;]["'”’)\]]*$`. **Joining:** if the accumulated text ends with a letter followed by `-` and the next line starts with a lowercase letter, drop the hyphen and join without a space; otherwise join with one space.
- [ ] **Step 4: Implement `PdfExtractor.extract`.** `source.read(pdf, onPage)` -> `PdfLayout.orderedLines(content.pages)` -> `PdfAssembler.assemble`. If no page has runs or the total run text length is below 25 characters, throw `ImportError.NoTextLayer`. Return `Document(content.title?.trim().orEmpty(), blocks)`.
- [ ] **Step 5: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 6: Commit**

```bash
git add core && git commit -m "feat(core): PDF assembler and extractor"
```

---

### Task 9: Format detection and the import pipeline

**Files:**
- Create: `core/src/main/kotlin/app/verso/core/importer/DocumentSource.kt`, `FormatDetector.kt`, `TextDecoding.kt`, `Importer.kt`
- Test: `core/src/test/kotlin/app/verso/core/importer/FormatDetectorTest.kt`, `TextDecodingTest.kt`, `DocumentImporterTest.kt`

**Interfaces:**
- Consumes: every extractor (`PlainTextExtractor.extract(String)`, `MarkdownExtractor.extract(String)`, `HtmlExtractor.extract(String)`, `EpubExtractor.extract(bytes, onChapter = ...)`, `PdfExtractor(source).extract(bytes, onPage)`), `BlockSplitter.splitLong`, `TextCleaner`, model, `ImportError`.
- Produces (package `app.verso.core.importer`):

```kotlin
class DocumentSource(val name: String?, val mimeType: String?, val bytes: ByteArray)
enum class SourceFormat { PlainText, Markdown, Html, Epub, Pdf }
object FormatDetector { fun detect(source: DocumentSource): SourceFormat }          // throws ImportError.Unsupported for other ZIPs
object TextDecoding { fun decode(bytes: ByteArray): String }
data class ImportResult(val document: Document, val format: SourceFormat)
interface Importer { suspend fun import(source: DocumentSource): ImportResult }
class DocumentImporter(private val pdfSource: PdfPageSource? = null, private val maxSourceBytes: Long = MAX_SOURCE_BYTES) : Importer {
    companion object { const val MAX_SOURCE_BYTES = 64L * 1024 * 1024 }
}
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// FormatDetectorTest  (helper src(name, mime, content: String))
@Test fun `magic bytes beat names`()   { assertEquals(Pdf, detect(DocumentSource("x.txt", null, "%PDF-1.7 ...".toByteArray()))) ; epub bytes (zip whose first entry is mimetype "application/epub+zip") named "x.bin" -> Epub; other zip -> Unsupported("ZIP archive") }
@Test fun `extensions and mime types`() { ".md", ".markdown", "text/markdown" -> Markdown; ".html", ".htm", ".xhtml", "text/html" -> Html; ".txt" -> PlainText }
@Test fun `unnamed text is sniffed`() {
    assertEquals(Html, detect(src(null, null, "  <!DOCTYPE html><html>..")))
    assertEquals(Markdown, detect(src(null, null, "# Title\n\ntext")))
    assertEquals(Markdown, detect(src(null, null, "- a\n- b")))              // two list lines
    assertEquals(Markdown, detect(src(null, null, "see [x](http://y) now")))
    assertEquals(PlainText, detect(src(null, null, "Just a sentence - with a dash.\nand costs \$5 and \$10.")))
}
// TextDecodingTest
@Test fun `utf8 bom utf16 bom and windows-1252 fallback`() { UTF-8 BOM stripped; UTF-16LE/BE BOM decoded; bytes [0x93,0x41,0x94] (invalid UTF-8) -> "“A”" }

// DocumentImporterTest
@Test fun `markdown import gets its title from the first heading`()
@Test fun `title fallback chain`()                      // extractor title -> first Heading -> file name without extension -> "Untitled"
@Test fun `html and epub and pdf all import`()          // pdf via a fake PdfPageSource and bytes starting with "%PDF-1.4"
@Test fun `blank input is Empty and oversize input is TooLarge`()   // DocumentImporter(maxSourceBytes = 10) with 11 bytes
@Test fun `a pdf source is required for pdf`()          // DocumentImporter(pdfSource = null) -> ImportError.Unsupported
@Test fun `one million characters on a single line become many small blocks quickly`() {            // Review Focus #1
    val r = runBlocking { DocumentImporter().import(DocumentSource("big.txt", "text/plain", "word ".repeat(200_000).toByteArray())) }
    assertTrue(r.document.blocks.size >= 400); assertTrue(r.document.blocks.all { it.plainText().length <= 2500 })
}
@Test fun `twenty thousand markdown paragraphs import in under five seconds`()                      // timing guard, generous
@Test fun `cancellation stops a pdf import`()          // fake source calls onPage repeatedly and the test cancels the job on page 3 -> CancellationException
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :core:test --tests "app.verso.core.importer.*"` Expected: FAIL.
- [ ] **Step 3: Implement `TextDecoding.decode`.** BOM check (UTF-8, UTF-16LE, UTF-16BE); else strict UTF-8 decode via `CharsetDecoder` with `REPORT`; on `CharacterCodingException` fall back to `windows-1252`.
- [ ] **Step 4: Implement `FormatDetector.detect`.** Order: `%PDF-` within the first 1024 bytes -> Pdf; ZIP magic `PK\u0003\u0004` -> Epub only if the first entry is named `mimetype` with content `application/epub+zip` (check the bytes at offsets 30 and 38) or the name ends `.epub`, otherwise throw `Unsupported("ZIP archive")`; extension (`md/markdown/mdown` Markdown, `html/htm/xhtml` Html, `txt` PlainText) ; MIME (`text/markdown`, `text/x-markdown`, `text/html`); otherwise sniff the first 8 KB: starts (after whitespace) with `<!doctype html` or `<html` -> Html; Markdown when at least one **strong** signal matches (`^#{1,6} \S`, a ``` fence, a table separator row, `$$`, `**bold**`, `[text](url)`) or at least two list lines (`^\s*([-*+]|\d+\.) \S`); else PlainText.
- [ ] **Step 5: Implement `DocumentImporter.import`.** Size check first (`TooLarge`); detect; extract (text formats: `TextDecoding.decode` then the extractor; Epub: `EpubExtractor.extract(bytes, onChapter = { job?.ensureActive() })`; Pdf: `pdfSource ?: throw Unsupported("PDF support is unavailable")`, `PdfExtractor(...).extract(bytes) { job?.ensureActive() }`, where `job = currentCoroutineContext()[Job]`); then drop blocks whose `plainText().isBlank()` except `Divider`; `BlockSplitter.splitLong`; if nothing is left throw `ImportError.Empty`; title fallback chain (first `Heading` text trimmed to 120 chars; file name without extension; `"Untitled"`). Other exceptions from extractors that are not `ImportError` are wrapped in `ImportError.Corrupt`. Call `ensureActive()` between stages.
- [ ] **Step 6: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 7: Commit**

```bash
git add core && git commit -m "feat(core): format detection and import pipeline"
```

---

### Task 10: Persistence (settings, recents, document cache)

**Files:**
- Create: `core/src/main/kotlin/app/verso/core/store/JsonFile.kt`, `ReaderSettings.kt`, `SettingsStore.kt`, `LibraryStore.kt`
- Test: `core/src/test/kotlin/app/verso/core/store/JsonFileTest.kt`, `SettingsStoreTest.kt`, `LibraryStoreTest.kt`

**Interfaces:**
- Consumes: `Document`, `DocumentCodec`, `SourceFormat` (Task 9), kotlinx-serialization.
- Produces (package `app.verso.core.store`):

```kotlin
object AtomicFile { fun writeText(file: File, text: String) }      // temp file + atomic move
class JsonFile<T>(private val file: File, private val serializer: KSerializer<T>, private val default: () -> T) { fun read(): T; fun write(value: T) }  // read() returns default() when missing OR corrupt

@Serializable enum class ThemeChoice { System, Light, Sepia, Dark, Black }
@Serializable enum class FontChoice { Sans, Serif, Mono }
@Serializable enum class ReadAlign { Start, Justify }
@Serializable data class ReaderSettings(
    val theme: ThemeChoice = ThemeChoice.System, val font: FontChoice = FontChoice.Serif,
    val fontSizeSp: Float = 20f, val lineHeight: Float = 1.5f, val paragraphSpacingEm: Float = 0.8f,
    val marginDp: Int = 20, val align: ReadAlign = ReadAlign.Start, val keepScreenOn: Boolean = false)
object SettingsLimits { val FONT_SIZE = 12f..48f; val LINE_HEIGHT = 1.0f..2.4f; val PARAGRAPH_SPACING = 0f..2f; val MARGIN = 0..48 }
fun ReaderSettings.coerced(): ReaderSettings
class SettingsStore(file: File) { fun load(): ReaderSettings; fun save(settings: ReaderSettings) }   // load() returns defaults for missing/corrupt; always coerced

@Serializable data class RecentEntry(val id: String, val title: String, val format: String, val addedAt: Long, val lastOpenedAt: Long,
                                     val blockCount: Int, val anchorBlock: Int = 0, val anchorOffsetPx: Int = 0)
val RecentEntry.progress: Float          // blockCount <= 1 -> 0f, else anchorBlock/(blockCount-1) clamped to 0..1
class LibraryStore(private val dir: File, private val maxEntries: Int = 20, private val clock: () -> Long = System::currentTimeMillis) {
    fun idFor(bytes: ByteArray): String                       // first 32 hex chars of SHA-256
    fun list(): List<RecentEntry>                             // newest lastOpenedAt first
    fun contains(id: String): Boolean
    fun save(id: String, document: Document, format: SourceFormat): RecentEntry   // new entry, or refresh an existing one keeping addedAt and anchor; evicts the oldest beyond maxEntries
    fun load(id: String): Document?                           // null if missing or corrupt (a dead entry is removed)
    fun entry(id: String): RecentEntry?
    fun touch(id: String)                                     // lastOpenedAt = now
    fun updateAnchor(id: String, block: Int, offsetPx: Int)
    fun delete(id: String)
}
```
Files: `dir/recents.json` (a list of `RecentEntry`), `dir/docs/<id>.json` (`DocumentCodec`). All methods `@Synchronized`.

- [ ] **Step 1: Write the failing tests** (use `createTempDirectory`; an injectable counter as the clock):

```kotlin
@Test fun `JsonFile returns the default for a missing or corrupt file`() {                              // Review Focus #3
    val f = File(tmp, "s.json"); assertEquals(ReaderSettings(), SettingsStore(f).load())
    f.writeText("{\"fontSizeSp\": 2"); assertEquals(ReaderSettings(), SettingsStore(f).load())          // truncated
}
@Test fun `atomic writes leave no temp files`()
@Test fun `settings are coerced into range on load and save`() {
    SettingsStore(f).save(ReaderSettings(fontSizeSp = 999f, lineHeight = 0.1f, paragraphSpacingEm = -3f, marginDp = 500))
    assertEquals(ReaderSettings(fontSizeSp = 48f, lineHeight = 1.0f, paragraphSpacingEm = 0f, marginDp = 48), SettingsStore(f).load())
}
@Test fun `unknown json keys are ignored`()
@Test fun `corrupt recents and corrupt document files are survivable`() {                               // Review Focus #3
    File(dir, "recents.json").writeText("[{\"id\":"); assertTrue(LibraryStore(dir).list().isEmpty())
    // save a doc, truncate docs/<id>.json -> load(id) == null and entry(id) == null afterwards
}
@Test fun `idFor is stable and content based`()
@Test fun `save then load round trips and list is newest-opened first`()
@Test fun `saving an existing id keeps its anchor and addedAt`()                                         // Review Focus #4
@Test fun `updateAnchor persists across a new LibraryStore instance and progress is derived`()           // Review Focus #4: anchor(5, 40) with blockCount 11 -> progress 0.5f
@Test fun `the 21st entry evicts the least recently opened and deletes its file`()
@Test fun `delete removes entry and file`()
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :core:test --tests "app.verso.core.store.*"` Expected: FAIL.
- [ ] **Step 3: Implement `AtomicFile`/`JsonFile`.** `AtomicFile.writeText` writes `<name>.tmp` in the same directory then `Files.move(tmp, file, REPLACE_EXISTING, ATOMIC_MOVE)` (fallback to a plain replace move when atomic is unsupported); creates parent directories. `JsonFile` uses `Json { ignoreUnknownKeys = true; encodeDefaults = true }`; `read()` catches every exception from reading or decoding and returns `default()`.
- [ ] **Step 4: Implement `ReaderSettings`, `SettingsLimits`, `coerced()`, `SettingsStore`.** `coerced()` clamps each numeric field to `SettingsLimits` and snaps `fontSizeSp` and `marginDp` to their steps (1 and 4); `lineHeight` and `paragraphSpacingEm` snap to 0.05 and 0.1.
- [ ] **Step 5: Implement `RecentEntry`, `progress`, `LibraryStore`.** `format` stores `SourceFormat.name`. `save` writes the document first and the index second so a kill never leaves an entry without a file; `list()` and `load()` tolerate and clean up inconsistencies.
- [ ] **Step 6: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 7: Commit**

```bash
git add core && git commit -m "feat(core): settings and library persistence"
```

---

### Task 11: Reader rendering (palette, styles, blocks, `ReaderScreen`)

**Files:**
- Create: `app/src/main/kotlin/app/verso/reader/ui/reader/ReaderPalette.kt`, `ReaderTextStyles.kt`, `InlineRender.kt`, `Spacing.kt`, `BlockViews.kt`, `ReaderScreen.kt`
- Test: `app/src/test/kotlin/app/verso/reader/ui/reader/ReaderStylesTest.kt` (pure JVM), `ReaderScreenTest.kt` (Robolectric Compose)

**Interfaces:**
- Consumes: `Block` family, `InlineText`, `Span`, `SpanKind`, `Document` (Task 2); `ReaderSettings`, `ThemeChoice`, `FontChoice`, `ReadAlign` (Task 10).
- Produces (package `app.verso.reader.ui.reader`):

```kotlin
data class ReaderPalette(val background: Color, val text: Color, val muted: Color, val link: Color, val codeBackground: Color, val rule: Color, val isDark: Boolean)
fun paletteFor(theme: ThemeChoice, systemDark: Boolean): ReaderPalette                 // hex values from Global Constraints
fun ReaderSettings.bodyStyle(palette: ReaderPalette): TextStyle
fun ReaderSettings.headingStyle(level: Int, palette: ReaderPalette): TextStyle
fun ReaderSettings.monoStyle(palette: ReaderPalette): TextStyle
fun InlineText.toAnnotatedString(palette: ReaderPalette): AnnotatedString
fun gapBeforeEm(prev: Block?, cur: Block, paragraphSpacingEm: Float): Float
@Composable fun BlockView(block: Block, settings: ReaderSettings, palette: ReaderPalette, modifier: Modifier = Modifier)
@Composable fun ReaderScreen(
    document: Document, settings: ReaderSettings, palette: ReaderPalette,
    initialBlock: Int, initialOffsetPx: Int, chromeVisible: Boolean,
    onToggleChrome: () -> Unit, onAnchor: (block: Int, offsetPx: Int) -> Unit,
    onBack: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier)
```
`ReaderScreen` test tag on the list: `"reader_list"`. `onAnchor` fires 400 ms after scrolling settles and once more when the composable leaves composition.

- [ ] **Step 1: Write the failing JVM tests** (`ReaderStylesTest`):

```kotlin
@Test fun `palettes match the spec and meet contrast`() {
    assertEquals(0xFF1B1B1D.toInt(), paletteFor(ThemeChoice.Dark, false).background.toArgb())
    assertEquals(0xFF000000.toInt(), paletteFor(ThemeChoice.Black, false).background.toArgb())
    assertEquals(paletteFor(ThemeChoice.Dark, false), paletteFor(ThemeChoice.System, true))
    assertEquals(paletteFor(ThemeChoice.Light, false), paletteFor(ThemeChoice.System, false))
    for (t in listOf(Light, Sepia, Dark, Black)) { val p = paletteFor(t, false)
        assertTrue(contrast(p.text, p.background) >= 7.0); assertTrue(contrast(p.muted, p.background) >= 4.5); assertTrue(contrast(p.link, p.background) >= 4.5) }
}   // contrast() = WCAG relative-luminance ratio, written in the test
@Test fun `body style follows settings`() {
    val s = ReaderSettings(fontSizeSp = 24f, lineHeight = 1.5f, font = FontChoice.Sans)
    val st = s.bodyStyle(paletteFor(ThemeChoice.Light, false))
    assertEquals(24f, st.fontSize.value); assertEquals(36f, st.lineHeight.value); assertEquals(FontFamily.SansSerif, st.fontFamily)
    assertEquals(TextDirection.Content, st.textDirection)                      // RTL guard
    assertEquals(TextAlign.Start, st.textAlign)
    assertEquals(TextAlign.Justify, s.copy(align = ReadAlign.Justify).bodyStyle(p).textAlign)
    assertEquals(Hyphens.Auto, s.copy(align = ReadAlign.Justify).bodyStyle(p).hyphens)
}
@Test fun `heading sizes follow the scale`()                                   // H1 1.6x, H2 1.4x, H3 1.2x, H4+ 1.0x, all Bold
@Test fun `mono style is 0_92 of body and monospace`()
@Test fun `inline spans map to Compose styles`()                               // Bold->FontWeight.Bold, Italic->FontStyle.Italic, Code and Math->monospace + codeBackground, Strike->LineThrough, Link->underline + link color + one LinkAnnotation.Url(href)
@Test fun `gaps follow the spec table`() {
    assertEquals(0f, gapBeforeEm(null, p, 0.8f)); assertEquals(0.25f, gapBeforeEm(li, li, 0.8f)); assertEquals(0.4f, gapBeforeEm(h, p, 0.8f))
    assertEquals(1.4f, gapBeforeEm(p, h, 0.8f), 1e-6f); assertEquals(0.8f, gapBeforeEm(p, p, 0.8f))
}
```
Robolectric tests (`@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [35])`, `createComposeRule()`; NATIVE graphics mode is required so text has real measured heights):

```kotlin
@Test fun `renders every block kind`()          // paragraph, heading, list item (marker "•" present), quote, code, math, table ("Name: Apple" substring), image ("[Image: fig]"), divider
@Test fun `restores the initial anchor`()       // 200 paragraphs "P0".."P199", initialBlock = 150 -> onNodeWithText("P150").assertIsDisplayed(); onNodeWithText("P0").assertDoesNotExist()
@Test fun `only visible blocks are composed`()  // 10_000 paragraphs "P<i>" -> fewer than 60 nodes with text starting "P"
@Test fun `tap toggles chrome and chrome shows title controls`()   // chromeVisible=true shows content descriptions "Back" and "Reading settings"; tapping the list calls onToggleChrome once
@Test fun `scrolling reports the anchor after settling`()          // performScrollToIndex(50); mainClock.advanceTimeBy(600) -> onAnchor called with block >= 50
@Test fun `rtl paragraph renders`()             // Paragraph("שלום עולם") is displayed
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :app:testDebugUnitTest --tests "app.verso.reader.ui.reader.*"` Expected: FAIL.
- [ ] **Step 3: Implement `ReaderPalette.kt`.** `paletteFor` with the exact hex values in Global Constraints (`isDark` true for Dark and Black).
- [ ] **Step 4: Implement `ReaderTextStyles.kt`.** `bodyStyle`: `fontSize = fontSizeSp.sp`, `lineHeight = (fontSizeSp * lineHeight).sp`, `fontFamily` per `FontChoice`, `color = palette.text`, `textAlign` per `ReadAlign`, `textDirection = TextDirection.Content`, `lineBreak = LineBreak.Paragraph`, `hyphens = Hyphens.Auto` only when justified (else `Hyphens.None`). `headingStyle` = body style scaled per the typography scale, `FontWeight.Bold`, line height scaled the same. `monoStyle` = body style with `FontFamily.Monospace` and size x 0.92.
- [ ] **Step 5: Implement `InlineRender.kt` (`toAnnotatedString`)** mapping each `Span` as listed in the test; links add `LinkAnnotation.Url(href, TextLinkStyles(SpanStyle(color = link, textDecoration = Underline)))`.
- [ ] **Step 6: Implement `Spacing.kt` (`gapBeforeEm`)** per the Product Spec gap rules.
- [ ] **Step 7: Implement `BlockViews.kt` (`BlockView`).** Follows "Block rendering rules" in the Product Spec exactly. All `Text` use the styles above; `ListItem` = `Row` with a fixed-width marker `Text` (1.5em) then the text; `CodeBlock`/`MathBlock` = `Text` with `monoStyle` inside a `Box` with `codeBackground` background and 8dp padding (soft wrap, no horizontal scroll); `TableBlock` = per row one `Text(buildAnnotatedString { for each cell: bold "Header: " then the cell, separated by "\n" })` with a thin `rule` divider between rows.
- [ ] **Step 8: Implement `ReaderScreen.kt`.** `Box` with `palette.background`; `LazyColumn(state = rememberLazyListState(initialBlock, initialOffsetPx), contentPadding = PaddingValues(start = marginDp.dp, end = marginDp.dp, top = 24.dp, bottom = 96.dp))` with `itemsIndexed(document.blocks, key = { i, _ -> i }, contentType = { _, b -> b::class })`; each item applies `Modifier.padding(top = (gapBeforeEm * fontSizeSp).dp)`; tag `"reader_list"`; `Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onToggleChrome() }) }` on the list. A 2dp progress bar at the top edge (width fraction from `derivedStateOf` of `(firstVisibleItemIndex + fraction) / blockCount`). When `chromeVisible`, a top bar with an `IconButton` (`Icons.AutoMirrored.Filled.ArrowBack`, description `Back`), the title (single line, ellipsis) and an `IconButton` (`Icons.Filled.Settings`, description `Reading settings`), on a 92%-opaque `palette.background` with `statusBarsPadding()`. Anchor reporting: `snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }.debounce(400)` collected in a `LaunchedEffect`, plus a `DisposableEffect` whose `onDispose` reports the latest value. Keep `ReaderScreen` free of settings UI.
- [ ] **Step 9: Run to verify pass.** Same command. Expected: PASS. If Robolectric cannot load SDK 35 jars in the sandbox, try `sdk = [34]` once and note it in `AGENTS.md`.
- [ ] **Step 10: Commit**

```bash
git add app && git commit -m "feat(app): reader rendering with palettes, typography and virtualized list"
```

---

### Task 12: Settings panel

**Files:**
- Create: `app/src/main/kotlin/app/verso/reader/ui/settings/SettingsPanel.kt`
- Test: `app/src/test/kotlin/app/verso/reader/ui/settings/SettingsPanelTest.kt`

**Interfaces:**
- Consumes: `ReaderSettings`, `ThemeChoice`, `FontChoice`, `ReadAlign`, `SettingsLimits`, `coerced()` (Task 10).
- Produces: `@Composable fun SettingsPanel(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit, modifier: Modifier = Modifier)`; test tag `"settings_panel"`. Non-modal: it never dims or blocks the reader behind it; its own height is capped at 45% of the screen and its content scrolls.

- [ ] **Step 1: Write the failing tests** (Robolectric Compose as in Task 11; `var last = ReaderSettings()`, `onChange = { last = it }`):

```kotlin
@Test fun `shows every label`()                                       // all labels and chip texts from Global Constraints exist
@Test fun `theme and font and align chips emit the matching setting`() {
    rule.onNodeWithText("Sepia").performClick();    assertEquals(ThemeChoice.Sepia, last.theme)
    rule.onNodeWithText("Mono").performClick();     assertEquals(FontChoice.Mono, last.font)
    rule.onNodeWithText("Justified").performClick(); assertEquals(ReadAlign.Justify, last.align)
}
@Test fun `sliders emit snapped in-range values`() {
    rule.onNodeWithContentDescription("Size").performSemanticsAction(SemanticsActions.SetProgress) { it(30f) };               assertEquals(30f, last.fontSizeSp)
    rule.onNodeWithContentDescription("Line spacing").performSemanticsAction(SemanticsActions.SetProgress) { it(2.0f) };      assertEquals(2.0f, last.lineHeight, 1e-4f)
    rule.onNodeWithContentDescription("Paragraph spacing").performSemanticsAction(SemanticsActions.SetProgress) { it(1.0f) }; assertEquals(1.0f, last.paragraphSpacingEm, 1e-4f)
    rule.onNodeWithContentDescription("Margins").performSemanticsAction(SemanticsActions.SetProgress) { it(32f) };            assertEquals(32, last.marginDp)
}
@Test fun `keep screen on switch toggles`()
@Test fun `reset restores defaults`()                                  // start from non-default settings, click "Reset" -> ReaderSettings()
@Test fun `current values are displayed`()                              // Size label shows "20" for defaults (text "Size 20" or a sibling value text; assert the number is visible)
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :app:testDebugUnitTest --tests "app.verso.reader.ui.settings.SettingsPanelTest"` Expected: FAIL.
- [ ] **Step 3: Implement `SettingsPanel`.** A `Surface` (rounded top corners, 8dp tonal elevation) with a vertically scrollable `Column`: rows of `FilterChip`s for Theme, Font and Align; `Slider`s for Size (`SettingsLimits.FONT_SIZE`, steps 35), Line spacing (steps 27), Paragraph spacing (steps 19), Margins (0..48, steps 11), each with `Modifier.semantics { contentDescription = "<label>" }`, a text label and the current value; a `Switch` row for Keep screen on; a `TextButton("Reset")`. Every change calls `onChange(newSettings.coerced())`. Use Material 3 colors from `MaterialTheme` (the reader palette is applied by the caller via `MaterialTheme` colors in Task 14).
- [ ] **Step 4: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add app && git commit -m "feat(app): reading settings panel"
```

---

### Task 13: `AppViewModel` (screen state machine)

**Files:**
- Create: `app/src/main/kotlin/app/verso/reader/AppViewModel.kt`
- Test: `app/src/test/kotlin/app/verso/reader/AppViewModelTest.kt`

**Interfaces:**
- Consumes: `Importer`, `ImportResult`, `DocumentSource`, `DocumentImporter`, `SourceFormat` (Task 9); `LibraryStore`, `SettingsStore`, `RecentEntry`, `ReaderSettings`, `coerced()` (Task 10); `Document`, `ImportError` (Task 2).
- Produces (package `app.verso.reader`):

```kotlin
class AppEnv(val library: LibraryStore, val settings: SettingsStore, val importer: Importer)
sealed interface Screen {
    data object Home : Screen
    data class Importing(val name: String?) : Screen
    data class Reading(val id: String, val title: String, val document: Document, val anchorBlock: Int, val anchorOffsetPx: Int) : Screen
    data class Failed(val message: String) : Screen
}
class AppViewModel(private val env: AppEnv, private val work: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
    val screen: StateFlow<Screen>                    // initial Home
    val settings: StateFlow<ReaderSettings>          // initial env.settings.load()
    val recents: StateFlow<List<RecentEntry>>        // initial env.library.list()
    fun import(source: DocumentSource)               // dedupes by LibraryStore.idFor(bytes): known id -> open(id)
    fun pasteText(text: String)                      // blank -> Failed("Clipboard is empty."), else import as text/plain
    fun open(id: String)                             // unknown/corrupt -> Failed("That document is no longer available.")
    fun closeReader(); fun dismissError(); fun cancelImport()      // all return to Home
    fun showFailure(message: String)
    fun updateSettings(settings: ReaderSettings)    // coerced, stored, published
    fun onAnchor(block: Int, offsetPx: Int)         // persists for the document being read; ignored unless Reading
    fun delete(id: String)
    companion object { fun factory(env: AppEnv): ViewModelProvider.Factory }
}
```

- [ ] **Step 1: Write the failing tests** (JVM; `Dispatchers.setMain(StandardTestDispatcher())`, real `LibraryStore`/`SettingsStore` in a temp dir, real `DocumentImporter()` for text, a fake `Importer` driven by `CompletableDeferred` for cancellation):

```kotlin
@Test fun `starts on Home with stored settings and recents`()
@Test fun `importing text goes Importing then Reading and records a recent`() {
    vm.import(DocumentSource(null, "text/plain", "# Title\n\nHello".toByteArray())); advanceUntilIdle()
    val s = vm.screen.value as Screen.Reading; assertEquals("Title", s.title); assertEquals(0, s.anchorBlock); assertEquals(1, vm.recents.value.size)
}
@Test fun `importing the same bytes twice reopens the existing entry at its saved position`() {                  // Review Focus #4
    import; vm.onAnchor(1, 40); vm.closeReader(); import again -> recents.size == 1 and Reading anchor == (1, 40)
}
@Test fun `a failed import shows the exact message and OK returns Home`() {
    vm.import(DocumentSource("a.txt", null, "   ".toByteArray())); advanceUntilIdle()
    assertEquals(Screen.Failed("No readable text was found in this file."), vm.screen.value); vm.dismissError(); assertEquals(Screen.Home, vm.screen.value)
}
@Test fun `open of an unknown id fails with the exact message`()
@Test fun `anchor survives a new view model over the same directory`() {                                         // Review Focus #4
    import; vm.onAnchor(5, 10); val vm2 = AppViewModel(sameEnvDir, ...); vm2.open(id) -> Reading anchor (5, 10)
}
@Test fun `settings are coerced persisted and published`() { vm.updateSettings(ReaderSettings(fontSizeSp = 999f)); assertEquals(48f, vm.settings.value.fontSizeSp); new VM loads 48f }
@Test fun `cancelImport during Importing returns Home and discards the late result`()
@Test fun `a new import while importing cancels the first`()
@Test fun `pasteText with blank text fails with Clipboard is empty`()
@Test fun `delete removes the recent and closeReader goes Home`()
@Test fun `onAnchor outside Reading is ignored`()
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :app:testDebugUnitTest --tests "app.verso.reader.AppViewModelTest"` Expected: FAIL.
- [ ] **Step 3: Implement `AppViewModel`.** `import`: cancel any running import job; set `Importing(source.name)`; in `viewModelScope.launch(work)` compute `id = library.idFor(bytes)`; if `library.contains(id)` -> open; else `env.importer.import(source)`, `library.save(id, document, format)`, then (back on Main) publish `Reading(id, title, document, 0, 0)` and refresh `recents`. `CancellationException` is rethrown; `ImportError` -> `Failed(error.userMessage)`; any other throwable -> `Failed(ImportError.Corrupt("unexpected", t).userMessage)`. `open` loads via `library.load`, calls `library.touch`, publishes `Reading` with the stored anchor. `onAnchor` -> `library.updateAnchor` and refreshes `recents` only when the screen is `Reading`. Heavy work never runs on Main.
- [ ] **Step 4: Run to verify pass.** Same command. Expected: PASS.
- [ ] **Step 5: Commit**

```bash
git add app && git commit -m "feat(app): AppViewModel screen state machine"
```

---

### Task 14: Android shell (manifest filters, intake, Home, `MainActivity`, system bars)

**Files:**
- Create: `app/src/main/kotlin/app/verso/reader/intake/IntentParser.kt`, `SourceReader.kt`, `ui/home/HomeScreen.kt`, `VersoApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/kotlin/app/verso/reader/MainActivity.kt` (replace the hello screen)
- Test: `app/src/test/kotlin/app/verso/reader/intake/IntentParserTest.kt`, `app/src/test/kotlin/app/verso/reader/ui/home/HomeScreenTest.kt`; `ManifestGuardTest` (Task 1) must still pass

**Interfaces:**
- Consumes: everything from Tasks 9 to 13 (`AppEnv`, `AppViewModel`, `Screen`, `ReaderScreen`, `SettingsPanel`, `paletteFor`, `DocumentImporter`, `LibraryStore`, `SettingsStore`, `RecentEntry.progress`, `DocumentSource`, `ImportError.TooLarge`).
- Produces:

```kotlin
sealed interface IntakeResult { data class Source(val source: DocumentSource) : IntakeResult; data class Failure(val message: String) : IntakeResult; data object Ignore : IntakeResult }
object IntentParser { fun parse(intent: Intent, resolver: ContentResolver): IntakeResult }        // blocking IO: call off the main thread
object SourceReader { fun readBytes(resolver: ContentResolver, uri: Uri, limit: Long): ByteArray?  }  // null when bigger than limit
@Composable fun HomeScreen(recents: List<RecentEntry>, palette: ReaderPalette, onOpen: (String) -> Unit, onDelete: (String) -> Unit, onOpenFile: () -> Unit, onPaste: () -> Unit)   // list tag "home_list"
class VersoApp : Application() { val env: AppEnv }    // lazily built: LibraryStore(filesDir/"library"), SettingsStore(filesDir/"settings.json"), DocumentImporter(pdfSource = null for now)
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// IntentParserTest (Robolectric; ShadowContentResolver.registerInputStream for content URIs)
@Test fun `ACTION_SEND text becomes a text source`()                              // EXTRA_TEXT "# Hi" -> Source(name=null, mime "text/plain", bytes == "# Hi")
@Test fun `ACTION_SEND with a stream reads bytes and uses the display name`()      // content://x/a.pdf with "%PDF-1.4" bytes -> Source(name "a.pdf", bytes)
@Test fun `ACTION_VIEW with data reads the uri`()
@Test fun `empty or blank shared text fails`() = assertEquals(IntakeResult.Failure("Nothing to read in the shared content."), ...)   // Review Focus #2
@Test fun `an unreadable stream fails without throwing`() {                       // Review Focus #2: uri not registered -> FileNotFoundException; and a resolver whose open throws SecurityException
    assertEquals(IntakeResult.Failure("Couldn't open the shared file."), ...)
}
@Test fun `a stream over the limit fails as too large`()                           // limit small in test via SourceReader; Failure("This file is larger than 64 MB.")
@Test fun `unrelated intents are ignored`()                                        // ACTION_MAIN -> Ignore

// HomeScreenTest (Robolectric Compose)
@Test fun `empty state shows the exact hint`()
@Test fun `entries show title format and percent and open or delete by id`()      // RecentEntry(progress 0.5) shows "50%"; click row -> onOpen(id); click "Remove from recents" -> onDelete(id)
@Test fun `buttons call back`()                                                    // "Open file", "Paste"
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :app:testDebugUnitTest --tests "app.verso.reader.intake.*" --tests "app.verso.reader.ui.home.*"` Expected: FAIL.
- [ ] **Step 3: Implement `SourceReader` and `IntentParser`.** `readBytes` streams from `resolver.openInputStream` with a hard cap (`limit + 1` bytes read -> return null). `parse`: `ACTION_SEND` -> `EXTRA_STREAM` URI (use the typed `getParcelableExtra(name, Uri::class.java)` on API 33+, the untyped form below) else `EXTRA_TEXT` (blank -> `Failure("Nothing to read in the shared content.")`; otherwise `Source(null, "text/plain", utf8 bytes)`); `ACTION_VIEW` -> `intent.data`; everything else -> `Ignore`. Display name from `OpenableColumns.DISPLAY_NAME`, falling back to `uri.lastPathSegment`; MIME from `intent.type` or `resolver.getType(uri)`. Catch `SecurityException`, `FileNotFoundException`, `IOException` -> `Failure("Couldn't open the shared file.")`; over the limit (`DocumentImporter.MAX_SOURCE_BYTES`) -> `Failure("This file is larger than 64 MB.")`.
- [ ] **Step 4: Update the manifest.** `MainActivity`: `android:exported="true"`, `android:launchMode="singleTask"`, `android:windowSoftInputMode="adjustNothing"`; `android:name=".VersoApp"` on `<application>`. Intent filters: MAIN/LAUNCHER; `SEND` + DEFAULT with `mimeType` `text/*`, `application/pdf`, `application/epub+zip` (three data elements); `VIEW` + DEFAULT + BROWSABLE with schemes `content` and `file` and mime types `application/pdf`, `application/epub+zip`, `text/plain`, `text/markdown`, `text/x-markdown`, `text/html`; one more `VIEW` filter with schemes `content` and `file`, `mimeType="*/*"`, `host="*"` and `pathPattern` entries `.*\\.md`, `.*\\.epub`, `.*\\.markdown` (best effort; the `Open file` button is the guaranteed route). No permissions.
- [ ] **Step 5: Implement `HomeScreen`.** Background and text colors from `palette`; header `Verso`; a row with two `Button`s (`Open file`, `Paste`); a `LazyColumn` (tag `home_list`) of rows (title, `"<format> · <percent>%"`, trailing `IconButton(Icons.Filled.Delete, contentDescription = "Remove from recents")`); empty state text from Global Constraints.
- [ ] **Step 6: Implement `VersoApp`, then `MainActivity`.** `VersoApp.env` is built lazily from `filesDir`. `MainActivity`: `enableEdgeToEdge()`; `viewModels { AppViewModel.factory((application as VersoApp).env) }`; `setContent` collects `screen`, `settings`, `recents` with `collectAsStateWithLifecycle`; theme: `paletteFor(settings.theme, isSystemInDarkTheme())`, wrapped so `MaterialTheme.colorScheme` uses the palette (`background`, `onBackground`, `surface`, `onSurface`, `primary = link`); `Screen.Home` -> `HomeScreen`; `Importing` -> centered `CircularProgressIndicator` + `Preparing…`; `Reading` -> `ReaderScreen` plus an `AnimatedVisibility` `SettingsPanel` aligned bottom (shown when the settings button was pressed; hiding the chrome also hides the panel); `Failed` -> title `Can't open this`, message, `OK` button. State kept with `rememberSaveable`: `chromeVisible` (default true), `settingsOpen`. `BackHandler`: settings open -> close; `Reading` -> `closeReader()`; `Importing` -> `cancelImport()`; `Failed` -> `dismissError()`; `Home` -> default. Intake: `onCreate` (only when `savedInstanceState == null`) and `onNewIntent` call `lifecycleScope.launch(Dispatchers.IO) { when (val r = IntentParser.parse(intent, contentResolver)) { Source -> vm.import; Failure -> vm.showFailure; Ignore -> {} } }`. `Open file`: `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())` launched with `arrayOf("*/*")`, result read through `SourceReader.readBytes` on `Dispatchers.IO` and imported with the document's display name. `Paste`: read `ClipboardManager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()` and call `pasteText` (null -> `pasteText("")`). While `Reading`: `LocalView.current.keepScreenOn = settings.keepScreenOn`; when `chromeVisible` is false hide `WindowInsetsCompat.Type.systemBars()` via `WindowCompat.getInsetsController(window, view)` with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, otherwise show them; status and navigation bar icon contrast follows `palette.isDark` (`isAppearanceLightStatusBars = !isDark`).
- [ ] **Step 7: Run to verify pass.** Run: `./gradlew :app:testDebugUnitTest` Expected: PASS (includes `ManifestGuardTest`). Then `./gradlew :app:assembleRelease` Expected: BUILD SUCCESSFUL.
- [ ] **Step 8: Commit and push**

```bash
git add -A && git commit -m "feat(app): share-sheet intake, home screen and main activity"
git push origin main
```
Expected: CI green and `latest` updated.

- [ ] **Step 9 (HUMAN CHECKPOINT):** the controller asks the human to install the new `latest` APK and verify on the phone: (1) share text from a chat app (Markdown with a table and `$x^2$`) into Verso and read it; (2) open a `.md`, an `.epub` and an `.html` file via Open file; (3) change size, font, theme, spacing and margins and confirm they apply live and persist after restarting the app; (4) scroll, leave and reopen: the position is restored; (5) tap once to hide the bars and the chrome; (6) Back from the reader returns to Home. Report anything that looks wrong (screenshots welcome) before Task 15 starts.

---

### Task 15: PDF on Android (PdfBox-Android adapter), R8 rules, release hardening

**Files:**
- Create: `app/src/main/kotlin/app/verso/reader/pdf/PdfBoxPageSource.kt`
- Modify: `app/build.gradle.kts` (add `implementation(libs.pdfbox.android)`), `app/proguard-rules.pro`, `app/src/main/kotlin/app/verso/reader/VersoApp.kt`, `AGENTS.md`, `README.md`
- Test: `app/src/test/kotlin/app/verso/reader/pdf/PdfBoxPageSourceTest.kt`

**Interfaces:**
- Consumes: `PdfPageSource`, `PdfContent`, `PdfPage`, `TextRun` (Task 7); `ImportError` (Task 2); `DocumentImporter(pdfSource = ...)` (Task 9); `VersoApp.env` (Task 14).
- Produces: `class PdfBoxPageSource(context: Context) : PdfPageSource`.

- [ ] **Step 1: Write the failing test** (Robolectric; build a one-page PDF in the test with PdfBox-Android: `PDDocument`, `PDPage(PDRectangle.A4)`, `PDPageContentStream` with `PDType1Font.HELVETICA` writing "Hello PDF world" at (72, 700) and a rotated line, save to `ByteArrayOutputStream`):

```kotlin
@Test fun `extracts text runs with top-left coordinates`() {
    val content = PdfBoxPageSource(context).read(pdfBytes)
    val run = content.pages.single().runs.first { it.text.contains("Hello") }
    assertEquals(595f, content.pages.single().width, 1f)
    assertTrue(run.baseline in 130f..150f)                     // text drawn at y=700 from the bottom of an 842pt page -> about 142pt from the top
    assertTrue(run.fontSize in 11.5f..12.5f)
}
@Test fun `rotated text is skipped`()
@Test fun `a password protected pdf is Unsupported`() = assertFailsWith<ImportError.Unsupported> { ... }          // Review Focus #5
@Test fun `garbage bytes are Corrupt`() = assertFailsWith<ImportError.Corrupt> { PdfBoxPageSource(context).read(ByteArray(50) { 1 }) }
@Test fun `onPage is called once per page`()
```
**Fallback, one attempt only:** if PdfBox-Android cannot run under Robolectric (class loading or missing resource errors), mark the whole class `@Ignore("device-only: verified at the Task 15 human checkpoint")` and keep the test code; do not spend more than one fix iteration on it.

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew :app:testDebugUnitTest --tests "app.verso.reader.pdf.PdfBoxPageSourceTest"` Expected: FAIL (unresolved reference).
- [ ] **Step 3: Implement `PdfBoxPageSource.read`.** Initialize `PDFBoxResourceLoader.init(context)` exactly once (guarded by an `AtomicBoolean`) before the first use. Load with `PDDocument.load(bytes, MemoryUsageSetting.setupMixed(32L * 1024 * 1024))`; `InvalidPasswordException` -> `ImportError.Unsupported("password-protected PDF")`; any other `IOException`/`RuntimeException` -> `ImportError.Corrupt`. A `PDFTextStripper` subclass overrides `writeString(text: String, positions: List<TextPosition>)`: skip when `positions.first().dir != 0f` (rotated watermarks and margin stamps); emit one `TextRun(text.trim(), x = first.xDirAdj, baseline = first.yDirAdj, width = last.xDirAdj + last.widthDirAdj - first.xDirAdj, fontSize = first.fontSizeInPt, bold = first.font.name contains "Bold" | "Black" | "Heavy" (ignore case))` per call, skipping blank text; `sortByPosition = false`. Extract page by page (`startPage = endPage = p`) writing to a no-op `java.io.Writer`, then call `onPage()`. Page size from `page.cropBox` (swap width and height when `page.rotation` is 90 or 270). Title from `document.documentInformation?.title`. Close the document in `finally`.
- [ ] **Step 4: Wire it in.** In `VersoApp`, build the importer as `DocumentImporter(pdfSource = PdfBoxPageSource(this))`.
- [ ] **Step 5: R8 rules.** Run `./gradlew :app:assembleRelease`. If R8 fails on missing classes, append `-dontwarn` rules from `app/build/outputs/mapping/release/missing_rules.txt` to `proguard-rules.pro` (typically `com.gemalto.jp2.**` and optional `org.bouncycastle.**`), and add `-keep class com.tom_roush.pdfbox.** { *; }` only if the HUMAN CHECKPOINT shows a runtime crash. Record every rule added and the final APK size in the `AGENTS.md` Notes section.
- [ ] **Step 6: Run everything.** Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease` Expected: PASS and APK size under 12 MiB (the CI gate).
- [ ] **Step 7: Finish docs.** `README.md`: add "Supported files", "Install and update" (install from the `latest` release; later builds update in place because the signing key is fixed; optional Obtainium), and "Known limitations" (copy from the Product Spec). `AGENTS.md`: final notes.
- [ ] **Step 8: Commit and push**

```bash
git add -A && git commit -m "feat(app): PDF text extraction via PdfBox-Android, R8 rules and docs"
git push origin main
```
Expected: CI green; `latest` release updated.

- [ ] **Step 9 (HUMAN CHECKPOINT):** the controller asks the human to test four PDFs from the Open file button and report: (1) a single-column textbook chapter (reads as clean paragraphs, no running headers or page numbers in the text); (2) a two-column arXiv-style paper (columns read in order; equations will be plain text, which is expected); (3) a scanned PDF (shows `This PDF has no selectable text (it looks scanned). OCR isn't supported.`); (4) a password-protected PDF (shows `Unsupported file: password-protected PDF`). Also open a 300+ page PDF and report how long it takes. Findings become fix tasks for `PdfLayout`/`PdfAssembler` thresholds (the constants are named for exactly this).
- [ ] **Step 10: Final review and tag.** Dispatch the final whole-implementation reviewer against the Spec Coverage table below, fix any gap, then: `git tag v0.1.0 && git push origin v0.1.0`.

---

## Spec Coverage

| Spec requirement | Task(s) |
|---|---|
| Read plain text, Markdown/LLM output (math as source), HTML | 3, 4, 5, 9 |
| Read EPUB (DRM refused) | 6, 9 |
| Read PDF text layer (columns, headers/footers, hyphens; scanned/protected refused) | 7, 8, 15 |
| Share sheet, Open with, Open file, Paste | 14 |
| One scrollable column, virtualized, no zoom, no horizontal scroll | 11 |
| Size, font, spacing, margins, alignment, keep-screen-on | 10, 11, 12 |
| Themes (System, Light, Sepia, Dark, Black) | 10, 11, 12, 14 |
| Position saved per document, progress bar, dedupe, recents | 10, 11, 13 |
| No permissions, no network | 1 (guard test), 14 |
| Fast and lightweight (off-main import, block splitting, APK size gate, R8) | 1, 3, 9, 11, 15 |
| Errors shown as readable messages | 2, 9, 13, 14, 15 |
| Signed APK delivered from GitHub, in-place updates | 1, 15 |
| Review Focus #1 to #5 | 3 and 9; 14; 10; 10 and 13; 8 and 15 |
