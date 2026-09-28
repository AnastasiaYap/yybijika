#!/usr/bin/env python3
"""What the deck can and cannot do yet.

Prints the coverage table that drives every later decision: which exercises each
word can already produce, and what is missing. The "missing" column is the
enrichment backlog, in priority order.

    ./.venv/bin/python pipeline/report.py
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DB_PATH = REPO / "content" / "content.db"

# Mirrors the Kotlin registry in app/.../exercise/Registry.kt. Each entry is
# (id, skill, SQL predicate over word_capability). Keeping the predicate here in
# SQL and there in Kotlin is a duplication we accept: this side answers "how many
# words could do this", that side answers "can this word do this right now", and
# a test on each side asserts they agree on the totals.
EXERCISES = [
    ("recall_zh2gloss", "recognition", "sense_count > 0"),
    ("mcq_meaning",     "recognition", "sense_count > 0"),
    ("recall_gloss2zh", "production",  "sense_count > 0"),
    ("type_hanzi",      "production",  "sense_count > 0 AND pinyin_verified = 1"),
    ("listen_choose",   "listening",   "pinyin_verified = 1 AND sense_count > 0"),
    ("cloze_example",   "usage",       "cloze_count > 0"),
    ("build_sentence",  "production",  "builder_count > 0"),
]


def bar(pct: float, width: int = 28) -> str:
    filled = round(pct / 100 * width)
    return "█" * filled + "·" * (width - filled)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--db", type=Path, default=DB_PATH)
    ap.add_argument("--list", metavar="WHAT",
                    choices=["no-gloss", "unverified", "flagged", "orphans"],
                    help="print the actual rows behind one of the gaps")
    args = ap.parse_args()

    if not args.db.exists():
        print(f"no database at {args.db} — run pipeline/build.py first",
              file=sys.stderr)
        return 1

    conn = sqlite3.connect(args.db)
    conn.row_factory = sqlite3.Row
    total = conn.execute("SELECT COUNT(*) FROM word").fetchone()[0]

    if args.list:
        return _list(conn, args.list)

    print(f"\n  盈盈笔记卡 — deck report")
    print(f"  {total} words from the notes\n")

    print("  CONTENT")
    rows = [
        ("with a gloss", "SELECT COUNT(*) FROM word_capability WHERE sense_count > 0"),
        ("pinyin trusted", "SELECT COUNT(*) FROM word WHERE pinyin_verified = 1"),
        ("with an example", "SELECT COUNT(*) FROM word_capability WHERE cloze_count > 0"),
        ("with 2+ examples",
         "SELECT COUNT(*) FROM (SELECT word_id FROM example GROUP BY word_id HAVING COUNT(*) >= 2)"),
        ("examples in EN too",
         "SELECT COUNT(DISTINCT word_id) FROM example WHERE gloss_en IS NOT NULL AND gloss_en != ''"),
        ("with an explanation", "SELECT COUNT(DISTINCT word_id) FROM usage_note"),
        ("with a relation", "SELECT COUNT(DISTINCT word_id) FROM relation"),
        ("with a usage note", "SELECT COUNT(DISTINCT word_id) FROM usage_note"),
        ("tagged", "SELECT COUNT(*) FROM word_capability WHERE tag_count > 0"),
        ("flagged for review", "SELECT COUNT(*) FROM word WHERE needs_review = 1"),
    ]
    for label, sql in rows:
        n = conn.execute(sql).fetchone()[0]
        pct = n / total * 100 if total else 0
        print(f"    {label:20} {n:5} / {total}  {bar(pct)} {pct:5.1f}%")

    print("\n  EXERCISE COVERAGE")
    print(f"    {'exercise':18} {'skill':12} {'ready':>6} {'missing':>8}")
    for ex_id, skill, predicate in EXERCISES:
        n = conn.execute(
            f"SELECT COUNT(*) FROM word_capability WHERE {predicate}"
        ).fetchone()[0]
        pct = n / total * 100 if total else 0
        print(f"    {ex_id:18} {skill:12} {n:6} {total - n:8}  {bar(pct, 20)} {pct:5.1f}%")

    print("\n  BACKLOG (what enrichment should fill, most valuable first)")
    backlog = [
        ("glosses", "SELECT COUNT(*) FROM word_capability WHERE sense_count = 0",
         "unlocks every recognition and production exercise"),
        ("example sentences", "SELECT COUNT(*) FROM word_capability WHERE cloze_count = 0",
         "unlocks cloze and sentence building"),
        ("pinyin checks", "SELECT COUNT(*) FROM word WHERE pinyin_verified = 0",
         "unlocks listening and typing"),
        ("topic tags", "SELECT COUNT(*) FROM word_capability WHERE tag_count = 0",
         "makes multiple-choice distractors plausible"),
        ("an explanation",
         "SELECT COUNT(*) FROM word w WHERE NOT EXISTS"
         " (SELECT 1 FROM usage_note n WHERE n.word_id = w.id)",
         "tells you which near-synonym to use and when"),
        ("a second example",
         "SELECT COUNT(*) FROM (SELECT w.id FROM word w LEFT JOIN example e"
         " ON e.word_id = w.id GROUP BY w.id HAVING COUNT(e.id) < 2)",
         "one sentence shows a word once; two show its range"),
        ("English example glosses",
         "SELECT COUNT(*) FROM example WHERE gloss_en IS NULL OR gloss_en = ''",
         "examples currently translate to Indonesian only"),
    ]
    for label, sql, why in backlog:
        n = conn.execute(sql).fetchone()[0]
        print(f"    {n:5} words need {label:20} — {why}")

    orphans = conn.execute("SELECT COUNT(*) FROM orphan").fetchone()[0]
    print(f"\n  {orphans} note lines could not be classified "
          f"(pipeline/report.py --list orphans)\n")

    conn.close()
    return 0


def _list(conn: sqlite3.Connection, what: str) -> int:
    queries = {
        "no-gloss": """SELECT w.hanzi, w.pinyin, w.source_page, w.source_line
                       FROM word w JOIN word_capability c ON c.word_id = w.id
                       WHERE c.sense_count = 0 ORDER BY w.source_page, w.source_line""",
        "unverified": """SELECT hanzi, pinyin, pinyin_reason AS source_page, id AS source_line
                         FROM word WHERE pinyin_verified = 0 ORDER BY hanzi""",
        "flagged": """SELECT hanzi, review_reason AS pinyin, source_page, source_line
                      FROM word WHERE needs_review = 1 ORDER BY source_page""",
        "orphans": """SELECT text AS hanzi, reason AS pinyin, page AS source_page,
                             line AS source_line FROM orphan ORDER BY page, line""",
    }
    for row in conn.execute(queries[what]):
        print(f"  p{row['source_page']}:{row['source_line']:<4} "
              f"{row['hanzi']:<12} {row['pinyin'] or ''}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
