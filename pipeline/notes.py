"""Extraction of the raw note text from the source PDF.

The notes are a Google Docs export, so the text layer is intact and there is no
OCR step. The 131 images embedded in the file are 80x76px emoji glyphs, which
carry no vocabulary, so they are ignored.
"""

from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_PDF = Path.home() / "Downloads" / "Untitled document (2) (1).pdf"
CACHE = REPO / "content" / "notes.txt"


@dataclass(frozen=True)
class SourceLine:
    """One line of the notes, with enough provenance to find it again on paper."""

    page: int
    line: int  # 1-based within the page
    text: str

    @property
    def ref(self) -> str:
        return f"p{self.page}:{self.line}"


def extract(pdf: Path = DEFAULT_PDF, refresh: bool = False) -> str:
    """Return the note text, extracting from the PDF the first time only.

    pdftotext keeps form feeds between pages, which is the only page signal the
    export gives us, so the cache preserves them verbatim.
    """
    if CACHE.exists() and not refresh:
        return CACHE.read_text(encoding="utf-8")

    if not pdf.exists():
        raise FileNotFoundError(f"source notes not found: {pdf}")
    if not shutil.which("pdftotext"):
        raise RuntimeError("pdftotext not on PATH (brew install poppler)")

    out = subprocess.run(
        ["pdftotext", "-layout", str(pdf), "-"],
        capture_output=True,
        check=True,
    )
    text = out.stdout.decode("utf-8")
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    CACHE.write_text(text, encoding="utf-8")
    return text


def source_lines(pdf: Path = DEFAULT_PDF, refresh: bool = False) -> list[SourceLine]:
    """Split the notes into lines carrying their page and line number.

    Blank lines are kept: a blank line is the only grouping signal in the notes,
    separating a topic cluster from the next one.
    """
    text = extract(pdf, refresh)
    lines: list[SourceLine] = []
    for page_no, page in enumerate(text.split("\f"), start=1):
        for line_no, raw in enumerate(page.split("\n"), start=1):
            lines.append(SourceLine(page=page_no, line=line_no, text=raw.rstrip()))
    return lines
