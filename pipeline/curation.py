"""Hand corrections applied on top of the parsed notes.

The notes were written fast, for one reader, and they contain the things fast
notes contain: characters typed wrong, glosses in personal shorthand, whole
sentences jotted in the vocabulary column. Importing them verbatim produced
cards that cannot be answered — "PR" is not a meaning, 宁静致玩 is not a word.

This file is the curation layer. It is deliberately separate from the parser:
the parser's job is to read what is on the page, and this file's job is to say
what should have been on the page. Re-parsing the notes never loses a correction,
and every correction is visible in one place rather than buried in a database.

Each map is keyed by the headword exactly as the parser produced it.
"""

from __future__ import annotations

# --------------------------------------------------------------------------
# DROP — not vocabulary at all
# --------------------------------------------------------------------------
# Mangled text, stray fragments, and counters that were captured mid-phrase.
# Dropping is reserved for entries with no recoverable meaning; anything that
# can be fixed is fixed below instead.

DROP: dict[str, str] = {
    "坚持练土趣取": "mangled text, no recoverable reading",
    "后平": "fragment, no such word",
    "方旅行": "fragment of 去旅行 / 方 + 旅行 run together",
    "三个": "counter phrase captured with a stray gloss 'de'",
    "已": "captured with the gloss '100 juta' from a neighbouring line",
    "占三分之二": "gloss was the digit '2'; the real word 占 is kept separately",
    "打一场官": "truncated 打一场官司",
    "年轻的代人": "typo for 年轻的一代",
    "老代人": "typo for 老一代",
    "自尊太强": "fragment; 自尊 is kept separately",
    "蒜皮": "fragment of 鸡毛蒜皮",
    "礼貌和客气": "two words joined by 和; both kept separately",
    "帮忙我": "ungrammatical; the correct forms are kept under 帮忙",
    "帮我": "fragment",
    "说这样": "ungrammatical inversion of 这样说",
    "总经": "truncated 总经理",
    "公资": "typo for 工资",
    "生动": "captured as '=生火', a note rather than a gloss",
    "几乎都有": "fragment of a 几乎 example",
    "几乎忘了": "fragment of a 几乎 example",
    "条裙子": "fragment of 一条裙子",
    "开音乐": "typo for 开音响 / 放音乐; not a set word",
    "碰巧遇见": "verb phrase; 碰巧 and 遇见 kept separately",
    "虽然旧": "fragment",
    "祖先坟墓": "two words joined; both kept separately",
    "妇女问题": "transparent compound",
    "批评的权利": "transparent compound",
    "选择的权利": "transparent compound",
    "社会进步": "transparent compound",
    "在社会上": "prepositional phrase",
    "不只": "captured mid-phrase; 不仅 carries this meaning and is kept",
    "神山圣湖": "proper-noun phrase from a travel note",
    "一部戏": "counter phrase",
    "敢写": "fragment",
    "婚姻等": "the 等 is a list marker, not part of the word",
    "单位负责人": "transparent compound of 单位 + 负责人",
    "农林牧渔水利生产人员": "an occupation-code label copied from a form",
    "生产运输设备操作工": "an occupation-code label copied from a form",
    "专业技术人员": "an occupation-code label copied from a form",
    "百首": "counter phrase from a sentence about songs",
    "丈": "fragment; the note meant 丈夫",
    "常说": "adverb + verb, both kept separately",
    "供": "captured with no gloss and no context to recover one",
    "展": "fragment; the note meant 展览",
    "告": "fragment; the note meant 告诉",
    "律": "fragment; the note meant 法律",
    "遇": "fragment; the note meant 遇到",
    "应": "fragment; the note meant 应该",
    "机": "fragment; the note meant 机器 or 手机",
}


# --------------------------------------------------------------------------
# RENAME — the right word, typed wrong
# --------------------------------------------------------------------------

RENAME: dict[str, str] = {
    "刚#始": "开始",
    "宁静致玩": "宁静致远",
    "博性生": "博士生",
    "古百分之七十": "占",
}


# --------------------------------------------------------------------------
# DEMOTE — a sentence that belongs under a word, not beside it
# --------------------------------------------------------------------------
# These were written as examples and the parser read them as headwords. Each
# moves to the example list of the word it illustrates, which is also how the
# cloze and sentence-builder exercises get content.

