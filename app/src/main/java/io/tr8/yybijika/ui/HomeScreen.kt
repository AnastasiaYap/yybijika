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
    update: UpdateState,
    onStudy: () -> Unit,
    onBrowse: () -> Unit,
    onSettings: () -> Unit,
    onAddNotes: () -> Unit,
    onWriting: () -> Unit,
    onDiagnosis: () -> Unit,
    writtenCount: Int = 0,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onGrantInstallPermission: () -> Unit,
    onDismissUpdate: () -> Unit,
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

        UpdateCard(
            state = update,
            onDownload = onDownloadUpdate,
            onInstall = onInstallUpdate,
            onGrantPermission = onGrantInstallPermission,
            onDismiss = onDismissUpdate,
        )

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

        Text(
            "v${io.tr8.yybijika.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
            Text("Search the deck")
        }
        // Above the deck figures on purpose. Those say 1173 / 1173 and will
        // say it in a year — they describe the deck, not the learner. This one
        // is the only thing on the screen that answers "how am I doing".
        OutlinedButton(onClick = onDiagnosis, modifier = Modifier.fillMaxWidth()) {
            Text("How you are doing")
        }

        OutlinedButton(onClick = onWriting, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (writtenCount == 0) "Your writing"
                else "Your writing  ·  $writtenCount"
            )
        }

        OutlinedButton(onClick = onAddNotes, modifier = Modifier.fillMaxWidth()) {
            Text("Add notes")
        }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Settings")
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
