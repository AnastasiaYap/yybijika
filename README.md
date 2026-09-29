# 盈盈笔记卡 · Yíngyíng Bǐjìkǎ

A Mandarin flashcard app built from your own notes, rather than from a syllabus.

Where a course teaches you its word list, this teaches the words *you* wrote
down. Each one becomes a card that can be drilled seventeen different ways,
carries a separate mastery score per skill, knows which exercises it cannot yet
produce, and links to the characters it is built from and the words it is
confused with.

## Two decks

The code here is shareable. The vocabulary in it is one person's, so it is not
in this repository.

| | applicationId | deck |
|---|---|---|
| **Starter** | `io.tr8.yybijika.starter` | 44 sample words, committed, in `starter/notes.txt` |
| **Personal** | `io.tr8.yybijika` | the author's notes — not committed, not buildable here |

Releases carry both APKs. They are separate applications on purpose: installing
one replaces `content.db` wholesale, so a build carrying one deck must never be
installable over a build carrying the other. Each build's updater matches its own
artifact by name for the same reason.

**Download `yybijika-starter-*.apk`** from the
[releases](https://github.com/AnastasiaYap/yybijika/releases) to try it, or build
your own deck below.

## Making it yours

The starter deck exists to be replaced. Put your own vocabulary in
`starter/notes.txt` — one word per line, the meaning immediately after it, and a
sentence on its own line becomes an example for the word above it:

```
上班masuk kerja / to go to work
我每天八点上班。
```

Blank lines separate topics, and a bare word on its own line (`food`, `work`)
tags everything under it. Then:

```bash
python3 -m venv .venv && ./.venv/bin/pip install pypinyin opencc-python-reimplemented jieba
./.venv/bin/python pipeline/build.py --starter --out content/starter.db
cp content/starter.db app/src/starter/assets/content.db
./gradlew assembleStarterDebug
```

Everything else follows from the notes: readings, the character cards, the
cross-references between words that sound alike, the topic tags, the segmentation
the tile exercises use, and which of the seventeen question types each word can
support. Nothing needs to be filled in by hand for the app to work — the hand
written parts (corrections, extra examples, reading passages) are refinements on
top, and the ones in `pipeline/` belong to the author's deck.

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
cp content/content.db app/src/personal/assets/
./build.sh assemblePersonalRelease
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

## Updates

The app asks GitHub for the latest release on launch and offers it on the home
screen — never a dialog, because an update is not urgent enough to interrupt a
study session. Tapping through downloads the APK to cache and hands it to the
system installer, which refuses anything not signed with the same key as the
installed app.

Installing an update keeps all progress. The new deck rides inside the APK and
replaces `content.db` wholesale, while `progress.db` — every interval, streak
and point — is a separate file the install never touches.

To cut a new version:

```bash
./release.sh 0.2.0 "Added examples for 300 more words"
```

That bumps `versionName` and `versionCode`, rebuilds the deck from the notes,
runs the tests, builds and signs, tags, pushes, and publishes the APK as a GitHub
release. Phones offer it on their next launch.

`versionCode` has to increase for Android to treat an install as an upgrade;
`release.sh` increments it automatically, and refuses a version that does not go
up.

## Signing

`keystore.properties` and `yybijika-release.jks` are gitignored and **must be
backed up**. Android identifies an app by its signing key — lose these and no
future build will install over the top of an existing one.

## Toolchain

JDK 17, Android SDK, Gradle 8.11.1, AGP 8.9.1, Kotlin 2.1.0, compileSdk 36,
minSdk 26. `./build.sh` sets `JAVA_HOME` and `ANDROID_HOME` for you.

Python side lives in `.venv` (pypinyin, opencc-python-reimplemented) and needs
`pdftotext` from poppler.
