# Task list

Four pieces of work, ordered so each one unlocks the next.

## A — Cards become a real rehearsal system  (your point 1)  — DONE

Today the Cards tab is a browser: it touches nothing and grades nothing. It
becomes the deck you groom, driven by gesture.

  - [x] Per-word card state in progress.db: struggling / learning / known /
        retired, with its own box and due date. Separate from the per-skill
        mastery that Review uses — here you are judging "do I know this word",
        not "can I hear it".
  - [x] Swipe left  → unknown. Box resets, due immediately, comes back often.
  - [x] Swipe right → known. Box advances, due later.
  - [x] Swipe down  → retire, out of rotation. Confirmation dialog first,
        because it is the only destructive gesture on the screen.
  - [x] Undo on the last swipe — a mis-swipe must not cost an interval.
  - [x] Filter bar: All · Due · Struggling · Learning · Known · Retired.
  - [x] Retired words are revivable from the Retired filter, one tap.

## B — Study splits into Flashcards, Quiz and Review  (your point 2)  — DONE

Mixing a flip-card with a multiple-choice question in one stream is two
different mental modes fighting each other. They separate.

  - [x] **Cards** — flip and swipe only. No grading buttons, no questions.
  - [x] **Quiz** — a real section. Pick the question type (or Mixed), pick how
        many, get a score at the end.
  - [x] **Review** — the scheduled queue across all four skills. Unchanged in
        purpose, but no longer the only way to study.

## C — A richer question set  (your point 3)  — 7 of 9

yyhsk had many question shapes; this deck has a tenth of the vocabulary, so the
questions have to carry more of the weight, not less. Nine new types, each
using data the deck already holds:

  - [x] Tone identification — which tone pattern did you hear? Uses the 114
        near-homophone pairs. The highest-value drill for a non-tonal speaker.
  - [x] Measure word — 一 __ 裤子. Uses the 44 measure pairs.
  - [x] Synonym / antonym choice — uses the 101 hand-written semantic links.
  - [x] Odd one out — three words share a character or topic, one does not.
  - [x] Sentence translation — Indonesian prompt, build the Chinese.
  - [x] Dictation — hear a whole sentence, type it. Uses all 2,371 sentences.
  - [x] Pinyin → hanzi — read the reading, write the characters.
  - [ ] Character meaning — what does 院 contribute? (needs D)
  - [ ] Word building — which characters make the word meaning "faculty"? (needs D)

Done along the way, because the types needed it:

  - [x] The example sentences are cut into **words**, not characters
        (`pipeline/segment.py`, jieba with the deck's own headwords force-added).
        A nine-character jigsaw asked nothing about Chinese; six word tiles ask
        where the words are.
  - [x] Measure-word coverage went from 22 nouns to 84. The first table was
        written from the measure words outwards and mostly named things the
        notes never mention; the second was written by reading the deck. The
        link emitter also stopped requiring the measure word to be vocabulary —
        间 and 座 are answers, not headwords.
  - [x] `Selectors.kt` holds one SQL statement per type, and `ContentDbSqlTest`
        runs every one of them against the shipped deck. It immediately found
        two real faults: `odd_one_out` reported 1,192 available words in a
        1,173-word deck, and `measure_word` could offer 部 as the wrong answer
        to 小说 when 一部小说 is also correct.

## D — Characters as first-class cards  (my recommendation)

1,173 words are built from 1,012 characters, 529 of which appear in two or more
words. Teaching the words without the characters teaches the same idea ten times.

  - [ ] A character table: meaning, reading(s), and the words it builds.
  - [ ] A character card in the app, reachable from any word that contains it.
  - [ ] Characters enter the schedule like words do.
  - [ ] Unlocks the last two question types in C.
