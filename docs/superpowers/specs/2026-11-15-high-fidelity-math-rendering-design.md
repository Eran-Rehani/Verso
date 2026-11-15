# High-fidelity math rendering: design

Date: 2026-11-15
Status: awaiting user approval

## Revision notes

**After builder check.** A fresh reviewer read this document as an outside builder and
raised ten questions. The gravest was that plain `pdflatex` emits Computer Modern, not
Latin Modern, so the original oracle would have compared a Latin Modern renderer against
a different font and measured font substitution instead of layout fidelity. That was
verified and corrected: goldens now compile with `lmodern`. Three further corrections
came out of the same review — script sizes follow TeX's drop-to-size ladder rather than
MATH-table percentages (measured 0.663, not 0.70), the two inline-math code paths must
actually be retired rather than left in place, and `LatexParser`'s dropped style commands
are a documented limitation.

**After user decisions and corpus inspection.** The user supplied three answers (Drive
corpus, full inline scope, local-only fidelity gate) and asked whether any app already
does this. Investigating both changed the document again: prior art was researched and
recorded, and inspecting the corpus revealed that it is RTL Hebrew built with XeLaTeX
whose compiled PDFs use Computer Modern, so goldens must be regenerated from sources with
`lmodern`. The remaining user-facing decision is the PDF unresolved-equation policy at M6.

## Intent and why

Verso currently renders math by approximating TeX in hand-written Compose code. The
user's report is that LaTeX "looks poor on a phone". Diagnosis against the codebase
narrows this to a rendering-quality problem, not a parsing or format-support problem.
The goal is a math renderer whose output is provably equivalent to TeX output, working
identically whether the source is a raw LaTeX string, an EPUB chapter, or a PDF.

## What already works (verified, not assumed)

These were checked by reading the code and running the suite, and are **not** part of
this project:

- **LaTeX environments.** `LatexParser` already handles `align`, `aligned`, `gather`,
  `split`, `equation`, `pmatrix`/`bmatrix`/`Bmatrix`/`vmatrix`/`Vmatrix`, `cases`,
  `array`, `alignedat`, `subarray` on top of a full `MathNode` AST.
- **EPUB MathML.** `EpubExtractor` routes chapters through `HtmlExtractor`, which calls
  `MathMlConverter.toLatex` for `mfrac`, `msqrt`, `mroot`, `mtable`, `msup`, `msub`,
  `msubsup`, `munder`, `mover`, `munderover`, `mfenced`, `menclose`, `mspace` and more.
- **Cross-format consistency at the data layer.** EPUB MathML and raw LaTeX both
  converge on the *same* LaTeX string and the *same* parser, so the consistency
  requirement is structurally satisfied before rendering begins. What remains is to
  prove it with tests rather than rebuild it.

## The actual deficit

1. **No math font.** No `.ttf`/`.otf` is bundled anywhere. Every glyph falls back to
   generic `FontFamily.Serif`; italic variables, operator weights and symbol shapes
   are whatever the system serif provides. This is the user's "glyph shape and weight"
   complaint.
2. **No TeX inter-atom spacing.** Spacing is hardcoded per node type, not the TeX
   8-class model (`Ord`, `Op`, `Bin`, `Rel`, `Open`, `Close`, `Punct`, `Inner`) or the
   three spacing levels (display/text, script, scriptscript). This produces cramped or
   loose gaps around `=`, `+`, `,` and is the user's "proportion and spacing"
   complaint. Confirmed both.
3. **Scripts are proportional guesses.** Inline fallbacks use `0.78`, `0.74` scale
   factors; TeX uses specific relative sizes and shifts driven by font metrics.
4. **Stretchy delimiters are hand-drawn.** `drawScalableDelimiter` builds `(`, `[`,
   `⌊`, and friends from cubic Béziers. A tall delimiter is a *stretched outline*, not
   assembled from font pieces, so it never matches TeX's parts-and-assembly result.
5. **PDF loses structure upstream of rendering.** `PdfBoxPageSource` flattens each
   baseline to a plain run; fraction bars and script positions are discarded before any
   renderer runs. No renderer change can recover this.
6. **No visual regression tests at all.** Math tests assert AST shape and symbol-map
   lookups only. Nothing proves appearance.

## Goals

