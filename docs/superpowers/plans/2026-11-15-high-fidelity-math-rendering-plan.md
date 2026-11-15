# High-fidelity math rendering: implementation plan

Design: `docs/superpowers/specs/2026-11-15-high-fidelity-math-rendering-design.md`
Worktree: `/home/eran/.codex/worktrees/b623/Verso` (branch `fix/audit-17-findings`)
Date: 2026-11-15

## Execution rules

- One task at a time, in order. Each task ends green, with its own tests.
- Every task names the exact command it was verified with. "Should work" is not a result.
- Goldens are generated **locally** with TeX Live and **checked in**. CI runs
  non-visual tests only; a green CI is never evidence that fidelity passed.
- The existing `LatexParser` is not modified in M1–M4. Where it drops information that
  matters, the workaround is documented rather than patched (see design "Parser limits").
- No shell command outside the worktree. Course material, extracted text and screenshots
  stay in the ignored `.gradle/course-pdf-test/`, never in Git or reports.
- Preserve: original checkout's staged/untracked files, the signing key, `.testing`
  package identity, and the uncommitted audit work for Tasks 7–9.

Verification command for visual work (verbatim):

```sh
cd /home/eran/.codex/worktrees/b623/Verso
export ANDROID_HOME="$HOME/Android/Sdk" JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$PWD/.gradle/tmp" GRADLE_USER_HOME="$PWD/.gradle/user-home"
./gradlew :app:testDebugUnitTest --tests "app.verso.mathgolden.*"
```

---

## Phase M1 — Golden harness

**Goal:** prove the loop. A test that renders `x` through our code, compares it to a
TeX golden, and says precisely which glyph box or gap disagreed and by how much.

### Task 1.1 — Golden generator script

Create `tools/mathgolden/generate.py` and `tools/mathgolden/cases/`.

- Input: one case per file, `cases/<name>.tex` containing only math source, e.g.
  `\frac{a+b}{c} = \sum_{i=1}^{n} x_i^2`.
- For each case, emit `cases/<name>.pdf` by compiling a wrapper:

  ```latex
  \documentclass[12pt]{article}
  \usepackage{lmodern,amsmath,amssymb,mathtools}
  \usepackage[active,tightpage]{preview}
  \pagestyle{empty}
  \begin{document}
  \begin{preview}
  $\displaystyle <case>$
  \end{preview}
  \end{document}
  ```

  Two variants per case: display (`$\displaystyle ...$`) and inline (`$...$`).
- Emit `cases/<name>.glyphs.json`: parse `pdftotext -bbox` output into
  `{ pageWidth, pageHeight, glyphs: [{ char, xMin, yMin, xMax, yMax }] }` in TeX points.
- Emit `cases/<name>.png` at 300 dpi via `pdftoppm -r 300 -singlefile`.
- Support `--only <name>` and `--check` (`--check` regenerates into a temp dir and fails
  if checked-in output differs — this catches a TeX version drift).
- Record the TeX version (`pdflatex --version` first line) into `cases/VERSIONS.txt`.
- **No private content:** cases are hand-authored constructs plus equations extracted by
  a separate, ignored task (see Task 1.5). Nothing is copied from the user's Drive into
  `cases/` without review.

Verify: run the script on 3 seed cases (`x`, `a+b`, `\frac{a+b}{c}`); confirm PDFs,
JSON and PNGs appear and `pdfinfo` reports a tight single page per case.

### Task 1.2 — Golden loader and model

In `app/src/test/java/app/verso/mathgolden/`:

- `GoldenModel.kt`: `data class GoldenGlyph(char, xMin, yMin, xMax, yMax)`,
  `data class GoldenCase(width, height, glyphs)`.
- Loader reads `glyphs.json` from the repo `tools/mathgolden/cases/` directory via a
  relative path from the module, with a clear failure message if absent.
- Units: everything in **TeX points**; convert to our pixel space only at comparison
  time, using the case's declared base font size (12pt) and our render's base size.

Verify: a unit test loads `x.glyphs.json` and asserts exactly one glyph.

