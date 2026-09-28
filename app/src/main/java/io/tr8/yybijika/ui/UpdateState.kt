package io.tr8.yybijika.ui

import io.tr8.yybijika.update.UpdateChecker
import java.io.File

/**
 * Where the app is in the update flow.
 *
 * Modelled as states rather than booleans so the card on the home screen has
 * exactly one thing to render, and an interrupted download cannot leave a
 * progress bar showing next to an "update available" button.
 */
sealed interface UpdateState {
    /** Nothing to say: either not checked yet, or already current. */
    data object Idle : UpdateState

    data class Available(val release: UpdateChecker.Release) : UpdateState

    data class Downloading(
        val release: UpdateChecker.Release,
        val percent: Int,
    ) : UpdateState

    data class Ready(
        val release: UpdateChecker.Release,
        val apk: File,
    ) : UpdateState

    /** Android requires a per-app opt-in before it will show the installer. */
    data class NeedsPermission(val release: UpdateChecker.Release) : UpdateState

    data class Failed(val release: UpdateChecker.Release, val message: String) : UpdateState
}
