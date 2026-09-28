"""How the words in the deck relate to each other.

A deck of 1,173 separate cards teaches 1,173 separate things. The same deck seen
as a network teaches far more, because Chinese is unusually well suited to it:
91% of these words share a character with another word, and 529 characters
appear in two or more. Learn 院 once and 医学院, 商学院, 外交学院, 工学院,
语言学院, 心理学院, 财经学间, 学院, 住院 and 出院 all become partly readable.

Six kinds of link, three computed and three written by hand.

Computed, because the data already says it:

  homophone       identical pinyin, different characters — 湖/壶, 代/带/戴/待
  near-homophone  same syllables, different tones — 经历/经理, 继续/积蓄.
                  The most useful kind for an Indonesian speaker, whose first
                  language has no tones: these are the pairs where a tone error
                  produces a different real word rather than an accent.
  shares          a character in common, with what that character contributes

Written by hand, because no algorithm knows them:

  synonym         close in meaning, and the note says what separates them
  antonym         the opposite, which is often easier to learn than the word
  measure         the measure word a noun takes — 裤子 takes 条, 信 takes 封.
                  Chinese will not let you count a noun without one, so a noun
                  learned without its measure word is a noun you cannot yet use.
"""

from __future__ import annotations

import re
from collections import defaultdict

RE_HAN = re.compile(r"[㐀-䶿一-鿿]")

_TONELESS = str.maketrans(
    "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜü",
    "aaaaeeeeiiiioooouuuuuuuuu",
)


def toneless(pinyin: str) -> str:
    return pinyin.translate(_TONELESS)


def computed(words: dict[str, str]) -> list[tuple[str, str, str, str | None]]:
    """(word, related, kind, note) for every link the data itself supports.

    `words` maps hanzi to pinyin.
    """
    links: list[tuple[str, str, str, str | None]] = []

    by_pinyin: dict[str, list[str]] = defaultdict(list)
    by_toneless: dict[str, list[str]] = defaultdict(list)
    for hanzi, pinyin in words.items():
        by_pinyin[pinyin].append(hanzi)
        by_toneless[toneless(pinyin)].append(hanzi)

    for group in by_pinyin.values():
        for a in group:
            for b in group:
                if a != b:
                    links.append((a, b, "homophone", None))

    # Same syllables, different tones. Excludes the true homophones above, which
    # are a different problem: there the tones already match.
    for group in by_toneless.values():
        tones = {words[h] for h in group}
        if len(tones) < 2:
            continue
        for a in group:
            for b in group:
                if a != b and words[a] != words[b]:
                    links.append(
                        (a, b, "near-homophone", f"{words[a]} vs {words[b]}")
                    )

    # Two-character words that are each other reversed: 事故 / 故事.
    # RE_HAN matches a single character, so fullmatch on a two-character word is
    # always false — the test has to be per character.
    two_char = {
        w for w in words
        if len(w) == 2 and all(RE_HAN.match(ch) for ch in w)
    }
    for word in two_char:
        flipped = word[::-1]
        if flipped in two_char and flipped != word:
            links.append((word, flipped, "reversed", "the same characters, the other way round"))

    return links


# Characters that two words can share without the sharing meaning anything.
# 子 is a noun suffix, 了 and 的 are particles, 一 and 不 are everywhere. Linking
# 裤子 to 饺子 because both end in 子 teaches nothing and buries the links that do.
EMPTY_COMPONENTS = set("子了的不一是有个这那我你他们在和就也都很么儿")


def shared_characters(
    words: list[str],
    max_links: int = 8,
) -> list[tuple[str, str, str, str]]:
    """Words that share a character, with the character named.

    Capped per word: 学 appears in twenty-four of these words, and a card
    listing all twenty-four teaches nothing. The cap keeps the link list to the
    size a person will actually read.

    Single-character words are skipped as sources — 不 linking to seventeen
    words that merely contain 不 is noise, not a relationship.
    """
    holders: dict[str, list[str]] = defaultdict(list)
    for word in words:
        for ch in set(RE_HAN.findall(word)):
            holders[ch].append(word)

    links: list[tuple[str, str, str, str]] = []
    for word in words:
        if len(RE_HAN.findall(word)) < 2:
            continue
        seen: set[str] = set()
        # Rarer shared characters are more informative: a word sharing 院 with
        # ten others tells you what 院 means, while sharing 不 tells you nothing.
        candidates = [
            ch for ch in set(RE_HAN.findall(word)) if ch not in EMPTY_COMPONENTS
        ]
        for ch in sorted(candidates, key=lambda c: len(holders[c])):
            for other in holders[ch]:
                if other == word or other in seen:
                    continue
                if len(RE_HAN.findall(other)) < 2 and other != ch:
                    continue
                seen.add(other)
                links.append((word, other, "shares", ch))
                if len(seen) >= max_links:
                    break
            if len(seen) >= max_links:
                break
    return links


