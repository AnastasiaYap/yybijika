#!/usr/bin/env python3
"""Fill the deck's gaps with DeepSeek.

Reads the coverage view to find what is missing, asks the model for it in
batches, runs every answer through validate.py, and writes what survives to
staging.db.

Nothing here touches content.db. Accepting a suggestion is a separate, human
step — see review.py. That split is the whole safety model: a bad batch costs
you a review session, never the deck.

    export DEEPSEEK_API_KEY=...          (or put it in pipeline/.env)
    ./.venv/bin/python pipeline/enrich.py --need gloss --limit 50
    ./.venv/bin/python pipeline/review.py
"""

from __future__ import annotations

import argparse
import json
import os
import sqlite3
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import validate  # noqa: E402
import zh  # noqa: E402

REPO = Path(__file__).resolve().parent.parent
CONTENT_DB = REPO / "content" / "content.db"
STAGING_DB = REPO / "content" / "staging.db"
ENV_FILE = Path(__file__).resolve().parent / ".env"

API_URL = "https://api.deepseek.com/chat/completions"
MODEL = "deepseek-chat"
BATCH_SIZE = 25

STAGING_SCHEMA = """
CREATE TABLE IF NOT EXISTS suggestion (
    id         INTEGER PRIMARY KEY,
    kind       TEXT NOT NULL,          -- gloss | example | pinyin
    word_id    INTEGER NOT NULL,
    hanzi      TEXT NOT NULL,
    payload    TEXT NOT NULL,          -- JSON: the suggested content
    notes      TEXT,                   -- advisories worth showing the reviewer
    status     TEXT NOT NULL DEFAULT 'pending',   -- pending | accepted | rejected
    created_at INTEGER NOT NULL,
    UNIQUE (kind, word_id, payload)
);
CREATE INDEX IF NOT EXISTS idx_suggestion_status ON suggestion(status, kind);
"""


def api_key() -> str:
    key = os.environ.get("DEEPSEEK_API_KEY")
    if not key and ENV_FILE.exists():
        for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
            if line.startswith("DEEPSEEK_API_KEY="):
                key = line.split("=", 1)[1].strip().strip("'\"")
    if not key:
        raise SystemExit(
            "No DEEPSEEK_API_KEY. Put it in the environment or in pipeline/.env\n"
            "  echo 'DEEPSEEK_API_KEY=sk-...' > pipeline/.env"
        )
    return key


def ask(prompt: str, system: str, retries: int = 3) -> str:
    body = json.dumps({
        "model": MODEL,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.3,
        "response_format": {"type": "json_object"},
    }).encode("utf-8")

    request = urllib.request.Request(
        API_URL,
        data=body,
        headers={
            "Authorization": f"Bearer {api_key()}",
            "Content-Type": "application/json",
        },
    )

    last: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=180) as response:
                payload = json.loads(response.read().decode("utf-8"))
                return payload["choices"][0]["message"]["content"]
        except (urllib.error.URLError, KeyError, json.JSONDecodeError) as exc:
            last = exc
            # Plain linear backoff: the failures worth retrying here are rate
            # limits and transient network errors, both of which clear in
            # seconds.
            time.sleep(2 * (attempt + 1))
    raise SystemExit(f"DeepSeek request failed after {retries} tries: {last}")


# --------------------------------------------------------------------------

SYSTEM = (
    "You help maintain a personal Mandarin flashcard deck built from a learner's "
    "own handwritten notes. The learner is Indonesian and studies in Indonesian "
    "and English. Answer only with JSON matching the requested shape. Never "
    "invent a word that was not asked about. Prefer plain, everyday wording over "
    "textbook register."
)


def needed(conn: sqlite3.Connection, kind: str, limit: int) -> list[tuple[int, str, str]]:
    """Words missing one thing, in the order they appear in the notes."""
    queries = {
        "gloss": """SELECT w.id, w.hanzi, w.pinyin
                    FROM word w JOIN word_capability c ON c.word_id = w.id
                    WHERE c.sense_count = 0
                    ORDER BY w.source_page, w.source_line LIMIT ?""",
        "example": """SELECT w.id, w.hanzi, w.pinyin
                      FROM word w JOIN word_capability c ON c.word_id = w.id
                      WHERE c.cloze_count = 0 AND c.sense_count > 0
                      ORDER BY w.source_page, w.source_line LIMIT ?""",
        "pinyin": """SELECT w.id, w.hanzi, w.pinyin
                     FROM word w
                     WHERE w.pinyin_verified = 0
                     ORDER BY w.char_count DESC, w.id LIMIT ?""",
    }
    return conn.execute(queries[kind], (limit,)).fetchall()


def prompt_for(kind: str, rows: list[tuple[int, str, str]]) -> str:
    words = [{"hanzi": h, "pinyin": p} for _, h, p in rows]
    listing = json.dumps(words, ensure_ascii=False, indent=1)

    if kind == "gloss":
        return (
            "Give a short meaning for each word, in Indonesian and in English.\n"
            "Skip nothing. If a word is a set phrase, gloss the phrase, not its "
            "parts. Keep each gloss under ten words.\n\n"
            f"{listing}\n\n"
            'Reply as {"items":[{"hanzi":"...","id":"...","en":"..."}]} '
            'where "id" is the Indonesian meaning.'
        )

    if kind == "example":
        return (
            "Write one short example sentence for each word — the kind of "
            "sentence a learner at intermediate level would actually say. Each "
            "sentence must contain the word itself, be between 5 and 20 "
            "characters, and come with its pinyin and an Indonesian "
            "translation.\n\n"
            f"{listing}\n\n"
            'Reply as {"items":[{"hanzi":"...","zh":"...","pinyin":"...",'
            '"id":"..."}]}'
        )

    if kind == "pinyin":
        return (
            "Give the correct pinyin with tone marks for each word. These are "
            "words where an automatic tool may have picked the wrong reading of "
            "a polyphonic character, so read each one in the context of the word "
            "it forms. One syllable per character, space separated.\n"
            "The 'pinyin' field shows what the automatic tool produced; correct "
            "it where it is wrong and repeat it where it is right.\n\n"
            f"{listing}\n\n"
            'Reply as {"items":[{"hanzi":"...","pinyin":"...","changed":true}]}'
        )

    raise ValueError(kind)


