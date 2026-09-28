package io.tr8.yybijika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.learn.Skill
import io.tr8.yybijika.learn.Xp
import io.tr8.yybijika.ui.theme.HanziMedium

@Composable
fun HomeScreen(
    state: HomeState,
    onStudy: () -> Unit,
    onBrowse: () -> Unit,
) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text("盈盈笔记卡", style = HanziMedium, fontWeight = FontWeight.Medium)
            Text(
                "Yíngyíng Bǐjìkǎ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LevelCard(state.totalXp, state.streak)

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (state.dueTotal > 0) "${state.dueTotal} due" else "Nothing due",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.dueBySkill.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (skill in Skill.entries) {
                            val n = state.dueBySkill[skill] ?: continue
                            SkillPip(skill, n)
                        }
                    }
                }
                Button(onClick = onStudy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.dueTotal > 0) "Study" else "Learn something new")
                }
            }
        }

        state.stats?.let { DeckCard(it) }

        if (!state.ttsAvailable) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Listening drills are off", fontWeight = FontWeight.Medium)
                    Text(
                        "No Chinese voice is installed. Add one under " +
                            "Settings → System → Languages → Text-to-speech, " +
                            "and listening exercises will appear on their own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
            Text("Browse the deck")
        }
    }
}

@Composable
private fun LevelCard(totalXp: Int, streak: Int) {
    val level = Xp.levelFor(totalXp)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Level $level", fontWeight = FontWeight.SemiBold)
                Text(
                    if (streak > 0) "$streak day streak" else "No streak yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { Xp.levelProgress(totalXp) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "$totalXp points",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SkillPip(skill: Skill, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(skill.labelZh, style = MaterialTheme.typography.titleLarge)
        Text("$count", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DeckCard(stats: io.tr8.yybijika.data.DeckStats) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your deck", fontWeight = FontWeight.SemiBold)
            Text(
                "${stats.words} words from your notes",
                style = MaterialTheme.typography.bodyMedium,
            )
            CoverageRow("Glossed", stats.withGloss, stats.words)
            CoverageRow("Pinyin checked", stats.pinyinVerified, stats.words)
            CoverageRow("With an example", stats.withExample, stats.words)
        }
    }
}

@Composable
private fun CoverageRow(label: String, n: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$n / $total", style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else n.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .size(height = 4.dp, width = 0.dp),
        )
    }
}