### Task 1.3 — Comparison engine

- `MathComparator.kt`:
  - Compare per-glyph boxes: for each golden glyph matched in order (same character
    sequence), compare `xMin`, `yMin` (baseline proxy), width, height.
  - Compute inter-glyph gaps as `next.xMin - current.xMax` and compare.
  - Report the **worst** offender with a human message:
    `"gap after '=' expected 6.53pt, got 3.10pt (Δ3.43pt > tol 0.15pt)"`.
  - Tolerances are declared constants, initially generous, then tightened once measured:
    `BOX_TOL_PT`, `GAP_TOL_PT`.
  - Emit a `MatchReport(ok, worstDelta, messages)`.
- Primary signal: boxes and gaps. Secondary: normalized pixel diff (scale the golden PNG
  to our render's size, compare luminance with a threshold, report % differing pixels).
- Failure output must be readable enough to fix the code without opening an image.

Verify: unit tests on hand-built `GoldenCase` values that are identical (pass),
0.4pt off (fail with the exact message), and a missing glyph (fail naming it).

### Task 1.4 — Our renderer's measurement tree

The renderer computes geometry inside Compose `Layout` blocks and does not expose it.
For M1–M2, derive boxes from a rasterized bitmap:

- `OursRaster.kt`: rasterize a `MathNode` tree to an `android.graphics.Bitmap` through
  the existing draw path at a known scale, then segment into glyph boxes by connected
  column runs.
- This is explicitly a **stopgap**; M3 replaces it with the engine's own measurement tree
  (`MeasuredBox`), which is also what the engine needs anyway.

Verify: rasterize `x` and assert one box with plausible dimensions.

### Task 1.5 — Corpus extraction (private, ignored)

- `tools/mathgolden/extract_corpus.py`, output to `.gradle/course-pdf-test/corpus/`
  (ignored). Reads `Hebrew_Summaries/*.tex`, pulls each `equation`/`align`/`cases`/
  `pmatrix` body, deduplicates, and writes candidate case files plus a report of
  construct frequency.
- A human-reviewed subset is then copied into `tools/mathgolden/cases/` — pure math
  strings only, no Hebrew prose, no course identifiers.

Verify: run it; it reports 233 `equation`, 9 `align`, 1 `cases`, 10 `pmatrix` extracted,
and writes nothing outside the ignored directory.

### Task 1.6 — M1 gate

Wire one end-to-end test: render `a+b` with our code, compare against the golden, and
assert it **fails** initially with a legible report (deliberate red). Record the report
in the plan ledger. This closes M1: the harness is proven and honest.

---

## Phase M2 — Font and glyph correctness

### Task 2.1 — Bundle fonts

- Add `app/src/main/res/font/` with `latinmodern_math.otf` and `stix_two_math.otf`
  (copied from TeX Live / system, OFL).
- Add `app/src/main/assets/licenses/` with `OFL-LatinModernMath.txt` and
  `OFL-STIXTwoMath.txt`, and extend the third-party notices file.
- Add `MathFontChoice` enum (`LATIN_MODERN`, `STIX_TWO`) to `ReaderSettings` with
  **default Latin Modern**, codec-compatible (defaulted, so old settings JSON loads).
- Confirm APK delta is as expected (~1.5 MB).

Verify: settings round-trip test proves old JSON without the field loads with the default;
`assembleDebug` succeeds; APK size delta recorded.

### Task 2.2 — Route glyphs through the bundled face

- Introduce `MathTypeface` provider that loads the chosen OTF via
  `Typeface.createFromFile` / resources and supplies families per style.
- Replace every `FontFamily.Serif`/`Monospace`/`SansSerif` in `LatexRenderer` and
  `InlineRender` with the math face, except text-mode runs (see design "Calls I made"):
  `\text{}` uses the reader body font.
- Only genuine `VARIABLE`-class symbols are italic; operators upright (already partly
  true — verify and test).
- `\mathbb`/`\mathcal`/`\mathfrak` already arrive as Unicode codepoints; assert the
  bundled font contains them (fail loudly if a glyph is missing).

Verify: golden comparison on a symbol corpus (Greek, operators, relations, arrows, big
operators, blackboard/fraktur/symbol letters); boxes within a loosened tolerance while
spacing is still wrong (expected), shapes and weights now comparable.

### Task 2.3 — M2 gate

A golden test over the symbol corpus where glyph **boxes** match within tolerance and
pixel diff drops sharply versus the pre-M2 baseline; record both numbers.

---

## Phase M3 — TeX metric layout

### Task 3.1 — OpenType MATH table parser

- `app/src/main/java/app/verso/core/math/MathTable.kt`: parse the `MATH` table from the
  bundled OTF bytes: version, 56 `MathConstants` (axis height, rule thickness, fraction
  gaps, script sizes/shifts, limits, delimiters), `MathVariants` (vertical/horizontal
  constructions), `MathGlyphInfo` (italic corrections, extended shapes).
- Pure Kotlin, no Android deps, fully unit-testable on the JVM.
- Fail loudly if the font is not a math font.

Verify: unit tests assert known values for Latin Modern Math (e.g. axis height, rule
thickness, `scriptPercentScaleDown == 70`) and that STIX parses without exception.

### Task 3.2 — Symbol classes and the 8-class spacing model

- `MathClass.kt`: `Ord`, `Op`, `Bin`, `Rel`, `Open`, `Close`, `Punct`, `Inner`.
  Assign class from the parser's existing `SymbolCategory` plus a table for ambiguous
  cases (`-` is `Bin` in `a-b` but `Ord` at expression start — implement the neighbours
  rule from TeX).
- `Spacing.kt`: the TeX spacing table for display/text, script, scriptscript levels
  (thin/medium/thick = 3/18, 4/18, 5/18 em; the specific pairs such as `Rel`–`Op` = 0).
- Levels: `Display`, `Text`, `Script`, `ScriptScript`.

Verify: unit tests asserting the computed gap for representative pairs
(`Ord–Rel`, `Rel–Ord`, `Bin–Ord`, `Punct–Ord`, `Open–Ord`, `Op–Ord`) at each level
against TeX's documented values.

### Task 3.3 — Script sizing on the TeX ladder

- Implement size selection: from a 12pt base, script = 8pt, scriptscript = 6pt (matching
  the measured 0.663 ratio and the `LMMathSymbols8` face actually used by TeX), with the
  ladder generalised for other base sizes and a floor.
- Apply to `SubSup`, nested fraction components, and `BigOp` limits.

Verify: golden comparison of `x^2`, `x^{2^3}`, `\sum_{i=1}^{n}` shows the superscript box
height within tolerance of the golden's `LMMathSymbols8` glyph.

### Task 3.4 — Fraction, root and accent geometry from MATH constants

- Replace the constants in `LatexRenderer` (`0.78`, `0.28`, `-0.16`, `1.95f`, `0.35f`,
  …) with values derived from `MathConstants`: axis height, fraction rule thickness,
  numerator/denominator gaps, radical vertical gap and rule thickness, accent base height.
- Keep a documented fallback if the font lacks a constant.

Verify: golden comparison of `\frac{a+b}{c}`, `\frac{\frac{1}{2}}{x}`, `\sqrt{x}`,
`\sqrt[3]{x}`, `\hat{x}`, `\vec{v}` — boxes and gaps within tolerance.

### Task 3.5 — M3 gate

A golden suite over the corpus-derived display equations (fractions, roots, sums,
integrals, Greek, `\text`, `\mathbf`) where **every** box and gap is within the declared
tolerance. This is the milestone that should visibly change the phone experience; record
before/after screenshots from a Robolectric raster (device capture is still blocked).

---

## Phase M4 — Inline layout, delimiters, display constructs

### Task 4.1 — Engine measurement tree

- Introduce `MeasuredBox(width, height, depth, baselineOffset, glyphs)` and have the
  layout compute it before drawing; drawing consumes it.
- Replace `OursRaster` in the harness (Task 1.4) with this tree.
- This is the foundation for both delimiters and inline layout.

Verify: harness still passes M3 goldens using the tree instead of rasterization.

### Task 4.2 — Delimiter assembly from font pieces

- Implement parts-and-assembly: select size variant from `MathVariants`, else assemble
  from top/extension/bottom glyphs; fall back to the largest available glyph scaled only
  when no construction exists.
- Delete `drawScalableDelimiter`'s Bézier shapes once assembly covers `( ) [ ] { } | ‖
  ⟨ ⟩ ⌊ ⌋ ⌈ ⌉` and `\left ... \right`.

Verify: golden comparison of `\left(\frac{a}{b}\right)`, a 3-row `pmatrix`, and nested
brackets — delimiter height within tolerance and no visible scaling of a single glyph.

### Task 4.3 — Retire the guessed inline path

- Delete the `appendMathNodeInline` flattened fallback and its `0.74`/`0.78`/`\u2009`
  heuristics. Every math span renders through the engine as inline content.
- Implement inline layout: baseline alignment against surrounding text, correct box
  metrics for the placeholder, wrapping behaviour.
- Preserve `alternateText` so selection/copy/search/TTS keep working (Task 9 contract).

Verify: the existing `BlockSelectionTest` stays green; golden comparison of inline
`$x^2$`, `$\frac{a}{b}$` and `$E=mc^2$` embedded in a sentence; a bidi smoke test with
RTL surrounding text (see design note about M4 vs follow-up).

### Task 4.4 — Display constructs

- Align/equation numbering placement, matrix column alignment and spacing, `cases`
  left brace, `\boxed`.

Verify: golden comparison of `align` (two rows), `pmatrix`, `bmatrix`, `cases`.

### Task 4.5 — M4 gate

Full golden suite green including inline-in-paragraph; no remaining guessed-spacing code
path (assert by grep that the old constants are gone); Task 9 selection tests green.

---

## Phase M5 — Consistency proof

### Task 5.1 — Fix `MathMlConverter` defects

- `mtable`: honour the source's fences and column alignment instead of always emitting
  `pmatrix`.
- `mover`: remove the unreachable duplicate `"^"` branch.
- Add round-trip tests per MathML element.

### Task 5.2 — Same-equation equivalence

- For a set of equations authored both as raw LaTeX and as MathML, assert the rendered
  result is pixel-identical (same `MathNode`, same boxes).
- Cover `mfrac`, `msqrt`, `mroot`, `msubsup`, `munderover`, `mfenced`, `mtable`, `mtext`.
- Document any converter lossiness explicitly as a test expectation, not a silent gap.

### Task 5.3 — M5 gate

Equivalence tests green; EPUB-origin and LaTeX-origin renders match; parser coverage
regression tests for `align`/`matrix`/`cases` pass.

---

## Phase M6 — PDF geometry reconstruction

Blocked on a user decision (fail-import vs marked-region). Do not start until answered.
Scope sketch (detailed plan to follow that decision): capture glyph positions and painted
rules in `PdfBoxPageSource`, extend `PdfPage` with defaulted `glyphs`/`rules`, reconstruct
fractions/scripts before flattening in `PdfAssembler`, keep prose reflow untouched.

---

## Risks

| Risk | Mitigation |
|---|---|
| Inline layout in justified text is much harder than it looks | M4.3 is isolated; the engine measurement tree (4.1) lands first; Task 9 tests guard selection |
| RTL Hebrew corpus exposes bidi issues | Dedicated bidi smoke test in 4.3; full bidi may be a follow-up (builder decision) |
| Golden drift when TeX versions differ | `--check` mode + `VERSIONS.txt` pinning |
| Robolectric raster differs from device AA | Primary signal is boxes/gaps in TeX points, not pixels |
| Fidelity gate not enforced in CI | Release checklist requires a local golden run; stated in the design |
| Scope: ~7k lines is the KOReader comparable | Milestones are independently shippable; M2 and M3 each produce visible improvement |
