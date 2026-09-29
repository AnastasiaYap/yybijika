"""One sample passage, so the reading section is not empty on a fresh install.

Written from starter/notes.txt and nothing else, which is also the point of it:
it shows what a passage built from your own vocabulary looks like, and it is
short enough that replacing it is obviously the intention.

The real passages live in pipeline/essays.py and pipeline/essays_long.py, and
are written around one particular person's notes. A deck built from anyone
else's notes gets this instead.
"""

from __future__ import annotations

STARTER_ESSAYS: list[dict] = [
    {
        "id": "S01",
        "title": "周末的早上",
        "title_id": "Pagi di akhir pekan",
        "title_en": "A weekend morning",
        "level": 1,
        "tags": ["time", "home"],
        "summary_id": (
            "Akhir pekan, tidak perlu masuk kerja, dan seorang teman datang "
            "tanpa memberi kabar."
        ),
        "summary_en": (
            "It is the weekend, there is no work to go to, and a friend turns up "
            "without saying first."
        ),
        "lines": [
            ("今天是周末，我不用上班。",
             "Hari ini akhir pekan, saya tidak perlu masuk kerja.",
             "Today is the weekend, so I do not have to go to work."),
            ("我很累，所以起得很晚。",
             "Saya lelah, jadi bangun kesiangan.",
             "I was tired, so I got up late."),
            ("早上我没有吃早餐。",
             "Pagi ini saya tidak sarapan.",
             "I did not have breakfast this morning."),
            ("我只喝水，然后打扫房间。",
             "Saya hanya minum air, lalu bersih-bersih kamar.",
             "I only drank some water, then cleaned my room."),
            ("十点的时候，我的朋友来了。",
             "Pukul sepuluh, teman saya datang.",
             "At ten o'clock my friend arrived."),
            ("他没说他要来，所以我很惊讶。",
             "Dia tidak bilang mau datang, jadi saya kaget.",
             "He had not said he was coming, so I was surprised."),
            ("他说：今天你有空吗？",
             "Dia berkata: hari ini kamu ada waktu?",
             "He said: are you free today?"),
            ("我说有空，我们就去吃面条。",
             "Saya bilang ada waktu, lalu kami pergi makan mi.",
             "I said I was, and we went for noodles."),
            ("那家的面条很好吃，可是有点辣。",
             "Mi di tempat itu enak, tapi agak pedas.",
             "The noodles there were good, but a little spicy."),
            ("吃完以后，我们在附近走了走。",
             "Setelah makan, kami jalan-jalan di sekitar situ.",
             "After eating we walked around nearby."),
            ("周末就这样过去了。",
             "Begitulah akhir pekan itu berlalu.",
             "And that was the weekend gone."),
        ],
        "questions": [
            {
                "q": "今天为什么不用上班？",
                "choices": ["因为是周末", "因为他生病了",
                            "因为他迟到了", "因为他搬家了"],
                "answer": 0,
                "explain": "第一句：“今天是周末，我不用上班。”",
            },
            {
                "q": "早上他做了什么？",
                "choices": ["吃了早餐", "喝水，然后打扫房间",
                            "去了机场", "开会了"],
                "answer": 1,
                "explain": "“我只喝水，然后打扫房间。”",
            },
            {
                "q": "他为什么很惊讶？",
                "choices": ["朋友没说要来就来了", "面条太辣了",
                            "他找不到钥匙", "他迟到了"],
                "answer": 0,
                "explain": "“他没说他要来，所以我很惊讶。”",
            },
            {
                "q": "面条怎么样？",
                "choices": ["不好吃", "很好吃，可是有点辣",
                            "太贵了", "太少了"],
                "answer": 1,
                "explain": "“那家的面条很好吃，可是有点辣。”",
            },
        ],
    },
]
