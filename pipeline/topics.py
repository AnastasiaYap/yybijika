"""What each word is *about*.

The deck already had tags, but they were built for one job: picking plausible
wrong answers. Half of them are section markers (``s071``) that mean "written on
the same half-page as", which works for distractors and is meaningless to a
reader. Nothing in the app could answer "show me the work vocabulary".

So topics are their own thing, and they are deliberately not exclusive — 面试 is
work and talking, 结婚 is relationships and officialdom, 医院 is health and
places. A word is about as many things as it is about, which is also how anyone
would search for it.

How a word is classified, strongest signal first:

  1. An explicit listing here. The last word on anything the rules get wrong.
  2. A phrase in the gloss ("satuan untuk", "measure word").
  3. A keyword in the gloss. The glosses are the author's own Indonesian and the
     deck's English, so matching them is matching how the words were explained.
  4. A character in the headword. Weakest and noisiest — 服 is clothing in 衣服
     and neither in 舒服 — so every cue that misfires gets an exclusion rather
     than being dropped, because the cue still earns its place on the rest.

Anything that matches nothing stays untopiced, which is the honest answer for
就, 却 and 难怪.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Topic:
    slug: str
    name_en: str
    name_id: str
    emoji: str
    #: Whole-word matches against the glosses, in either language.
    words: tuple[str, ...] = ()
    #: Substring matches, for anything that is more than one word.
    phrases: tuple[str, ...] = ()
    #: Characters in the headword that imply the topic.
    hanzi: tuple[str, ...] = ()
    #: Headwords that always belong here, whatever the rules say.
    include: tuple[str, ...] = ()
    #: Headwords this topic must never claim, however it matched.
    exclude: tuple[str, ...] = ()
    #: Require the keyword to *be* a whole sense, not merely appear in one.
    #:
    #: For the describing words. "tinggi | tall, high" is a word about height;
    #: "gedung tinggi | tower block, high-rise" is a building that happens to
    #: be tall, and 大使馆 "kedutaan besar" is an embassy and not about size at
    #: all. Adjectives turn up inside other words' glosses constantly, so for
    #: these topics the adjective has to be the entire meaning.
    exact_sense: bool = False


# Scores, so a word with six weak hits does not out-rank the thing it is plainly
# about. A character cue alone is enough to file a word, but never enough to
# beat a gloss that says so in words.
SCORE_INCLUDE = 100
SCORE_PHRASE = 8
SCORE_WORD = 5
SCORE_HANZI = 2

#: At most this many topics per word. More than three stops being a category and
#: starts being a description.
MAX_TOPICS = 3


TOPICS: tuple[Topic, ...] = (
    Topic(
        "work", "Work", "Pekerjaan", "💼",
        words=(
            "kerja", "bekerja", "pekerjaan", "kantor", "karyawan", "pegawai",
            "staf", "staff", "bos", "atasan", "bawahan", "gaji", "upah",
            "magang", "perusahaan", "jabatan", "rapat", "proyek", "klien",
            "pelanggan", "lembur", "pensiun", "wawancara", "melamar", "dipecat",
            "manajer", "direktur", "sekretaris", "teknisi", "mekanik",
            "insinyur", "buruh", "pabrik", "bisnis", "kolega",
            "rekan", "shift", "tugas", "profesi", "karir", "karier",
            "job", "work", "office", "salary", "wage", "manager", "boss",
            "colleague", "employee", "employer", "business",
            "meeting", "project", "client", "customer", "career", "profession",
            "professional", "overtime", "interview", "hire", "resign",
            "firm",
            "retire", "workplace", "factory", "intern", "internship",
            "department", "position", "editor", "economy",
            "economic", "serve", "service", "waiter", "attendant",
            "ekonomi", "pelayan", "editor", "mengedit", "melayani",
            "pelayanan", "pemasaran", "produksi",
            "training", "promote", "promotion", "dismiss", "expel",
            "allocate", "maintain", "maintenance", "repair", "enterprise",
            "employment",
            "pelatihan", "mempromosikan", "memecat", "cuti", "memperbaiki",
            "perawatan", "mendistribusikan", "perusahaan", "mandor",
        ),
        phrases=("tempat kerja", "jam kerja", "cari kerja", "dunia kerja",
                 "take leave", "mengambil cuti", "annual leave"),
        hanzi=("工", "职", "司", "雇", "聘", "班"),
        exclude=(
            "上班车", "班级", "同班", "工具", "手工",
            "毕业", "作业", "专业", "业余",
            "做伴", "派出所", "警察局", "官司", "打官司", "打赢官司",
        ),
    ),
    Topic(
        "study", "Study", "Belajar", "📚",
        words=(
            "belajar", "sekolah", "kuliah", "universitas", "kampus", "ujian",
            "nilai", "buku", "baca", "membaca", "tulis", "menulis", "bahasa",
            "kata", "kalimat", "grammar", "murid", "siswa", "mahasiswa",
            "guru", "dosen", "pelajaran", "jurusan", "lulus", "wisuda",
            "kelas", "sd", "smp", "sma", "pr", "kamus", "catatan", "latihan",
            "study", "learn", "school", "university", "college", "class",
            "lesson", "exam", "test", "homework", "book", "read", "write",
            "word", "sentence", "student", "teacher", "professor", "course",
            "major", "graduate", "library", "dictionary", "notebook",
            "pencil", "essay", "article", "textbook", "revise", "semester",
            "pupil", "degree", "research", "pronunciation", "tone",
            "translate", "translator", "fluent", "knowledge", "meaning",
            "pelafalan", "nada", "menerjemahkan", "penerjemah", "fasih",
            "pengetahuan", "mengeja", "menghafal", "mencatat",
        ),
        phrases=("tata bahasa", "mata kuliah", "ruang kelas", "junior high",
                 "senior high", "primary school"),
        hanzi=("学", "书", "读", "课", "考", "教", "习", "字", "词", "题"),
        exclude=("学会", "科学家", "心理学家"),
    ),
    Topic(
        "money", "Money", "Uang", "💰",
        words=(
            "uang", "harga", "bayar", "membayar", "beli", "membeli", "jual",
            "menjual", "murah", "mahal", "gratis", "untung", "rugi", "diskon",
            "belanja", "toko", "pasar", "kasir", "dompet", "tabungan", "bank",
            "pinjam", "utang", "hutang", "kredit", "biaya", "ongkos",
            "tagihan", "kaya", "miskin", "modal", "hemat", "boros", "bonus",
            "money", "price", "cost", "pay", "buy", "sell", "cheap",
            "expensive", "discount", "shop", "shopping", "store", "market",
            "cash", "bank", "loan", "debt", "credit", "fee", "bill", "rich",
            "poor", "spend", "save", "afford", "wallet", "budget", "profit",
            "invest", "refund", "wealth",
            "income", "owe", "export", "import", "salary", "earn", "rent",
            "pendapatan", "berutang", "ekspor", "impor", "penghasilan",
            "menyumbang", "tarif",
        ),
        phrases=("cuci mata", "tanpa bayar", "uang muka", "window-shop"),
        hanzi=("钱", "买", "卖", "费", "价", "贵", "账", "付", "赚", "存", "币"),
        exclude=("卖力", "买卖人",),
    ),
    Topic(
        "food", "Food", "Makanan", "🍜",
        words=(
            "makan", "makanan", "minum", "minuman", "nasi", "mie", "ayam",
            "sapi", "ikan", "sayur", "sayuran", "buah", "daging", "telur",
            "roti", "kue", "gula", "garam", "masak", "memasak", "goreng",
            "rebus", "tumis", "menu", "restoran", "warung", "piring",
            "mangkuk", "sendok", "garpu", "sumpit", "koki", "chef", "rasa",
            "manis", "asin", "pedas", "asam", "pahit", "lapar", "haus",
            "kenyang", "sarapan", "teh", "kopi", "susu", "bir", "camilan",
            "eat", "drink", "food", "meal", "dish", "cook", "rice", "noodle",
            "noodles", "chicken", "beef", "fish", "vegetable", "fruit",
            "meat", "egg", "bread", "cake", "sugar", "salt", "restaurant",
            "menu", "plate", "bowl", "spoon", "chopsticks", "taste", "sweet",
            "salty", "spicy", "sour", "bitter", "hungry", "thirsty",
            "breakfast", "lunch", "dinner", "tea", "coffee", "milk", "beer",
            "wine", "snack", "delicious", "recipe", "fried", "boiled",
            "duck", "pork", "soup", "greasy", "oily", "roast", "grilled",
            "bebek", "babi", "sup", "berminyak", "panggang", "segar",
            "fresh", "mentah", "matang", "porsi",
            "eel", "aubergine", "eggplant", "tofu", "sauce", "pepper",
            "belut", "terong", "tahu", "saus", "merica", "bawang", "jamur",
        ),
        phrases=("makan siang", "makan malam", "rumah makan"),
        hanzi=("吃", "喝", "饭", "菜", "餐", "茶", "酒", "肉", "厨", "味"),
        exclude=("有意思", "意味", "口味儿",),
    ),
    Topic(
        "home", "Home", "Rumah", "🏠",
        words=(
            "rumah", "kamar", "dapur", "pintu", "jendela", "meja", "kursi",
            "lemari", "kasur", "lampu", "kipas", "bersih", "kotor", "menyapu",
            "mengepel", "cuci", "mencuci", "sewa", "kontrakan", "apartemen",
            "tetangga", "perabot", "halaman", "atap", "dinding", "lantai",
            "home", "house", "room", "kitchen", "bathroom", "door", "window",
            "table", "chair", "bed", "lamp", "clean", "dirty", "sweep",
            "wash", "rent", "apartment", "neighbour", "neighbor", "furniture",
            "floor", "wall", "roof", "garden", "yard", "household", "tidy",
            "move house", "housework",
            "fridge", "refrigerator", "box", "mirror", "dormitory", "corridor",
            "supplies", "cupboard", "shelf", "curtain",
            "kulkas", "kotak", "cermin", "asrama", "lorong", "koridor",
            "keperluan", "rak", "gorden", "perumahan",
        ),
        phrases=("kamar mandi", "tempat tidur", "ruang tamu", "bersih-bersih",
                 "pekerjaan rumah tangga"),
        hanzi=("房", "厨", "屋", "床", "门", "窗", "墙"),
        exclude=("门口", "部门", "出门", "专门", "门票", "逛街"),
    ),
    Topic(
        "family", "Family", "Keluarga", "👪",
        words=(
            "keluarga", "ibu", "ayah", "bapak", "mama", "papa", "anak",
            "putra", "putri", "kakak", "adik", "saudara", "kakek", "nenek",
            "paman", "bibi", "sepupu", "cucu", "istri", "suami", "menantu",
            "mertua", "bayi", "keponakan",
            "family", "mother", "father", "parent", "parents", "child",
            "children", "son", "daughter", "brother", "sister", "grandmother",
            "grandfather", "uncle", "aunt", "cousin", "wife", "husband",
            "relative", "baby", "grandchild", "nephew", "niece",
        ),
        phrases=("orang tua", "anggota keluarga", "ibu rumah tangga"),
        hanzi=("爸", "妈", "父", "母", "哥", "姐", "弟", "妹", "妻", "爷",
               "奶", "孙"),
        exclude=("姐妹城市",),
    ),
    Topic(
        "social", "Friends & love", "Teman & hubungan", "🤝",
        words=(
            "teman", "sahabat", "pacar", "pacaran", "jodoh", "nikah",
            "menikah", "pernikahan", "cerai", "cinta", "sayang", "kenalan",
            "berkenalan", "kencan", "tamu", "undangan", "pesta", "sopan",
            "salam", "hubungan", "kumpul", "berkumpul", "akrab", "gaul",
            "friend", "friendship", "boyfriend", "girlfriend", "partner",
            "date", "dating", "marry", "married", "marriage", "wedding",
            "divorce", "love", "guest", "invite", "invitation", "party",
            "polite", "politeness", "manners", "greet", "relationship",
            "acquainted", "acquaintance", "gather", "romance", "romantic",
            "together", "host", "participate", "participant", "register",
            "welcome", "visit", "farewell", "congratulate",
            "ikut", "peserta", "partisipan", "mendaftar", "menyambut",
            "berkunjung", "berpamitan", "mengucapkan",
            "kiss", "flirt", "tease", "quarrel", "popular",
            "accompany", "appointment",
            "mencium", "menggoda", "bertengkar", "digemari", "menemani",
            "mengurus", "menjaga", "mengajak", "janji",
        ),
        phrases=("sopan santun", "teman dekat", "jatuh cinta", "kumpul-kumpul",
                 "get together", "well-acquainted", "keep company",
                 "look after", "care for"),
        hanzi=("友", "朋", "婚", "爱", "恋", "客"),
        exclude=("客观", "顾客", "客户", "乘客", "爱好"),
    ),
    Topic(
        "feeling", "Feelings", "Perasaan", "💗",
        words=(
            "senang", "bahagia", "gembira", "sedih", "marah", "kesal",
            "takut", "malu", "kaget", "terkejut", "bosan", "capek", "lelah",
            "ngantuk", "khawatir", "cemas", "kecewa", "bangga", "lega",
            "rindu", "kangen", "stres", "semangat", "perasaan", "emosi",
            "tega", "terharu", "puas", "heran",
            "happy", "glad", "sad", "angry", "upset", "afraid", "scared",
            "shy", "embarrassed", "surprised", "bored", "tired", "sleepy",
            "worried", "anxious", "disappointed", "proud", "relieved",
            "stressed", "feeling", "emotion", "mood", "nervous", "lonely",
            "excited", "satisfied", "grateful", "reluctant", "moved",
            "hope", "lucky", "fortunate", "endure", "cherish", "care",
            "berharap", "harapan", "beruntung", "menahan", "menghargai",
            "menyayangi", "peduli", "tahan",
        ),
        phrases=("berat hati", "tidak tega", "lega hati", "merasa"),
        hanzi=("情", "怕", "愁", "兴", "悲", "恨"),
        exclude=("情况", "事情", "兴趣",),
    ),
    Topic(
        "people", "People & character", "Orang & sifat", "🙂",
        words=(
            "sifat", "rajin", "malas", "pintar", "pandai", "bodoh", "jujur",
            "bohong", "sombong", "ramah", "galak", "pelit", "sabar",
            "cerewet", "pendiam", "lucu", "serius", "dewasa", "setia",
            "tegas", "cuek", "teliti", "ceroboh", "berani", "penakut",
            "character", "personality", "diligent", "hardworking", "lazy",
            "clever", "smart", "stupid", "honest", "arrogant", "humble",
            "friendly", "kind", "strict", "stingy", "generous", "patient",
            "talkative", "quiet", "funny", "serious", "mature", "loyal",
            "brave", "selfish", "careless", "careful", "rude", "gentle",
            "person", "people", "age", "young", "old", "adult", "generation",
            "life", "name", "stranger", "neighbour", "crowd",
            "usia", "umur", "muda", "tua", "dewasa", "generasi", "nyawa",
            "kehidupan", "nama", "orang", "wibawa", "aura", "penampilan",
            "society", "women", "men", "minister", "scientist", "passenger",
            "swindler", "attitude", "ability", "famous", "society",
            "masyarakat", "perempuan", "wanita", "pria", "menteri",
            "ilmuwan", "penumpang", "penipu", "sikap", "kemampuan",
            "terkenal", "sosial",
        ),
        phrases=("rendah hati", "baik hati", "murah hati", "keras kepala"),
        hanzi=(),
    ),
    Topic(
        "health", "Health & body", "Kesehatan", "🩺",
        words=(
            "badan", "tubuh", "kepala", "rambut", "mata", "hidung", "mulut",
            "gigi", "telinga", "leher", "tangan", "kaki", "perut", "jantung",
            "darah", "kulit", "sakit", "nyeri", "demam", "flu", "batuk",
            "pilek", "luka", "sembuh", "sehat", "obat", "dokter", "perawat",
            "suster", "klinik", "periksa", "operasi", "hamil", "meninggal",
            "istirahat", "gemuk", "kurus", "tidur",
            "body", "head", "hair", "eye", "nose", "mouth", "tooth", "teeth",
            "ear", "neck", "hand", "foot", "leg", "stomach", "heart", "blood",
            "skin", "sick", "ill", "illness", "pain", "fever", "cough",
            "wound", "recover", "healthy", "health", "medicine", "doctor",
            "nurse", "hospital", "clinic", "surgery", "pregnant", "die",
            "death", "rest", "sleep", "fat", "thin", "injury", "patient",
            "accident", "breathe", "breath", "ambulance", "vaccine",
            "kecelakaan", "bernapas", "napas", "ambulans", "vaksin",
            "cedera", "pingsan", "mual", "pusing",
            "obese", "overweight", "tumour", "tumor", "crash", "safe",
            "danger", "dangerous", "protect", "safety",
            "obesitas", "kegemukan", "menabrak", "aman", "keamanan",
            "bahaya", "berbahaya", "melindungi",
        ),
        phrases=("rumah sakit", "masuk angin", "catch a cold", "masuk rumah sakit"),
        hanzi=("病", "医", "药", "疼", "伤", "体", "眼", "鼻", "牙", "睡"),
        exclude=("体会", "具体", "整体", "身份", "逛街",),
    ),
    Topic(
        "sport", "Sport", "Olahraga", "⚽",
        words=(
            "olahraga", "lari", "berlari", "jogging", "renang", "berenang",
            "bola", "basket", "badminton", "tenis", "pingpong", "senam",
            "yoga", "gym", "berlatih", "tanding", "bertanding", "lomba",
            "pertandingan", "juara", "menang", "kalah", "tim", "pemain",
            "pelatih", "stadion", "atlet", "kebugaran",
            "sport", "sports", "exercise", "run", "running", "jog", "swim",
            "swimming", "football", "soccer", "basketball", "badminton",
            "tennis", "gym", "match", "race", "competition", "compete",
            "champion", "championship", "win", "lose", "team", "player",
            "coach", "stadium", "fitness", "workout", "athlete", "score",
        ),
        phrases=("sepak bola", "tenis meja", "bulu tangkis", "angkat besi"),
        hanzi=("球", "跑", "赛", "操", "泳"),
        exclude=("地球", "球形",),
    ),
    Topic(
        "travel", "Travel", "Perjalanan", "✈️",
        words=(
            "wisata", "liburan", "tur", "turis", "peta", "tiket", "paspor",
            "visa", "koper", "hotel", "penginapan", "bandara", "stasiun",
            "terminal", "pelabuhan", "kereta", "bus", "taksi", "pesawat",
            "kapal", "sepeda", "motor", "mobil", "berangkat", "tiba",
            "macet", "perjalanan", "pelancong", "bagasi", "rombongan",
            "travel", "trip", "journey", "tour", "tourist", "holiday",
            "vacation", "map", "ticket", "passport", "visa", "luggage",
            "suitcase", "hotel", "airport", "station", "train", "bus",
            "taxi", "plane", "flight", "ship", "boat", "bicycle", "car",
            "drive", "depart", "arrive", "traffic", "abroad", "sightseeing",
            "board", "transfer",
            "boarding", "gate", "conveyor", "passenger", "aisle",
            "gerbang", "keberangkatan", "penumpang", "ban berjalan",
        ),
        phrases=("jalan-jalan", "naik pesawat", "tiket pesawat", "ruang tunggu"),
        hanzi=("车", "旅", "船", "票", "飞"),
        exclude=("车间", "开车门", "火车站台",),
    ),
    Topic(
        "city", "Out and about", "Tempat umum", "🏙️",
        words=(
            "kota", "desa", "alamat", "gedung", "bioskop", "perpustakaan",
            "museum", "taman", "supermarket", "apotek", "antre", "antri",
            "lift", "tangga", "toilet", "parkir", "jalan", "jembatan",
            "trotoar", "persimpangan", "daerah", "wilayah", "lingkungan",
            "city", "town", "village", "address", "building", "cinema",
            "library", "museum", "park", "supermarket", "pharmacy", "queue",
            "lift", "elevator", "stairs", "toilet", "parking", "street",
            "road", "bridge", "district", "neighbourhood", "downtown",
            "square", "corner", "centre", "center", "entrance", "exit",
        ),
        phrases=("kantor pos", "pusat kota", "kebun binatang", "jalan raya",
                 "tempat umum", "high-rise", "tower block"),
        hanzi=("城", "市", "店", "馆", "街", "楼", "厦"),
        exclude=("市场经济",),
    ),
    Topic(
        "nature", "Weather & nature", "Cuaca & alam", "🌤️",
        words=(
            "cuaca", "hujan", "panas", "dingin", "angin", "awan", "mendung",
            "salju", "badai", "petir", "matahari", "bulan", "bintang",
            "langit", "laut", "gunung", "sungai", "danau", "pohon", "bunga",
            "rumput", "hewan", "binatang", "anjing", "kucing", "burung",
            "serangga", "polusi", "musim", "alam", "udara", "banjir",
            "weather", "rain", "wind", "cloud", "cloudy", "snow", "storm",
            "thunder", "sun", "sunny", "moon", "star", "sky", "sea",
            "mountain", "river", "lake", "tree", "flower", "grass", "animal",
            "dog", "cat", "bird", "insect", "environment", "pollution",
            "season", "spring", "summer", "autumn", "winter", "nature",
            "earth", "air", "flood", "temperature", "warm", "cool",
            "plant", "geography", "soil", "field", "forest", "island",
            "menanam", "geografi", "tanah", "ladang", "hutan", "pulau",
            "panen", "bertani", "pertanian", "agricultural",
        ),
        phrases=("kebun binatang", "musim semi", "musim gugur", "musim dingin",
                 "musim panas", "cuaca buruk"),
        hanzi=("雨", "风", "云", "山", "海", "花", "树", "草", "鸟", "狗",
               "猫", "雪"),
        exclude=("山东", "风格", "作风", "海关", "感冒",),
    ),
    Topic(
        "time", "Time", "Waktu", "🕒",
        words=(
            "waktu", "jam", "menit", "detik", "hari", "minggu", "bulan",
            "tahun", "pagi", "siang", "sore", "malam", "kemarin", "besok",
            "lusa", "sekarang", "nanti", "tadi", "dulu", "lama", "sebentar",
            "telat", "terlambat", "awal", "akhir", "sering", "jarang",
            "selalu", "kadang", "tanggal", "kalender", "jadwal", "zaman",
            "time", "hour", "minute", "second", "day", "week", "month",
            "year", "morning", "noon", "afternoon", "evening", "night",
            "yesterday", "today", "tomorrow", "now", "later", "late",
            "early", "often", "rarely", "always", "sometimes", "date",
            "calendar", "schedule", "moment", "period", "past", "future",
            "weekend", "daily", "era", "century",
        ),
        phrases=("jam berapa", "akhir pekan", "sehari-hari", "a moment ago",
                 "setiap hari"),
        hanzi=("时", "日", "周", "今", "明", "昨", "晚", "钟", "季", "期"),
        exclude=("日本", "明白", "明显", "时髦", "周到", "期待", "星期"),
    ),
    Topic(
        "media", "Phone & media", "Telepon & media", "📱",
        words=(
            "telepon", "hp", "handphone", "ponsel", "sms", "pesan", "email",
            "internet", "wifi", "online", "aplikasi", "komputer", "laptop",
            "website", "foto", "kamera", "video", "televisi", "radio",
            "koran", "majalah", "berita", "iklan", "sinyal", "layar",
            "phone", "call", "message", "text", "email", "internet",
            "online", "app", "computer", "laptop", "website", "photo",
            "camera", "video", "television", "radio", "newspaper",
            "magazine", "news", "advertisement", "signal", "download",
            "screen", "broadcast", "media", "report", "electricity",
            "battery", "charger", "scan", "disc", "cd", "channel",
            "listrik", "baterai", "cas", "memindai", "cakram", "saluran",
            "siaran", "unduh", "gawai",
        ),
        phrases=("media sosial", "nomor telepon", "media massa", "telepon genggam"),
        hanzi=("网", "视", "报", "讯"),
        exclude=("报名", "报告书",),
    ),
    Topic(
        "leisure", "Leisure", "Hiburan", "🎬",
        words=(
            "film", "musik", "lagu", "nyanyi", "menyanyi", "penyanyi",
            "konser", "menari", "tari", "gitar", "piano", "hobi", "main",
            "bermain", "game", "permainan", "kartu", "catur", "mancing",
            "lukis", "melukis", "menggambar", "novel", "cerita", "drama",
            "teater", "santai", "pameran", "liburan",
            "movie", "cinema", "music", "song", "sing", "singer", "concert",
            "dance", "guitar", "piano", "instrument", "hobby", "play",
            "game", "chess", "fishing", "paint", "draw", "drawing", "novel",
            "story", "drama", "theatre", "relax", "entertainment", "fun",
            "poem", "poetry", "art", "exhibition", "museum", "perform",
            "performance", "show", "programme", "program", "activity",
            "event", "celebrate", "festival", "opera", "carving",
            "tampil", "pertunjukan", "acara", "kegiatan", "merayakan",
            "perayaan", "festival", "opera", "ukiran", "seru", "memukau",
            "director", "lead", "collect", "collection", "actor", "actress",
            "sutradara", "pemeran", "mengoleksi", "koleksi", "aktor",
        ),
        phrases=("alat musik", "waktu luang", "jalan-jalan santai"),
        hanzi=("歌", "戏", "影", "画", "玩", "舞"),
        exclude=("影响", "电影院里",),
    ),
    Topic(
        "law", "Rules & officialdom", "Aturan & urusan resmi", "⚖️",
        words=(
            "hukum", "peraturan", "aturan", "izin", "dokumen", "formulir",
            "ktp", "pemerintah", "pejabat", "polisi", "pengadilan", "hakim",
            "pengacara", "saksi", "denda", "pajak", "asuransi", "klaim",
            "kontrak", "perjanjian", "dilarang", "wajib", "hak", "kewajiban",
            "instansi", "resmi", "sanksi", "penjara", "kejahatan", "melanggar",
            "law", "legal", "regulation", "rule", "permit", "licence",
            "license", "document", "form", "government", "official",
            "police", "court", "judge", "lawyer", "witness", "sue", "fine",
            "tax", "insurance", "claim", "contract", "agreement", "signature",
            "forbidden", "prohibited", "duty", "responsibility", "prison",
            "crime", "lawsuit", "compensation", "authority", "department",
            "fair", "obey", "comply", "complaint", "admit", "requirement",
            "demand", "condition", "terms", "private", "correct", "public",
            "adil", "mematuhi", "mengadu", "komplain", "mengakui",
            "persyaratan", "menuntut", "syarat", "swasta", "demonstrasi",
            "berdemonstrasi", "pawai",
        ),
        phrases=("tanda tangan", "undang-undang", "surat izin", "kantor polisi",
                 "instansi pemerintah", "government department"),
        hanzi=("法", "律", "政", "府", "证", "官", "警", "罚", "税", "赔"),
        exclude=("办法", "方法", "想法", "看法", "法国",),
    ),
    Topic(
        "clothes", "Clothes & looks", "Pakaian & penampilan", "👕",
        words=(
            "baju", "pakaian", "celana", "rok", "kemeja", "kaos", "jaket",
            "sepatu", "sandal", "topi", "tas", "kacamata", "cincin",
            "memakai", "ukuran", "warna", "cantik", "ganteng", "tampan",
            "jelek", "rapi", "gaya", "mode", "seragam", "dandan",
            "clothes", "clothing", "shirt", "trousers", "pants", "skirt",
            "dress", "jacket", "shoes", "socks", "hat", "bag", "watch",
            "glasses", "ring", "wear", "size", "colour", "color",
            "beautiful", "handsome", "ugly", "tidy", "style", "fashion",
            "makeup", "uniform", "underpants", "coat",
        ),
        phrases=("kaus kaki", "jam tangan", "celana dalam", "ganti baju"),
        hanzi=("衣", "裤", "鞋", "帽", "穿", "戴", "袜", "裙"),
        exclude=("穿过", "服务", "舒服", "说服", "佩戴式",),
    ),
    Topic(
        "speech", "Talking", "Bicara", "💬",
        words=(
            "bicara", "berbicara", "ngomong", "bilang", "mengatakan",
            "berkata", "tanya", "bertanya", "jawab", "menjawab", "bercerita",
            "diskusi", "berdebat", "setuju", "menolak", "menyapa",
            "memanggil", "menjelaskan", "menyebut", "memuji", "mengkritik",
            "mengeluh", "berjanji", "menghubungi", "kontak", "ngobrol",
            "say", "tell", "speak", "talk", "ask", "answer", "reply",
            "discuss", "discussion", "agree", "refuse", "explain", "mention",
            "praise", "compliment", "criticise", "criticize", "complain",
            "promise", "shout", "chat", "conversation", "contact",
            "introduce", "argue", "advise", "suggest", "remind", "repeat",
        ),
        phrases=("to get in touch", "angkat bicara", "basa-basi"),
        hanzi=("说", "讲", "问", "答", "谈", "叫", "告", "议"),
        exclude=("问题", "叫做",),
    ),
    Topic(
        "place", "Places & directions", "Tempat & arah", "🧭",
        words=(
            "tempat", "sini", "sana", "situ", "sebelah", "samping", "depan",
            "belakang", "atas", "bawah", "dalam", "luar", "tengah", "pinggir",
            "sisi", "kiri", "kanan", "utara", "selatan", "timur", "barat",
            "arah", "jarak", "dekat", "jauh", "sekitar", "seberang",
            "place", "here", "there", "beside", "next", "front", "behind",
            "above", "below", "inside", "outside", "middle", "side", "left",
            "right", "north", "south", "east", "west", "direction",
            "distance", "near", "nearby", "far", "around", "opposite",
            "location", "everywhere", "underground", "vicinity", "position",
        ),
        phrases=("di sini", "di sana", "di sebelah", "di dalam", "di luar",
                 "di mana-mana", "next to", "in the vicinity", "bawah tanah"),
        hanzi=("边", "旁", "处", "方向", "位"),
        exclude=("方便", "办法", "位子",),
    ),
    Topic(
        "thinking", "Thinking & deciding", "Pikiran & keputusan", "🤔",
        words=(
            "pikir", "berpikir", "pendapat", "berpendapat", "menganggap",
            "mengira", "paham", "memahami", "mengerti", "tahu", "mengetahui",
            "ingat", "lupa", "percaya", "ragu", "curiga", "yakin",
            "memutuskan", "keputusan", "memilih", "pilihan", "rencana",
            "berencana", "menyadari", "menemukan", "menduga", "salah paham",
            "think", "thought", "opinion", "consider", "assume", "understand",
            "know", "remember", "forget", "believe", "doubt", "suspect",
            "sure", "certain", "decide", "decision", "choose", "choice",
            "plan", "realise", "realize", "discover", "guess", "expect",
            "misunderstanding", "idea", "mind", "familiar", "view",
            "admit", "affirm", "summary", "conclude", "correct", "actually",
            "menyimpulkan", "kesimpulan", "memastikan", "pasti", "mengakui",
            "sebenarnya", "kenyataannya", "menganggap", "menduga",
        ),
        phrases=("salah paham", "hold the view", "to be sure", "make up one's mind"),
        hanzi=("想", "思", "忘", "信", "疑", "识"),
        exclude=("信封", "相信地",),
    ),
    Topic(
        "description", "Describing things", "Ciri & ukuran", "📏",
        words=(
            "besar", "kecil", "panjang", "pendek", "tinggi", "rendah",
            "tebal", "tipis", "berat", "ringan", "luas", "sempit", "penuh",
            "kosong", "baru", "lama", "cepat", "lambat", "pelan", "keras",
            "lembut", "halus", "kasar", "mudah", "gampang", "sulit", "susah",
            "rumit", "sederhana", "praktis", "cocok", "pas", "bagus",
            "buruk", "rusak", "aneh", "biasa", "umum", "khusus", "penting",
            "big", "large", "small", "long", "short", "tall", "high", "low",
            "thick", "thin", "heavy", "light", "wide", "narrow", "full",
            "empty", "new", "fast", "slow", "loud", "soft", "smooth",
            "rough", "easy", "simple", "difficult", "hard", "complicated",
            "convenient", "suitable", "good", "bad", "broken", "strange",
            "ordinary", "common", "special", "important", "troublesome",
            "quality", "standard", "level", "extremely", "very",
        ),
        phrases=(),
        hanzi=(),
        exclude=("高中", "大学", "小学", "大人",),
        exact_sense=True,
    ),
    Topic(
        "quantity", "Numbers & amounts", "Angka & jumlah", "🔢",
        words=(
            "angka", "nomor", "jumlah", "satuan", "ukuran", "berapa",
            "banyak", "sedikit", "setengah", "pasang", "lembar", "persen",
            "kilo", "meter", "liter", "total", "rata-rata", "kali",
            "number", "amount", "quantity", "count", "half", "double",
            "pair", "piece", "sheet", "percent", "kilo", "metre", "litre",
            "total", "average", "measure", "dozen", "several", "majority",
            "kilometre", "kilometer", "million", "billion", "thousand",
            "hundred", "juta", "miliar", "ribu", "ratus", "sebagian",
            "seluruh", "jumlahnya", "menghitung", "calculate", "add",
        ),
        phrases=("satuan untuk", "measure word", "kata bantu bilangan",
                 "kata penggolong"),
        hanzi=("倍", "数", "量", "半"),
        exclude=("数学", "量力",),
    ),
)

BY_SLUG = {t.slug: t for t in TOPICS}

#: A measure word is about counting, not about what it counts.
#:
#: 首 is glossed "satuan untuk lagu dan puisi" and would otherwise be filed
#: under music and under study, neither of which is what the word means. When
#: the gloss says the word is a counter, that is the whole classification.
MEASURE_PHRASES = ("satuan untuk", "measure word", "kata bantu bilangan",
                   "kata penggolong", "satuan hitung")

_TOKEN = re.compile(r"[^a-z]+")
_SPLIT = re.compile(r"[,;/|()]| - ")


def _tokens(blob: str) -> set[str]:
    return {t for t in _TOKEN.split(blob) if t}


def senses(glosses: list[str]) -> set[str]:
    """Each gloss cut into the separate meanings it lists.

    "menaruh, meletakkan | to put, to place" is four senses, and a topic that
    requires an exact sense has to be able to see them one at a time. The
    English infinitive marker goes: "to put" and "put" are the same claim.
    """
    out = set()
    for gloss in glosses:
        for chunk in _SPLIT.split(gloss.lower()):
            chunk = " ".join(_TOKEN.split(chunk)).strip()
            if not chunk:
                continue
            out.add(chunk)
            if chunk.startswith("to "):
                out.add(chunk[3:])
            if chunk.startswith("be "):
                out.add(chunk[3:])
    return out


def score_for(
    topic: Topic,
    hanzi: str,
    blob: str,
    tokens: set[str],
    parts: set[str],
) -> int:
    """How strongly this word belongs to this topic. 0 means it does not."""
    if hanzi in topic.exclude:
        return 0
    if hanzi in topic.include:
        return SCORE_INCLUDE
    score = 0
    for phrase in topic.phrases:
        if phrase in blob:
            score += SCORE_PHRASE
            break
    haystack = parts if topic.exact_sense else tokens
    for word in topic.words:
        if word in haystack:
            score += SCORE_WORD
            break
    for char in topic.hanzi:
        if char in hanzi:
            score += SCORE_HANZI
            break
    return score


def topics_for(hanzi: str, glosses: list[str]) -> list[str]:
    """The topics this word belongs to, strongest first, at most [MAX_TOPICS]."""
    blob = " ".join(glosses).lower()
    if any(p in blob for p in MEASURE_PHRASES):
        return ["quantity"]
    tokens = _tokens(blob)
    parts = senses(glosses)
    scored = [
        (score, -i, t.slug)
        for i, t in enumerate(TOPICS)
        if (score := score_for(t, hanzi, blob, tokens, parts)) > 0
    ]
    scored.sort(reverse=True)
    return [slug for _, _, slug in scored[:MAX_TOPICS]]