def parse(raw: str) -> list[dict]:
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return []
    items = data.get("items", data if isinstance(data, list) else [])
    return [i for i in items if isinstance(i, dict)]


def stage(
    staging: sqlite3.Connection,
    kind: str,
    word_id: int,
    hanzi: str,
    payload: dict,
    notes: str | None,
) -> bool:
    try:
        staging.execute(
            """INSERT OR IGNORE INTO suggestion
               (kind, word_id, hanzi, payload, notes, created_at)
               VALUES (?,?,?,?,?,?)""",
            (kind, word_id, hanzi,
             json.dumps(payload, ensure_ascii=False, sort_keys=True),
             notes, int(time.time())),
        )
        return True
    except sqlite3.Error:
        return False


def run(kind: str, limit: int, dry_run: bool) -> int:
    content = sqlite3.connect(CONTENT_DB)
    rows = needed(content, kind, limit)
    if not rows:
        print(f"nothing missing a {kind}")
        return 0

    known_glosses: dict[str, list[str]] = {}
    for hanzi, gloss in content.execute(
        "SELECT w.hanzi, s.gloss FROM word w JOIN sense s ON s.word_id = w.id"
    ):
        known_glosses.setdefault(hanzi, []).append(gloss)

    staging = sqlite3.connect(STAGING_DB)
    staging.executescript(STAGING_SCHEMA)

    by_hanzi = {h: (i, p) for i, h, p in rows}
    staged = rejected = 0

    for start in range(0, len(rows), BATCH_SIZE):
        batch = rows[start:start + BATCH_SIZE]
        print(f"  asking about {len(batch)} words "
              f"({start + 1}-{start + len(batch)} of {len(rows)})…", flush=True)

        if dry_run:
            print(prompt_for(kind, batch)[:600])
            continue

        for item in parse(ask(prompt_for(kind, batch), SYSTEM)):
            hanzi = item.get("hanzi", "")
            if hanzi not in by_hanzi:
                rejected += 1
                continue
            word_id, current_pinyin = by_hanzi[hanzi]

            verdict, payload, notes = _check(
                kind, hanzi, item, current_pinyin, known_glosses
            )
            if not verdict:
                rejected += 1
                print(f"    rejected {hanzi}: {verdict.reason}")
                continue
            if stage(staging, kind, word_id, hanzi, payload, notes):
                staged += 1

        staging.commit()

    print(f"\nstaged {staged}, rejected {rejected} → {STAGING_DB.name}")
    if staged:
        print("review them with:  ./.venv/bin/python pipeline/review.py")
    return 0


def _check(kind, hanzi, item, current_pinyin, known):
    """Validate one suggestion, returning (verdict, payload, advisory notes)."""
    if kind == "gloss":
        gloss_id = (item.get("id") or "").strip()
        gloss_en = (item.get("en") or "").strip()
        for candidate in (gloss_id, gloss_en):
            verdict = validate.check_gloss(hanzi, candidate, known)
            if not verdict:
                return verdict, {}, None
        return validate.OK, {"id": gloss_id, "en": gloss_en}, None

    if kind == "example":
        sentence = (item.get("zh") or "").strip()
        verdict = validate.check_example(hanzi, sentence)
        if not verdict:
            return verdict, {}, None

        py = (item.get("pinyin") or "").strip()
        py_verdict = validate.check_pinyin(sentence, py)
        # A bad reading is not worth discarding a good sentence over: the
        # pipeline can generate one locally.
        if not py_verdict:
            py = zh.read(sentence).pinyin
        notes = "; ".join(validate.sandhi_notes(sentence, py)) or None
        return (validate.OK,
                {"zh": zh.to_simplified(sentence), "pinyin": py,
                 "gloss": (item.get("id") or "").strip()},
                notes)

    if kind == "pinyin":
        py = (item.get("pinyin") or "").strip()
        verdict = validate.check_pinyin(hanzi, py)
        if not verdict:
            return verdict, {}, None
        notes = "; ".join(validate.sandhi_notes(hanzi, py)) or None
        if py == current_pinyin:
            notes = "confirms the generated reading" + (f"; {notes}" if notes else "")
        else:
            notes = f"was {current_pinyin}" + (f"; {notes}" if notes else "")
        return validate.OK, {"pinyin": py}, notes

    raise ValueError(kind)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--need", required=True, choices=["gloss", "example", "pinyin"])
    ap.add_argument("--limit", type=int, default=50)
    ap.add_argument("--dry-run", action="store_true",
                    help="print the prompt instead of calling the API")
    args = ap.parse_args()

    if not CONTENT_DB.exists():
        raise SystemExit("run pipeline/build.py first")
    return run(args.need, args.limit, args.dry_run)


if __name__ == "__main__":
    raise SystemExit(main())
