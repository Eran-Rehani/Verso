# Roadmap

## Completed Recently
* Native offline LaTeX math typesetting (`LatexParser`, `MathRenderer`, `InlineRender`)
* Hierarchical Table of Contents (`TocExtractor`, `TocPanel`, bottom chapter bar, `.tex` section macro support)
* Full-text document search (`DocumentSearcher`, `SearchResultsPanel`, inline match highlighting, math-aware search)
* Page bookmarking & slide-in Bookmarks Sidebar (`BookmarkEntity`, `BookmarkHelper`, `BookmarksSidebar`, Room v3 migration)
* Multi-format sample showcase (`docs/samples/` & Home Screen sample cards for Markdown+Math, LaTeX `.tex`, GFM Tables+Code, HTML, and Plain Text)
* Performance & battery optimizations (`LatexParser` LRU AST cache, precompiled `Regex` constants, `@Immutable` Compose models)

## In Progress
* Reader upgrade ([docs/plans/complete-reader-upgrade.md](docs/plans/complete-reader-upgrade.md))

## Upcoming Feature Requests (Detailed in [docs/FEATURE_REQUESTS.md](docs/FEATURE_REQUESTS.md))
* **FR-01**: Inline text highlights, margin annotations, and Markdown (`.md`) note export
* **FR-02**: Offline Text-to-Speech (TTS) read-aloud with math verbalization and sentence tracking
* **FR-03**: Collections, custom tags, reading status filters, and batch folder import
* **FR-04**: Cross-document Library full-text search
* **FR-05**: Custom `.tex` preamble macro expansion (`\newcommand`, `\DeclareMathOperator`)
* **FR-06**: Embedded offline image & SVG figure rendering with pinch-to-zoom lightbox
* **FR-07**: User-controlled `.verso.zip` library backup and restore

## Upcoming UI/UX & Performance Enhancements ([docs/FEATURE_REQUESTS.md](docs/FEATURE_REQUESTS.md))
* **UX-01**: Interactive bottom progress scrubber with *"Return to previous position"* undo pill
* **UX-02**: Full-screen zoomable equation & wide table inspector with *"Copy LaTeX"*
* **UX-03**: Adaptive two-column and docked sidebar layouts for tablets and foldables
* **UX-04**: Hardware volume-key and edge-tap page turning mode (e-ink friendly)
* **UX-05**: Footnote, citation, and `\eqref` inline popover previews
* **PERF-05 / PERF-06**: Chunked lazy block pagination and trigram search indexing for 10,000+ block books
