-- content.db — everything the app knows, generated from the notes.
--
-- This database ships read-only inside the APK. It is rebuilt from scratch on
-- every pipeline run, so nothing user-specific may ever live here: mastery, XP
-- and streaks belong to progress.db, which the app owns. That separation is
-- what lets new vocabulary ship without resetting anyone's progress.

PRAGMA foreign_keys = ON;

CREATE TABLE word (
    id              INTEGER PRIMARY KEY,
    hanzi           TEXT NOT NULL UNIQUE,   -- always simplified
    hanzi_trad      TEXT,                   -- set only when it differs
    pinyin          TEXT NOT NULL,
    pinyin_verified INTEGER NOT NULL DEFAULT 0,
    pinyin_reason   TEXT,                   -- how the reading was arrived at
    char_count      INTEGER NOT NULL,
    is_phrase       INTEGER NOT NULL DEFAULT 0,  -- 5+ characters: a set phrase
    source_page     INTEGER,
    source_line     INTEGER,
    needs_review    INTEGER NOT NULL DEFAULT 0,
    review_reason   TEXT
);

CREATE TABLE sense (
    id        INTEGER PRIMARY KEY,
    word_id   INTEGER NOT NULL REFERENCES word(id) ON DELETE CASCADE,
    gloss     TEXT NOT NULL,
    lang      TEXT NOT NULL,           -- id | en | mixed
    ordinal   INTEGER NOT NULL DEFAULT 0,
    source    TEXT NOT NULL DEFAULT 'notes'   -- notes | deepseek
);

CREATE TABLE usage_note (
    id      INTEGER PRIMARY KEY,
    word_id INTEGER NOT NULL REFERENCES word(id) ON DELETE CASCADE,
    note    TEXT NOT NULL
);

CREATE TABLE relation (
    word_id    INTEGER NOT NULL REFERENCES word(id) ON DELETE CASCADE,
    related_id INTEGER REFERENCES word(id) ON DELETE CASCADE,
    -- Kept as text too: the author cross-references words she never wrote an
    -- entry for, and dropping those would lose real information.
    related_hanzi TEXT NOT NULL,
    kind       TEXT NOT NULL,          -- variant | synonym | antonym | see-also
    PRIMARY KEY (word_id, related_hanzi, kind)
);

CREATE TABLE example (
    id       INTEGER PRIMARY KEY,
    word_id  INTEGER NOT NULL REFERENCES word(id) ON DELETE CASCADE,
    zh       TEXT NOT NULL,
    pinyin   TEXT,
    gloss    TEXT,
    source   TEXT NOT NULL DEFAULT 'notes',
    -- An example is only usable for a cloze if the target word appears in it
    -- verbatim; computed once at build time rather than re-checked per session.
    contains_target INTEGER NOT NULL DEFAULT 0,
    token_count     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE pattern (
    id          INTEGER PRIMARY KEY,
    formula     TEXT NOT NULL,
    note        TEXT,
    source_page INTEGER,
    source_line INTEGER
);

CREATE TABLE pattern_example (
    id         INTEGER PRIMARY KEY,
    pattern_id INTEGER NOT NULL REFERENCES pattern(id) ON DELETE CASCADE,
    zh         TEXT NOT NULL,
    pinyin     TEXT,
    gloss      TEXT
);

CREATE TABLE tag (
    word_id INTEGER NOT NULL REFERENCES word(id) ON DELETE CASCADE,
    tag     TEXT NOT NULL,
    PRIMARY KEY (word_id, tag)
);

-- Lines the parser could not confidently read. Kept in the database rather than
-- a log file so the review tool and the app's own "about the deck" screen can
-- both show what was left on the table.
CREATE TABLE orphan (
    id     INTEGER PRIMARY KEY,
    page   INTEGER,
    line   INTEGER,
    text   TEXT NOT NULL,
    reason TEXT
);

CREATE TABLE passage (
    id          INTEGER PRIMARY KEY,
    title       TEXT NOT NULL,
    level       INTEGER NOT NULL DEFAULT 1,
    body        TEXT NOT NULL,
    translation TEXT,
    source      TEXT NOT NULL DEFAULT 'deepseek'
);

CREATE TABLE passage_token (
    passage_id INTEGER NOT NULL REFERENCES passage(id) ON DELETE CASCADE,
    idx        INTEGER NOT NULL,
    zh         TEXT NOT NULL,
    pinyin     TEXT,
    word_id    INTEGER REFERENCES word(id) ON DELETE SET NULL,
    PRIMARY KEY (passage_id, idx)
);

CREATE TABLE passage_question (
    id           INTEGER PRIMARY KEY,
    passage_id   INTEGER NOT NULL REFERENCES passage(id) ON DELETE CASCADE,
    q            TEXT NOT NULL,
    choices_json TEXT NOT NULL,
    answer       INTEGER NOT NULL,
    explain      TEXT
);

CREATE INDEX idx_sense_word ON sense(word_id);
CREATE INDEX idx_example_word ON example(word_id);
CREATE INDEX idx_relation_word ON relation(word_id);
CREATE INDEX idx_tag_word ON tag(word_id);
CREATE INDEX idx_tag_name ON tag(tag);
CREATE INDEX idx_word_unverified ON word(pinyin_verified) WHERE pinyin_verified = 0;

-- What each word can currently be drilled with.
--
-- This is the contract between the content pipeline and the exercise registry:
-- the app never generates an exercise whose requirements are unmet, and the
-- enrichment step reads the same view backwards to find what is missing. One
-- row per word; the registry maps its Requirement set onto these columns.
CREATE VIEW word_capability AS
SELECT
    w.id                                            AS word_id,
    w.hanzi,
    w.pinyin_verified,
    (SELECT COUNT(*) FROM sense s WHERE s.word_id = w.id)          AS sense_count,
    (SELECT COUNT(*) FROM example e
       WHERE e.word_id = w.id AND e.contains_target = 1)           AS cloze_count,
    (SELECT COUNT(*) FROM example e
       WHERE e.word_id = w.id AND e.contains_target = 1
         AND e.token_count >= 4)                                   AS builder_count,
    (SELECT COUNT(*) FROM tag t WHERE t.word_id = w.id)            AS tag_count
FROM word w;
