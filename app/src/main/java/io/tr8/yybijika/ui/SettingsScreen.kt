package io.tr8.yybijika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.clip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.BuildConfig
import io.tr8.yybijika.data.GlossLanguage
import io.tr8.yybijika.ui.theme.Palette
import io.tr8.yybijika.ui.theme.ThemeMode

data class SettingsState(
    val maskedKey: String = "not set",
    val hasKey: Boolean = false,
    val sessionSize: Int = 20,
    val newPerSession: Int = 10,
    val autoCheckUpdates: Boolean = true,
    val userWordCount: Int = 0,
    val deckWords: Int = 0,
    val checking: Boolean = false,
    val checkResult: String? = null,
    val glossLanguage: GlossLanguage = GlossLanguage.INDONESIAN,
    val audioEnabled: Boolean = true,
    val voiceReport: String = "",
    val voiceWorks: Boolean = false,
    val palette: Palette = Palette.CINNABAR,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

@Composable
fun SettingsScreen(
    state: SettingsState,
    update: UpdateState,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onSessionSize: (Int) -> Unit,
    onNewPerSession: (Int) -> Unit,
    onAutoCheck: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onGrantInstallPermission: () -> Unit,
    onDismissUpdate: () -> Unit,
    onGlossLanguage: (GlossLanguage) -> Unit,
    onToggleAudio: (Boolean) -> Unit,
    onTestSound: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onPalette: (Palette) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ---- Appearance ---------------------------------------------------
        Section("Appearance") {
            Text(
                "Every palette keeps the same warm paper and soft black text. " +
                    "Hanzi carry a lot of strokes in a small space, and maximum " +
                    "contrast makes them shimmer after half an hour — so what " +
                    "changes is the colour, not the readability.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Palette.entries.forEach { palette ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPalette(palette) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The swatch is the palette's own accent, so the list shows
                    // the choice rather than describing it.
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(palette.swatch)
                    )
                    Text(palette.label, Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    RadioButton(
                        selected = state.palette == palette,
                        onClick = { onPalette(palette) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
        }

        // ---- Language -----------------------------------------------------
        Section("Question language") {
            Text(
                "Every word in your deck has both meanings on its card. This is " +
                    "only about which one the questions are asked and answered in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlossLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = state.glossLanguage == language,
                        onClick = { onGlossLanguage(language) },
                        label = { Text(language.label) },
                    )
                }
            }
        }

        // ---- Sound --------------------------------------------------------
        Section("Sound") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Speak words and sentences",
                    style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.audioEnabled, onCheckedChange = onToggleAudio)
            }
            Text(
                // Everything the engine reported, verbatim. "No sound" has half
                // a dozen causes that look identical from the outside, and only
                // some of them are anything this app can fix.
                state.voiceReport,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.voiceWorks) {
                Text(
                    "Chinese speech needs a voice installed on the phone itself. " +
                        "Open the system text-to-speech settings, pick an engine " +
                        "that supports Chinese, and install its Chinese data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTestSound) { Text("Test sound") }
                OutlinedButton(onClick = onOpenVoiceSettings) { Text("Voice settings") }
            }
        }

        // ---- Updates ------------------------------------------------------
        Section("Updates") {
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Releases come from github.com/${BuildConfig.UPDATE_REPO}. " +
                    "Installing one keeps your progress — points, streak and every " +
                    "review interval live in a file the update does not touch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            UpdateCard(
                state = update,
                onDownload = onDownloadUpdate,
                onInstall = onInstallUpdate,
                onGrantPermission = onGrantInstallPermission,
                onDismiss = onDismissUpdate,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onCheckNow, enabled = !state.checking) {
                    Text(if (state.checking) "Checking…" else "Check for updates")
                }
                state.checkResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Toggle(
                label = "Check automatically on launch",
                checked = state.autoCheckUpdates,
                onChange = onAutoCheck,
            )
        }

        // ---- DeepSeek -----------------------------------------------------
        Section("DeepSeek key") {
            Text(
                "Used only when you paste new notes, to fill in a missing reading, " +
                    "meaning or example. It is never used for anything you did not " +
                    "paste, and the deck never gains a word you did not write down.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            var draft by remember { mutableStateOf("") }
            var reveal by remember { mutableStateOf(false) }

            Text(
                "Current: ${state.maskedKey}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("sk-…") },
                singleLine = true,
                visualTransformation = if (reveal) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveKey(draft); draft = "" },
                    enabled = draft.isNotBlank(),
                ) { Text("Save") }
                TextButton(onClick = { reveal = !reveal }) {
                    Text(if (reveal) "Hide" else "Show")
                }
                if (state.hasKey) {
                    TextButton(onClick = onClearKey) { Text("Remove") }
                }
            }

            Text(
                "Stored in this app's private storage. That keeps it away from " +
                    "other apps, but anyone holding your unlocked phone could still " +
                    "reach it — treat it like any other key on a device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Study --------------------------------------------------------
        Section("Study") {
            SliderRow(
                label = "Cards per session",
                value = state.sessionSize,
                range = 5f..60f,
                onChange = onSessionSize,
            )
            SliderRow(
                label = "New words per session",
                value = state.newPerSession,
                range = 0f..30f,
                onChange = onNewPerSession,
            )
            Text(
                "Every new word creates up to four schedules, one per skill. A big " +
                    "intake today becomes an unmanageable review day next week.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---- Deck ---------------------------------------------------------
        Section("Deck") {
            Text("${state.deckWords} words in total",
                style = MaterialTheme.typography.bodyMedium)
            Text(
                if (state.userWordCount > 0)
                    "${state.userWordCount} of them you added yourself"
                else "None added by you yet — paste some notes in Add notes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$value", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range,
        )
    }
}
