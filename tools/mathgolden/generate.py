#!/usr/bin/env python3
"""Generate TeX golden references for the math rendering fidelity harness.

For every math case in ``cases/<name>.tex`` this produces:

  build/<name>-display.pdf / .glyphs.json / .png
  build/<name>-inline.pdf  / .glyphs.json / .png

The golden is compiled from the *raw* case LaTeX with ``lmodern`` so the output uses the
Latin Modern Math family that Verso bundles. Plain ``pdflatex`` would embed Computer
Modern, which has different glyph shapes and advance widths, and comparing against it
would measure font substitution instead of layout fidelity.

Glyph boxes come from ``pdftotext -bbox`` in TeX points; the raster comes from
``pdftoppm`` at 300 dpi. Both are secondary evidence next to the box/gap comparison.

Usage:
    python3 tools/mathgolden/generate.py              # regenerate all cases
    python3 tools/mathgolden/generate.py --only name  # one case
    python3 tools/mathgolden/generate.py --check      # fail if checked-in output drifts
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from xml.etree import ElementTree

HERE = Path(__file__).resolve().parent
CASES_DIR = HERE / "cases"
BUILD_DIR = HERE / "build"

# Base size the goldens are compiled at. The Kotlin side converts to its own pixel space.
BASE_FONT_PT = 12
RASTER_DPI = 300

WRAPPER = r"""\documentclass[%(size)dpt]{article}
\usepackage{lmodern}
\usepackage{amsmath,amssymb,mathtools}
\usepackage[active,tightpage]{preview}
\pagestyle{empty}
\begin{document}
\begin{preview}
%(body)s
\end{preview}
\end{document}
"""


@dataclass
class Glyph:
    char: str
    x_min: float
    y_min: float
    x_max: float
    y_max: float

    def as_dict(self) -> dict:
        return {
            "char": self.char,
            "xMin": round(self.x_min, 4),
            "yMin": round(self.y_min, 4),
            "xMax": round(self.x_max, 4),
            "yMax": round(self.y_max, 4),
        }


def require(tool: str) -> str:
    path = shutil.which(tool)
    if path is None:
        sys.exit(f"error: required tool '{tool}' not found on PATH")
    return path


def tex_version() -> str:
    out = subprocess.run(
        [require("pdflatex"), "--version"], capture_output=True, text=True, check=True
    ).stdout
    return out.splitlines()[0].strip()


def compile_case(name: str, mode: str, body: str, work: Path) -> Path:
    """Compile one case into ``work`` and return the produced PDF path."""
    tex = work / f"{name}-{mode}.tex"
    tex.write_text(WRAPPER % {"size": BASE_FONT_PT, "body": body}, encoding="utf-8")

    # Two passes are not required (no references), but keep it to one for speed;
    # preview's tightpage only needs a single pass.
    result = subprocess.run(
        [
            require("pdflatex"),
            "-interaction=nonstopmode",
            "-halt-on-error",
            "-output-directory",
            str(work),
            str(tex),
        ],
        capture_output=True,
        text=True,
        cwd=work,
    )
    pdf = work / f"{name}-{mode}.pdf"
    if result.returncode != 0 or not pdf.exists():
        log_tail = "\n".join(result.stdout.splitlines()[-25:])
        sys.exit(f"error: failed to compile case '{name}' ({mode}):\n{log_tail}")
    return pdf


def extract_glyphs(pdf: Path) -> tuple[float, float, list[Glyph]]:
    """Parse per-glyph boxes and page size from ``pdftotext -bbox``."""
    out = subprocess.run(
        [require("pdftotext"), "-bbox", str(pdf), "-"],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    # pdftotext emits raw C0 control characters for glyphs that have no Unicode mapping
    # (notably the stretched delimiters in LMMathExtension10). They are not legal XML and
    # also not meaningful characters, so remove them before parsing; the box itself is
    # still recorded (with a placeholder) and participates in gap comparison.
    sanitized, stripped = re.subn(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", out)
    if stripped:
        print(f"note: {pdf.name} contained {stripped} unmapped control character(s); "
              f"replaced with '?'", file=sys.stderr)
        sanitized = re.sub(r"(<word\b[^>]*>)</word>", r"\1?</word>", out)
        sanitized = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f]", "", sanitized)
    try:
        root = ElementTree.fromstring(sanitized)
    except ElementTree.ParseError as exc:
        # pdftotext can emit text that is not XML-valid (e.g. a raw control character
        # from an unusual glyph). Report the offending line so the case can be fixed
        # instead of failing with a bare parser error.
        lines = out.splitlines()
        offending = lines[17][:200] if len(lines) > 17 else "<short output>"
        raise SystemExit(
            f"error: pdftotext produced invalid XML for {pdf.name}: {exc}\n"
            f"  offending line: {offending}"
        ) from exc
    ns = {"x": "http://www.w3.org/1999/xhtml"}

    page = root.find(".//x:page", ns)
    if page is None:
        page = root.find(".//page")
    if page is None:
        sys.exit(f"error: no page element in pdftotext output for {pdf}")
    width = float(page.get("width", "0"))
    height = float(page.get("height", "0"))

    glyphs: list[Glyph] = []
    for word in page.iter():
        if not word.tag.endswith("word"):
            continue
        text = (word.text or "").strip()
        if not text:
            text = "?"
        if not text:
            continue
        # pdftotext groups runs into words; split into per-character boxes only when the
        # word is a single character. Multi-character words keep one combined box, which
        # is still valid for gap comparison between words.
        glyphs.append(
            Glyph(
                char=text,
                x_min=float(word.get("xMin", "0")),
                y_min=float(word.get("yMin", "0")),
                x_max=float(word.get("xMax", "0")),
                y_max=float(word.get("yMax", "0")),
            )
        )
    if not glyphs:
        sys.exit(f"error: no glyphs extracted from {pdf}")

    # pdftotext emits glyphs in its own extraction order, not visual order: fraction
    # numerators and superscripts can appear before the main line. Sort into a stable
    # reading order — primarily left-to-right by xMin, with a small vertical tolerance so
    # that a superscript is not pulled ahead of the base it sits next to — otherwise the
    # golden text is scrambled and every comparison is meaningless.
    ordered = sorted(glyphs, key=lambda g: (round(g.x_min, 1), -g.y_max))
    return width, height, ordered


def rasterize(pdf: Path, out_png: Path) -> None:
    subprocess.run(
        [
            require("pdftoppm"),
            "-r",
            str(RASTER_DPI),
            "-png",
            "-singlefile",
            str(pdf),
            str(out_png.with_suffix("")),
        ],
        capture_output=True,
        check=True,
    )


def load_cases(only: str | None) -> dict[str, str]:
    if not CASES_DIR.is_dir():
        sys.exit(f"error: no cases directory at {CASES_DIR}")
    cases: dict[str, str] = {}
    for path in sorted(CASES_DIR.glob("*.tex")):
        if only and path.stem != only:
            continue
        source = path.read_text(encoding="utf-8").strip()
        if not source:
            sys.exit(f"error: empty case {path.name}")
        if re.search(r"\\documentclass", source):
            sys.exit(f"error: case {path.name} must contain math only, not a document")
        cases[path.stem] = source
    if not cases:
        sys.exit("error: no cases found" + (f" matching '{only}'" if only else ""))
    return cases


def generate_into(target: Path, only: str | None) -> list[str]:
    cases = load_cases(only)
    target.mkdir(parents=True, exist_ok=True)
    written: list[str] = []
    with tempfile.TemporaryDirectory(prefix="mathgolden-") as tmp:
        work = Path(tmp)
        for name, source in cases.items():
            for mode, body in (
                ("display", rf"$\displaystyle {source}$"),
                ("inline", rf"${source}$"),
            ):
                pdf = compile_case(name, mode, body, work)
                width, height, glyphs = extract_glyphs(pdf)
                stem = f"{name}-{mode}"

                payload = {
                    "case": name,
                    "mode": mode,
                    "latex": source,
                    "baseFontPt": BASE_FONT_PT,
                    "rasterDpi": RASTER_DPI,
                    "pageWidth": round(width, 4),
                    "pageHeight": round(height, 4),
                    "glyphs": [g.as_dict() for g in glyphs],
                }
                (target / f"{stem}.glyphs.json").write_text(
                    json.dumps(payload, indent=2, ensure_ascii=False) + "\n",
                    encoding="utf-8",
                )
                shutil.copyfile(pdf, target / f"{stem}.pdf")
                rasterize(pdf, target / f"{stem}.png")
                written.append(stem)
    (target / "VERSIONS.txt").write_text(
        "generated by tools/mathgolden/generate.py\n" f"tex: {tex_version()}\n"
        f"baseFontPt: {BASE_FONT_PT}\nrasterDpi: {RASTER_DPI}\n",
        encoding="utf-8",
    )
    return written


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", help="generate a single case by name")
    parser.add_argument(
        "--check",
        action="store_true",
        help="regenerate elsewhere and fail if checked-in output differs",
    )
    args = parser.parse_args()

    if args.check:
        with tempfile.TemporaryDirectory(prefix="mathgolden-check-") as tmp:
            fresh = Path(tmp)
            generate_into(fresh, args.only)
            names = sorted(p.name for p in fresh.glob("*.glyphs.json"))
            stale = []
            for name in names:
                reference = BUILD_DIR / name
                candidate = fresh / name
                if not reference.exists():
                    stale.append(f"missing checked-in golden: {name}")
                elif json.loads(reference.read_text()) != json.loads(candidate.read_text()):
                    stale.append(f"golden drift: {name}")
            if stale:
                print("\n".join(stale), file=sys.stderr)
                return 1
            print(f"ok: {len(names)} golden files match")
            return 0

    written = generate_into(BUILD_DIR, args.only)
    print(f"generated {len(written)} golden sets in {BUILD_DIR}")
    for stem in written:
        print(f"  {stem}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