DEMOTE: dict[str, tuple[str, str]] = {
    # sentence: (owner word, Indonesian gloss)
    "你忙什么呢": ("呢", "Lagi sibuk apa?"),
    "我打球呢": ("呢", "Aku lagi main bola."),
    "你怎么能这样": ("怎么", "Kok kamu bisa begitu?"),
    "祝你一路顺风": ("祝", "Semoga perjalananmu lancar."),
    "差一点撞到了": ("差一点", "Hampir saja tertabrak."),
    "按照原来的计划": ("原来", "Sesuai rencana semula."),
    "谁也说服不了谁": ("说服", "Tidak ada yang bisa meyakinkan siapa pun."),
    "站在我们的身边": ("身边", "Berdiri di sisi kita."),
    "我怎么了": ("怎么", "Aku kenapa ya?"),
    "几乎没有买到": ("几乎", "Hampir saja tidak kebagian."),
    "读懂他的心": ("读懂", "Memahami isi hatinya."),
    "记在心里": ("心里", "Menyimpannya di dalam hati."),
    "对我说一声": ("一声", "Bilang saja padaku."),
    "住在一起": ("一起", "Tinggal bersama."),
    "什么都不怕": ("怕", "Tidak takut apa pun."),
    "究竟由谁买单": ("究竟", "Sebenarnya siapa yang bayar?"),
    "开错了": ("错", "Salah buka / salah nyalakan."),
    "终于开了": ("终于", "Akhirnya buka juga."),
    "说脏话": ("脏话", "Berkata kasar."),
    "一只鸡": ("只", "Seekor ayam."),
    "一封信": ("封", "Sepucuk surat."),
    "一张票": ("张", "Selembar tiket."),
    "两个多小时": ("多", "Dua jam lebih."),
    "三点左右": ("左右", "Sekitar jam tiga."),
    "一米六十三": ("米", "Tingginya 1,63 meter."),
    "占百分之七十": ("占", "Menyumbang tujuh puluh persen."),
    "帮我的忙": ("帮忙", "Tolong bantu aku."),
    "用电脑": ("电脑", "Pakai komputer."),
}


# --------------------------------------------------------------------------
# PINYIN — readings the generator got wrong
# --------------------------------------------------------------------------
# Every entry here is a polyphonic character read out of context. Correcting
# them also marks the reading trusted, which is what lets the word be used for
# listening and typing drills.

PINYIN: dict[str, str] = {
    "睡不着": "shuì bù zháo",
    "着急": "zháo jí",
    "差一点": "chà yì diǎn",
    "差不多": "chà bu duō",
    "差别": "chā bié",
    "音乐": "yīn yuè",
    "行李": "xíng li",
    "银行": "yín háng",
    "重要": "zhòng yào",
    "长城": "cháng chéng",
    "发现": "fā xiàn",
    "教育": "jiào yù",
    "干净": "gān jìng",
    "了解": "liǎo jiě",
    "为了": "wèi le",
    "认为": "rèn wéi",
    "以为": "yǐ wéi",
    "觉得": "jué de",
    "便宜": "pián yi",
    "方便": "fāng biàn",
    "种上": "zhòng shàng",
    "散步": "sàn bù",
    "凉快": "liáng kuai",
    "热闹": "rè nao",
    "客气": "kè qi",
    "舒服": "shū fu",
    "漂亮": "piào liang",
    "消息": "xiāo xi",
    "故事": "gù shi",
    "打算": "dǎ suan",
    "厉害": "lì hai",
    "麻烦": "má fan",
}


# --------------------------------------------------------------------------
# GLOSSES — meanings the notes left blank, and shorthand made answerable
# --------------------------------------------------------------------------
# Two kinds of entry live here.
#
# The blanks: words written down with no meaning beside them, because at the
# time they needed no explanation. A card with no answer cannot be reviewed, so
# each one gets the meaning the note assumed.
#
# The shorthand: "PR", "PD", "kira2", "S2". Perfectly clear to the person who
# wrote them and useless as the back of a flashcard, because the card tests
# whether you know the word, not whether you can decode your own abbreviation.
#
# Indonesian first, then English — the order the notes themselves use.

