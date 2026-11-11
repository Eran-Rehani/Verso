# Verso

**An offline home for books, papers, and Markdown.**

Verso is an Android reader that turns the documents you already have — PDF, EPUB, Markdown, HTML, plain text — into continuous, comfortable text that fits the screen in your hand. Adjust the text to the screen and read at your own pace.

Everything happens on the device. Reading does not require a connection or an account.

[![Android 8.0+ (API 26)](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin + Compose](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)](#the-reading-experience)
[![Offline by design](https://img.shields.io/badge/Offline-no%20INTERNET%20permission-4B5563)](#privacy)

## The reading experience

### Reflow instead of zoom

PDFs are rebuilt as text: paragraphs, headings, and blocks that wrap to your screen width, at the size you actually want to read. Zoom controls, panning, and "fit to width" stop being part of your evening.

### One library, every format

Drop in a file, share it from another app, or paste text straight into Verso. The library keeps everything together with reading progress, and you can search by title, filter by format, and sort by recent, title, or date added.

### Reading that adapts to you

Five themes — System, Light, Sepia, Dark, Black. Serif, sans, or mono. Font size from 12 to 48sp, adjustable line height, paragraph spacing, and margins, plus start-aligned or justified text. Keep-screen-on is one tap away, because the page shouldn't go dark mid-sentence.

### A library that keeps your books

Your full library stays available while Home shows the 20 most recently opened documents. Adding another book no longer discards an older one. Reading progress is saved per document, and missing or damaged files remain visible for repair.

Imports can be cancelled or replaced with another file. Files up to 64 MiB are supported; EPUB archives have additional expansion limits. Documents are imported once and stay available offline.

## What you can read today

| Format | How it comes in | What you get |
| --- | --- | --- |
| **Markdown** | File, share sheet, clipboard | Reflowed headings, lists, quotes, code, GFM tables, and inline styling |
| **HTML** | File, share sheet | Main article text, cleaned of page chrome and navigation |
| **Plain text** | File, share sheet, clipboard | Reflowed paragraphs with automatic encoding detection |
| **EPUB** | File, share sheet | Chapter text in reading order |
| **PDF** | File, share sheet | Extracted text reflowed into readable blocks |

Text-based PDFs work well. Scanned PDFs — pages that are only images — are not supported yet. If a PDF has no text layer, there is nothing to reflow.

Math is the other current limit: Markdown containing LaTeX shows the source inline instead of typeset equations. That is being addressed (see below).

## In progress: the next upgrade

The reader upgrade is underway. **The following reading features are still in progress**, not shipped behavior:

- **Native math rendering** with RaTeX: inline and display equations, plus a standalone `.tex` reading subset. No macro execution, no arbitrary compilation, no external resources.
- **Original-page PDF mode**, including scanned pages, for documents where layout is the point.
- **Document search, table of contents, and bookmarks** for long books and papers.
- **Per-block selection and copy**, so you can take a paragraph or equation with you.
- **Better fidelity**: aligned tables, a copy action for code blocks, and images from EPUBs.

Build, library recovery, and cancellable intake are implemented and covered by automated checks. The reader features above remain planned until their implementation and verification are complete.

### The math we're aiming for

```markdown
When $a \ne 0$, the quadratic $ax^2 + bx + c = 0$ has roots

$$x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}$$
```

In current builds this appears as TeX source. In the upgrade it will render as inline and display equations, entirely on-device.

## Getting started

**Build it yourself.** Verso is a standard Gradle Android project; build and run instructions live in [docs/BUILDING.md](docs/BUILDING.md).

**Install it.** Sideload the debug APK produced by your build — no Play Store account, no sign-in, no setup wizard.

**Start reading.** Open Verso and pick a file, share a document to it from Files or your browser, or paste a block of Markdown or text into the library.

<a id="requirements"></a>
### Requirements

- Android 8.0 (API 26) or newer
- A build of the project from this repository; APKs attached to older commits are baseline debug artifacts, not an upgraded release

<a id="privacy"></a>
## Privacy

Verso does not request `INTERNET`. Documents stay in app-private storage, and Android backup is disabled. Import files through the Android file picker or share sheet.

## Status

The reader upgrade is in progress. [Automated checks cover the completed foundation](docs/verification/foundation.md); Android device checks remain pending. Text reflow across five formats works; math rendering, scanned PDFs, in-document search, and selection are the upgrade in progress. Expect the README to change as those pieces are verified, not before.

## Feedback

Bugs, format quirks, and requests are welcome in [GitHub Issues](https://github.com/Eran-Rehani/Verso/issues). A sample file that breaks reflow is the most useful bug report you can send.

## License

No LICENSE file has been added to this repository.
