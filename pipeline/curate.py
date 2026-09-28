"""Applying the curation layer to the parsed entries.

Order matters and is fixed:

  1. RENAME  — a mistyped headword becomes the word it was meant to be, so
               everything downstream sees the real word.
  2. DEMOTE  — sentences move under the word they illustrate.
  3. DROP    — what is left over that is not vocabulary goes away.
  4. GLOSSES — blanks are filled and shorthand is replaced.
  5. PINYIN  — corrected readings are marked trusted.

A correction that names a word the notes never contained is a mistake in the
curation file, not in the notes, so `check()` reports it rather than letting it
sit unnoticed.
"""

from __future__ import annotations

from dataclasses import dataclass

import curation
import entries as entry_table
import lex
import zh


@dataclass
class Report:
    renamed: int = 0
    demoted: int = 0
    dropped: int = 0
    glossed: int = 0
    written: int = 0
    repinned: int = 0
    unused: list[str] = None  # corrections that matched nothing

    def __str__(self) -> str:
        return (f"curation: {self.renamed} renamed, {self.demoted} demoted, "
                f"{self.dropped} dropped, {self.glossed} glossed, "
                f"{self.repinned} readings corrected")


def check() -> list[str]:
    """Contradictions inside the curation file itself."""
    problems = []

    # A renamed word must not also be dropped: the rename is what saves it.
    for key in curation.RENAME:
        if key in curation.DROP:
            problems.append(f"{key}: both RENAME and DROP — rename wins, drop is dead weight")

    # A demoted sentence is already removed from the vocabulary by the demotion.
    for key in curation.DEMOTE:
        if key in curation.DROP:
            problems.append(f"{key}: both DEMOTE and DROP — demote wins, drop is dead weight")

    # Glossing something that is dropped is wasted work and usually a mistake.
    for key in curation.GLOSSES:
        if key in curation.DROP and key not in curation.RENAME:
            problems.append(f"{key}: both GLOSSES and DROP — one of them is wrong")

    return problems