# --------------------------------------------------------------------------
# Hand-written links
# --------------------------------------------------------------------------

# (word, related, note) — the note says what actually separates them, because
# "these two are similar" is the part the learner already knew.
SYNONYMS: list[tuple[str, str, str]] = [
    ("认为", "觉得", "认为 lebih serius dan formal; 觉得 dipakai sehari-hari"),
    ("认为", "以为", "以为 selalu berarti dugaan yang ternyata salah"),
    ("以为", "觉得", "以为 keliru, 觉得 netral"),
    ("帮", "帮忙", "帮 langsung punya objek (帮我); 帮忙 tidak bisa (bukan 帮忙我)"),
    ("帮忙", "帮助", "帮助 lebih formal dan bisa jadi kata benda"),
    ("经历", "经验", "经历 peristiwa yang dilalui; 经验 keahlian yang terkumpul"),
    ("常常", "经常", "hampir sama; 经常 sedikit lebih netral"),
    ("常常", "总", "总 bernada mengeluh, 常常 netral"),
    ("旅行", "旅游", "旅行 perjalanannya; 旅游 pariwisata sebagai kegiatan"),
    ("漂亮", "美丽", "美丽 formal dan tertulis; 漂亮 sehari-hari"),
    ("了解", "熟悉", "了解 paham isinya; 熟悉 kenal karena sering berhubungan"),
    ("了解", "懂", "懂 paham maksudnya; 了解 kenal keseluruhannya"),
    ("词典", "辞典", "artinya sama; 辞典 terasa lebih formal dan kuno"),
    ("看法", "想法", "看法 penilaian atas sesuatu; 想法 gagasan"),
    ("意见", "建议", "建议 menawarkan jalan keluar; 意见 bisa juga keberatan"),
    ("改", "改变", "改 memperbaiki bagian; 改变 mengubah keseluruhan"),
    ("害怕", "怕", "sama artinya; 怕 lebih pendek dan lebih sering"),
    ("安静", "宁静", "宁静 lebih puitis dan positif"),
    ("宁静", "清静", "清静 karena sepi orang; 宁静 karena damai"),
    ("工资", "薪水", "薪水 lebih formal; 工资 sehari-hari"),
    ("挣钱", "赚", "挣 dari kerja; 赚 dari untung dagang"),
    ("客气", "礼貌", "礼貌 sifat sopan; 客气 sungkan dalam situasi tertentu"),
    ("参观", "访问", "参观 mengunjungi tempat; 访问 mengunjungi orang"),
    ("表示", "表达", "表达 mengungkapkan perasaan; 表示 menyatakan sikap"),
    ("提高", "提升", "提升 juga untuk jabatan; 提高 hanya untuk mutu"),
    ("突然", "忽然", "hampir sama; 突然 juga bisa jadi kata sifat"),
    ("其实", "实际上", "artinya sama; 实际上 sedikit lebih formal"),
    ("究竟", "到底", "sama artinya dalam pertanyaan; 到底 lebih sehari-hari"),
    ("大概", "差不多", "大概 perkiraan; 差不多 kemiripan dua hal"),
    ("修理", "维修", "维修 perawatan berkala dan teknis; 修理 memperbaiki yang rusak"),
    ("辞职", "解雇", "辞职 keputusan sendiri; 解雇 diberhentikan"),
    ("必须", "应该", "必须 keharusan; 应该 saran"),
    ("可能性", "机会", "机会 kesempatan baik; 可能性 sekadar kemungkinan"),
    ("生气", "凶", "凶 galak sebagai sifat; 生气 marah sesaat"),
    ("困", "累", "困 ngantuk; 累 capek badan"),
    ("热闹", "吵", "热闹 ramai yang menyenangkan; 吵 berisik yang mengganggu"),
]

