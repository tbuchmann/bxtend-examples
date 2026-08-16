#!/usr/bin/env python3
"""
code_metrics.py — measures hand-written implementation effort per BXtend
transformation example, counting only .xtend source files.

Classification policy
----------------------
Not everything under src/**/*.xtend is hand-written. Two kinds of generated
boilerplate are excluded from the counts:

1. Elem2Elem.xtend (one per project) is the abstract base rule class:
   resource handles, factory/package singletons, the elementsToCorr lookup
   table, and the getOrCreate*/create* helper methods. Its shape is
   identical across every example (only metamodel names differ) and would
   be emitted by a generator from the metamodel pairing, so the whole file
   is excluded.

2. "<Example>Transformation.xtend" (one per project) is the top-level
   orchestrator. It mixes generated scaffolding with genuine hand-written
   reconciliation logic. The methods listed in SKELETON_METHOD_NAMES are
   the scaffolding — resource wiring, rule-list assembly, the plain
   forward/backward driver loops, and the generic dangling-correspondence
   cleanup — the same shape in every example, parameterized only by which
   rule classes are wired in. Everything else in this file (synch(),
   configure(), and any project-specific helper method) is hand-written
   design/implementation work and stays in the count.

Every remaining .xtend file (the concrete "X2Y" mapping rules, and any
decision-strategy classes) is counted in full — that is the actual
transformation logic this project set out to measure.

package/import lines are stripped from every counted file before any
metric is computed, per the requesting instructions.

This is a heuristic line/word/character counter based on brace-depth
tracking, not a certified SLOC tool — it is tuned to this codebase's
consistent Xtend formatting (single-line method signatures ending in "{",
no braces inside string literals). Treat the numbers as good-faith
estimates of relative effort across the 8 examples, not as an audited
metric.
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

GENERATED_FILENAMES = {"Elem2Elem.xtend"}

ENTRY_CLASS_SUFFIX = "Transformation.xtend"

SKELETON_METHOD_NAMES = {
    "new",
    "addRules", "addRulesFwd", "addRulesBwd",
    "sourceToTarget", "targetToSource",
    "checkCorrespondences",
    "detectSourceDeletions", "detectTargetDeletions",
    "deleteUnreferencedTargetElements", "deleteUnreferencedSourceElements",
}

DEF_RE = re.compile(r"^(?:def|override)\s+(?:private\s+)?(?:static\s+)?(?:[\w\[\]<>]+\s+)*?(\w+)\s*\(")
CTOR_RE = re.compile(r"^new\s*\(")

STRING_LITERAL_RE = re.compile(r'"(?:[^"\\]|\\.)*"')


def strip_string_literals(line: str) -> str:
    """Blank out string literal contents so braces/quotes inside them don't
    confuse brace-depth tracking."""
    return STRING_LITERAL_RE.sub('""', line)


# ---------------------------------------------------------------------------
# Comment/blank classification
# ---------------------------------------------------------------------------

def classify_lines(lines: list[str]) -> list[str]:
    """Returns a parallel list tagging each line as 'blank', 'comment', or 'code'."""
    tags = []
    in_block_comment = False
    for raw in lines:
        stripped = raw.strip()
        if in_block_comment:
            tags.append("comment")
            if "*/" in stripped:
                in_block_comment = False
            continue
        if stripped == "":
            tags.append("blank")
            continue
        if stripped.startswith("//"):
            tags.append("comment")
            continue
        if stripped.startswith("/*") or stripped.startswith("*"):
            tags.append("comment")
            if "*/" not in stripped or stripped.count("/*") > stripped.count("*/"):
                in_block_comment = True
            continue
        tags.append("code")
    return tags


# ---------------------------------------------------------------------------
# Method-span detection (brace-depth walk), used only for the entry class
# ---------------------------------------------------------------------------

@dataclass
class MethodSpan:
    start: int  # inclusive line index (may include leading javadoc)
    end: int    # inclusive line index of closing brace
    name: str


def find_method_spans(lines: list[str], comment_tags: list[str]) -> list[MethodSpan]:
    depth = 0
    in_method = False
    entered_body = False
    method_start = None
    method_name = None
    spans: list[MethodSpan] = []

    for i, raw in enumerate(lines):
        line = strip_string_literals(raw)
        stripped = line.strip()

        if not in_method and depth == 1:
            m = DEF_RE.match(stripped)
            name = m.group(1) if m else ("new" if CTOR_RE.match(stripped) else None)
            if name:
                in_method = True
                entered_body = False
                method_start = i
                method_name = name

        depth += line.count("{") - line.count("}")

        if in_method:
            if depth > 1:
                entered_body = True
            if entered_body and depth == 1:
                spans.append(MethodSpan(method_start, i, method_name))
                in_method = False
                method_name = None

    # Extend each span backward over an immediately preceding, contiguous
    # comment block (its Javadoc), so the doc travels with the method.
    extended = []
    for span in spans:
        start = span.start
        while start - 1 >= 0 and comment_tags[start - 1] == "comment":
            start -= 1
        extended.append(MethodSpan(start, span.end, span.name))
    return extended


# ---------------------------------------------------------------------------
# Per-file metrics
# ---------------------------------------------------------------------------

@dataclass
class Metrics:
    loc: int = 0
    blank: int = 0
    comment: int = 0
    total_lines: int = 0
    # Pure code (comment lines excluded from these):
    words: int = 0
    chars: int = 0
    chars_nonspace: int = 0
    # Comment-only counterparts, kept separate:
    comment_words: int = 0
    comment_chars: int = 0
    comment_chars_nonspace: int = 0
    methods: int = 0

    def __iadd__(self, other: "Metrics") -> "Metrics":
        self.loc += other.loc
        self.blank += other.blank
        self.comment += other.comment
        self.total_lines += other.total_lines
        self.words += other.words
        self.chars += other.chars
        self.chars_nonspace += other.chars_nonspace
        self.comment_words += other.comment_words
        self.comment_chars += other.comment_chars
        self.comment_chars_nonspace += other.comment_chars_nonspace
        self.methods += other.methods
        return self


def metrics_for_lines(lines: list[str], tags: list[str], method_count: int) -> Metrics:
    m = Metrics()
    m.total_lines = len(lines)
    m.methods = method_count
    for line, tag in zip(lines, tags):
        if tag == "blank":
            m.blank += 1
            continue
        if tag == "comment":
            m.comment += 1
            m.comment_words += len(line.split())
            m.comment_chars += len(line)
            m.comment_chars_nonspace += len("".join(line.split()))
            continue
        m.loc += 1
        m.words += len(line.split())
        m.chars += len(line)
        m.chars_nonspace += len("".join(line.split()))
    return m


def strip_package_and_imports(text: str) -> list[str]:
    lines = text.splitlines()
    return [l for l in lines if not re.match(r"^\s*(package|import)\s", l)]


@dataclass
class FileReport:
    path: Path
    status: str  # "excluded", "handwritten", "mixed"
    metrics: Metrics = field(default_factory=Metrics)
    excluded_methods: list[str] = field(default_factory=list)
    kept_methods: list[str] = field(default_factory=list)
    excluded_raw_lines: int = 0


def analyze_file(path: Path) -> FileReport:
    text = path.read_text(encoding="utf-8")

    if path.name in GENERATED_FILENAMES:
        raw_lines = len(text.splitlines())
        return FileReport(path=path, status="excluded", excluded_raw_lines=raw_lines)

    lines = strip_package_and_imports(text)
    tags = classify_lines(lines)

    if path.name.endswith(ENTRY_CLASS_SUFFIX):
        spans = find_method_spans(lines, tags)
        excluded_idx: set[int] = set()
        excluded_methods, kept_methods = [], []
        for span in spans:
            if span.name in SKELETON_METHOD_NAMES:
                excluded_idx.update(range(span.start, span.end + 1))
                excluded_methods.append(span.name)
            else:
                kept_methods.append(span.name)

        kept_lines = [l for i, l in enumerate(lines) if i not in excluded_idx]
        kept_tags = [t for i, t in enumerate(tags) if i not in excluded_idx]
        metrics = metrics_for_lines(kept_lines, kept_tags, len(kept_methods))
        return FileReport(
            path=path, status="mixed", metrics=metrics,
            excluded_methods=excluded_methods, kept_methods=kept_methods,
            excluded_raw_lines=len(excluded_idx),
        )

    method_count = sum(1 for l in lines if DEF_RE.match(l.strip()) or CTOR_RE.match(l.strip()))
    metrics = metrics_for_lines(lines, tags, method_count)
    return FileReport(path=path, status="handwritten", metrics=metrics)


# ---------------------------------------------------------------------------
# Project-level report
# ---------------------------------------------------------------------------

@dataclass
class ProjectReport:
    name: str
    files: list[FileReport]

    @property
    def totals(self) -> Metrics:
        m = Metrics()
        for f in self.files:
            if f.status != "excluded":
                m += f.metrics
        return m


def analyze_project(project_dir: Path) -> ProjectReport:
    xtend_files = sorted(project_dir.glob("src/**/*.xtend"))
    reports = [analyze_file(p) for p in xtend_files]
    return ProjectReport(name=project_dir.name, files=reports)


# ---------------------------------------------------------------------------
# Markdown rendering
# ---------------------------------------------------------------------------

def rel(path: Path, project_dir: Path) -> str:
    return str(path.relative_to(project_dir))


def render_markdown(report: ProjectReport, project_dir: Path) -> str:
    t = report.totals
    lines = []
    lines.append(f"# Code Metrics — {report.name}")
    lines.append("")
    lines.append("_Generated by `code_metrics.py` (repo root). Counts only hand-written "
                  "`.xtend` source; see Methodology below for what is excluded and why._")
    lines.append("")
    lines.append("## Summary (pure code — comments excluded)")
    lines.append("")
    lines.append("| Metric | Value |")
    lines.append("|---|---|")
    lines.append(f"| Lines of code (LOC, non-blank/non-comment) | {t.loc} |")
    lines.append(f"| Blank lines | {t.blank} |")
    lines.append(f"| Total lines counted (code + blank + comment) | {t.total_lines} |")
    lines.append(f"| Words (code only) | {t.words} |")
    lines.append(f"| Characters, code only (incl. whitespace) | {t.chars} |")
    lines.append(f"| Characters, code only (non-whitespace) | {t.chars_nonspace} |")
    lines.append(f"| Hand-written methods | {t.methods} |")
    lines.append(f"| Avg. characters / word | {t.chars_nonspace / t.words:.2f} |" if t.words else "| Avg. characters / word | n/a |")
    lines.append(f"| Avg. LOC / method | {t.loc / t.methods:.1f} |" if t.methods else "| Avg. LOC / method | n/a |")
    lines.append("")
    lines.append("## Comments (excluded from the code metrics above, reported separately)")
    lines.append("")
    lines.append("| Metric | Value |")
    lines.append("|---|---|")
    lines.append(f"| Comment lines | {t.comment} |")
    lines.append(f"| Comment words | {t.comment_words} |")
    lines.append(f"| Comment characters (incl. whitespace) | {t.comment_chars} |")
    lines.append(f"| Comment characters (non-whitespace) | {t.comment_chars_nonspace} |")
    lines.append("")

    n_full = sum(1 for f in report.files if f.status == "handwritten")
    n_mixed = sum(1 for f in report.files if f.status == "mixed")
    n_excluded = sum(1 for f in report.files if f.status == "excluded")
    lines.append(f"Files: {n_full} fully hand-written, {n_mixed} mixed "
                 f"(generated skeleton stripped out), {n_excluded} fully generated/excluded "
                 f"— {len(report.files)} `.xtend` files total.")
    lines.append("")

    lines.append("## Per-file breakdown")
    lines.append("")
    lines.append("Code columns (LOC/Words/Chars) exclude comments; the two Comment columns report "
                 "comment-only word/character counts separately.")
    lines.append("")
    lines.append("| File | Status | LOC | Blank | Words | Chars | Methods | Comment Lines | Comment Words | Comment Chars |")
    lines.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|")
    for f in report.files:
        p = rel(f.path, project_dir)
        if f.status == "excluded":
            lines.append(f"| `{p}` | generated (excluded) | — | — | — | — | — | — | — | — |")
        else:
            m = f.metrics
            status = "handwritten" if f.status == "handwritten" else "mixed (skeleton stripped)"
            lines.append(f"| `{p}` | {status} | {m.loc} | {m.blank} | "
                         f"{m.words} | {m.chars} | {m.methods} | "
                         f"{m.comment} | {m.comment_words} | {m.comment_chars} |")
    lines.append("")

    mixed_files = [f for f in report.files if f.status == "mixed"]
    excluded_files = [f for f in report.files if f.status == "excluded"]
    if mixed_files or excluded_files:
        lines.append("## Excluded generated content (detail)")
        lines.append("")
        for f in excluded_files:
            lines.append(f"- `{rel(f.path, project_dir)}` — fully excluded, "
                         f"{f.excluded_raw_lines} raw lines (base rule class, identical shape "
                         f"across all examples).")
        for f in mixed_files:
            lines.append(f"- `{rel(f.path, project_dir)}` — {f.excluded_raw_lines} raw lines "
                         f"of generated skeleton excluded (methods: "
                         f"{', '.join(f.excluded_methods) if f.excluded_methods else 'none'}); "
                         f"hand-written methods kept: "
                         f"{', '.join(f.kept_methods) if f.kept_methods else 'none'}.")
        lines.append("")

    lines.append("## Methodology")
    lines.append("")
    lines.append("- Only `.xtend` files under `src/` are considered; all other file types are ignored.")
    lines.append("- `package` and `import` lines are stripped from every counted file before any metric is computed.")
    lines.append(f"- `Elem2Elem.xtend` is excluded entirely — identical-shape abstract base rule class in every example, would be generator output.")
    lines.append(f"- `<Example>Transformation.xtend` is counted, minus its generated-skeleton methods: "
                 f"`{'`, `'.join(sorted(SKELETON_METHOD_NAMES))}`. Everything else in that file "
                 f"(notably `synch()`, and any project-specific helper) is hand-written and counted.")
    lines.append("- LOC = non-blank, non-comment lines. Comment lines include `//` lines and `/* ... */`/Javadoc blocks (tracked line-by-line).")
    lines.append("- Words/Characters in the code metrics are computed from LOC lines only — comment text is excluded from those totals and reported separately under Comments.")
    lines.append("- Words = whitespace-split tokens. Characters counted both including and excluding internal whitespace.")
    lines.append("- Methods = `def`/`override`/constructor (`new(...)`) signatures found in the counted scope.")
    lines.append("- This is a heuristic brace-depth/regex-based counter tuned to this codebase's formatting conventions, not a certified SLOC tool.")
    lines.append("")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def find_projects(repo_root: Path) -> list[Path]:
    return sorted(p for p in repo_root.glob("de.tbuchmann.bxtend.*") if p.is_dir())


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("projects", nargs="*", help="Project directories to analyze (default: all de.tbuchmann.bxtend.* under repo root)")
    parser.add_argument("--repo-root", default=".", help="Repo root to search for projects (default: cwd)")
    parser.add_argument("--write", action="store_true", help="Write METRICS.md into each project's root directory")
    parser.add_argument("--no-summary", action="store_true", help="Suppress the cross-project summary table on stdout")
    args = parser.parse_args()

    repo_root = Path(args.repo_root).resolve()
    project_dirs = [Path(p).resolve() for p in args.projects] if args.projects else find_projects(repo_root)

    if not project_dirs:
        print("No projects found.", file=sys.stderr)
        return 1

    reports = []
    for project_dir in project_dirs:
        report = analyze_project(project_dir)
        reports.append((project_dir, report))
        if args.write:
            out_path = project_dir / "METRICS.md"
            out_path.write_text(render_markdown(report, project_dir), encoding="utf-8")
            print(f"wrote {out_path}")

    if not args.no_summary:
        print()
        header = f"{'project':30} {'LOC':>7} {'words':>8} {'chars':>9} {'methods':>8} {'files':>6}"
        print(header)
        print("-" * len(header))
        for project_dir, report in reports:
            t = report.totals
            n_files = sum(1 for f in report.files if f.status != "excluded")
            print(f"{report.name:30} {t.loc:7} {t.words:8} {t.chars:9} {t.methods:8} {n_files:6}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