- Rendering fidelity: same glyph shapes, weights, proportions, and inter-atom spacing
  as TeX output, for inline and display math.
- Provable equivalence: every construct is validated against **real `pdflatex` output**,
  compared on glyph boxes and inter-atom spacings within declared tolerances, with a
  pixel diff as a secondary signal.
- One renderer for all sources: LaTeX, EPUB MathML, and PDF-derived LaTeX render through
  identical code, so the same equation looks the same regardless of origin.
- Recover fraction and script structure from PDF text layers.

## The inline-vs-display problem (the biggest architectural decision)

Inline math currently has **two** rendering paths: a 2D Compose widget inside
`InlineTextContent` (used when an equation contains a fraction/root/matrix and is under a
width threshold), and a flattened `AnnotatedString` fallback that guesses script sizes
(0.74/0.78) and injects hardcoded `\u2009` around operators. So today the *same* `x^2`
can render two different ways depending only on whether its paragraph happens to contain
a fraction. That directly contradicts the "one renderer for every source" goal.

A staged engine keeps both paths until the last one is retired. **Decision: the engine
owns every math span by M4, and the `AnnotatedString` math fallback is deleted, not
merely bypassed.** M2 and M3 may run the engine only for display math and 2D widgets
while the metric work lands, but no milestone may be declared complete while a math span
still uses guessed spacing.

This has a real consequence the plan must budget for: laying out inline math inside a
justified, word-wrapping paragraph requires baseline alignment against surrounding text
and interaction with Task 9's `SelectionContainer`. That is the hardest single piece of
this project, it is why M4 is the largest milestone, and it is not optional if the
consistency requirement is to be met. Where inline math remains an inline-content
widget, selection/copy/search continue to use the placeholder's `alternateText` — the
same contract Task 9's tests already assert.

## Parser limits the engine must work around

`LatexParser` is explicitly out of scope, but it drops or reclassifies information the
engine needs. Verified in source:

- `\displaystyle`, `\textstyle`, `\scriptstyle`, `\scriptscriptstyle` return an **empty
  `Row`** (line 923) — the style is discarded entirely, so an engine cannot tell
  `\displaystyle\sum` from inline `\sum`. The engine derives style from context
  (display vs inline) and must treat an explicit style command as a known limitation
  until the parser is revisited.
- Unknown commands become a visible `FUNCTION` symbol (line 894), so unrecognised LaTeX
  renders as its own command name. Goldens must therefore be built from commands the
  parser actually supports, and a parity test should assert that any construct in the
  golden corpus parses without falling back to a `FUNCTION` symbol carrying a backslash.
- The parser strips trailing `,`/`.` and extracts trailing `(n)` into a `tag`. Golden
  generation therefore compiles the **raw** LaTeX, not parser output, so that stripping
  does not silently change what is compared.
- `\mathbb`/`\mathcal`/`\mathfrak` are pre-mapped to Unicode codepoints by the parser, so
  the renderer must find those codepoints in the bundled math font rather than applying
  a font style.

## Non-goals

- Cross-block/lazy-list selection (already out of scope for Task 9).
- Scanned/OCR PDFs. Text-layer geometry only.
- A general TeX interpreter. Coverage is the command set the app already parses, plus
  what the corpus of real documents exercises.
- Replacing the `MathNode` AST or the parser. This project changes rendering and PDF
  extraction, not parsing.
- Fixing the remaining audit findings (Tasks 10–14), which are paused by explicit user
  decision.

## Anti-goals

- A WebView-rendered equation in a document. It would look subtly different from
  natively-rendered inline math, breaking the consistency requirement, and could not
  participate in selection, search, or TTS.
- Shipping approximate output presented as TeX-equivalent. If a construct cannot match
  within tolerance, it fails visibly in tests rather than silently degrading.
- Silently dropping PDF equations whose structure cannot be recovered.

## Verified technical facts the plan rests on

Each of these was checked on this machine during design, not assumed:

- `pdflatex` + `\usepackage[active,tightpage]{preview}` crops the page to the equation
  (measured: 140.5×35.6 pt). `pdftotext -bbox` yields per-glyph boxes and baselines
  (18 glyphs for the probe equation). `pdftoppm -r 300` yields a raster (586×149).
