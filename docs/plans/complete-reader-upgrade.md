# Complete reader upgrade

Source of intent: user-approved implementation plan supplied 2026-11-11.

## Goal

Reliable offline Kotlin/Compose/Room reader for academic documents, EPUBs and
Markdown, preserving application identity, API 26 support and existing data.

## Constraints

No WebView, accounts, telemetry, reading-time network downloads, arbitrary TeX
execution, destructive migration, force push, release tags or GitHub Releases.
Keep signing identity outside Git. Selection is per block/cell. PDF reflow does
not infer LaTeX. Preserve source and unsupported constructs visibly.

## Ordered tasks

1. Restore Gradle 9.3.1 wrapper; JDK 21 and exact SDK setup; baseline unit tests,
   lint and debug APK; remove verified unused network dependencies; add CI.
2. Consolidate metadata/positions/availability in Room; explicit schema migrations;
   lossless restartable legacy reconciliation with backups; retain all books,
   including corrupt/missing payload records; stage and reconcile file writes.
3. One cancellable import coordinator; IO off Main; stale-completion rejection;
   64 MiB sources, 256 MiB expanded ZIP, 32 MiB entries, 10,000 entries;
   safe resource paths/dimensions and specific failures.
4. Versioned document and shared reflow/cell/PDF locations; deterministic block
   IDs; preserve source targets/outline/page ranges; normalization offset maps;
   stale unresolved locations remain visible.
5. Gate RaTeX Android 0.1.15 artifact/API/ABIs/16 KB alignment, then implement
   native inline/display rendering with bundled fonts, CommonMark extensions,
   bounded native work/cache, source fallback/copy/accessibility. Test delimiters,
   escapes, currency, code, RTL, links, selection and font scaling.
6. Standalone TeX reading subset: metadata, headings, text styles, lists, quotes,
   verbatim, simple tables, labels/references, math environments/tags. Bounded
   scanner; no includes, macro expansion, packages or execution. Preserve unknowns.
7. Retain originals; PdfRenderer original/reflow modes, zoom, page navigation,
   independent positions, bounded cache and serialized/closed resources. Scans
   support original mode; retain legacy reflow and matching-source attachment.
8. Shared resolver for contents, links/history, literal search and Room bookmarks;
   canonical UTF-16 offsets, table/code/math search; explicit identity in position
   events; flush on Back/lifecycle stop. EPUB chapter-relative fragments/nav/NCX,
   supplemental footnotes and missing-spine diagnostics.
9. Aligned scrollable tables with linear accessibility/copy; exact code whitespace
   and copy; embedded EPUB images/alt text and bounded decoding; block/cell
   selection, safe links, RTL/language/heading/focus/large-text semantics. Home
   and Library use the same repository; preserve library search/filter/sort.
10. README, actual screenshots, build/install/test/offline/math/TeX/migration
    documentation, contributor guide, notices, changelog and prioritized roadmap.
    Integrated review/tests/device/performance checks; commit, fetch/integrate,
    normal push to origin/main; verify remote HEAD and Actions.

## Done when

`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` and
`./gradlew :app:connectedDebugAndroidTest` pass; regression coverage includes
legacy disagreements/corruption/interruption/25+ books, cancellation/replacement,
durable navigation, math/parser edge cases, TeX preservation and malformed input,
EPUB fragment/resource/spine cases, text/scanned/damaged/protected PDFs and cleanup.
Record actual offline/theme/RTL/TalkBack/selection/font/ABI/device results and
cold-start/open/import/scroll/peak-memory comparisons on the same device. Native
math must render; existing data must survive; screenshots must match the app;
verified final commit must be on GitHub. Unavailable checks are never passes.

Follow-ups (outside this release): annotations/export, collections/batch import,
backup/restore, OCR, TTS, multi-file TeX, optional sync, richer MathML/EPUB layout.