GLOSSES: dict[str, tuple[str, str]] = {
    # -- shorthand expanded ------------------------------------------------
    "作业": ("pekerjaan rumah, tugas", "homework, assignment"),
    "自信": ("percaya diri", "self-confident"),
    "大概": ("kira-kira, sekitar", "roughly, approximately"),
    "聚会": ("berkumpul, acara kumpul", "gathering, get-together"),
    "没电": ("kehabisan baterai", "out of battery"),
    "空调": ("AC, penyejuk udara", "air conditioning"),
    "光盘": ("cakram CD", "CD, disc"),
    "研究生": ("mahasiswa pascasarjana (S2)", "graduate student"),
    "博士生": ("mahasiswa doktoral (S3)", "doctoral student"),
    "恤": ("kaos (dalam kata 'T恤')", "shirt, as in T-shirt"),
    "刻": ("seperempat jam, 15 menit", "a quarter of an hour"),
    "两亿": ("dua ratus juta", "two hundred million"),
    "危险": ("bahaya, berbahaya", "danger, dangerous"),
    "了解": ("paham, kenal (tapi belum tentu yakin)", "to understand, be familiar with"),
    "睡不着": ("tidak bisa tidur", "unable to fall asleep"),
    "它": ("itu, ia (untuk benda/hewan)", "it"),
    "离": ("dari, berjarak dari", "away from, apart from"),
    "被": ("oleh (penanda kalimat pasif)", "by (passive marker)"),

    # -- blanks filled -----------------------------------------------------
    "平时": ("biasanya, sehari-hari", "usually, ordinarily"),
    "准备": ("bersiap, menyiapkan", "to prepare, get ready"),
    "以为": ("mengira (ternyata salah)", "to assume wrongly"),
    "觉得": ("merasa, menurutku", "to feel, to think"),
    "认为": ("berpendapat, menganggap", "to consider, to hold the view"),
    "这儿": ("di sini", "here"),
    "那儿": ("di sana", "there"),
    "吵死了": ("berisik banget", "so noisy"),
    "别闹": ("jangan berulah", "stop messing about"),
    "风景": ("pemandangan", "scenery, landscape"),
    "开灯": ("menyalakan lampu", "to turn on the light"),
    "晚会": ("acara malam, pesta", "evening party"),
    "亲戚": ("saudara, kerabat", "relative"),
    "照相馆": ("studio foto", "photo studio"),
    "应该": ("seharusnya, sebaiknya", "should, ought to"),
    "公共汽车": ("bus kota", "public bus"),
    "几": ("berapa, beberapa", "how many, several"),
    "八达岭": ("Badaling (bagian Tembok Besar)", "Badaling, a section of the Great Wall"),
    "里": ("di dalam", "inside, in"),
    "万里长征": ("Long March", "the Long March"),
    "麻烦": ("merepotkan, ribet", "troublesome, to bother"),
    "方便": ("praktis, memudahkan", "convenient"),
    "简单": ("sederhana, gampang", "simple, easy"),
    "路": ("jalan, rute", "road, route"),
    "宋": ("Dinasti Song", "the Song dynasty"),
    "倒": ("menuang; malah, justru", "to pour; on the contrary"),
    "学期": ("semester", "semester, term"),
    "累": ("capek, lelah", "tired"),
    "讲故事": ("bercerita", "to tell a story"),
    "才": ("baru saja; hanya", "only just, not until"),
    "高": ("tinggi", "tall, high"),
    "表达": ("mengungkapkan", "to express"),
    "花时间": ("menghabiskan waktu", "to spend time"),
    "花钱": ("mengeluarkan uang", "to spend money"),
    "电": ("listrik", "electricity"),
    "国际学校": ("sekolah internasional", "international school"),
    "房租": ("uang sewa rumah", "rent"),
    "再": ("lagi, sekali lagi", "again, once more"),
    "办": ("mengurus, mengerjakan", "to handle, to take care of"),
    "话题": ("topik pembicaraan", "topic of conversation"),
    "过": ("pernah (penanda pengalaman); lewat", "marker of past experience; to pass"),
    "留": ("meninggalkan, menyimpan", "to leave behind, to keep"),
    "地": ("penanda kata keterangan; tanah", "adverb marker; ground"),
    "出去": ("keluar", "to go out"),
    "西北": ("barat laut", "northwest"),
    "锻炼": ("olahraga, melatih diri", "to exercise, to train"),
    "碰巧": ("kebetulan", "by coincidence"),
    "遇见": ("bertemu tanpa sengaja", "to run into, to meet"),
    "帮": ("menolong, membantu", "to help"),
    "烤鸭": ("bebek panggang", "roast duck"),
    "天安门": ("Tiananmen", "Tiananmen"),
    "夏令营": ("kemah musim panas", "summer camp"),
    "经常": ("sering", "often, frequently"),
    "边": ("sisi, pinggir", "side, edge"),
    "告诉": ("memberi tahu", "to tell"),
    "保证": ("menjamin", "to guarantee"),
    "穿": ("memakai (baju/sepatu)", "to wear, to put on"),
    "床": ("tempat tidur", "bed"),
    "大多数": ("sebagian besar", "the majority"),
    "大使馆": ("kedutaan besar", "embassy"),
    "当然": ("tentu saja", "of course"),
    "点钟": ("jam (penunjuk waktu)", "o'clock"),
    "电子词典": ("kamus elektronik", "electronic dictionary"),
    "读书": ("membaca, bersekolah", "to read, to study"),
    "服务员": ("pelayan", "waiter, attendant"),
    "付钱": ("membayar", "to pay"),
    "感觉": ("perasaan, merasa", "feeling, to feel"),
    "感谢": ("berterima kasih", "to thank"),
    "公司": ("perusahaan", "company"),
    "狗": ("anjing", "dog"),
    "贵": ("mahal", "expensive"),
    "寒冷": ("dingin sekali", "bitterly cold"),
    "欢迎": ("selamat datang, menyambut", "to welcome"),
    "灰色": ("abu-abu", "grey"),
    "假期": ("masa libur", "holiday, vacation"),
    "饺子": ("pangsit rebus", "dumpling"),
    "考试": ("ujian", "exam"),
    "快": ("cepat", "fast, quick"),
    "快餐店": ("restoran cepat saji", "fast-food restaurant"),
    "冷": ("dingin", "cold"),
    "利用": ("memanfaatkan", "to make use of"),
    "练习": ("berlatih, latihan", "to practise, exercise"),
    "旅游": ("berwisata", "to travel, tourism"),
    "慢慢": ("pelan-pelan", "slowly"),
    "帽子": ("topi", "hat"),
    "课文": ("teks pelajaran", "text, lesson passage"),
    "新鲜": ("segar", "fresh"),
    "声调": ("nada (dalam pelafalan)", "tone"),
    "举": ("mengangkat; memberi (contoh)", "to raise; to cite"),
    "特别": ("khusus, terutama", "special, especially"),
    "常常": ("sering", "often"),
    "习惯": ("kebiasaan, terbiasa", "habit, to be used to"),
    "新闻": ("berita", "news"),
    "踢足球": ("main sepak bola", "to play football"),
    "比赛": ("pertandingan", "match, competition"),
    "饿": ("lapar", "hungry"),
    "蔬菜": ("sayuran", "vegetables"),
    "开饭馆": ("membuka rumah makan", "to open a restaurant"),
    "老板": ("bos, pemilik usaha", "boss, proprietor"),
    "老板娘": ("bu bos, istri pemilik usaha", "proprietress"),
    "欧洲": ("Eropa", "Europe"),
    "努力": ("berusaha keras", "to work hard"),
    "而且": ("lagipula, selain itu", "moreover, and also"),
    "一定": ("pasti, harus", "certainly, definitely"),
    "挣钱": ("menghasilkan uang", "to earn money"),
    "世界": ("dunia", "world"),
    "懂": ("mengerti", "to understand"),
    "愿意": ("bersedia, mau", "to be willing"),
    "突然": ("tiba-tiba", "suddenly"),
    "瘦": ("kurus", "thin, skinny"),
    "心里": ("di dalam hati", "in one's heart, inwardly"),
    "读研究生": ("kuliah S2", "to study for a master's degree"),
    "生活质量": ("kualitas hidup", "quality of life"),
    "管": ("mengurus, mengatur", "to manage, to be in charge of"),
    "考": ("mengikuti ujian", "to sit an exam"),
    "握": ("menggenggam", "to grip, to hold"),
    "散": ("bubar, menyebar", "to scatter, to disperse"),
    "蓝色": ("biru", "blue"),
    "下来": ("turun", "to come down"),
    "停下来": ("berhenti", "to stop"),
    "清楚": ("jelas", "clear"),
    "问题": ("masalah, pertanyaan", "problem, question"),
    "帮助": ("bantuan, membantu", "help, to help"),
    "大街": ("jalan besar", "main street"),
    "把": ("penanda objek yang dikenai tindakan", "object marker"),
    "参观": ("mengunjungi, meninjau", "to visit, to tour"),
    "兵马俑": ("Prajurit Terakota", "the Terracotta Army"),
    "颜色": ("warna", "colour"),
    "极了": ("banget, amat sangat", "extremely"),
    "屋": ("kamar, ruangan", "room"),
    "选": ("memilih", "to choose"),
    "差不多": ("hampir sama, kurang lebih", "about the same, more or less"),
    "静静": ("dengan tenang", "quietly"),
    "美丽": ("indah, cantik", "beautiful"),
    "生病": ("jatuh sakit", "to fall ill"),
    "登上了": ("berhasil mendaki", "to have climbed up"),
    "厉害": ("hebat; parah", "impressive; severe"),
    "不必": ("tidak perlu", "need not"),
    "月亮": ("bulan", "the moon"),
    "赛": ("bertanding", "to compete"),
    "上网": ("berselancar internet", "to go online"),
    "闹钟": ("jam weker", "alarm clock"),
    "汇款": ("mengirim uang", "to remit money"),
    "之前": ("sebelum", "before"),
    "排队": ("mengantre", "to queue"),
    "久": ("lama", "long (in time)"),
    "常": ("sering", "often"),
    "服": ("menaati; tunduk", "to obey, to be convinced"),
    "礼貌": ("sopan santun", "politeness, manners"),
    "客气": ("sungkan, basa-basi", "polite, courteous"),
    "舒服": ("nyaman, enak badan", "comfortable, well"),
    "当": ("menjadi, berperan sebagai", "to serve as, to be"),
    "或者": ("atau", "or"),
    "感到": ("merasakan", "to feel"),
    "这样说": ("bilang begitu", "to put it that way"),
    "却": ("padahal, namun", "however, yet"),
    "青藏": ("Qinghai-Tibet", "Qinghai-Tibet"),
    "全国": ("seluruh negeri", "the whole country"),
    "各": ("masing-masing", "each, every"),
    "纳木错": ("Danau Namtso", "Lake Namtso"),
    "蜜月": ("bulan madu", "honeymoon"),
    "相互": ("saling", "mutually, each other"),
    "树": ("pohon", "tree"),
    "希望": ("berharap, harapan", "to hope, hope"),
    "种上": ("menanam", "to plant"),
    "谈话": ("berbincang, percakapan", "to talk, conversation"),
    "短": ("pendek", "short"),
    "奇怪": ("aneh", "strange, odd"),
    "进城": ("pergi ke kota", "to go into town"),
    "急急忙忙": ("terburu-buru", "in a great hurry"),
    "直": ("lurus; terus", "straight; directly"),
    "该": ("seharusnya; giliran", "should; one's turn"),
    "段": ("bagian, penggal", "section, stretch"),
    "传到": ("tersebar sampai ke", "to spread to, to reach"),
    "有作用": ("ada pengaruhnya", "to have an effect"),
    "安全": ("aman, keamanan", "safe, safety"),
    "要是": ("kalau, seandainya", "if"),
    "健康": ("sehat, kesehatan", "healthy, health"),
    "件": ("satuan untuk baju/perkara", "measure word for clothes and matters"),
    "忘": ("lupa", "to forget"),
    "办成": ("berhasil mengurus", "to get something done"),
    "汽车": ("mobil", "car"),
    "耐心": ("sabar, kesabaran", "patient, patience"),
    "费": ("biaya; menghabiskan", "fee; to consume"),
    "辈子": ("seumur hidup", "lifetime"),
    "小区": ("kompleks perumahan", "residential complex"),
    "工作": ("pekerjaan, bekerja", "work, to work"),
    "惊讶": ("terkejut, heran", "astonished"),
    "文具": ("alat tulis", "stationery"),
    "日常用品": ("barang kebutuhan sehari-hari", "daily necessities"),
    "肯定": ("pasti; memastikan", "certainly; to affirm"),
    "厦门航空": ("Xiamen Airlines", "Xiamen Airlines"),
    "三思而后行": ("pikir masak-masak sebelum bertindak", "think thrice before acting"),
    "椅": ("kursi", "chair"),
    "随": ("mengikuti, terserah", "to follow, according to"),
    "生意": ("bisnis, dagang", "business"),
    "得意": ("bangga, puas diri", "pleased with oneself"),
    "厕所": ("toilet", "toilet"),
    "平安": ("selamat, aman", "safe and sound"),
    "宿舍": ("asrama", "dormitory"),
    "不管怎么样": ("bagaimanapun juga", "in any case, whatever happens"),
    "脏话": ("kata kasar", "swear word"),
    "去世": ("meninggal dunia", "to pass away"),
    "不在乎": ("tidak peduli", "not to care"),
    "实际上": ("sebenarnya, kenyataannya", "in fact, actually"),
    "干": ("mengerjakan; kering", "to do; dry"),
    "怕": ("takut", "to fear"),
    "开始": ("mulai", "to begin"),
    "需要": ("butuh, memerlukan", "to need"),
    "首先": ("pertama-tama", "first of all"),
    "国家公务员": ("pegawai negeri", "civil servant"),
    "凭什么": ("atas dasar apa", "on what grounds"),
    "苦": ("pahit; susah", "bitter; hard"),
    "苦干": ("bekerja keras", "to work doggedly"),
    "苦练": ("berlatih keras", "to train hard"),
    "苦学": ("belajar mati-matian", "to study hard"),
    "事情": ("urusan, hal", "matter, affair"),
    "出名": ("terkenal", "to become famous"),
    "骗子": ("penipu", "swindler"),
    "骗": ("menipu", "to deceive"),
    "招聘通知": ("pengumuman lowongan", "job advertisement"),
    "省钱": ("hemat, menghemat uang", "to save money"),
    "自尊": ("harga diri", "self-respect"),
    "新事情": ("hal baru", "something new"),
    "接受": ("menerima", "to accept"),
    "原因": ("sebab, alasan", "reason, cause"),
    "谈恋爱": ("pacaran", "to date, to be in a relationship"),
    "级": ("tingkat, kelas", "level, grade"),
    "等": ("menunggu; dan lain-lain", "to wait; et cetera"),
    "内衣": ("pakaian dalam", "underwear"),
    "内裤": ("celana dalam", "underpants"),
    "放心": ("tenang, tidak khawatir", "to be at ease"),
    "同性恋": ("homoseksual", "homosexual"),
    "祝贺": ("mengucapkan selamat", "to congratulate"),
    "认不出来": ("tidak mengenali", "to fail to recognise"),
    "歌手": ("penyanyi", "singer"),
    "汉族": ("suku Han", "the Han ethnicity"),
    "舍不得": ("berat hati, tidak tega", "to be reluctant to part with"),
    "西瓜": ("semangka", "watermelon"),
    "甜": ("manis", "sweet"),
    "靠": ("bersandar; mengandalkan", "to lean on, to rely on"),
    "介绍": ("memperkenalkan", "to introduce"),
    "教育": ("pendidikan, mendidik", "education, to educate"),
    "舅舅": ("paman (dari pihak ibu)", "maternal uncle"),
    "方式": ("cara, metode", "way, manner"),
    "推荐": ("merekomendasikan", "to recommend"),
    "美甲": ("perawatan kuku, nail art", "manicure, nail art"),
    "厨房": ("dapur", "kitchen"),
    "私立学校": ("sekolah swasta", "private school"),
    "重要": ("penting", "important"),
    "律师事务所": ("kantor hukum", "law firm"),
    "心理学家": ("psikolog", "psychologist"),
    "中国大陆": ("Tiongkok daratan", "mainland China"),
    "答应": ("menyanggupi, berjanji", "to agree, to promise"),
    "职业": ("profesi, pekerjaan", "occupation, profession"),
    "一起": ("bersama-sama", "together"),
    "只": ("hanya; satuan untuk hewan", "only; measure word for animals"),
    "封": ("satuan untuk surat", "measure word for letters"),
    "张": ("satuan untuk benda pipih", "measure word for flat objects"),
    "多": ("banyak; lebih", "many; more than"),
    "左右": ("kira-kira, sekitar", "approximately"),
    "米": ("meter; beras", "metre; rice"),
    "占": ("menempati, menyumbang (persentase)", "to occupy, to account for"),
    "帮忙": ("membantu", "to help out"),
    "电脑": ("komputer", "computer"),
    "呢": ("partikel penanda 'sedang' / penegas pertanyaan", "particle marking ongoing action or a follow-up question"),
    "怎么": ("bagaimana, kenapa", "how, why"),
    "祝": ("mendoakan, mengucapkan", "to wish (someone something)"),
    "差一点": ("nyaris, hampir saja", "very nearly, almost"),
    "原来": ("ternyata; semula", "as it turns out; originally"),
    "说服": ("meyakinkan, membujuk", "to persuade"),
    "身边": ("di sisi, di dekat", "at one's side"),
    "几乎": ("hampir", "almost, nearly"),
    "读懂": ("membaca sampai paham", "to read and understand"),
    "一声": ("sepatah kata, sekali bunyi", "a word, a single sound"),
    "究竟": ("sebenarnya, pada akhirnya", "after all, exactly"),
    "错": ("salah", "wrong, mistaken"),
    "终于": ("akhirnya", "finally, at last"),
    "宁静致远": ("ketenangan membawa pencapaian jauh", "a quiet mind reaches far"),
}


