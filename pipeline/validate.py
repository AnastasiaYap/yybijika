"""Gates that generated content has to pass before a human looks at it.

A model asked for a thousand example sentences will produce some that are wrong
in boring, mechanical ways: a pinyin string with the wrong number of syllables,
an "example" that never uses the word, a gloss that just restates the characters.
Catching those automatically is what keeps the review queue worth reading — if
half the queue is obvious junk, the review stops being careful.

Everything here is a rejection rule. Passing means "worth a human's attention",
never "correct".
"""

from __future__ import annotations

import re
from dataclasses import dataclass

import zh

RE_HAN = zh.RE_HAN


@dataclass(frozen=True)
class Verdict:
    ok: bool
    reason: str = ""

    def __bool__(self) -> bool:
        return self.ok


OK = Verdict(True)


def _reject(reason: str) -> Verdict:
    return Verdict(False, reason)


# --------------------------------------------------------------------------
# Pinyin
# --------------------------------------------------------------------------

TONE_MARKS = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜüńňǹ"
RE_SYLLABLE = re.compile(rf"^[a-zA-Z{TONE_MARKS}]+[1-5]?$")


def check_pinyin(hanzi: str, py: str) -> Verdict:
    if not py or not py.strip():
        return _reject("empty")

    syllables = py.split()
    expected = len(RE_HAN.findall(zh.to_simplified(hanzi)))
    if len(syllables) != expected:
        return _reject(f"{len(syllables)} syllables for {expected} characters")

    for syllable in syllables:
        if not RE_SYLLABLE.match(syllable):
            return _reject(f"not a syllable: {syllable!r}")

    # A reading with no tone marks anywhere is almost always a model dropping
    # them rather than a genuinely toneless word.
    if expected > 1 and not any(c in TONE_MARKS for c in py):
        return _reject("no tone marks")

    return OK


# --------------------------------------------------------------------------
# Examples
# --------------------------------------------------------------------------

MIN_EXAMPLE_CHARS = 4
MAX_EXAMPLE_CHARS = 30


def check_example(hanzi: str, sentence: str) -> Verdict:
    if not sentence or not sentence.strip():
        return _reject("empty")

    simp = zh.to_simplified(sentence)
    target = zh.to_simplified(hanzi)

    # The whole point of the sentence is to show the word in use.
    if target not in simp:
        return _reject(f"does not contain {target}")

    chars = RE_HAN.findall(simp)
    if len(chars) < MIN_EXAMPLE_CHARS:
        return _reject(f"too short ({len(chars)} characters)")
    if len(chars) > MAX_EXAMPLE_CHARS:
        return _reject(f"too long ({len(chars)} characters)")

    # A "sentence" that is just the word again teaches nothing.
    if simp.strip("。！？，") == target:
        return _reject("is just the word")

    if re.search(r"[A-Za-z]{3,}", simp):
        return _reject("contains latin text")

    return OK


# --------------------------------------------------------------------------
# Glosses
# --------------------------------------------------------------------------

def check_gloss(hanzi: str, gloss: str, known: dict[str, list[str]]) -> Verdict:
    """Reject meanings that are not meanings.

    The specific failure worth catching is the compound restatement — glossing
    大学毕业 as "university graduation" when 大学 and 毕业 are already separate
    cards. It is not wrong, it is just not a new thing to learn, and a deck full
    of them wastes reviews.
    """
    if not gloss or not gloss.strip():
        return _reject("empty")

    text = gloss.strip()
    if len(text) < 2:
        return _reject("too short")
    if len(text) > 120:
        return _reject("too long")

    if RE_HAN.search(text):
        return _reject("gloss is in Chinese")

    # A gloss that is the concatenation of its parts' glosses.
    if len(hanzi) >= 4:
        parts = [hanzi[i:i + 2] for i in range(0, len(hanzi), 2)]
        part_glosses = [known.get(p, [None])[0] for p in parts]
        if all(part_glosses):
            joined = " ".join(g.lower() for g in part_glosses if g)
            if _similar(text.lower(), joined):
                return _reject("restates its parts")

    return OK


def _similar(a: str, b: str) -> bool:
    """Cheap bag-of-words overlap; good enough to catch restatement."""
    aw = set(re.split(r"[^a-z]+", a)) - {""}
    bw = set(re.split(r"[^a-z]+", b)) - {""}
    if not aw or not bw:
        return False
    return len(aw & bw) / len(aw | bw) > 0.6


# --------------------------------------------------------------------------
# Tone sandhi advisories
# --------------------------------------------------------------------------

def sandhi_notes(hanzi: str, py: str) -> list[str]:
    """Places where the written tone and the spoken tone differ.

    These are not rejections. Dictionaries cite 不 as bù and 一 as yī regardless
    of what follows, and that is the right thing to show on a card; the note just
    records that the spoken form shifts, so a reviewer is not surprised by it.
    """
    notes: list[str] = []
    simp = zh.to_simplified(hanzi)
    syllables = py.split()

    if "不" in simp and len(syllables) > 1:
        i = simp.index("不")
        if i + 1 < len(syllables) and _tone_of(syllables[i + 1]) == 4:
            notes.append("不 is said bú before a fourth tone")

    if "一" in simp and len(syllables) > 1:
        i = simp.index("一")
        if i + 1 < len(syllables):
            nxt = _tone_of(syllables[i + 1])
            if nxt == 4:
                notes.append("一 is said yí before a fourth tone")
            elif nxt in (1, 2, 3):
                notes.append("一 is said yì before a first, second or third tone")

    for i in range(len(syllables) - 1):
        if _tone_of(syllables[i]) == 3 and _tone_of(syllables[i + 1]) == 3:
            notes.append("a third tone before another is said as a second")
            break

    return notes


_TONE_TABLE = {
    1: "āēīōūǖ", 2: "áéíóúǘń", 3: "ǎěǐǒǔǚň", 4: "àèìòùǜǹ",
}


def _tone_of(syllable: str) -> int:
    for tone, marks in _TONE_TABLE.items():
        if any(m in syllable for m in marks):
            return tone
    if syllable and syllable[-1].isdigit():
        return int(syllable[-1])
    return 5
