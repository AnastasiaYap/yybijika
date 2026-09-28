"""Reading passages, written around the vocabulary the notes already contain.

Every passage here is built from words that are in the deck. That constraint is
the point: a reading section made of unfamiliar vocabulary is a dictionary
exercise, not reading practice. Where a passage needs a word the notes lack, the
sentence gets rewritten rather than the word imported.

Each passage carries an Indonesian translation per line, because that is the
language the notes gloss in, and comprehension questions whose wrong answers are
plausible rather than silly.
"""

from __future__ import annotations

ESSAYS: list[dict] = [

    {
        "id": "e01",
        "title": "第一天上班",
        "title_id": "Hari pertama kerja",
        "level": 1,
        "lines": [
            ("今天是我上班的第一天。", "Hari ini hari pertama aku kerja."),
            ("我早起了，因为我怕迟到。", "Aku bangun pagi karena takut telat."),
            ("我坐公共汽车去公司。", "Aku naik bus kota ke kantor."),
            ("路上人很多，很热闹。", "Di jalan banyak orang, ramai sekali."),
            ("到了公司，经理很客气地欢迎我。", "Sampai di kantor, manajer menyambutku dengan ramah."),
            ("他介绍了我的工作和任务。", "Dia menjelaskan pekerjaan dan tugasku."),
            ("同事们都很努力，也愿意帮忙。", "Rekan-rekan kerja rajin dan mau membantu."),
            ("中午我们一起吃快餐。", "Siang hari kami makan cepat saji bersama."),
            ("下班的时候我很累，可是很愉快。", "Waktu pulang aku capek, tapi senang."),
        ],
        "questions": [
            {
                "q": "作者为什么早起？",
                "choices": ["因为怕迟到", "因为要打扫", "因为睡不着", "因为要去旅行"],
                "answer": 0,
                "explain": "第二句说“我早起了，因为我怕迟到”。",
            },
            {
                "q": "作者怎么去公司？",
                "choices": ["坐公共汽车", "走路", "坐火车", "开车"],
                "answer": 0,
                "explain": "“我坐公共汽车去公司。”",
            },
            {
                "q": "经理对作者的态度怎么样？",
                "choices": ["很客气", "很不高兴", "不理他", "很着急"],
                "answer": 0,
                "explain": "“经理很客气地欢迎我。”",
            },
            {
                "q": "下班的时候作者觉得怎么样？",
                "choices": ["又累又愉快", "很生气", "很害羞", "很饿"],
                "answer": 0,
                "explain": "最后一句：“我很累，可是很愉快”。",
            },
        ],
    },

    {
        "id": "e02",
        "title": "周末的菜市场",
        "title_id": "Pasar di akhir pekan",
        "level": 1,
        "lines": [
            ("周末我常常去菜市场。", "Akhir pekan aku sering ke pasar."),
            ("那儿的蔬菜又新鲜又便宜。", "Sayuran di sana segar dan murah."),
            ("卖菜的老板娘认识我了。", "Ibu penjual sayur sudah kenal aku."),
            ("她常说：“今天的菜特别好。”", "Dia sering bilang: 'Sayur hari ini bagus sekali.'"),
            ("我喜欢跟她聊天儿。", "Aku suka mengobrol dengannya."),
            ("买完菜以后，我去旁边的快餐店。", "Setelah belanja, aku ke kedai cepat saji di sebelah."),
            ("那儿有我最爱吃的炒鸡丁。", "Di sana ada ayam tumis kesukaanku."),
            ("我付钱的时候才发现没带现金。", "Waktu bayar aku baru sadar tidak bawa uang tunai."),
            ("幸亏可以扫码。", "Untung bisa scan QR."),
        ],
        "questions": [
            {
                "q": "菜市场的蔬菜怎么样？",
                "choices": ["又新鲜又便宜", "很贵", "不新鲜", "卖完了"],
                "answer": 0,
                "explain": "“那儿的蔬菜又新鲜又便宜。”",
            },
            {
                "q": "作者在快餐店最爱吃什么？",
                "choices": ["炒鸡丁", "饺子", "烤鸭", "西瓜"],
                "answer": 0,
                "explain": "“那儿有我最爱吃的炒鸡丁。”",
            },
            {
                "q": "付钱的时候发生了什么？",
                "choices": ["发现没带现金", "钱包丢了", "停电了", "排队太长"],
                "answer": 0,
                "explain": "“我付钱的时候才发现没带现金。”",
            },
            {
                "q": "最后问题怎么解决了？",
                "choices": ["扫码付钱", "老板娘请客", "回家拿钱", "没有吃"],
                "answer": 0,
                "explain": "“幸亏可以扫码。”",
            },
        ],
    },

    {
        "id": "e03",
        "title": "搬家那天",
        "title_id": "Hari pindahan",
        "level": 2,
        "lines": [
            ("上个月我搬到了一个新的小区。", "Bulan lalu aku pindah ke kompleks perumahan baru."),
            ("房租不便宜，可是屋子很干净。", "Sewanya tidak murah, tapi kamarnya bersih."),
            ("搬家那天，朋友们都来帮忙。", "Hari pindahan, teman-teman datang membantu."),
            ("我们一起打扫厨房和厕所。", "Kami bersama-sama membersihkan dapur dan toilet."),
            ("突然停电了，屋里很黑。", "Tiba-tiba mati lampu, di dalam gelap."),
            ("我打电话给房东，他说等一会儿就好。", "Aku telepon pemilik rumah, katanya sebentar lagi beres."),
            ("我们只好坐着聊天儿。", "Kami terpaksa duduk mengobrol saja."),
            ("后来灯亮了，大家都笑了。", "Kemudian lampu menyala, semua tertawa."),
            ("那天虽然很累，可是我记在心里。", "Hari itu melelahkan, tapi kusimpan di hati."),
        ],
        "questions": [
            {
                "q": "新屋子有什么好处？",
                "choices": ["很干净", "房租很便宜", "很大", "离公司近"],
                "answer": 0,
                "explain": "“房租不便宜，可是屋子很干净。”",
            },
            {
                "q": "搬家那天出了什么事？",
                "choices": ["停电了", "下雨了", "钥匙丢了", "朋友没来"],
                "answer": 0,
                "explain": "“突然停电了，屋里很黑。”",
            },
            {
                "q": "房东怎么说？",
                "choices": ["等一会儿就好", "他不管", "明天才能修", "让他们搬走"],
                "answer": 0,
                "explain": "“他说等一会儿就好。”",
            },
            {
                "q": "作者对那天的感觉是什么？",
                "choices": ["累，可是难忘", "很生气", "很害羞", "很无聊"],
                "answer": 0,
                "explain": "“虽然很累，可是我记在心里。”",
            },
        ],
    },

    {
        "id": "e04",
        "title": "要不要读研究生",
        "title_id": "Lanjut S2 atau tidak",
        "level": 3,
        "lines": [
            ("毕业以后，我一直在想要不要读研究生。", "Setelah lulus, aku terus memikirkan apakah perlu lanjut S2."),
            ("同学们的看法不一样。", "Pendapat teman-teman berbeda-beda."),
            ("有的人认为应该先工作，得到经验。", "Ada yang berpendapat sebaiknya kerja dulu, dapat pengalaman."),
            ("他们说，工资和升职更重要。", "Kata mereka, gaji dan promosi lebih penting."),
            ("也有人说，学历会影响将来的机会。", "Ada juga yang bilang, gelar memengaruhi peluang ke depan."),
            ("我的教授建议我先想清楚自己的目的。", "Profesorku menyarankan agar aku memikirkan tujuanku dulu."),
            ("他说：“不必跟别人比较。”", "Dia bilang: 'Tidak perlu membandingkan diri dengan orang lain.'"),
            ("我觉得他说得对。", "Menurutku ucapannya benar."),
            ("究竟走哪条路，得由我自己决定。", "Jalan mana yang diambil, akhirnya aku sendiri yang menentukan."),
        ],
        "questions": [
            {
                "q": "同学们对读研究生的看法怎么样？",
                "choices": ["不一样", "都同意", "都反对", "都不关心"],
                "answer": 0,
                "explain": "“同学们的看法不一样。”",
            },
            {
                "q": "主张先工作的人认为什么更重要？",
                "choices": ["工资和升职", "学历", "旅行", "朋友"],
                "answer": 0,
                "explain": "“他们说，工资和升职更重要。”",
            },
            {
                "q": "教授给了什么建议？",
                "choices": ["先想清楚自己的目的", "马上读研究生", "先去旅行", "听父母的"],
                "answer": 0,
                "explain": "“他建议我先想清楚自己的目的。”",
            },
            {
                "q": "作者最后的想法是什么？",
                "choices": ["要自己决定", "听同学的", "不读了", "明年再说"],
                "answer": 0,
                "explain": "“究竟走哪条路，得由我自己决定。”",
            },
        ],
    },

    {
        "id": "e05",
        "title": "第一次一个人旅行",
        "title_id": "Pertama kali jalan sendiri",
        "level": 2,
        "lines": [
            ("去年放假，我第一次一个人去旅行。", "Libur tahun lalu, pertama kali aku jalan-jalan sendiri."),
            ("出发以前，我准备了很久。", "Sebelum berangkat, aku menyiapkan lama sekali."),
            ("我买了火车票，也订了宾馆。", "Aku beli tiket kereta dan pesan hotel."),
            ("火车上的风景很美丽。", "Pemandangan dari kereta indah sekali."),
            ("到了以后，我先去参观了一个老地方。", "Setibanya, aku dulu mengunjungi tempat tua."),
            ("导游告诉我们很多故事。", "Pemandu bercerita banyak hal."),
            ("晚上我一个人在大街上散步。", "Malamnya aku jalan-jalan sendiri di jalan besar."),
            ("那时候我才感觉到，一个人也可以很愉快。", "Saat itu aku baru merasa, sendirian pun bisa menyenangkan."),
            ("回来以后，我把照片给朋友看。", "Sepulangnya, aku tunjukkan foto-fotonya ke teman."),
        ],
        "questions": [
            {
                "q": "作者出发以前做了什么？",
                "choices": ["买票、订宾馆", "什么都没准备", "跟朋友商量", "学外语"],
                "answer": 0,
                "explain": "“我买了火车票，也订了宾馆。”",
            },
            {
                "q": "到了以后作者先做什么？",
                "choices": ["参观老地方", "睡觉", "吃饭", "买东西"],
                "answer": 0,
                "explain": "“我先去参观了一个老地方。”",
            },
            {
                "q": "晚上作者在做什么？",
                "choices": ["在大街上散步", "在宾馆看电视", "跟导游聊天", "排队买票"],
                "answer": 0,
                "explain": "“晚上我一个人在大街上散步。”",
            },
            {
                "q": "这次旅行让作者明白了什么？",
                "choices": ["一个人也可以很愉快", "旅行太贵", "不该一个人去", "要多带钱"],
                "answer": 0,
                "explain": "“一个人也可以很愉快。”",
            },
        ],
    },

    {
        "id": "e06",
        "title": "朋友之间的误会",
        "title_id": "Salah paham antar teman",
        "level": 3,
        "lines": [
            ("上个星期，我跟一个好朋友有了误会。", "Minggu lalu aku salah paham dengan teman baik."),
            ("事情其实很小，可是我们谁也说服不了谁。", "Masalahnya kecil, tapi tak ada yang bisa meyakinkan siapa pun."),
            ("那几天我心里很不舒服。", "Beberapa hari itu hatiku tidak enak."),
            ("我想给他打电话，可是又不好意思。", "Aku ingin menelepon, tapi sungkan."),
            ("后来他先来找我了。", "Akhirnya dia yang datang lebih dulu."),
            ("他说：“我们别为这种小事生气。”", "Dia bilang: 'Jangan marah karena hal sekecil ini.'"),
            ("我听了，觉得自己也有不对的地方。", "Mendengar itu, aku merasa aku juga ada salahnya."),
            ("我们互相道歉，然后一起去吃饭。", "Kami saling minta maaf, lalu makan bersama."),
            ("我才明白，先开口的那个人最勇敢。", "Aku baru paham, yang bicara duluan itu yang paling berani."),
        ],
        "questions": [
            {
                "q": "他们的误会是怎么样的？",
                "choices": ["事情很小，可是互相不让", "很严重", "跟钱有关", "跟工作有关"],
                "answer": 0,
                "explain": "“事情其实很小，可是我们谁也说服不了谁。”",
            },
            {
                "q": "作者为什么没有先打电话？",
                "choices": ["不好意思", "没有他的号码", "太忙了", "不想和好"],
                "answer": 0,
                "explain": "“我想给他打电话，可是又不好意思。”",
            },
            {
                "q": "谁先来找对方？",
                "choices": ["朋友", "作者", "两个人同时", "没有人"],
                "answer": 0,
                "explain": "“后来他先来找我了。”",
            },
            {
                "q": "作者最后明白了什么？",
                "choices": ["先开口的人最勇敢", "朋友不可靠", "小事也要争", "别交朋友"],
                "answer": 0,
                "explain": "最后一句。",
            },
        ],
    },
]