def apply(entries: list[lex.Entry]) -> tuple[list[lex.Entry], Report]:
    report = Report(unused=[])

    # Curation keys are written in simplified characters, but the notes mix
    # scripts — 開音樂 sits a few lines from 开. Normalising the headword first
    # is what makes a correction apply to both spellings; without it a fix for a
    # traditionally-written entry silently matches nothing.
    for entry in entries:
        entry.hanzi = zh.to_simplified(entry.hanzi)

    by_hanzi = {e.hanzi: e for e in entries}

    # 1. Rename ------------------------------------------------------------
    for wrong, right in curation.RENAME.items():
        entry = by_hanzi.get(wrong)
        if entry is None:
            continue
        entry.hanzi = right
        # The typo was the reason it was flagged; the corrected word is clean.
        entry.flags = [f for f in entry.flags if f != "suspect-characters"]
        report.renamed += 1

    # Renaming can collide with a word the notes already had (开始 appears both
    # correctly and as 刚#始). Merge rather than keeping two cards for one word.
    merged: dict[str, lex.Entry] = {}
    for entry in entries:
        existing = merged.get(entry.hanzi)
        if existing is None:
            merged[entry.hanzi] = entry
            continue
        seen = {g.text.lower() for g in existing.glosses}
        for g in entry.glosses:
            if g.text.lower() not in seen:
                existing.glosses.append(g)
        existing.usage_notes.extend(
            n for n in entry.usage_notes if n not in existing.usage_notes
        )
        existing.examples.extend(e for e in entry.examples if e not in existing.examples)
        existing.relations.extend(r for r in entry.relations if r not in existing.relations)
    entries = list(merged.values())
    by_hanzi = {e.hanzi: e for e in entries}

    # 2. Demote ------------------------------------------------------------
    for sentence, (owner, gloss) in curation.DEMOTE.items():
        source = by_hanzi.get(sentence)
        target = by_hanzi.get(owner)
        if target is None:
            # The owner may not exist as its own entry yet; GLOSSES usually adds
            # it. Create a bare entry so the example has somewhere to live.
            target = lex.Entry(hanzi=owner, page=0, line=0, raw="(added by curation)")
            entries.append(target)
            by_hanzi[owner] = target
        if sentence not in [e for e in target.examples]:
            target.examples.append(sentence)
        if source is not None:
            source.flags.append("demoted")
        report.demoted += 1

    # 3. Drop --------------------------------------------------------------
    doomed = set(curation.DROP) | set(entry_table.NOT_WORDS) | {
        s for s in curation.DEMOTE if s not in curation.RENAME
    }
    # A rename rescues a word from the drop list.
    doomed -= set(curation.RENAME.values())
    kept = []
    matched_drops: set[str] = set()
    for entry in entries:
        if entry.hanzi in doomed:
            report.dropped += 1
            matched_drops.add(entry.hanzi)
            continue
        kept.append(entry)
    entries = kept
    by_hanzi = {e.hanzi: e for e in entries}

    # 4. Gloss -------------------------------------------------------------
    for hanzi, (indonesian, english) in curation.GLOSSES.items():
        # A word that was just dropped must not be brought back by a gloss
        # written for it in an earlier pass — the drop is the later decision.
        if hanzi in doomed:
            continue
        entry = by_hanzi.get(hanzi)
        if entry is None:
            # The word is worth having even if the notes only ever mentioned it
            # in passing — several of these are the owners of demoted examples.
            entry = lex.Entry(hanzi=hanzi, page=0, line=0, raw="(added by curation)")
            entries.append(entry)
            by_hanzi[hanzi] = entry
        # A curated gloss replaces the raw one rather than joining it: the raw
        # one is what made the card unanswerable.
        entry.glosses = [
            lex.Gloss(text=indonesian, lang="id"),
            lex.Gloss(text=english, lang="en"),
        ]
        entry.flags = [f for f in entry.flags if f != "no-gloss"]
        report.glossed += 1

    # 5. Clean entries -----------------------------------------------------
    # A hand-written entry replaces the note's gloss outright and brings its own
    # example. Applied after GLOSSES so it wins where both cover a word.
    for hanzi, (indonesian, english, example, example_gloss) in entry_table.ENTRIES.items():
        if hanzi in doomed:
            continue
        entry = by_hanzi.get(hanzi)
        if entry is None:
            entry = lex.Entry(hanzi=hanzi, page=0, line=0, raw="(added by curation)")
            entries.append(entry)
            by_hanzi[hanzi] = entry
        entry.glosses = [
            lex.Gloss(text=indonesian, lang="id"),
            lex.Gloss(text=english, lang="en"),
        ]
        entry.flags = [f for f in entry.flags if f != "no-gloss"]
        # The written example replaces the notes' examples rather than joining
        # them. Sentences were attached to words by proximity on the page, which
        # got it right often enough to be worth doing and wrong often enough to
        # leave 烤鸭 illustrated by a sentence about translating. Now that every
        # word has a checked sentence, the guesses are only noise — except the
        # ones placed by hand in DEMOTE, which are kept.
        if example:
            demoted = [e for e in entry.examples if e in curation.DEMOTE]
            entry.examples = [example] + [e for e in demoted if e != example]
        report.written += 1

    # 6. Pinyin ------------------------------------------------------------
    # Stored on the entry for build.py to pick up, rather than applied here,
    # because the reading is generated after script normalisation.
    for hanzi in curation.PINYIN:
        if hanzi in by_hanzi:
            report.repinned += 1

    # A correction that matched nothing is a correction that is not doing its
    # job — usually a headword typed slightly differently from the one in the
    # notes, or an edit that was written against a line that had already moved.
    # Silent no-ops are the failure mode this whole file is most prone to.
    # A drop that fired removes the word, so "is it still here" cannot be the
    # test — what matters is whether it ever matched anything.
    present = {e.hanzi for e in entries}
    report.unused = sorted(
        (set(curation.DROP) - matched_drops - set(curation.RENAME))
        | (set(curation.PINYIN) - present)
    )

    return entries, report


def example_gloss_for(hanzi: str, sentence: str) -> str | None:
    """The translation that belongs to a hand-placed example, if there is one."""
    written = entry_table.ENTRIES.get(hanzi)
    if written and written[2] == sentence:
        return written[3]
    # Sentences moved under a word by DEMOTE carry their translation there.
    demoted = curation.DEMOTE.get(sentence)
    if demoted and demoted[0] == hanzi:
        return demoted[1]
    return None


def corrected_pinyin(hanzi: str) -> str | None:
    return curation.PINYIN.get(hanzi)
