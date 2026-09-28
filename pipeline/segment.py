"""Word-level segmentation of the example sentences.

The tile exercises used to cut sentences into single characters, which makes
them a jigsaw rather than a language task: 他 想 提 高 自 己 的 水 平 has only one
plausible order for a learner who recognises none of the words. Cutting at word
boundaries instead gives 他 / 想 / 提高 / 自己 / 的 / 水平, which is a real
question — where a word ends is itself something you learn.

jieba does the general segmentation; the deck's own headwords are added to its
dictionary with a high weight first. That ordering matters. A word the app is
teaching must survive as one tile even when jieba would rather split it, because
the whole point of the sentence is that it contains that word — 早起 cut into
早 / 起 turns the question into one about two characters the learner was never
taught.

jieba is a build-time dependency only. Nothing here ships in the APK; the result
is baked into example.segments.
"""

from __future__ import annotations

import re

import jieba

RE_HAN = re.compile(r"[一-鿿]")

_loaded: set[str] = set()


def load(headwords) -> None:
    """Teach jieba the deck's vocabulary. Idempotent; safe to call per build."""
    for word in headwords:
        if word in _loaded or not RE_HAN.search(word):
            continue
        # Weight high enough that the deck word beats jieba's own split of the
        # same span, which is the only reason for adding it at all.
        jieba.add_word(word, freq=100000)
        _loaded.add(word)


#: Characters that are a word on their own and glue to whatever follows them.
#: jieba reads 我怕 and 最美 as units because they are frequent bigrams, but a
#: learner needs 我 / 怕 — the subject and the verb are separate choices.
STICKY_HEADS = set("我你您他她它这那最很也都就还又再才只不没每有太")

#: Two-character words that begin with a sticky head but are single words all
#: the same. Listed rather than inferred: 我们 and 我怕 are the same shape, and
#: only a dictionary knows that the first is a pronoun and the second is not.
NEVER_SPLIT = {
    "我们", "你们", "您们", "他们", "她们", "它们", "咱们",
    "这个", "那个", "哪个", "这些", "那些", "哪些",
    "这里", "那里", "哪里", "这儿", "那儿", "哪儿",
    "这样", "那样", "怎样", "这么", "那么", "怎么",
    "没有", "不是", "不会", "不能", "不用", "不要", "不错", "不但", "不过",
    "只有", "只是", "只要", "还是", "还有", "也是", "就是", "都是",
    "最后", "最近", "最好", "最初", "很多", "很少",
    "有的", "有点", "有些", "有时",
    "再见", "又是", "才能", "就要",
}


def _unglue(token: str, lex: set[str]) -> list[str]:
    """Split a two-character token whose first half is a standalone word.

    Skipped when the token is itself something the deck teaches: 那里 and 没有
    match the pattern but are words in their own right, and splitting them would
    contradict the dictionary entry the learner is being shown.
    """
    if (len(token) == 2 and token not in lex and token not in NEVER_SPLIT
            and token[0] in STICKY_HEADS):
        return [token[0], token[1]]
    return [token]


def segment(sentence: str, lex: set[str] | None = None) -> list[str]:
    """Cut a sentence into words.

    Punctuation is dropped rather than tokenised: a full stop as a draggable
    tile is a piece with exactly one home, which adds a step without adding a
    decision. The UI re-adds it when it shows the finished sentence.
    """
    known = lex if lex is not None else _loaded
    out: list[str] = []
    for token in jieba.cut(sentence, cut_all=False):
        han = "".join(ch for ch in token if RE_HAN.match(ch))
        if han:
            out.extend(_unglue(han, known))
    return out
