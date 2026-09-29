"""Does this passage read from the deck the learner actually has?

The whole claim of the reading section is that it is built from her own notes.
That claim is cheap to make and easy to break: one unfamiliar word per sentence
turns reading practice back into dictionary work, and nothing in the writing
process notices.

So it is checked. Every passage is segmented with the same segmenter the tile
exercises use, each word is looked up in the deck, and the build prints what is
not there. A word the deck lacks is not automatically wrong — 李明 is a name, 因为
is grammar nobody wrote down — but it has to be a deliberate exception rather
than an oversight, which is what ALLOWED is for.
"""

from __future__ import annotations

import re

import baseline
import segment

RE_HAN = re.compile(r"[一-鿿]")

#: Grammatical scaffolding, on top of [baseline.ASSUMED_KNOWN].
#:
#: HSK 1-2 covers the ordinary words; this covers the joints — the conjunctions
#: and structural particles that hold a sentence of any length together and that
#: no vocabulary list bothers to teach as items.
ALLOWED: set[str] = set("""
的 了 着 过 是 不 没 很 也 都 就 还 又 再 才 只 和 跟 与 或 但 而 及
我 你 您 他 她 它 们 我们 你们 他们 她们 咱们 自己 别人
这 那 哪 这个 那个 哪个 这些 那些 这里 那里 哪里 这样 那样 怎么 什么 为什么 谁 多少
一 二 两 三 四 五 六 七 八 九 十 百 千 万 几 第一 第二 第三
在 从 到 对 给 把 被 让 向 往 为 因为 所以 如果 虽然 但是 不过 而且 然后 于是 后来
会 能 可以 要 想 应该 必须 得 可能 一定 也许
有 没有 个 些 点 点儿 一下 一点 一些 一起 一样 一边
上 下 里 外 前 后 中 间 之 以 其 者 地 得
吗 呢 吧 啊 呀 嘛 啦 呗
时候 的话 什么样 怎么样
一个 两个 几个 一天 几天 一年 一次 第一 第一天 第一次 第二天 最后
很多 不少 好多 一部分 大部分
上午 中午 下午 早上 晚上 上班 下班 上个月 下个月 上个星期 下个星期
同学们 朋友们 孩子们 老师们
想要 不要 不想 不能 不会 不用 没想到 没关系
""".split())


#: Characters that start a personal name in these passages.
#:
#: 小林 and 老王 are people, not vocabulary, and flagging them as unfamiliar
#: buries the words that genuinely are.
NAME_PREFIXES = set("小老大阿")


def unknown_words(text: str, deck: set[str], lex: set[str]) -> list[str]:
    """Words a reader of these notes would have to work out.

    Not the same as "words outside the deck": HSK 1-3 is assumed, so 今天 and
    朋友 pass even though the notes never list them.

    What comes back is a budget, not a ban. A passage with no unfamiliar words
    teaches no vocabulary — reading is where you meet a word in enough context
    to guess it. A passage with many is a dictionary exercise. The build prints
    the count so the line between those stays a decision rather than an accident.
    """
    out: list[str] = []
    for token in segment.segment(text, lex):
        if token in deck or token in ALLOWED or token in baseline.ASSUMED_KNOWN:
            continue
        # A compound whose halves are both known is not a new word: 火车票 is
        # 火车 plus 票, and a reader who has both can read it without being
        # taught it. Checked after the lists so a real headword still wins.
        if _transparent(token, deck):
            continue
        if len(token) == 2 and token[0] in NAME_PREFIXES:
            continue
        # A single character the deck teaches inside compounds is fine on its
        # own: it is not new to the reader, it is just not a headword.
        if len(token) == 1 and any(token in w for w in deck):
            continue
        out.append(token)
    return out


_known_cache: dict[int, set[str]] = {}


def _known(deck: set[str]) -> set[str]:
    """Everything a reader can already read, characters included.

    Single characters count: 年, 街 and 页 are never headwords in the notes, but
    they sit inside 去年, 大街 and 第十一页, and a reader who has those can read
    them alone. Cached because it is rebuilt for every token otherwise.
    """
    key = id(deck)
    if key not in _known_cache:
        _known_cache[key] = (
            deck | ALLOWED | baseline.ASSUMED_KNOWN
            | {c for word in deck for c in word if RE_HAN.match(c)}
            | {c for word in baseline.ASSUMED_KNOWN for c in word if RE_HAN.match(c)}
        )
    return _known_cache[key]


#: Digits and the words that count with them.
_NUMERALS = set("一二两三四五六七八九十百千万亿几半第零")

#: The infixes that build a resultative: 听得懂, 听不懂, 装不下.
_INFIXES = ("得", "不")


def _transparent(token: str, deck: set[str]) -> bool:
    """True when a reader who knows the pieces can read the whole.

    The segmenter cuts on its own judgement, not on where words end for a
    learner, so it produces a steady trickle of things like 四十分钟, 第十一 and
    听不懂. None of those is a new word; treating them as one buries the handful
    that genuinely are.
    """
    if len(token) < 2:
        return False
    known = _known(deck)

    # Two halves, both known: 火车票, 快餐店.
    if any(token[:i] in known and token[i:] in known for i in range(1, len(token))):
        return True

    # A number stuck to whatever it counts: 四十分钟, 二十年, 第十一, 一条街.
    stripped = token.lstrip("".join(_NUMERALS))
    if stripped != token and (not stripped or stripped in known
                              or _transparent(stripped, deck)):
        return True

    # A resultative built from a verb and a result: 听不懂, 装不下, 走得快.
    for infix in _INFIXES:
        head, sep, tail = token.partition(infix)
        if sep and head in known and (not tail or tail in known):
            return True

    # An adverb marked with 地, or a reduplicated verb: 轻轻地, 摸摸, 摆摆手.
    if token.endswith("地") and token[:-1] in known:
        return True
    if len(token) >= 2 and token[0] == token[1]:
        rest = token[2:]
        if token[0] in known and (not rest or rest in known):
            return True

    return False


def coverage(text: str, deck: set[str], lex: set[str]) -> tuple[int, int, list[str]]:
    """(distinct deck words used, total words, which deck words they were).

    Distinct on purpose: "practises 47 of your words" should mean 47 different
    ones, not 一 counted twelve times.
    """
    tokens = [t for t in segment.segment(text, lex) if RE_HAN.search(t)]
    used = sorted({t for t in tokens if t in deck})
    return len(used), len(tokens), used
