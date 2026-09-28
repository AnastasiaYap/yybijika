"""Script normalisation and pinyin generation.

Order matters here and is not negotiable. The notes mix simplified and
traditional characters (收音機 and 开 sit a few lines apart), and pypinyin's
phrase dictionary is keyed on simplified forms only. Reading traditional text
directly gives wrong tones:

    開音樂  ->  "kāi yīn lè"    wrong: 樂 read in isolation
    开音乐  ->  "kāi yīn yuè"   right: matched as a phrase

So every string is converted to simplified before a reading is asked for.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from functools import lru_cache

from opencc import OpenCC
from pypinyin import Style, pinyin
from pypinyin.phrases_dict import phrases_dict
from pypinyin.pinyin_dict import pinyin_dict

_t2s = OpenCC("t2s")
_s2t = OpenCC("s2t")

RE_HAN = re.compile(r"[㐀-䶿一-鿿]")


def to_simplified(text: str) -> str:
    return _t2s.convert(text)


def to_traditional(text: str) -> str:
    return _s2t.convert(text)


def is_traditional(text: str) -> bool:
    """True when converting to simplified actually changes something."""
    return to_simplified(text) != text


@lru_cache(maxsize=4096)
def readings_for_char(ch: str) -> tuple[str, ...]:
    raw = pinyin_dict.get(ord(ch))
    return tuple(raw.split(",")) if raw else ()


def is_polyphonic(ch: str) -> bool:
    return len(readings_for_char(ch)) > 1


@dataclass(frozen=True)
class Reading:
    pinyin: str
    verified: bool
    reason: str = ""

    @property
    def syllables(self) -> list[str]:
        return self.pinyin.split()


def read(hanzi: str) -> Reading:
    """Generate pinyin for a word, and say honestly how much to trust it.

    pypinyin is right whenever it can match a whole phrase, and right on
    single-reading characters by construction. It guesses only when a word
    contains a polyphonic character and is not in its phrase dictionary — and
    there it is sometimes wrong (睡不着 comes back as "shuì bù zhe" rather than
    "zháo"). Those are exactly the words flagged for a second opinion, so the
    listening and typing exercises can prefer readings we trust.
    """
    simp = to_simplified(hanzi)
    syllables = [x[0] for x in pinyin(simp, style=Style.TONE)]
    text = " ".join(syllables)

    if simp in phrases_dict:
        return Reading(text, True, "phrase-dict")

    risky = [c for c in simp if RE_HAN.match(c) and is_polyphonic(c)]
    if risky:
        return Reading(text, False, f"polyphonic:{''.join(risky)}")

    if not RE_HAN.search(simp):
        return Reading(text, False, "no-hanzi")

    return Reading(text, True, "single-reading")


def read_sentence(text: str) -> str:
    """Pinyin for a whole line, with the punctuation left out.

    pypinyin passes punctuation through untouched, which puts a bare 。 in the
    middle of the romanisation where a reader expects only syllables. The Chinese
    line above it already shows the punctuation.
    """
    simp = to_simplified(text)
    syllables = [
        x[0] for x in pinyin(simp, style=Style.TONE)
        if RE_HAN.search(x[0]) or x[0].strip(PUNCTUATION)
    ]
    return " ".join(s for s in (t.strip(PUNCTUATION) for t in syllables) if s)


PUNCTUATION = "。，、；：？！“”‘’（）《》〈〉…—·.,;:?!\"'()[]"


def syllable_count_matches(hanzi: str, py: str) -> bool:
    """A reading must have exactly one syllable per Chinese character.

    This is the cheapest check that catches a mangled or hallucinated pinyin
    string, and it is the first gate anything generated has to pass.
    """
    return len(RE_HAN.findall(to_simplified(hanzi))) == len(py.split())
