package io.tr8.yybijika.learn

import kotlin.random.Random

/**
 * Something to write about.
 *
 * A bare instruction — "use 索赔 in a sentence" — reliably produces a sentence
 * nobody meant: 我索赔了。 Grammatical, empty, and forgotten by the next screen.
 * A situation produces a sentence with a point to it, and a point is what makes
 * the word stick.
 *
 * The frames are chosen by the word's own topic tag, so a work word gets a work
 * scene. Several of them ask about the writer's own life on purpose: a sentence
 * about yourself is remembered better than the same sentence about a stranger,
 * and it is also the sentence you are actually going to need.
 */
object Prompts {

    private val BY_TAG: Map<String, List<String>> = mapOf(
        "work" to listOf(
            "你的经理问你这个星期做了什么。回答他。",
            "同事第一天上班，你告诉他这里的规矩。",
            "你想换工作，跟朋友说说为什么。",
            "写一句话，说说你现在的工作最难的地方。",
        ),
        "money" to listOf(
            "朋友问你要不要一起投资，你说说你的看法。",
            "你这个月花得太多了，跟家人解释一下。",
            "写一句话，说说你怎么存钱。",
        ),
        "travel" to listOf(
            "你在机场遇到了问题，告诉工作人员。",
            "朋友问你上次旅行怎么样，回答他。",
            "写一句话，说说你最想去的地方。",
        ),
        "home" to listOf(
            "房东问你屋子有什么问题，告诉他。",
            "你刚搬家，跟朋友说说新地方。",
            "写一句话，说说你周末在家做什么。",
        ),
        "food" to listOf(
            "你在饭馆点菜，跟服务员说话。",
            "朋友问你昨天吃了什么，回答他。",
            "写一句话，说说你会做的一道菜。",
        ),
        "people" to listOf(
            "跟别人介绍你的一个朋友。",
            "写一句话，说说你家里的一个人。",
            "你要请人帮忙，先说说是什么事。",
        ),
        "time" to listOf(
            "朋友约你见面，你说说你什么时候有空。",
            "写一句话，说说你平时几点起床，为什么。",
        ),
        "feeling" to listOf(
            "写一句话，说说今天让你高兴或者不高兴的事。",
            "朋友心情不好，你说一句话安慰他。",
        ),
        "body" to listOf(
            "你不舒服，去看病，告诉医生怎么了。",
            "写一句话，说说你怎么锻炼。",
        ),
    )

    /** Used when the word has no topic tag, or none that has frames. */
    private val GENERAL = listOf(
        "写一句关于你自己的话。",
        "写一句话，说说今天发生的事。",
        "跟朋友说一件最近的事。",
        "写一句话，说说你的看法。",
    )

    /**
     * A scene for this word.
     *
     * Seeded by the word so the same word does not get a different scene every
     * time it comes round — a changing prompt makes the exercise feel random,
     * and the point is to return to the same idea and say it better.
     */
    fun forTags(tags: List<String>, seed: Long): String {
        val frames = tags.firstNotNullOfOrNull { BY_TAG[it] } ?: GENERAL
        return frames[Random(seed).nextInt(frames.size)]
    }

    /** Two words in one sentence is harder, and the instruction has to say so. */
    fun instruction(words: List<String>): String = when (words.size) {
        0 -> "写一句完整的话。"
        1 -> "用「${words[0]}」写一句完整的话。"
        else -> "用「${words.joinToString("」和「")}」写一句完整的话。"
    }
}