- Robolectric 4.16.1 `@GraphicsMode(NATIVE)` rasterizes real glyphs through
  `Canvas`/`Bitmap`: 820 non-white pixels for `Ag∫∑`.
- `captureToImage()` under Robolectric **hangs** (`forceRedraw` never satisfies after
  2000 ms). Goldens must therefore not use Compose window capture.
- An OpenType MATH font loads via `Typeface.createFromFile` and rasterizes real math
  glyphs (3099 non-white pixels for `∫∑ ½ √`).
- Both candidate fonts carry a `MATH` table (STIX Two Math 27,408 bytes, version 1.0;
  Latin Modern Math 716.5 KB total). **Android exposes no public SDK API for MATH-table
  metrics at minSdk 26**, so M3 requires a hand-written OpenType MATH parser. Scope is
  bounded and known: 56 math constants (axis height, rule thickness, script size/shift,
  fraction gaps) plus a `MathVariants` table for stretchy assembly.
- `PdfPage` currently carries only flattened `TextRun` values (text, x, baseline, width,
  size, bold) — no glyph positions and no painted rules. The M6 additions are
  backward-compatible fields, matching the already-reviewed reflow design.
- Both fonts are OFL-licensed families and may be bundled with their license texts;
  the plan must ship the OFL text and third-party notice.

## Defects found in current MathML conversion (in scope for M5)

Reading `MathMlConverter` surfaced two real bugs that directly affect the consistency
goal, and they belong in M5 rather than being left implicit:

- `mtable` always emits `\begin{pmatrix}` regardless of the source's column alignment or
  fence style, so a `cases`-like or bracket-delimited table renders with wrong fences.
- `mover`'s mapping at line 260 contains a duplicated `"^"` branch, making the second
  entry unreachable.

## Constraints

- Offline. No network fetch of fonts, assets, or TeX at runtime.
- APK growth accepted (user approved): ~717 KB Latin Modern Math, ~819 KB STIX Two Math.
- Compose, no framework upgrade.
- Host tooling available and verified: `pdflatex` is **TeX Live 2026 (Arch Linux)**,
  kpathsea resolving `/usr/share/texmf-dist`; the `/usr/local/texlive/2025` tree is also
  installed but is not what the `pdflatex` binary uses. Node 24, Chromium, Playwright
  also present.
- Robolectric 4.16.1 with `@GraphicsMode(NATIVE)` rasterizes real glyphs via
  `Canvas`/`Bitmap`. Verified by probe: 820 non-white pixels for `Ag∫∑`.
- `captureToImage()` under Robolectric **hangs** (`forceRedraw` never completes).
  Verified by probe. Goldens therefore must not use Compose window capture, and image
  comparison happens in JVM tests against pre-generated golden files.

## Approaches considered

**A. Bundled font plus TeX metric tables, keeping Compose layout.** Bundle an OpenType
math font, teach the renderer glyph-level TeX metrics (8-class spacing, script sizes,
axis height, rule thickness, fraction gaps), and correct script/delimiter proportions.
Trades: large immediate improvement to shapes and spacing, but delimiters remain
stretched outlines rather than font-assembled, and full TeX layout (breakable
paragraph math, `\mathchoice` levels) is out of reach.

**B. A real TeX math layout engine.** Box/glue/kerning model, 8-class spacing, script
levels, delimiter assembly from font piece glyphs, `\over`/`\atop` rule placement,
rendered to `DrawScope`. Trades: genuinely TeX-grade and fully controllable, but it is
a multi-week build with a golden test per construct, and nothing is visible until well
into it.

**C. A then B, in milestones.** Font and metric table first for immediate visible gains,
then grow the same renderer into an engine with delimiter assembly and script levels.
Trades: nothing visible gets worse, both problems eventually fixed; total scope is
larger than either alone. **User chose C.**

KaTeX and equivalents were ruled out as the *shipped* renderer for the consistency and
integration reasons above, but remain available as a fast secondary oracle.

## Decided details