# --------------------------------------------------------------------------
# PINYIN, second pass — readings checked one by one
# --------------------------------------------------------------------------
# Everything below was a word the generator flagged as uncertain because it
# contains a genuinely ambiguous character. Each was read in the context of its
# own meaning and either confirmed or corrected. Recording the confirmations
# matters as much as the corrections: an unconfirmed reading is barred from the
# listening and typing drills, so leaving a correct one flagged costs the word
# two of its four skills.

PINYIN.update({
    # -- corrected ---------------------------------------------------------
    "青藏": "qīng zàng",          # 藏 is zàng in Qinghai-Tibet, not cáng
    "差得多": "chà de duō",        # 得 is the neutral-tone particle here, not dé
    "细数": "xì shǔ",             # shǔ "to count", not shù "a number"
    "舍不得": "shě bu de",         # both syllables reduce after 舍
    "得": "de",                   # the notes use it as the V+得+Adj particle
    "地": "de",                   # likewise the adverb marker, not dì "ground"

    # -- confirmed ---------------------------------------------------------
    "教堂": "jiào táng",
    "教室": "jiào shì",
    "教师": "jiào shī",
    "教授": "jiào shòu",
    "教练": "jiào liàn",
    "副教练": "fù jiào liàn",
    "娱乐场所": "yú lè chǎng suǒ",
    "游行": "yóu xíng",
    "照相馆": "zhào xiàng guǎn",
    "生产队长": "shēng chǎn duì zhǎng",
    "生活质量": "shēng huó zhì liàng",
    "感兴趣": "gǎn xìng qù",
    "晚会": "wǎn huì",
    "尊重": "zūn zhòng",
    "假装": "jiǎ zhuāng",
    "得到": "dé dào",
    "传到": "chuán dào",
    "参与者": "cān yù zhě",
    "扫码": "sǎo mǎ",
    "扫码点单": "sǎo mǎ diǎn dān",
    "厦门航空": "xià mén háng kōng",
    "宁静致远": "níng jìng zhì yuǎn",
    "异地恋": "yì dì liàn",
    "十几": "shí jǐ",
    "几": "jǐ",
    "占": "zhàn",
    "只": "zhǐ",
    "干": "gàn",
    "长": "zhǎng",
    "度": "dù",
    "强": "qiáng",
    "当": "dāng",
    "待": "dài",
    "将": "jiāng",
    "提": "tí",
    "散": "sàn",
    "数": "shù",
    "称": "chēng",
    "结": "jié",
    "职称": "zhí chēng",
    "世界大战": "shì jiè dà zhàn",
    "农业大学": "nóng yè dà xué",
    "工业大学": "gōng yè dà xué",
    "科技大学": "kē jì dà xué",
    "中国大陆": "zhōng guó dà lù",
    "发挥水平": "fā huī shuǐ píng",
    "发挥特点": "fā huī tè diǎn",
    "发挥特色": "fā huī tè sè",
    "修好屋顶": "xiū hǎo wū dǐng",
    "祝你好运": "zhù nǐ hǎo yùn",
    "挺好": "tǐng hǎo",
    "这不挺好吗": "zhè bù tǐng hǎo ma",
    "也就是说": "yě jiù shì shuō",
    "这样说": "zhè yàng shuō",
    "那当然": "nà dāng rán",
    "多难": "duō nán",
    "多么难": "duō me nán",
    "不难怪": "bù nán guài",
    "遇到困难": "yù dào kùn nán",
    "没电": "méi diàn",
    "没省钱": "méi shěng qián",
    "没有我高": "méi yǒu wǒ gāo",
    "吵死了": "chǎo sǐ le",
    "太油了": "tài yóu le",
    "太闹了": "tài nào le",
    "太不像话了": "tài bù xiàng huà le",
    "扔了": "rēng le",
    "极了": "jí le",
    "糟糕了": "zāo gāo le",
    "登上了": "dēng shàng le",
    "被别人告了": "bèi bié rén gào le",
    "除了以外": "chú le yǐ wài",
    "参观": "cān guān",
    "兵马俑": "bīng mǎ yǒng",
})

