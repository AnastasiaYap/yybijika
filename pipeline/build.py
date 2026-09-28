#!/usr/bin/env python3
"""Build content.db from the notes.

Rebuilt from scratch every run, so it is always reproducible from the source
PDF plus whatever enrichment has been reviewed and accepted.

    ./.venv/bin/python pipeline/build.py
"""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import curate  # noqa: E402
import essays  # noqa: E402
import lex  # noqa: E402
import relations  # noqa: E402
import segment  # noqa: E402
import notes  # noqa: E402
import tags  # noqa: E402
import zh  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
DB_PATH = REPO / "content" / "content.db"
SCHEMA = Path(__file__).resolve().parent / "schema.sql"

# A headword of five or more characters is a set phrase or a clause the author
# noted whole, not a vocabulary word. It still earns a card, but the exercise
# registry treats it differently: no single-character production drills.
PHRASE_THRESHOLD = 5


def merge_entries(entries: list[lex.Entry]) -> dict[str, lex.Entry]:
    """Fold repeated headwords into one entry carrying every sense.

    了解 appears five times across the notes, each time with a different gloss
    ("paham", "familiar paham tapi ga terlalu yakin", "=明白", ":名牌"). They are
    one word with several senses, not five words, and the first occurrence keeps
    the page reference.
    """
    merged: dict[str, lex.Entry] = {}
    for entry in entries:
        key = zh.to_simplified(entry.hanzi)
        if key not in merged:
            clone = lex.Entry(
                hanzi=key,
                glosses=list(entry.glosses),
                usage_notes=list(entry.usage_notes),
                relations=list(entry.relations),
                examples=list(entry.examples),
                page=entry.page,
                line=entry.line,
                raw=entry.raw,
                flags=list(entry.flags),
            )
            merged[key] = clone
            continue

        target = merged[key]
        seen = {g.text.lower() for g in target.glosses}
        for g in entry.glosses:
            if g.text.lower() not in seen:
                target.glosses.append(g)
                seen.add(g.text.lower())
        for note in entry.usage_notes:
            if note not in target.usage_notes:
                target.usage_notes.append(note)
        for rel in entry.relations:
            if rel not in target.relations:
                target.relations.append(rel)
        for ex in entry.examples:
            if ex not in target.examples:
                target.examples.append(ex)
        # A repeat that supplies a gloss clears the no-gloss flag raised earlier.
        if target.glosses and "no-gloss" in target.flags:
            target.flags.remove("no-gloss")
        for f in entry.flags:
            if f not in target.flags and f != "no-gloss":
                target.flags.append(f)

    return merged


def tokens_in(sentence: str) -> int:
    """Rough token count, used only to decide if a sentence can be rebuilt.

    The sentence builder needs enough pieces to be a puzzle rather than a
    giveaway. Counting characters overestimates, so this counts characters and
    lets the threshold absorb the slack — a real tokenizer would be false
    precision for a yes/no decision.
    """
    return len(zh.RE_HAN.findall(sentence))


