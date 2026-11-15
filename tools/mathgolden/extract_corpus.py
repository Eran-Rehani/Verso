#!/usr/bin/env python3
"""Extract candidate math cases from the user's private LaTeX corpus.

The corpus lives on a **read-only Google Drive mount** and contains private course
material, so this script never writes into the repository. Output goes to the ignored
`.gradle/course-pdf-test/corpus/` directory, where the user's existing private artifacts
already live.

Only math bodies are written out (the contents of `equation`, `align`, `cases`,
`pmatrix`, …). Hebrew prose, chapter structure, names and course identifiers are not
copied, so a reviewed subset can later be moved into the public golden cases.

Usage:
    python3 tools/mathgolden/extract_corpus.py
    python3 tools/mathgolden/extract_corpus.py --report
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import sys
from collections import Counter
from pathlib import Path

# No default path: the corpus is private material that lives on the maintainer's machine.
# Callers pass --corpus explicitly (or set MATHGOLDEN_CORPUS). Keeping a personal path here
# would hard-code one machine and leak a course identifier into a public repository.
DEFAULT_CORPUS = Path(os.environ.get("MATHGOLDEN_CORPUS", "")) if os.environ.get("MATHGOLDEN_CORPUS") else None
OUTPUT_DIR = Path(".gradle/course-pdf-test/corpus")

# Environments whose body is a self-contained math expression worth testing.
MATH_ENVS = [
    "equation", "equation*",
    "align", "align*",
    "gather", "gather*",
    "multline", "multline*",
    "cases", "aligned", "array",
    "pmatrix", "bmatrix", "Bmatrix", "vmatrix", "Vmatrix",
]

# Constructs we care about for coverage reporting.
CONSTRUCTS = [
    r"\\frac", r"\\sqrt", r"\\partial", r"\\nabla", r"\\sum", r"\\int",
    r"\\left", r"\\right", r"\\mathbf", r"\\mathbb", r"\\mathcal", r"\\text",
    r"\\hat", r"\\vec", r"\\dot", r"\\bar", r"\\begin\{pmatrix\}",
    r"\\begin\{align", r"\\begin\{cases\}", r"\\implies", r"\\cdot", r"\\times",
]

# Inline math: $...$ not preceded by a backslash, not `$$`.
INLINE_RE = re.compile(r"(?<!\\)(?<!\$)\$([^$]{2,300})\$(?!\$)")


def strip_comments(text: str) -> str:
    return re.sub(r"(?<!\\)%[^\n]*", "", text)


def extract_environments(text: str) -> list[tuple[str, str]]:
    """Return (env_name, body) pairs for every math environment."""
    found: list[tuple[str, str]] = []
    for env in MATH_ENVS:
        pattern = re.compile(
            r"\\begin\{" + re.escape(env) + r"\}(.*?)\\end\{" + re.escape(env) + r"\}",
            re.DOTALL,
        )
        for match in pattern.finditer(text):
            found.append((env, match.group(1).strip()))
    return found


def normalise(body: str) -> str:
    """Collapse whitespace so deduplication is meaningful."""
    body = re.sub(r"\\label\{[^}]*\}", "", body)
    body = re.sub(r"\s+", " ", body).strip()
    return body


def sanitise_for_case(body: str) -> str | None:
    """Reject bodies that are not self-contained math we can compile stand-alone."""
    if not body or len(body) > 400:
        return None
    # Drop labels, spacing hints that need package context, and alignment row markers
    # that only make sense inside a multi-row environment.
    if re.search(r"\\begin\{(document|figure|table|itemize|enumerate)\}", body):
        return None
    # Any Hebrew characters make it prose, not a pure math case.
    if re.search(r"[\u0590-\u05FF]", body):
        return None
    return body


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--corpus",
        type=Path,
        default=DEFAULT_CORPUS,
        help="directory of private .tex sources (or set MATHGOLDEN_CORPUS)",
    )
    parser.add_argument("--report", action="store_true", help="report only, write nothing")
    args = parser.parse_args()

    if args.corpus is None:
        sys.exit(
            "error: no corpus given. Pass --corpus <dir> or set MATHGOLDEN_CORPUS.\n"
            "       The corpus is private material and is intentionally not hard-coded."
        )
    if not args.corpus.is_dir():
        sys.exit(f"error: corpus directory not found: {args.corpus}")

    sources = sorted(args.corpus.glob("*.tex"))
    if not sources:
        sys.exit(f"error: no .tex sources under {args.corpus}")

    env_counter: Counter[str] = Counter()
    construct_counter: Counter[str] = Counter()
    unique: dict[str, str] = {}
    inline_pool: set[str] = set()
    total_lines = 0

    for source in sources:
        raw = source.read_text(encoding="utf-8", errors="replace")
        text = strip_comments(raw)
        total_lines += raw.count("\n")

        for name, construct in [(c, c) for c in CONSTRUCTS]:
            construct_counter[construct] += len(re.findall(name, text))

        for env, body in extract_environments(text):
            env_counter[env] += 1
            cleaned = sanitise_for_case(normalise(body))
            if cleaned is None:
                continue
            # Skip multi-row bodies: a stand-alone case must compile without context.
            if re.search(r"\\\\\\\\", cleaned):
                continue
            digest = hashlib.sha256(cleaned.encode()).hexdigest()[:12]
            unique.setdefault(digest, cleaned)

        for match in INLINE_RE.finditer(text):
            body = normalise(match.group(1))
            if sanitise_for_case(body) and "&" not in body:
                inline_pool.add(body)

    print(f"sources:        {len(sources)} ({total_lines} lines)")
    print(f"math envs:      {sum(env_counter.values())}")
    for env, count in env_counter.most_common():
        print(f"  {env:<12} {count}")
    print(f"constructs:     {dict(construct_counter.most_common(10))}")
    print(f"unique display: {len(unique)}")
    print(f"unique inline:  {len(inline_pool)}")

    if args.report:
        return 0

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for index, body in enumerate(sorted(unique.values())):
        (OUTPUT_DIR / f"display-{index:03d}.tex").write_text(body + "\n", encoding="utf-8")
    for index, body in enumerate(sorted(inline_pool)):
        (OUTPUT_DIR / f"inline-{index:03d}.tex").write_text(body + "\n", encoding="utf-8")

    summary = [f"sources={len(sources)} lines={total_lines}",
               f"unique_display={len(unique)} unique_inline={len(inline_pool)}", ""]
    summary += [f"{k}: {v}" for k, v in construct_counter.most_common()]
    (OUTPUT_DIR / "SUMMARY.txt").write_text("\n".join(summary) + "\n", encoding="utf-8")

    print(f"\nwrote {len(unique) + len(inline_pool)} candidate cases to {OUTPUT_DIR}")
    print("PRIVATE: this directory is git-ignored and must never be committed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