# --------------------------------------------------------------------------
# Second-pass typos, found while checking readings
# --------------------------------------------------------------------------
# Reading each word aloud in context is what surfaced these — a wrong character
# usually still has a plausible reading, so nothing earlier in the pipeline had
# any reason to object.

RENAME.update({
    "切井": "切开",        # source line "切井qikaimemotong buka": qie kai, "cut open"
    "纯洁无假": "纯洁无瑕",  # "tidak bernoda" — flawless; 瑕 (flaw), not 假 (false)
})

GLOSSES.update({
    "切开": ("memotong, membuka dengan memotong", "to cut open"),
    "纯洁无瑕": ("murni tanpa noda", "pure and flawless"),
})

PINYIN.update({
    "切开": "qiē kāi",
    "纯洁无瑕": "chún jié wú xiá",
})

DROP.update({
    "大排队好忙": "run-together note; 排队 carries the meaning and is kept",
    "参观兵马俑": "verb + object; 参观 and 兵马俑 are kept separately",
    "舍不得离开": "verb phrase; 舍不得 and 离开 are kept separately",
    "累极": "fragment of 累极了; 累 and 极了 are kept separately",
})


# --------------------------------------------------------------------------
# Neutral tones, and one entry that a bad edit removed from DROP
# --------------------------------------------------------------------------
# The generator gives these their citation tone. Spoken, the second syllable
# reduces, and a learner drilling them from audio would hear the reduced form.

