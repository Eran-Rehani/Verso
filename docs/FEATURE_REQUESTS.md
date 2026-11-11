# Verso — Feature Requests, Performance Audit & UI/UX Proposals

This document tracks proposed feature requests (`FR-*`), UI/UX enhancements (`UX-*`), and performance/battery optimizations (`PERF-*`) for the Verso Android reader.

---

## 1. Feature Requests (`FR-*`)

### FR-01: Inline Text Highlights, Margin Notes & Markdown Export
- **Summary**: Allow users to long-press and select text ranges within any `Paragraph`, `Quote`, or `ListItem` block to apply multi-color highlights (Amber, Sage, Sky, Coral) and attach inline margin notes.
- **User Value**: Essential for students, researchers, and book readers who synthesize notes from technical papers, textbooks, and EPUBs.
- **Proposed Architecture**:
  - Add `AnnotationEntity` in Room (`bookId`, `blockIndex`, `charStart`, `charEnd`, `color`, `note`, `createdAt`) with foreign key cascade on `BookEntity`.
  - Render highlight spans inside `rememberRichInlineText` alongside search and math spans.
  - Add an **Export Notes as Markdown (`.md`)** action in the Bookmarks & Notes sidebar that generates a clean Markdown summary with blockquotes, page numbers, section headers, and user commentary.

### FR-02: Offline Text-to-Speech (TTS) Read-Aloud with Math Verbalization
- **Summary**: Integrate Android's native on-device `TextToSpeech` engine with block-by-block sentence tracking, auto-scrolling, playback speed controls (`0.75x` – `2.5x`), and sleep timer.
- **User Value**: Enables hands-free listening during commutes or eyes-rest sessions while preserving Verso's strict zero-network, offline-first privacy guarantee.
- **Technical Highlight**: Leverage `LatexParser.toReadablePlainText(latex)` to verbalize mathematical expressions naturally (e.g., reading `\sum_{n=1}^{\infty} \frac{1}{n^s}` as spoken mathematical notation rather than raw LaTeX backslashes).

### FR-03: Collections, Tags, Reading Status Filters & Batch Folder Import
- **Summary**: Let users organize their Library with custom tags/collections (e.g., *Physics*, *Papers*, *Fiction*, *To Read*, *Completed*) and import an entire folder of `.md`, `.tex`, `.epub`, or `.pdf` files at once via `ActivityResultContracts.OpenDocumentTree`.
- **User Value**: Scales the Library screen from dozens to hundreds of documents without clutter.

### FR-04: Cross-Document Library Full-Text Search
- **Summary**: Extend `DocumentSearcher` to search across all imported documents in the Library (or within a filtered tag/collection), returning grouped matches by book and chapter.
- **User Value**: Turns Verso into a personal offline knowledge base where users can find a theorem, quote, or code snippet across all their saved papers and books.

### FR-05: Custom `.tex` Preamble Macro Expansion (`\newcommand` / `\DeclareMathOperator`)
- **Summary**: Parse document-level `\newcommand{\R}{\mathbb{R}}`, `\def`, and `\DeclareMathOperator` definitions in `.tex` and `.md` preambles and expand them automatically before `LatexParser` AST construction.
- **User Value**: Academic `.tex` manuscripts from arXiv heavily rely on author-defined shorthand macros; supporting preamble macro expansion makes raw `.tex` papers render out-of-the-box without manual editing.

### FR-06: Embedded Offline Image & SVG Figure Rendering for EPUB & PDF
- **Summary**: Extract embedded raster images and SVG diagrams from EPUB archives and PDF pages into the book's sandboxed storage directory (`filesDir/books/<id>/media/`) and render them in `ImageBlock` with pinch-to-zoom lightbox inspection and dark-mode contrast inversion toggle.
- **User Value**: Technical papers and non-fiction EPUBs frequently rely on figures, commutative diagrams, and plots.

### FR-07: User-Controlled Encrypted / ZIP Library Backup & Restore
- **Summary**: Provide a single-tap **Export Library Backup (`.verso.zip`)** and **Restore Backup** in Settings/Library that bundles `books/` canonical JSON documents, original sources, Room metadata (`books` + `bookmarks`), and `ReaderSettings` into a portable archive via Android Storage Access Framework (SAF).
- **User Value**: Allows seamless device migration and local backups without requiring any cloud account or internet permission.

### FR-08: Reading Session Statistics & Estimated Time Left in Chapter/Book
- **Summary**: Compute word/symbol counts per chapter and track active reading speed (words per minute) locally to display subtle `"~6 min left in chapter · 22 min left in book"` indicators in the bottom TOC bar.
- **User Value**: Helps readers pace their study or reading sessions.

---

## 2. UI/UX Improvement Proposals (`UX-*`)

