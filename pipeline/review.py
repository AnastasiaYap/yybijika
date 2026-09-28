#!/usr/bin/env python3
"""Review staged suggestions and merge the accepted ones into the deck.

Nothing generated reaches content.db without passing through here. The deck is
the learner's own notes, and a model's guess sitting next to her own wording
should be something she put there on purpose.

    ./.venv/bin/python pipeline/review.py            # review pending items
    ./.venv/bin/python pipeline/review.py --merge    # write accepted ones in
    ./.venv/bin/python pipeline/review.py --status   # counts only
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

REPO = Path(__file__).resolve().parent.parent
CONTENT_DB = REPO / "content" / "content.db"
STAGING_DB = REPO / "content" / "staging.db"


def describe(kind: str, payload: dict) -> str:
    if kind == "gloss":
        return f"{payload.get('id', '')}  /  {payload.get('en', '')}"
    if kind == "example":
        return (f"{payload.get('zh', '')}\n        {payload.get('pinyin', '')}"
                f"\n        {payload.get('gloss', '')}")
    if kind == "pinyin":
        return payload.get("pinyin", "")
    return json.dumps(payload, ensure_ascii=False)


def review(staging: sqlite3.Connection, kind: str | None, limit: int) -> None:
    where = "status = 'pending'" + (" AND kind = ?" if kind else "")
    args = (kind, limit) if kind else (limit,)
    rows = staging.execute(
        f"SELECT id, kind, hanzi, payload, notes FROM suggestion "
        f"WHERE {where} ORDER BY kind, id LIMIT ?", args
    ).fetchall()

    if not rows:
        print("nothing pending")
        return

    print(f"\n{len(rows)} pending. "
          "[enter]=accept  n=reject  s=skip  q=quit and save\n")

    for sid, k, hanzi, payload_json, notes in rows:
        payload = json.loads(payload_json)
        print(f"  {hanzi}   [{k}]")
        print(f"        {describe(k, payload)}")
        if notes:
            print(f"        note: {notes}")
        try:
            answer = input("        > ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            print("\nstopping, decisions so far are saved")
            break

        if answer == "q":
            break
        if answer == "s":
            print()
            continue
        status = "rejected" if answer == "n" else "accepted"
        staging.execute("UPDATE suggestion SET status = ? WHERE id = ?", (status, sid))
        staging.commit()
        print(f"        {status}\n")

    status(staging)


def merge(staging: sqlite3.Connection, content: sqlite3.Connection) -> None:
    """Write accepted suggestions into the deck.

    Source is recorded as 'deepseek' on every row, so a later pass can always
    tell the learner's own words apart from generated ones — including to strip
    them all back out if she decides she would rather not have them.
    """
    rows = staging.execute(
        "SELECT id, kind, word_id, hanzi, payload FROM suggestion "
        "WHERE status = 'accepted'"
    ).fetchall()

    if not rows:
        print("nothing accepted to merge")
        return

    merged = 0
    for sid, kind, word_id, hanzi, payload_json in rows:
        payload = json.loads(payload_json)

        if kind == "gloss":
            for lang, key in (("id", "id"), ("en", "en")):
                text = (payload.get(key) or "").strip()
                if not text:
                    continue
                content.execute(
                    """INSERT INTO sense (word_id, gloss, lang, ordinal, source)
                       VALUES (?,?,?,?, 'deepseek')""",
                    (word_id, text, lang,
                     content.execute(
                         "SELECT COUNT(*) FROM sense WHERE word_id = ?", (word_id,)
                     ).fetchone()[0]),
                )

        elif kind == "example":
            sentence = payload["zh"]
            content.execute(
                """INSERT INTO example
                   (word_id, zh, pinyin, gloss, source, contains_target, token_count)
                   VALUES (?,?,?,?, 'deepseek', ?, ?)""",
                (word_id, sentence, payload.get("pinyin"), payload.get("gloss"),
                 int(hanzi in sentence),
                 sum(1 for c in sentence if "一" <= c <= "鿿")),
            )

        elif kind == "pinyin":
            content.execute(
                """UPDATE word SET pinyin = ?, pinyin_verified = 1,
                                   pinyin_reason = 'reviewed'
                   WHERE id = ?""",
                (payload["pinyin"], word_id),
            )

        staging.execute("UPDATE suggestion SET status = 'merged' WHERE id = ?", (sid,))
        merged += 1

    content.commit()
    staging.commit()
    print(f"merged {merged} suggestions into content.db")
    print("rebuild the app asset with:  cp content/content.db app/src/main/assets/")


def status(staging: sqlite3.Connection) -> None:
    print("\n  staging")
    for kind, state, n in staging.execute(
        "SELECT kind, status, COUNT(*) FROM suggestion GROUP BY kind, status "
        "ORDER BY kind, status"
    ):
        print(f"    {kind:10} {state:10} {n}")
    print()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--kind", choices=["gloss", "example", "pinyin"])
    ap.add_argument("--limit", type=int, default=40)
    ap.add_argument("--merge", action="store_true")
    ap.add_argument("--status", action="store_true")
    args = ap.parse_args()

    if not STAGING_DB.exists():
        raise SystemExit("no staging.db — run pipeline/enrich.py first")

    staging = sqlite3.connect(STAGING_DB)

    if args.status:
        status(staging)
    elif args.merge:
        merge(staging, sqlite3.connect(CONTENT_DB))
    else:
        review(staging, args.kind, args.limit)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