ANTONYMS: list[tuple[str, str, str]] = [
    ("全职", "兼职", ""),
    ("干净", "脏", ""),
    ("便宜", "贵", ""),
    ("冷", "热", ""),
    ("新鲜", "旧", ""),
    ("穷", "富", ""),
    ("高", "低", ""),
    ("长", "短", ""),
    ("深", "浅", ""),
    ("瘦", "胖", ""),
    ("快", "慢慢", ""),
    ("简单", "复杂", ""),
    ("安全", "危险", ""),
    ("成功", "失望", "bukan lawan langsung; lawan sebenarnya 失败"),
    ("开始", "结束", ""),
    ("出去", "进", ""),
    ("上涨", "下跌", ""),
    ("表扬", "批评", ""),
    ("公立", "私立", ""),
    ("进口", "出口", ""),
    ("提前", "推迟", ""),
    ("认识", "陌生人", "陌生人 orang yang belum dikenal"),
    ("来得及", "来不及", ""),
    ("放心", "着急", ""),
    ("复习", "预习", "复习 sesudah pelajaran; 预习 sebelum"),
]

# The measure word a noun takes. Chinese cannot count a noun without one, so a
# noun learned without its measure word is a noun you cannot yet use in a
# sentence — this is grammar, not trivia.
MEASURE_WORDS: list[tuple[str, str]] = [
    ("裤子", "条"), ("路", "条"), ("河", "条"), ("裙子", "条"), ("辫子", "条"),
    ("信", "封"),
    ("票", "张"), ("纸", "张"), ("照片", "张"), ("床", "张"), ("名片", "张"),
    ("桌子", "张"), ("光盘", "张"), ("报纸", "张"),
    ("鸡", "只"), ("狗", "只"), ("猫", "只"),
    ("树", "棵"),
    ("汽车", "辆"), ("自行车", "辆"), ("电动车", "辆"), ("三轮车", "辆"),
    ("衬衫", "件"), ("衣服", "件"), ("棉衣", "件"), ("事情", "件"),
    ("民歌", "首"), ("歌曲", "首"), ("诗", "首"),
    ("学校", "所"), ("医院", "所"), ("私立学校", "所"), ("房子", "所"),
    ("楼", "层"), ("大厦", "层"),
    ("菜", "盘"),
    ("课", "节"),
    ("文章", "篇"), ("小说", "篇"),
    ("公司", "家"), ("饭馆", "家"), ("快餐店", "家"), ("事务所", "家"),
    ("题", "道"), ("菜谱", "本"), ("词典", "本"), ("书架", "个"),
    ("茶", "壶"), ("水", "壶"),
    ("戏", "部"), ("电影", "部"),
    ("帽子", "顶"),
    ("椅", "把"), ("钥匙", "把"),
    ("镜子", "面"),
    ("包裹", "个"), ("箱子", "个"), ("盒子", "个"),

    # A second pass over the deck's own concrete nouns. The notes are
    # verb-heavy, so the first list — written from the measure words outwards —
    # matched only 22 headwords and left the drill too thin to be worth sitting.
    # These were chosen the other way round: read the deck, then say how each
    # thing is counted.
    ("宿舍", "间"), ("厨房", "间"), ("厕所", "间"), ("屋", "间"),
    ("健身房", "间"), ("实验室", "间"), ("房间", "间"),
    ("体育馆", "座"), ("大使馆", "座"), ("动物园", "座"), ("加油站", "座"),
    ("山", "座"),
    ("医务所", "家"), ("宾馆", "家"), ("工厂", "家"),
    ("商学院", "所"), ("医学院", "所"), ("学院", "所"),
    ("小区", "个"), ("口袋", "个"), ("故事", "个"), ("湖", "个"),
    ("合同", "份"), ("作业", "份"), ("报告", "份"), ("名单", "份"),
    ("文件", "份"), ("礼物", "份"),
    ("菜单", "张"), ("地图", "张"), ("信用卡", "张"), ("学生证", "张"),
    ("公交卡", "张"), ("图表", "张"), ("表格", "张"), ("发票", "张"),
    ("家具", "件"), ("内衣", "件"), ("大衣", "件"),
    ("内裤", "条"), ("毛巾", "条"), ("河", "条"),
    ("冰箱", "台"), ("电脑", "台"),
    ("手机", "部"), ("小说", "部"), ("电视剧", "部"),
    ("伞", "把"),
    # An event is counted by 场 when you sit through it and by 次 when you
    # merely did it again, which is the distinction this drill is for.
    ("会议", "场"), ("婚礼", "场"), ("比赛", "场"), ("演出", "场"),
    ("官司", "场"),
    ("考试", "次"), ("培训", "次"), ("面试", "次"),
    ("事故", "起"),
    ("京剧", "出"), ("地方戏", "出"),
    ("咖啡", "杯"), ("牛奶", "杯"), ("啤酒", "瓶"),
    ("面条", "碗"), ("米饭", "碗"),
    ("午饭", "顿"), ("晚饭", "顿"), ("早饭", "顿"),
    ("花", "朵"), ("云", "片"),
]