- **Golden oracle: real `pdflatex` with `\usepackage{lmodern,amsmath}`.** Each test case
  is compiled with `\usepackage[active,tightpage]{preview}` so the PDF page is cropped to
  the equation. Measured: a probe equation produced a 140.5×35.6 pt page, 18 glyph boxes
  from `pdftotext -bbox`, and a 586×149 PNG at 300 dpi.

  **This correction matters.** Plain `pdflatex` embeds Computer Modern (`CMMI12`,
  `CMR12`), verified with `pdffonts`. Latin Modern Math is a *look-alike* of Computer
  Modern with different glyph shapes and advance widths, so comparing our Latin Modern
  rendering against vanilla pdflatex goldens would have measured font substitution rather
  than layout correctness. With `lmodern` the golden embeds `LMMathItalic12`,
  `LMRoman12`, `LMMathSymbols8`, `LMMathExtension10` — the same family we bundle. **All
  goldens therefore compile with `lmodern`, and Latin Modern Math is the canonical golden
  face until the user selects STIX.**
- **Script sizes follow TeX's drop-to-size ladder, not font percentages.**
  `latinmodern-math.otf` declares `scriptPercentScaleDown = 70`, which would scale a 12pt
  base to 8.4pt. But the golden's superscript renders in the **`LMMathSymbols8` face at
  8pt**, and the measured glyph-height ratio is **0.663** (= 8/12), not 0.70. TeX drops to
  the nearest traditional size (12→10→8→6, stopping per its size table; ≥10pt always
  10/7/5-style pairs). M3 implements the ladder, not a flat multiplier.
- **Comparison metric: boxes and spacings with tolerances**, with pixel diff as a
  secondary signal. Rationale: a pixel diff alone can mask a systematic 0.2 px spacing
  error or fail catastrophically on sub-pixel differences, and when it fails it does not
  say which gap was wrong.
- **Fonts: bundle both, selectable.** Latin Modern Math (matches default `pdflatex`
  output, so it is the canonical golden face) and STIX Two Math (more legible at small
  sizes, common in EPUB/PDF). Default to Latin Modern; STIX is selectable for readers
  who prefer its letterforms.
- **PDF strategy: geometry reconstruction from the text layer.** Consume glyph positions
  and painted rules to rebuild fractions and scripts into LaTeX. No OCR, no model.

## Milestones

Each milestone lands with its goldens; a milestone is not complete until its tests pass.

**M1 — Golden harness (thin end-to-end slice).** Build the reference generator
(corpus `.tex` → compile with `lmodern` + `preview` → glyph boxes + PNG) for a small
seed corpus extracted from the user's Drive sources, a Kotlin golden loader, and a
comparison function producing a legible diff (which glyph/gap disagreed and by how
much). Render one trivial construct (`x`, then `a+b`) through the existing renderer and
compare. This proves the loop before any renderer rewrite, and deliberately fails first
so the harness itself is trustworthy.

Scale handling matters here: a 300 dpi golden of a small display equation is ~586×148 px,
while the same equation on a phone at 20sp is roughly 40–60 px tall. Comparison must
therefore normalize by declared font size (compare boxes and gaps in em units, and scale
raster goldens to a common reference size) rather than diffing raw pixels. Keeping the
metric space in em/TeX points is the reason boxes-and-spacings is the primary signal.

**M2 — Font and glyph correctness.** Bundle Latin Modern Math and STIX Two Math with
the OFL license text and a settings entry. Route all math glyphs through the bundled
face; only genuine class-Words go italic. Compare glyph shapes and weights on a broad
symbol corpus.

**M3 — TeX metric layout.** Replace hardcoded spacing with the 8-class inter-atom model
and the three style levels; derive axis height, rule thickness and fraction gaps from the
font's MATH table, and take script sizes from TeX's **drop-to-size ladder** (8/12 = 0.667
for a 12pt base, verified by measurement), not from `scriptPercentScaleDown` (70). Because
Android has no MATH-table API at minSdk 26, this milestone includes a bounded
hand-written OpenType MATH parser (56 constants plus `MathVariants`). This is the
milestone you will feel most on a phone.

**M4 — Inline layout, delimiter assembly and display constructs.** Retire the
guessed-spacing `AnnotatedString` math path so every math span goes through the engine,
including inline math inside justified wrapping text with correct baseline alignment and
intact Task 9 selection. Grow delimiters from font piece glyphs (parts-and-assembly), and
correct display centering, equation-number placement, matrices, `cases` and accents to
TeX geometry. This is the largest milestone.