PINYIN.update({
    "好处": "hǎo chu",
    "商量": "shāng liang",
    "答应": "dā ying",
    "地道": "dì dao",
    "客气": "kè qi",
    "热闹": "rè nao",
    "厉害": "lì hai",
    "麻烦": "má fan",
    "打算": "dǎ suan",
    "故事": "gù shi",
    "消息": "xiāo xi",
    "便宜": "pián yi",
})

DROP.update({
    # Re-stated here because an earlier prune removed it from DROP by accident:
    # the pattern it matched on was not scoped to one section of this file.
    "只靠": "fragment; 靠 is kept separately",
})


# --------------------------------------------------------------------------
# Third pass — glosses and headwords found broken while checking readings
# --------------------------------------------------------------------------
# Typing at speed leaves a particular trail: a letter dropped from an Indonesian
# word, a space landing mid-word, and occasionally a homophone or lookalike
# character. None of these stop the pipeline, because a wrong character usually
# still has a perfectly good reading — they only show up on being read.

GLOSSES.update({
    "不可靠": ("tidak bisa diandalkan", "unreliable"),
    "二手": ("bekas, secondhand", "second-hand"),
    "农村": ("kampung, pedesaan", "countryside, village"),
    "即使": ("sekalipun, meskipun", "even if"),
    "合作": ("kerja sama", "to cooperate, cooperation"),
    "地久天长": ("selamanya, langgeng", "enduring as heaven and earth"),
    "实在": ("sesungguhnya, benar-benar", "really, honestly"),
    "太不像话了": ("keterlaluan ini", "this is outrageous"),
    "成功": ("berhasil, sukses", "to succeed, successful"),
    "改口": ("mengubah ucapan, meralat", "to take back what one said"),
    "能力": ("kemampuan", "ability"),
    "生活费": ("biaya hidup", "living costs"),
    "看不起": ("meremehkan", "to look down on"),
    "翻译": ("menerjemahkan, penerjemah", "to translate, translator"),
    "情况": ("situasi, keadaan", "situation, circumstances"),
    "逛逛": ("jalan-jalan, lihat-lihat", "to stroll around"),
    "细数": ("menghitung satu per satu", "to count off one by one"),
    "得": ("penanda pelengkap setelah kata kerja", "particle linking a verb to its complement"),

    # Glosses that belonged to a different word.
    "气场": ("aura, wibawa", "presence, aura"),
    "机场": ("bandara", "airport"),
    "表扬": ("memuji", "to praise, to commend"),
    "货款": ("pembayaran barang", "payment for goods"),
    "贷款": ("pinjaman, kredit", "loan"),
})

