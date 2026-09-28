"""Turning note lines into structured entries.

The notes were written for the author's own eyes, so the format is consistent in
spirit but not in punctuation. Every rule in this module was derived by reading
the actual file rather than from a spec, and the classifier is deliberately
conservative: anything it cannot confidently read is flagged for review instead
of being guessed at and silently imported.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass, field
from enum import Enum

from notes import SourceLine

HAN = r"㐀-䶿一-鿿"
RE_HAN = re.compile(f"[{HAN}]")
RE_HAN_RUN = re.compile(f"^([{HAN}]+)")
RE_LATIN = re.compile(r"[A-Za-z]")

# Full-width and half-width forms of the same punctuation appear interchangeably.
OPEN_PAREN = "（("
CLOSE_PAREN = "）)"
RE_PAREN = re.compile(r"[（(]([^）)]*)[）)]")

# Sentence-final and clause punctuation. Their presence in a line with no latin
# gloss is the strongest signal that the line is an example, not a headword.
RE_SENT_PUNCT = re.compile(r"[。？！]")
RE_GRAMMAR_MARKER = re.compile(r"[我你他她们的了是在不很也都呢吗吧把被给就还]")

# The author writes ellipses as runs of ideographic full stops or ASCII dots,
# often mixed: 正在。。。(呢), 通过=因为。。。所以, 。。..。
RE_ELLIPSIS = re.compile(r"[。.]{2,}")

# The author writes an antonym pair as A><B: 全职><兼职.
RE_ANTONYM = re.compile(r"><\s*([" + HAN + r"]{1,8})")

# Characters that should never appear in hand-written Chinese notes. Their
# presence means a typo or a mangled paste, so the entry goes to review. The
# angle brackets of an antonym pair are stripped before this runs.
RE_SUSPECT_CHARS = re.compile(r"[#*\[\]{}<>\\|~^`]")

# A few entries are written the other way round, with the gloss first:
# "meeting会议", "T恤". The latin part is short and the Chinese follows it.
RE_GLOSS_FIRST = re.compile(r"^([A-Za-z][A-Za-z ]{0,14}?)\s*([" + HAN + r"]{1,8})$")


class Kind(Enum):
    BLANK = "blank"
    ENTRY = "entry"          # headword + optional gloss
    CLUSTER = "cluster"      # several headwords on one line: 认为，以为，觉得
    SENTENCE = "sentence"    # example sentence belonging to what precedes it
    PATTERN = "pattern"      # grammar formula: S+（V+）O+V+得+Adj+AP
    MARKER = "marker"        # latin-only section label: "grammar", "Contoh"
    UNKNOWN = "unknown"


@dataclass
class Gloss:
    text: str
    lang: str  # "id" | "en" | "mixed"


@dataclass
class Entry:
    hanzi: str
    glosses: list[Gloss] = field(default_factory=list)
    usage_notes: list[str] = field(default_factory=list)
    relations: list[tuple[str, str]] = field(default_factory=list)  # (kind, hanzi)
    examples: list[str] = field(default_factory=list)
    page: int = 0
    line: int = 0
    raw: str = ""
    flags: list[str] = field(default_factory=list)

    @property
    def needs_review(self) -> bool:
        return bool(self.flags)


@dataclass
class Pattern:
    formula: str
    examples: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)
    page: int = 0
    line: int = 0


# --------------------------------------------------------------------------
# Language detection
# --------------------------------------------------------------------------

# The glosses are overwhelmingly Indonesian, frequently informal, and sometimes
# English. Rather than pull in a language-detection dependency for strings that
# are often a single word, we score against markers that actually occur in this
# file.
_ID_WORDS = {
    "yang", "tidak", "bisa", "ga", "gak", "untuk", "banyak", "orang", "jalan",
    "sangat", "kerja", "satuan", "dari", "dan", "atau", "ke", "di", "dengan",
    "sudah", "belum", "juga", "lebih", "kurang", "saja", "aja", "kalau", "kalo",
    "biasa", "sekitar", "selain", "itu", "ini", "pagi", "malam", "siang",
    "rumah", "uang", "anak", "makan", "minum", "pergi", "datang", "punya",
    "bikin", "buat", "sama", "kayak", "gitu", "gini", "banget", "dong", "nih",
    "pelayan", "pengacara", "hukum", "harus", "masuk", "keluar", "naik", "turun",
    "temenin", "nyapu", "ngantre", "ngantuk", "muji", "rame", "meriah", "malu",
    "hilang", "cantik", "telat", "selesai", "paham", "tau", "semula", "ternyata",
    "sebelah", "perbedaan", "kumpul", "kira", "jadi", "maka", "sehingga",
    "dibanding", "menyediakan", "persyaratan", "permintaan", "perbandingan",
    "penanggung", "jawab", "kelompok", "jenis", "jumlah", "tempat", "tinggal",
    "lagi", "pas", "sedang", "tentu", "memang", "begini", "opini", "berbeda",
}
_EN_WORDS = {
    "the", "to", "of", "a", "an", "is", "are", "be", "very", "good", "make",
    "with", "for", "from", "and", "or", "not", "in", "on", "at", "by", "it",
    "that", "this", "have", "has", "do", "does", "can", "will", "would",
    "lake", "radio", "tour", "cash", "menu", "scan", "service", "deep",
    "familiar", "personality", "always", "finally", "remember", "graduation",
    "chef", "professor", "management", "standard", "support", "attitude",
    "future", "ideal", "prize", "actually", "impression", "beauties", "edit",
}
# Indonesian derivational morphology is distinctive enough to carry the decision
# when the word itself is not in the list above.
_ID_AFFIX = re.compile(
    r"^(me[mnrlwy]?|ber|di|ter|pe[mnrlwy]?|ke)\w{3,}|"
    r"\w{3,}(kan|nya|an|in)$",
    re.I,
)


def detect_lang(text: str) -> str:
    tokens = [t for t in re.split(r"[^A-Za-z]+", text.lower()) if t]
    if not tokens:
        return "id"
    id_score = sum(1 for t in tokens if t in _ID_WORDS)
    en_score = sum(1 for t in tokens if t in _EN_WORDS)
    id_score += sum(0.5 for t in tokens if t not in _ID_WORDS and _ID_AFFIX.match(t))
    if id_score and en_score:
        return "mixed"
    if en_score > id_score:
        return "en"
    # Indonesian is the default: it is the language the notes were written in.
    return "id"


# --------------------------------------------------------------------------
# Line classification
# --------------------------------------------------------------------------

def classify(text: str) -> Kind:
    s = text.strip()
    if not s:
        return Kind.BLANK

    hanzi = RE_HAN.findall(s)
    if not hanzi:
        return Kind.MARKER if RE_LATIN.search(s) else Kind.UNKNOWN

    latin = RE_LATIN.findall(s)

    # A formula names slots rather than words: S+V+O, nomor+多+Satuan. The plus
    # sign is the giveaway, and formulas are always short.
    if "+" in s and len(hanzi) < 12:
        return Kind.PATTERN

    # An ellipsis stands for an omitted slot, which only happens in patterns.
    if RE_ELLIPSIS.search(s) and len(hanzi) < 12:
        return Kind.PATTERN

    # Several headwords separated by Chinese punctuation, no gloss attached:
    # 认为，以为，觉得 or 那儿/这儿. Kept apart from sentences by being short and
    # by every segment being a plausible word.
    if not latin and _is_cluster(s):
        return Kind.CLUSTER

    # An example sentence: long, entirely Chinese, and carrying the function
    # words that only appear in running text.
    if not latin and len(hanzi) >= 6 and (
        RE_SENT_PUNCT.search(s) or RE_GRAMMAR_MARKER.search(s)
    ):
        return Kind.SENTENCE

    if RE_HAN_RUN.match(s) or RE_GLOSS_FIRST.match(s):
        return Kind.ENTRY

    return Kind.UNKNOWN


def _is_cluster(s: str) -> bool:
    parts = [p for p in re.split(r"[，、/]", s) if p]
    if len(parts) < 2:
        return False
    # Every part must be a short all-hanzi run for this to be a list of words
    # rather than a sentence that happens to contain a comma.
    return all(1 <= len(p) <= 4 and RE_HAN.fullmatch(p[0]) and
               not RE_LATIN.search(p) and len(RE_HAN.findall(p)) == len(p)
               for p in parts)


# --------------------------------------------------------------------------
# Entry parsing
# --------------------------------------------------------------------------

def parse_entry(src: SourceLine) -> Entry | None:
    """Pull one headword and everything the author attached to it out of a line."""
    s = src.text.strip()

    # Gloss-first entries ("meeting会议") are rare but real; normalise them into
    # the usual headword-first shape before anything else looks at the line.
    flipped = RE_GLOSS_FIRST.match(s)
    if flipped and not RE_HAN_RUN.match(s):
        gloss, hanzi = flipped.group(1).strip(), flipped.group(2)
        entry = Entry(hanzi=hanzi, page=src.page, line=src.line, raw=s)
        entry.glosses.append(Gloss(text=gloss, lang=detect_lang(gloss)))
        return entry

    m = RE_HAN_RUN.match(s)
    if not m:
        return None

    entry = Entry(hanzi=m.group(1), page=src.page, line=src.line, raw=s)
    rest = s[m.end():].strip()

    # An antonym pair is marked A><B. Consume it before the suspect-character
    # check, which would otherwise flag the angle brackets.
    for anti in RE_ANTONYM.findall(rest):
        entry.relations.append(("antonym", anti))
    rest = RE_ANTONYM.sub(" ", rest)

    if RE_SUSPECT_CHARS.search(entry.hanzi + rest):
        entry.flags.append("suspect-characters")

    # Headwords longer than six characters are almost always a sentence that the
    # classifier let through, or a phrase worth checking by eye.
    if len(entry.hanzi) > 6:
        entry.flags.append("long-headword")

    # 1. Parentheticals are usage notes, not meanings:
    #    刚baru baru aja （不能在前面）（可以用时间）
    for note in RE_PAREN.findall(rest):
        note = note.strip()
        if note:
            entry.usage_notes.append(note)
    rest = RE_PAREN.sub(" ", rest)

    # 2. A colon introduces examples the author wrote inline:
    #    接menerima：接电话, 接到电话
    if re.search(r"[：:]", rest):
        head, tail = re.split(r"[：:]", rest, maxsplit=1)
        for candidate in re.split(r"[,，、/]", tail):
            candidate = candidate.strip()
            if candidate and RE_HAN.search(candidate) and not RE_LATIN.search(candidate):
                entry.examples.append(candidate)
        rest = head

    # 3. An equals sign links to another word, but only when its right-hand side
    #    is Chinese. "多=tanya Jumlah" is a definition, and "吵=Verb" (already
    #    stripped as a parenthetical above) is a part-of-speech note.
    while True:
        eq = re.search(r"=\s*([" + HAN + r"]{1,8})", rest)
        if not eq:
            break
        entry.relations.append(("variant", eq.group(1)))
        rest = rest[: eq.start()] + " " + rest[eq.end():]

    # Anything Chinese still sitting in the tail is a cross-reference the author
    # wrote without an equals sign; keep it rather than folding it into a gloss.
    for extra in re.findall(r"[" + HAN + r"]{2,8}", rest):
        entry.relations.append(("see-also", extra))
    rest = re.sub(r"[" + HAN + r"]+", " ", rest)

    # 4. What remains is the gloss, possibly several alternatives.
    for chunk in re.split(r"[/,;，、]| {2,}", rest):
        chunk = _clean_gloss(chunk)
        if chunk:
            entry.glosses.append(Gloss(text=chunk, lang=detect_lang(chunk)))

    if not entry.glosses and not entry.relations:
        entry.flags.append("no-gloss")

    return entry


def _clean_gloss(text: str) -> str:
    text = unicodedata.normalize("NFKC", text)
    text = re.sub(r"[=:：]+", " ", text)
    text = re.sub(r"\s+", " ", text).strip(" .·-–—")
    # A gloss of one or two letters is debris from splitting, not a meaning.
    if len(text) < 2 and not text.isdigit():
        return ""
    return text


def split_cluster(src: SourceLine) -> list[Entry]:
    """Turn 认为，以为，觉得 into three entries that point at each other."""
    parts = [p.strip() for p in re.split(r"[，、/]", src.text.strip()) if p.strip()]
    entries = [
        Entry(hanzi=p, page=src.page, line=src.line, raw=src.text.strip(),
              flags=["no-gloss"])
        for p in parts
    ]
    for e in entries:
        for other in parts:
            if other != e.hanzi:
                e.relations.append(("synonym", other))
    return entries


# --------------------------------------------------------------------------
# Document assembly
# --------------------------------------------------------------------------

@dataclass
class Document:
    entries: list[Entry] = field(default_factory=list)
    patterns: list[Pattern] = field(default_factory=list)
    orphans: list[SourceLine] = field(default_factory=list)


def parse_document(lines: list[SourceLine]) -> Document:
    """Walk the notes in order, attaching loose lines to what they belong to.

    Order carries meaning here. An example sentence sits under the word or
    pattern it illustrates, and a stray latin line ("positif=zhe") is a note on
    whatever came just before it. A blank line closes the current topic, which
    is the author's only explicit grouping signal.
    """
    doc = Document()
    current: Entry | Pattern | None = None

    for src in lines:
        kind = classify(src.text)

        if kind is Kind.BLANK:
            current = None
            continue

        if kind is Kind.ENTRY:
            entry = parse_entry(src)
            if entry is None:
                doc.orphans.append(src)
                continue
            doc.entries.append(entry)
            current = entry

        elif kind is Kind.CLUSTER:
            group = split_cluster(src)
            doc.entries.extend(group)
            current = group[-1] if group else None

        elif kind is Kind.PATTERN:
            pattern = Pattern(formula=src.text.strip(), page=src.page, line=src.line)
            doc.patterns.append(pattern)
            current = pattern

        elif kind is Kind.SENTENCE:
            # An example belongs to the nearest thing above it. With nothing
            # above it, it is a sentence the author noted on its own; keep it as
            # an orphan rather than inventing an owner for it.
            if isinstance(current, (Entry, Pattern)):
                current.examples.append(src.text.strip())
            else:
                doc.orphans.append(src)

        elif kind is Kind.MARKER:
            text = src.text.strip()
            if _resets_topic(text):
                current = None
            elif _is_section_label(text):
                pass  # announces what follows; the current topic carries on
            elif isinstance(current, Entry):
                current.usage_notes.append(text)
            elif isinstance(current, Pattern):
                current.notes.append(text)
            else:
                doc.orphans.append(src)

        else:
            doc.orphans.append(src)

    return doc


# Labels that open a new topic, so whatever follows belongs to nothing above.
_RESET_LABELS = {"grammar", "tata bahasa"}

# Labels that announce what comes next without breaking the current topic.
# "Contoh" is Indonesian for "example", so the sentences under it illustrate the
# pattern above it — treating it as a reset orphaned those sentences.
_CONTINUE_LABELS = {"contoh", "example", "examples", "catatan", "negative",
                    "negatif", "positif", "pertanyaan", "question"}


def _is_section_label(text: str) -> bool:
    """True when the line organises the notes rather than annotating the line above."""
    key = text.strip().strip(":：").lower()
    return key in _RESET_LABELS or key in _CONTINUE_LABELS


def _resets_topic(text: str) -> bool:
    return text.strip().strip(":：").lower() in _RESET_LABELS
