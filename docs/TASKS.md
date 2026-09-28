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

## C — A richer question set  (your point 3)  — DONE

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
  - [x] Character meaning — what does 院 contribute?
  - [x] Word building — which characters make the word meaning "faculty"?

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

## D — Characters as first-class cards  (my recommendation)  — DONE

1,173 words are built from 1,012 characters, 529 of which appear in two or more
words. Teaching the words without the characters teaches the same idea ten times.

  - [x] A character table: meaning, reading(s), and the words it builds.
        529 characters appear in two or more words; all 529 carry a meaning in
        both Indonesian and English. 131 inherit it from their own headword
        entry, 398 were written for `pipeline/characters.py`, and 19 headword
        characters have an override because the word sense and the compound
        sense genuinely differ — 所 is filed as "the measure word for buildings",
        which is useless in 事务所 and 派出所.
  - [x] A character card in the app, reachable from any word that contains it.
        The characters sit under the word on its detail screen, before the
        meaning, because that is the order the word is read in.
  - [x] Characters enter the schedule like words do. The Cards screen gained a
        Words / Characters switch; the character deck has the same three
        gestures, the same filters and the same ladder, which now lives in one
        place (`CardDeck.step`) with a test asserting the two decks move
        identically.
  - [x] Unlocks the last two question types in C.

## Also done — sound on and off  (your ask)

  - [x] A mute toggle in the header of both Cards and the session player, on one
        stored preference. Muting also takes the three listening question types
        off the Quiz menu rather than leaving them there to produce an empty
        quiz, and hides the play buttons instead of leaving them dead.
  - [x] Cards now speak themselves as they arrive when sound is on, which is
        also why the mute button had to be on that screen: unattended speech is
        the kind you want to stop immediately, not after finding Settings.