def build(db_path: Path = DB_PATH, refresh: bool = False) -> sqlite3.Connection:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()

    conn = sqlite3.connect(db_path)
    conn.executescript(SCHEMA.read_text(encoding="utf-8"))

    doc = lex.parse_document(notes.source_lines(refresh=refresh))

    # The notes are what was written; curation is what was meant. Applying it
    # before merging means a renamed headword folds into the real word's entry
    # instead of sitting beside it as a near-duplicate.
    problems = curate.check()
    if problems:
        raise SystemExit(
            "curation.py contradicts itself:\n  " + "\n  ".join(problems)
        )
    entries, report = curate.apply(doc.entries)
    print(f"  {report}")
    for stale in report.unused:
        print(f"    ! correction for {stale!r} matched nothing")

    merged = merge_entries(entries)
    # Every headword becomes a tile the segmenter must not break apart.
    segment.load(merged.keys())

    word_ids: dict[str, int] = {}
    for hanzi, entry in merged.items():
        # A hand-corrected reading overrides the generated one and is trusted:
        # someone read it in context, which is exactly what the generator cannot do.
        corrected = curate.corrected_pinyin(hanzi)
        reading = (
            zh.Reading(corrected, True, "curated") if corrected else zh.read(hanzi)
        )
        trad = zh.to_traditional(hanzi)
        cur = conn.execute(
            """INSERT INTO word (hanzi, hanzi_trad, pinyin, pinyin_verified,
                                 pinyin_reason, char_count, is_phrase,
                                 source_page, source_line, needs_review, review_reason)
               VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
            (
                hanzi,
                trad if trad != hanzi else None,
                reading.pinyin,
                int(reading.verified),
                reading.reason,
                len(zh.RE_HAN.findall(hanzi)),
                int(len(zh.RE_HAN.findall(hanzi)) >= PHRASE_THRESHOLD),
                entry.page,
                entry.line,
                int(entry.needs_review),
                ",".join(entry.flags) or None,
            ),
        )
        word_ids[hanzi] = cur.lastrowid

    for hanzi, entry in merged.items():
        wid = word_ids[hanzi]

        for i, gloss in enumerate(entry.glosses):
            conn.execute(
                "INSERT INTO sense (word_id, gloss, lang, ordinal) VALUES (?,?,?,?)",
                (wid, gloss.text, gloss.lang, i),
            )

        for note in entry.usage_notes:
            conn.execute(
                "INSERT INTO usage_note (word_id, note) VALUES (?,?)", (wid, note)
            )

        for kind, other in entry.relations:
            other_simp = zh.to_simplified(other)
            conn.execute(
                """INSERT OR IGNORE INTO relation
                   (word_id, related_id, related_hanzi, kind, note)
                   VALUES (?,?,?,?,NULL)""",
                (wid, word_ids.get(other_simp), other_simp, kind),
            )

        for tag in sorted(tags.tags_for(entry.page, entry.line,
                                        [g.text for g in entry.glosses])):
            conn.execute(
                "INSERT OR IGNORE INTO tag (word_id, tag) VALUES (?,?)", (wid, tag)
            )

        # The same sentence can arrive twice with and without its full stop —
        # once from a demotion, once from the written entry. Compare on the bare
        # characters so the pair is recognised as one sentence.
        seen_examples: set[str] = set()
        for sentence in entry.examples:
            simp = zh.to_simplified(sentence)
            key = "".join(zh.RE_HAN.findall(simp))
            if key in seen_examples:
                continue
            seen_examples.add(key)
            conn.execute(
                """INSERT INTO example
                   (word_id, zh, pinyin, gloss, gloss_en, source,
                    contains_target, token_count, segments, segment_count)
                   VALUES (?,?,?,?,?,?,?,?,?,?)""",
                (
                    wid,
                    simp,
                    zh.read_sentence(simp),
                    *curate.example_glosses_for(hanzi, simp),
                    "written" if hanzi in curate.card_table.CARDS else "notes",
                    int(hanzi in simp),
                    tokens_in(simp),
                    " ".join(pieces := segment.segment(simp)),
                    len(pieces),
                ),
            )

    for pattern in doc.patterns:
        cur = conn.execute(
            "INSERT INTO pattern (formula, note, source_page, source_line) VALUES (?,?,?,?)",
            (
                pattern.formula,
                " · ".join(pattern.notes) or None,
                pattern.page,
                pattern.line,
            ),
        )
        pid = cur.lastrowid
        for sentence in pattern.examples:
            simp = zh.to_simplified(sentence)
            conn.execute(
                "INSERT INTO pattern_example (pattern_id, zh, pinyin) VALUES (?,?,?)",
                (pid, simp, zh.read(simp).pinyin),
            )

    # The relation network. Everything the data supports is computed; the links
    # that need a human are written out in relations.py.
    pinyin_of = {
        h: conn.execute("SELECT pinyin FROM word WHERE id = ?", (i,)).fetchone()[0]
        for h, i in word_ids.items()
    }

    def link(word: str, other: str, kind: str, note: str | None) -> None:
        if word not in word_ids or other not in word_ids:
            return
        conn.execute(
            """INSERT OR IGNORE INTO relation
               (word_id, related_id, related_hanzi, kind, note) VALUES (?,?,?,?,?)""",
            (word_ids[word], word_ids[other], other, kind, note or None),
        )

    for word, other, kind, note in relations.computed(pinyin_of):
        link(word, other, kind, note)

    for word, other, kind, ch in relations.shared_characters(list(word_ids)):
        link(word, other, kind, ch)

    for word, other, note in relations.SYNONYMS:
        link(word, other, "synonym", note)
        link(other, word, "synonym", note)

    for word, other, note in relations.ANTONYMS:
        link(word, other, "antonym", note)
        link(other, word, "antonym", note)

    # Measure words are the one link where the far side need not be vocabulary.
    # 间 and 座 are answers, not headwords, and requiring both ends to be in the
    # deck threw away two thirds of the pairs — the drill is about the noun.
    for noun, measure in relations.MEASURE_WORDS:
        if noun not in word_ids:
            continue
        conn.execute(
            """INSERT OR IGNORE INTO relation
               (word_id, related_id, related_hanzi, kind, note) VALUES (?,?,?,?,?)""",
            (word_ids[noun], word_ids.get(measure), measure, "measure",
             f"一{measure}{noun}"),
        )
        # The reverse direction is only interesting when the measure word is
        # itself something the notes taught.
        link(measure, noun, "measure", f"一{measure}{noun}")

    # Reading passages, written around words that are already in the deck.
    for essay in essays.ESSAYS:
        body = "".join(line for line, _ in essay["lines"])
        translation = " · ".join(
            [essay["title_id"]] + [gloss for _, gloss in essay["lines"]]
        )
        cur = conn.execute(
            """INSERT INTO passage (title, level, body, translation, source)
               VALUES (?,?,?,?, 'written')""",
            (essay["title"], essay["level"], body, translation),
        )
        pid = cur.lastrowid

        # One token per line rather than per word: the reader shows a line at a
        # time with its translation beneath, which is how the notes gloss too.
        for idx, (zh_line, gloss) in enumerate(essay["lines"]):
            conn.execute(
                """INSERT INTO passage_token (passage_id, idx, zh, pinyin, word_id)
                   VALUES (?,?,?,?,NULL)""",
                (pid, idx, zh_line, zh.read_sentence(zh_line)),
            )

        for q in essay["questions"]:
            conn.execute(
                """INSERT INTO passage_question
                   (passage_id, q, choices_json, answer, explain) VALUES (?,?,?,?,?)""",
                (pid, q["q"], json.dumps(q["choices"], ensure_ascii=False),
                 q["answer"], q.get("explain")),
            )

    for orphan in doc.orphans:
        conn.execute(
            "INSERT INTO orphan (page, line, text, reason) VALUES (?,?,?,?)",
            (orphan.page, orphan.line, orphan.text.strip(), "unclassified"),
        )

    conn.commit()
    return conn


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--refresh", action="store_true",
                    help="re-extract text from the source PDF")
    ap.add_argument("--out", type=Path, default=DB_PATH)
    args = ap.parse_args()

    conn = build(args.out, refresh=args.refresh)
    counts = {
        t: conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
        for t in ("word", "sense", "usage_note", "relation", "example",
                  "pattern", "pattern_example", "tag", "passage",
                  "passage_question", "orphan")
    }
    print(f"built {args.out.relative_to(REPO)}")
    for name, n in counts.items():
        print(f"  {name:16} {n:5}")
    conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
