package io.tr8.yybijika.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.tr8.yybijika.BuildConfig
import io.tr8.yybijika.audio.Speaker
import io.tr8.yybijika.data.DeckStats
import io.tr8.yybijika.data.Mastery
import io.tr8.yybijika.data.Repo
import io.tr8.yybijika.exercise.WordBundle
import io.tr8.yybijika.learn.Grade
import io.tr8.yybijika.learn.Scheduler
import io.tr8.yybijika.learn.SessionItem
import io.tr8.yybijika.learn.Skill
import io.tr8.yybijika.learn.Xp
import io.tr8.yybijika.update.UpdateChecker
import io.tr8.yybijika.update.Updater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val loading: Boolean = true,
    val stats: DeckStats? = null,
    val dueTotal: Int = 0,
    val dueBySkill: Map<Skill, Int> = emptyMap(),
    val totalXp: Int = 0,
    val streak: Int = 0,
    val ttsAvailable: Boolean = false,
)

data class SessionState(
    val items: List<SessionItem> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val chosen: Int? = null,
    val typed: String = "",
    val assembled: List<Int> = emptyList(),
    val combo: Int = 0,
    val earned: Int = 0,
    val correct: Int = 0,
    val finished: Boolean = false,
) {
    val current: SessionItem? get() = items.getOrNull(index)
    val total: Int get() = items.size
    val progress: Float get() = if (items.isEmpty()) 0f else index.toFloat() / items.size
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repo.get(app)
    val speaker = Speaker(app)

    private val _home = MutableStateFlow(HomeState())
    val home: StateFlow<HomeState> = _home.asStateFlow()

    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _browse = MutableStateFlow<List<WordBundle>>(emptyList())
    val browse: StateFlow<List<WordBundle>> = _browse.asStateFlow()

    private val _detail = MutableStateFlow<Pair<WordBundle, Map<Skill, Mastery>>?>(null)
    val detail: StateFlow<Pair<WordBundle, Map<Skill, Mastery>>?> = _detail.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    init {
        speaker.whenReady { available ->
            repo.setTtsAvailable(available)
            _home.update { it.copy(ttsAvailable = available) }
        }
        refresh()
        checkForUpdate()
    }

    /**
     * Ask GitHub whether there is a newer build, once per launch.
     *
     * Silent on every failure — no network, rate limited, no releases yet. An
     * update check is not something the learner asked for, so it must never
     * interrupt them to report that it could not happen.
     */
    fun checkForUpdate() = viewModelScope.launch {
        val release = UpdateChecker.latest() ?: return@launch
        if (UpdateChecker.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
            _update.value = UpdateState.Available(release)
        }
    }

    fun downloadUpdate() = viewModelScope.launch {
        val release = when (val state = _update.value) {
            is UpdateState.Available -> state.release
            is UpdateState.Failed -> state.release
            is UpdateState.NeedsPermission -> state.release
            else -> return@launch
        }

        _update.value = UpdateState.Downloading(release, 0)
        val apk = Updater.download(getApplication(), release) { percent ->
            _update.value = UpdateState.Downloading(release, percent)
        }

        _update.value = when {
            apk == null -> UpdateState.Failed(release, "Download failed")
            !Updater.canInstall(getApplication()) -> UpdateState.NeedsPermission(release)
            else -> UpdateState.Ready(release, apk)
        }
    }

    fun installUpdate() {
        val state = _update.value
        if (state is UpdateState.Ready) {
            Updater.install(getApplication(), state.apk)
        }
    }

    fun grantInstallPermission() {
        Updater.requestInstallPermission(getApplication())
    }

    /** Called when returning to the app, in case the permission was just granted. */
    fun recheckInstallPermission() {
        val state = _update.value
        if (state is UpdateState.NeedsPermission && Updater.canInstall(getApplication())) {
            downloadUpdate()
        }
    }

    fun dismissUpdate() {
        _update.value = UpdateState.Idle
    }

    fun refresh() = viewModelScope.launch {
        _home.update { it.copy(loading = true) }
        val stats = repo.deckStats()
        val due = repo.dueCount()
        val bySkill = repo.dueBySkill()
        val xp = repo.totalXp()
        val streak = repo.streak()
        _home.update {
            it.copy(
                loading = false,
                stats = stats,
                dueTotal = due,
                dueBySkill = bySkill,
                totalXp = xp,
                streak = streak,
            )
        }
    }

    fun startSession(size: Int = 20) = viewModelScope.launch {
        _session.value = SessionState()
        val items = repo.buildSession(size)
        _session.value = SessionState(items = items, finished = items.isEmpty())
    }

    fun reveal() = _session.update { it.copy(revealed = true) }

    fun choose(index: Int) = _session.update {
        if (it.revealed) it else it.copy(chosen = index, revealed = true)
    }

    fun type(text: String) = _session.update { it.copy(typed = text) }

    fun tapTile(index: Int) = _session.update {
        if (it.revealed || index in it.assembled) it
        else it.copy(assembled = it.assembled + index)
    }

    fun undoTile() = _session.update {
        if (it.assembled.isEmpty()) it else it.copy(assembled = it.assembled.dropLast(1))
    }

    fun grade(grade: Grade) = viewModelScope.launch {
        val state = _session.value
        val item = state.current ?: return@launch

        val points = repo.answer(item, grade, state.combo)
        val combo = Xp.nextCombo(state.combo, grade)
        val atEnd = state.index + 1 >= state.items.size

        _session.value = state.copy(
            index = state.index + 1,
            revealed = false,
            chosen = null,
            typed = "",
            assembled = emptyList(),
            combo = combo,
            earned = state.earned + points,
            correct = state.correct + if (grade == Grade.AGAIN) 0 else 1,
            finished = atEnd,
        )
        if (atEnd) refresh()
    }

    /** The interval each button will schedule, shown on the button itself. */
    fun previews(): Map<Grade, Int> {
        val item = _session.value.current ?: return emptyMap()
        val state = Scheduler.State(box = item.box)
        return Grade.entries.associateWith { Scheduler.previewDays(state, it) }
    }

    fun speak(text: String) = speaker.speak(text)

    fun search(query: String) = viewModelScope.launch {
        _browse.value = repo.search(query)
    }

    fun openWord(id: Long) = viewModelScope.launch {
        val word = repo.word(id) ?: return@launch
        _detail.value = word to repo.masteryFor(id)
    }

    fun closeWord() {
        _detail.value = null
    }

    override fun onCleared() {
        speaker.release()
        super.onCleared()
    }
}