**M5 — Consistency proof.** Prove identical rendering for the same equation arriving as
raw LaTeX and as EPUB MathML, add regression tests for existing parser coverage
(`align`, `matrix`, `cases`), and fix the two `MathMlConverter` defects above. Note that
EPUB math is lossy where the converter is lossy (`mfenced`, `mtable` always `pmatrix`,
`mspace` always `\quad`), so the consistency corpus covers MathML features the converter
round-trips, and round-trip improvements are included here rather than deferred.

**M6 — PDF geometry reconstruction.** Capture glyph positions and painted rules during
extraction, reconstruct fractions and scripts into LaTeX before flattening, and keep
prose reflow intact. Includes the unresolved-equation policy decision.

## What is left to the builder

- **How our side produces measurable boxes.** The current renderer places glyphs through
  Compose `Layout`/`Constraints` custom layouts (`SubSupLayout`, `SqrtLayout`,
  `ScalableDelimitedLayout`, `AccentLayout`, `MatrixGridLayout`), which compute geometry
  but do not expose it as data. The builder must choose how to obtain per-glyph boxes for
  comparison: either have the engine emit a measurement tree alongside layout (preferred
  once M3 makes the engine explicit), or derive boxes from a rasterized bitmap during
  M1–M2 as a stopgap. This choice is deliberately left open so M1 can be built cheaply.
- Concrete tolerance values (starting sub-pixel for boxes, ~1/100 em for gaps), to be
  calibrated by measuring the harness on known-equivalent output before enforcing.
- The exact extracted case list from the corpus: which equations are pulled from
  `Hebrew_Summaries/*.tex` into the checked-in fixture set, and how per-case minimal
  standalone `.tex` files are derived from the chapters.
- Internal structure of the engine (box model, node visitors) and the exact shape of
  the comparison diff output.
- Performance targets for long documents once layout is metric-driven.
- How much of the RTL requirement lands in M4 versus a follow-up: the corpus is
  bidirectional prose, and the plan must decide whether M4 ships LTR inline math with
  RTL-correct surrounding text, or full bidi math inline in one milestone.

## Calls I made as the builder

Settled in this design without changing what gets built:

- `\text{...}` and text-mode faces inside math stay in the **reader's body font**, so the
  prose and the math's text runs match. Bundled fonts cover math glyphs,
  `\mathbb`/`\mathcal`/`\mathfrak` and symbols only.
- **STIX Two Math is visual-review only.** Goldens are Latin Modern only, because
  measuring STIX against a Latin Modern golden would repeat the substitution error
  described above. STIX is validated by a separate, smaller contract-consistency check,
  not by the fidelity gate.
- Glyph metrics come from the bundled OTF's MATH table via an in-app parser, **not** from
  `Paint.getTextBounds`/`getRunAdvance`, whose advances are integer-rounded and
  hinting-influenced and therefore cannot support sub-em tolerances.