RENAME.update({
    "技木": "技术",      # 木 for 术
    "竟争": "竞争",      # 竟 for 竞
    "窗戸": "窗户",      # 戸 is the Japanese form of 户
    "朴愫": "朴素",      # 愫 for 素
    "寮": "撩",          # 撩 liāo "to tease"; 寮 is a hut
})

GLOSSES.update({
    "技术": ("teknologi, keterampilan teknis", "technology, technique"),
    "竞争": ("bersaing, persaingan", "to compete, competition"),
    "窗户": ("jendela", "window"),
    "朴素": ("sederhana, bersahaja", "plain, simple"),
    "撩": ("menggoda", "to tease, to flirt with"),
})

PINYIN.update({
    "技术": "jì shù",
    "竞争": "jìng zhēng",
    "窗户": "chuāng hu",
    "朴素": "pǔ sù",
    "撩": "liāo",
    "气场": "qì chǎng",
    "机场": "jī chǎng",
})

DROP.update({
    "彳": "a radical noted for reference (shuangrenpang), not a word",
    "穴": "captured with unreadable text beside it",
    "章印": "unclear which word was meant; 印章 and 盖章 are both plausible",
})


# Readings for words the curation itself introduced.
PINYIN.update({"得到奖学金": "dé dào jiǎng xué jīn"})

# Two corrections written for words later removed. Kept as a record of why they
# are gone rather than deleted, but the build flags them as unused, so they are
# retired here.
for _retired in ("登上了", "除了以外"):
    PINYIN.pop(_retired, None)