### UX-01: Interactive Bottom Scrubber Bar with "Return to Previous Position" Pill
- **Problem**: Jumping to a distant chapter via TOC or Search makes it hard to return to the exact paragraph where the user was previously reading.
- **Proposal**: Add a horizontal scrub slider in the bottom chrome bar for rapid page scrubbing, plus a floating **"↩ Back to Page X"** undo chip whenever the user jumps more than 3 pages via TOC, Search, or Scrubber.

### UX-02: Full-Screen Zoomable Equation & Wide Table Inspector
- **Problem**: Multi-column matrices, long Feynman path integral derivations, or wide data tables on compact phone screens can benefit from dedicated inspection.
- **Proposal**: Double-tapping any `MathBlock`, `CodeBlock`, or `TableBlock` opens a distraction-free modal inspector with 2D pan/pinch-to-zoom, **"Copy raw LaTeX"**, **"Copy Unicode text"**, and **"Save equation as PNG"**.

### UX-03: Adaptive Two-Column & Supporting-Pane Layout for Tablets & Foldables
- **Problem**: On unfolded foldables and tablets (`WindowWidthSizeClass.Expanded`), single-column chrome drawers cover content unnecessarily.
- **Proposal**: Dock the **Table of Contents** or **Bookmarks Sidebar** as a persistent supporting side rail on wide screens (`> 840.dp`) and offer an optional two-column paginated reading mode.

### UX-04: Hardware Volume-Key & Edge-Tap Page Turning
- **Problem**: Continuous vertical scrolling requires repeated thumb swipes during long reading sessions, and e-ink Android devices (e.g., Onyx Boox) suffer from ghosting during smooth scroll animations.
- **Proposal**: Add an optional **Paginated / Tap-to-Turn** mode and **Volume Key Page Turn** toggle in `SettingsPanel` that snaps cleanly by viewport height without animation jitter.

### UX-05: Footnote & Citation Inline Popover Preview
- **Problem**: Tapping a footnote (`[^1]`) or internal section link scrolls away from the current context.
- **Proposal**: Render footnotes, citations (`\cite{...}`), and equation references (`\eqref{1.1}`) as interactive pills that open a compact bottom popover card previewing the target equation or footnote without losing reading position.

---

## 3. Codebase Performance & Battery Optimization Audit (`PERF-*`)

### Implemented in Current Release
- **PERF-01 (Implemented — `LatexParser` AST & Plain-Text LRU Memoization)**:
  - Added thread-safe bounded `LruCache` (256 entries) for `LatexParser.parse()`, `LatexParser.parseEquation()`, and `LatexParser.toReadablePlainText()`, plus precompiled `TAG_REGEX`, `WHITESPACE_REGEX`, and `DELIMITER_SIZING_COMMANDS`.
  - *Impact*: Eliminates repeated recursive-descent AST parsing during `LazyColumn` scroll flings, TOC generation, bookmark snippet extraction, and full-text search.
- **PERF-02 (Implemented — Hoisted Precompiled `Regex` Constants)**:
  - Hoisted 16 inline `Regex(...)` allocations across `FormatDetector.kt`, `MarkdownExtractor.kt`, `PlainTextExtractor.kt`, and `BookmarkHelper.kt` into static `private val` constants, and bounded `LIST_REGEX` matching with `.take(2)`.
  - *Impact*: Avoids NFA regex compilation overhead and GC churn during document import and snippet formatting.
- **PERF-03 (Implemented — Compose `@Immutable` Stability Annotations)**:
  - Annotated `Document`, `Block` subtypes, `InlineText`, `Span`, `ReaderPalette`, and `ReaderSettings` with `@Immutable`.
  - *Impact*: Enables Jetpack Compose to skip recomposing visible `BlockView` items when top/bottom chrome, search bars, or sidebars animate open/closed.
- **PERF-04 (Implemented — Binary-Searched TOC Reuse in `DocumentSearcher`)**:
  - Updated `DocumentSearcher.search()` to accept pre-extracted `tocEntries` from `ReaderScreen` and resolve section titles via binary search (`TocExtractor.activeEntryIndex`).

### Recommended Next-Stage Optimizations
- **PERF-05 (Chunked Lazy Pagination for 10,000+ Block Books)**:
  - Currently `BookRepository.read(id)` loads the entire `document.json` into memory at once. Partitioning large books into 250-block chapter chunks on disk with a lightweight Room block-index table will reduce peak heap usage by ~80% on 1,000-page textbooks.
- **PERF-06 (Pre-computed Inverted Trigram Search Index on Import)**:
  - Build a compact searchable text index during `ImportCoordinator` background import so `DocumentSearcher` queries run in sub-millisecond time on very large books without scanning every block on the fly.
- **PERF-07 (OLED Battery Saver Auto-Prompt)**:
  - Offer an automatic switch from `Dark` (`#1B1B1D`) to true `Black` (`#000000`) theme when Android system Battery Saver mode (`PowerManager.isPowerSaveMode`) is active.
