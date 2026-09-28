"""Topic tags, derived from the notes rather than guessed.

Tags exist for one reason: a multiple-choice question is only a test if the wrong
answers are plausible. Four random words from a 1,260-word deck are trivially
easy to tell apart, so distractors are drawn from the same topic instead.

Two signals, both free:

  1. Position. The notes were written a topic at a time — a run about food, then
     a run about work, then one about weather. Words written near each other are
     related, and that is a stronger semantic signal than anything derivable from
     the characters themselves.

  2. The glosses. They are the author's own words, so matching keywords against
     them is matching against how she actually thinks about the vocabulary.
"""

from __future__ import annotations

import re

# Keyword topics, matched against the Indonesian and English glosses. Kept small
# and specific on purpose: a tag that catches half the deck groups nothing.
KEYWORD_TOPICS: dict[str, tuple[str, ...]] = {
    "food": (
        "makan", "minum", "nasi", "ayam", "sayur", "daging", "masak", "tumis",
        "menu", "restoran", "piring", "chef", "koki", "rasa", "manis", "asin",
        "pedas", "lapar", "haus", "eat", "drink", "food", "meal", "cook",
    ),
    "work": (
        "kerja", "kantor", "staff", "karyawan", "bos", "gaji", "magang",
        "perusahaan", "jabatan", "tugas", "rapat", "proyek", "work", "office",
        "salary", "manager", "management", "profession", "teknisi", "mekanik",
    ),
    "money": (
        "uang", "harga", "bayar", "beli", "jual", "murah", "mahal", "cash",
        "gratis", "untung", "rugi", "juta", "ribu", "money", "price", "pay",
        "buy", "sell", "cheap", "expensive",
    ),
    "travel": (
        "jalan", "pergi", "datang", "naik", "turun", "kereta", "bus", "pesawat",
        "hotel", "wisata", "liburan", "tur", "peta", "tiket", "travel", "trip",
        "train", "flight", "ticket", "tour",
    ),
    "time": (
        "waktu", "jam", "hari", "minggu", "bulan", "tahun", "pagi", "siang",
        "sore", "malam", "kemarin", "besok", "sekarang", "nanti", "lama",
        "telat", "time", "hour", "day", "week", "month", "year", "morning",
    ),
    "people": (
        "orang", "anak", "teman", "keluarga", "ibu", "ayah", "istri", "suami",
        "murid", "guru", "dokter", "pengacara", "person", "friend", "family",
        "teacher", "student", "doctor", "lawyer", "professor",
    ),
    "feeling": (
        "senang", "sedih", "marah", "takut", "malu", "kaget", "bosan", "capek",
        "ngantuk", "gembira", "ceria", "khawatir", "happy", "sad", "angry",
        "afraid", "tired", "bored", "feeling", "emotion",
    ),
    "study": (
        "belajar", "sekolah", "kuliah", "ujian", "pr", "buku", "baca", "tulis",
        "bahasa", "kata", "kalimat", "grammar", "study", "learn", "school",
        "exam", "book", "read", "write", "word", "sentence", "language",
    ),
    "body": (
        "badan", "kepala", "tangan", "kaki", "mata", "mulut", "sakit", "sehat",
        "obat", "rumah sakit", "body", "head", "hand", "foot", "eye", "sick",
        "health", "medicine",
    ),
    "home": (
        "rumah", "kamar", "dapur", "pintu", "jendela", "meja", "kursi", "lampu",
        "bersih", "kotor", "nyapu", "home", "room", "kitchen", "door", "window",
        "table", "chair", "lamp", "clean",
    ),
}


def keyword_topics(glosses: list[str]) -> set[str]:
    """Topics implied by what the author wrote as the meaning."""
    blob = " ".join(glosses).lower()
    words = set(re.split(r"[^a-z]+", blob))
    found = set()
    for topic, keywords in KEYWORD_TOPICS.items():
        for keyword in keywords:
            # Multi-word keywords need a substring test; single words must match
            # whole so "pr" does not fire on "proyek".
            if (" " in keyword and keyword in blob) or keyword in words:
                found.add(topic)
                break
    return found


# How many note lines count as "the same stretch of writing". The notes average
# roughly 45 lines a page, so this keeps a section to about half a page — long
# enough to hold a topic, short enough that it is still one.
SECTION_SPAN = 20


def section_tag(page: int, line: int) -> str:
    """A tag for the stretch of notes a word was written in.

    This is the signal that actually does the work: whatever the keyword lists
    miss, proximity catches, because the author wrote related words together.
    """
    return f"s{page:02d}{line // SECTION_SPAN}"


def tags_for(page: int, line: int, glosses: list[str]) -> set[str]:
    return {section_tag(page, line)} | keyword_topics(glosses)
