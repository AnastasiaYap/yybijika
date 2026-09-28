# 盈盈笔记卡 · Yíngyíng Bǐjìkǎ

A Mandarin flashcard app built from one person's own notes.

Where yyhsk teaches a syllabus — 13,200 words from HSK 1–9 — this teaches the
1,260 words actually written down in `Untitled document (2) (1).pdf`. Each word
becomes a card that can be drilled several ways, carries a separate mastery score
per skill, and knows which exercises it cannot yet produce.

## How it fits together

```
pipeline/     Python, build-time only. Notes → content.db. Never ships.
content/      Generated. content.db ships read-only; staging.db never does.
app/          Kotlin + Compose. Ships content.db, owns a separate progress.db.
```

Keeping **content.db separate from progress.db** is the decision everything else
rests on. New vocabulary, corrected readings and generated examples all reship
the deck; mastery, XP and streaks live in a different file that a release never
touches. You can rebuild the deck as often as you like without costing yourself a
single interval.

## Everyday use

```bash
# Rebuild the deck from the notes
./.venv/bin/python pipeline/build.py

# See what the deck can and cannot do yet
./.venv/bin/python pipeline/report.py
./.venv/bin/python pipeline/report.py --list no-gloss     # or unverified / flagged / orphans

# Ship it
cp content/content.db app/src/main/assets/
./build.sh assembleRelease
```

## Filling the gaps

The report's backlog is a real queue, not a figure of speech: every exercise
declares what it needs, and a SQL view works out which words fall short. That
same view is what the enrichment step reads.

```bash
echo 'DEEPSEEK_API_KEY=sk-...' > pipeline/.env

./.venv/bin/python pipeline/enrich.py --need gloss   --limit 50
./.venv/bin/python pipeline/enrich.py --need example --limit 50
./.venv/bin/python pipeline/enrich.py --need pinyin  --limit 50

./.venv/bin/python pipeline/review.py          # accept / reject each one
./.venv/bin/python pipeline/review.py --merge  # write the accepted ones in
```

Suggestions land in `staging.db` and go nowhere near the deck until you accept
them. Before you ever see one it has to survive `validate.py`: pinyin with the
wrong syllable count, an "example" that never uses its word, a gloss that just
restates the characters — all rejected automatically, so the review queue stays
worth reading.

`--dry-run` prints the prompt instead of calling the API.

## Adding a new exercise type

One object in `app/.../exercise/Registry.kt`, declaring which `Requirement`s it
needs, plus one branch in `SessionScreen.kt` to draw its `Exercise` shape.
Coverage, scheduling and the enrichment backlog pick it up with no further
changes — and a test in `RegistryTest.kt` will fail if `generate()` and
`requires` ever disagree, because coverage is computed from `requires` alone and
would otherwise be free to lie.

## What the notes turned out to be

47 pages, fully text-extractable — the 131 embedded images are emoji, so there is
no OCR step.

| | |
|---|---:|
| Unique headwords | 1,260 |
| With a gloss | 946 |
| Pinyin trusted without review | 683 |
| Grammar patterns | 57 |
| Lines the parser could not classify | 12 |

Two things about the source shape the pipeline:

**Traditional characters break pinyin generation.** The notes mix scripts (收音機
and 开 a few lines apart), and pypinyin's phrase dictionary is keyed on
simplified forms only:

```
開音樂  →  "kāi yīn lè"    wrong — 樂 read in isolation
开音乐  →  "kāi yīn yuè"   right — matched as a phrase
```

So `zh.py` converts to simplified before asking for any reading. Order is not
negotiable.

**pypinyin is good but not authoritative.** It gets 银行 yín háng and 长大 zhǎng dà
right, but reads 睡不着 as `shuì bù zhe` rather than `zháo`. Every reading is
therefore stored with a confidence flag: a word whose characters are all
single-reading, or which matched a whole phrase, is trusted; anything else is
queued for a second opinion. Listening and typing drills only use trusted
readings, so a wrong tone can never mark a right answer wrong.

## Signing

`keystore.properties` and `yybijika-release.jks` are gitignored and **must be
backed up**. Android identifies an app by its signing key — lose these and no
future build will install over the top of an existing one.

## Toolchain

JDK 17, Android SDK, Gradle 8.11.1, AGP 8.9.1, Kotlin 2.1.0, compileSdk 36,
minSdk 26. `./build.sh` sets `JAVA_HOME` and `ANDROID_HOME` for you.

Python side lives in `.venv` (pypinyin, opencc-python-reimplemented) and needs
`pdftotext` from poppler.
