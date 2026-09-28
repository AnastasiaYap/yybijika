package io.tr8.yybijika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The one place an update is offered.
 *
 * Never a dialog. An update is not urgent, and interrupting a study session
 * with a modal is a worse outcome than the update waiting until tomorrow.
 */
@Composable
fun UpdateCard(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is UpdateState.Idle) return

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state) {
                is UpdateState.Idle -> Unit

                is UpdateState.Available -> {
                    Text("Version ${state.release.versionName} is available",
                        fontWeight = FontWeight.SemiBold)
                    if (state.release.notes.isNotBlank()) {
                        Text(
                            state.release.notes.lineSequence().take(4).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload) {
                            Text("Download " + sizeLabel(state.release.sizeBytes))
                        }
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                }

                is UpdateState.Downloading -> {
                    Text("Downloading ${state.release.versionName}…")
                    if (state.percent >= 0) {
                        LinearProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("${state.percent}%", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }

                is UpdateState.Ready -> {
                    Text("Ready to install ${state.release.versionName}",
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your progress is kept — points, streak and every review " +
                            "interval live in a separate file the update does not touch.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onInstall) { Text("Install") }
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                }

                is UpdateState.NeedsPermission -> {
                    Text("Allow installing updates", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android needs your permission before this app can hand an " +
                            "update to the installer. You only grant it once.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onGrantPermission) { Text("Open settings") }
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                }

                is UpdateState.Failed -> {
                    Text(state.message, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload) { Text("Try again") }
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}

private fun sizeLabel(bytes: Long): String =
    if (bytes <= 0) "" else "(${"%.1f".format(bytes / 1_000_000.0)} MB)"