- Goldens are generated **locally and checked in as fixtures**, with a regeneration
  script; **CI runs non-visual tests only** (user's explicit choice — no TeX Live in CI).
  Regeneration must pin the TeX distribution version, since the local `pdflatex` (2026)
  and the installed 2025 tree already differ. Because the fidelity gate does not run in
  CI, the plan must state plainly that a green CI does not mean fidelity passed, and the
  release checklist must require a local golden run.
- Math font setting is **per-app, not per-document.**

## Questions for the user (all answered)

1. **Golden corpus — answered: the user's Google Drive.** Decided below under "Corpus".
2. **Inline scope — answered: confirmed.** The user stated math rendering is the main
   feature of the app, so the full inline-in-justified-text solve is in scope.
3. **CI and the fidelity gate — answered: local only.** Goldens are generated locally and
   checked in; CI runs non-visual tests only.

## Prior art: does an app already do this?

Researched because the user asked. Short answer: several render EPUB MathML; none is
known to reproduce TeX typesetting, and the best open-source attempt by a major reader
took months and is still rated worse than its commercial rival.

- **KOReader** added MathML in 2021. It is not a WebView and not a PDF-to-LaTeX path: it
  is a native implementation inside the C++ crengine layout engine
  ([koreader-base#1337](https://github.com/koreader/koreader-base/pull/1337),
  [crengine#429](https://github.com/koreader/crengine/pull/429)). **Scale: 17 files,
  +6,924/−166 lines** — `mathml.cpp` alone is 2,745 lines, plus a generated
  `mathml_operators.h` (1,127) and `mathml_table_ext.h` (1,061). The author describes
  "months of work", starting from MathML-for-CSS and finding it insufficient.
- **Community verdict** ([MobileRead](https://www.mobileread.com/forums/printthread.php?s=affe3a1ba1572141ea91c11ee12bcf34&t=339219)):
  KOReader renders math books acceptably but "not as nice as PocketBook"; PocketBook is
  called "very professional". Users were seeking this in 2021 and the gap persists.
- **Librera** has open issues around SVG-based equations not displaying
  ([issue #841](https://github.com/foobnix/LibreraReader/issues/841)), a common
  publishers' workaround that bypasses real math rendering.
- **Reasily** is frequently recommended for MathML reading, but it is a reader with
  MathML support, not a claim of TeX-equivalent typesetting.

**What this changes for us:** our plan is *not* naive. We attempt something none of these
apps claims — provable equivalence to TeX — and we attempt it without a C++ engine
rewrite. The KOReader data point is the best available scope calibration: ~7k lines of
mature, reviewed C++ for MathML layout in an existing layout engine. We are doing this in
Kotlin/Compose on an existing AST, with the font's MATH table instead of a bespoke
operator table. Expect comparable order-of-magnitude work for real fidelity; the
milestones are sized accordingly, and M3–M4 are the bulk of it.

## Corpus: the user's Drive

The user authorized using their Google Drive course material as test input. The corpus is
private and lives on the maintainer's machine; no paths or course identifiers are recorded
in the repository. Findings from inspecting it:

- The Drive is mounted read-only (rclone FUSE). Its year folders contain **~380+ PDFs**, and
  a single physics course folder is 263 MB.
- The richest source is a set of **12 LaTeX chapter sources, 2,163 lines**, containing
  **262 `\frac`, 233 `equation`, 328 `\partial`, 97 `\left`, 92 `\mathbf`, 68 `\text`,
  48 `\nabla`, 30 `\sqrt`, 23 `\sum`, 15 `\int`, 10 `pmatrix`, 9 `align`, 1 `cases`**.
  This is real physics typesetting, not a toy list.
- **It is RTL Hebrew prose with embedded LTR math.** That is a harder case than English
  LaTeX and a genuine risk for inline layout in M4, because math must sit in a
  bidirectional paragraph. This corpus is therefore the *right* hard test, and M4 must
  handle direction explicitly rather than assuming LTR.
- Verified by compiling a copy in `/tmp`: builds to **50 pages**. The source set builds
  with `xelatex` + `polyglossia` (`\setmainlanguage{hebrew}`); it does **not** use
  `unicode-math`.
- **Consequence for goldens:** the corpus's own compiled PDFs use Computer Modern
  (`CMMI10`, `CMEX10`, `CMR10`), because XeLaTeX still defaults to CM math. Goldens must
  therefore be **regenerated from the `.tex` sources with `\usepackage{lmodern}`**, not
  copied from the user's existing PDFs. Verified: adding `lmodern` under XeLaTeX yields
  `LMMathItalic12`/`LMMathExtension10`/`LMMathItalic5` plus the Hebrew text face
  `DavidCLM`, so one math font serves the whole corpus.
- The user's library also contains math-heavy EPUB and DjVu files (Feynman lectures), but
  the `.tex` sources are the better golden input because they carry exact math semantics
  that compiled PDFs have already lost.

Privacy: course PDFs, screenshots and extracted text stay in the ignored
`.gradle/course-pdf-test/` area and never enter Git, reports, or worker prompts. Corpus
golds are generated from the sources and may be checked in only after review confirms
they contain no private content beyond test equations.

## Open decision

M6 requires the PDF unresolved-equation policy: **fail the whole import** on a detected
ambiguous equation, or **keep the document with a visibly marked unresolved region**.
Neither choice permits presenting flattened or partial math as success. A third option
the builder raised — silently falling back to readable text for equations that cannot be
reconstructed — is **ruled out** here, because it is indistinguishable from the original
bug. Choose between fail-import and marked-region before M6.

## Project interaction

This supersedes audit Tasks 10–14 as the active priority by explicit user decision. The
17-finding audit work already reviewed (Tasks 7, 8, 9 and the Unicode repair) is
preserved and remains uncommitted behind the existing commit gate; nothing is discarded.
