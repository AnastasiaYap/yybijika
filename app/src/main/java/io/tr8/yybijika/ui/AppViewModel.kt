package io.tr8.yybijika.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.tr8.yybijika.BuildConfig
import io.tr8.yybijika.audio.Speaker
import io.tr8.yybijika.data.DeckStats
import io.tr8.yybijika.data.Mastery
import io.tr8.yybijika.data.GrammarPattern
import io.tr8.yybijika.data.Passage
import io.tr8.yybijika.data.Repo
import io.tr8.yybijika.data.Settings
import io.tr8.yybijika.data.UserWord
import io.tr8.yybijika.notes.DeepSeek
import io.tr8.yybijika.notes.NoteParser
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
    private val settings = Settings(app)
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

    private val _cards = MutableStateFlow<List<WordBundle>>(emptyList())
    val cards: StateFlow<List<WordBundle>> = _cards.asStateFlow()

    private val _passages = MutableStateFlow<List<Passage>>(emptyList())
    val passages: StateFlow<List<Passage>> = _passages.asStateFlow()

    private val _patterns = MutableStateFlow<List<GrammarPattern>>(emptyList())
    val patterns: StateFlow<List<GrammarPattern>> = _patterns.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _addText = MutableStateFlow("")
    val addText: StateFlow<String> = _addText.asStateFlow()

    private val _addState = MutableStateFlow<AddState>(AddState.Editing)
    val addState: StateFlow<AddState> = _addState.asStateFlow()

    init {
        speaker.whenReady { available ->
            repo.setTtsAvailable(available)
            _home.update { it.copy(ttsAvailable = available) }
        }
        refresh()
        loadLibrary()
        if (settings.autoCheckUpdates) checkForUpdate()
    }

    private fun loadLibrary() = viewModelScope.launch {
        _cards.value = repo.allWords()
        _passages.value = repo.passages()
        _patterns.value = repo.grammarPatterns()
        refreshSettings()
    }

    private suspend fun refreshSettings() {
        _settingsState.value = _settingsState.value.copy(
            maskedKey = settings.maskedKey(),
            hasKey = settings.hasDeepseekKey,
            sessionSize = settings.sessionSize,
            newPerSession = settings.newPerSession,
            autoCheckUpdates = settings.autoCheckUpdates,
            userWordCount = repo.userWordCount(),
            deckWords = repo.deckStats().words,
        )
    }

    // ---- settings --------------------------------------------------------

    fun saveKey(key: String) = viewModelScope.launch {
        settings.deepseekKey = key
        refreshSettings()
    }

    fun clearKey() = viewModelScope.launch {
        settings.deepseekKey = ""
        refreshSettings()
    }

    fun setSessionSize(n: Int) = viewModelScope.launch {
        settings.sessionSize = n
        refreshSettings()
    }

    fun setNewPerSession(n: Int) = viewModelScope.launch {
        settings.newPerSession = n
        refreshSettings()
    }

    fun setAutoCheck(on: Boolean) = viewModelScope.launch {
        settings.autoCheckUpdates = on
        refreshSettings()
    }

    /** The manual check, which unlike the launch check reports when it finds nothing. */
    fun checkNow() = viewModelScope.launch {
        _settingsState.value = _settingsState.value.copy(checking = true, checkResult = null)
        val release = UpdateChecker.latest()
        val message = when {
            release == null -> "Could not reach GitHub"
            UpdateChecker.isNewer(release.versionName, BuildConfig.VERSION_NAME) -> {
                _update.value = UpdateState.Available(release)
                "Version ${release.versionName} found"
            }
            else -> "You are on the latest version"
        }
        _settingsState.value = _settingsState.value.copy(checking = false, checkResult = message)
    }

    // ---- adding notes ----------------------------------------------------

    fun setAddText(text: String) { _addText.value = text }

    fun resetAdd() {
        _addText.value = ""
        _addState.value = AddState.Editing
    }

    fun parseNotes() = viewModelScope.launch {
        _addState.value = AddState.Parsing
        val drafts = NoteParser.parse(_addText.value)
        _addState.value =
            if (drafts.isEmpty()) AddState.Failed("No Chinese words found in that text.", emptyList())
            else AddState.Parsed(drafts)
    }

    /**
     * Ask DeepSeek for what the paste left out.
     *
     * Only the incomplete drafts are sent, and only the fields they are missing
     * are requested — a word you glossed yourself keeps your wording.
     */
    fun fillBlanks() = viewModelScope.launch {
        val drafts = (_addState.value as? AddState.Parsed)?.drafts ?: return@launch
        val key = settings.deepseekKey
        if (key.isBlank()) {
            _addState.value = AddState.Failed("No DeepSeek key saved.", drafts)
            return@launch
        }

        val incomplete = drafts.filter { it.missing.isNotEmpty() }
        _addState.value = AddState.Filling(0, incomplete.size)

        val filled = try {
            DeepSeek(key).fill(incomplete)
        } catch (e: DeepSeek.Failure) {
            _addState.value = AddState.Failed(e.message ?: "DeepSeek failed", drafts)
            return@launch
        }

        val byHanzi = filled.associateBy { it.hanzi }
        _addState.value = AddState.Ready(drafts.map { it.toPending(byHanzi[it.hanzi]) })
    }

    /** Save without asking DeepSeek — whatever was written is what gets stored. */
    fun saveParsedAsIs() = viewModelScope.launch {
        val drafts = (_addState.value as? AddState.Parsed)?.drafts ?: return@launch
        _addState.value = AddState.Ready(drafts.map { it.toPending(null) })
    }

    private suspend fun NoteParser.Draft.toPending(filled: DeepSeek.Filled?) = PendingCard(
        hanzi = hanzi,
        pinyin = filled?.pinyin ?: pinyin ?: "",
        glossId = filled?.glossId ?: glosses.firstOrNull(),
        glossEn = filled?.glossEn ?: glosses.getOrNull(1),
        exampleZh = filled?.exampleZh ?: examples.firstOrNull(),
        examplePinyin = filled?.examplePinyin,
        exampleGloss = filled?.exampleGloss,
        notes = usageNotes.firstOrNull(),
        filledByModel = filled != null && missing.isNotEmpty(),
        duplicate = repo.hasWord(hanzi),
        warnings = filled?.rejected.orEmpty(),
    )

    fun toggleCard(index: Int) {
        val ready = _addState.value as? AddState.Ready ?: return
        _addState.value = AddState.Ready(
            ready.cards.mapIndexed { i, c -> if (i == index) c.copy(include = !c.include) else c }
        )
    }

    fun saveCards() = viewModelScope.launch {
        val cards = when (val state = _addState.value) {
            is AddState.Ready -> state.cards.filter { it.include }
            is AddState.Parsed -> state.drafts.map { it.toPending(null) }
            else -> return@launch
        }
        cards.forEach { card ->
            repo.addUserWord(
                UserWord(
                    hanzi = card.hanzi,
                    pinyin = card.pinyin,
                    // A reading nobody checked stays untrusted, wherever it came
                    // from, so it cannot reach the listening or typing drills.
                    pinyinVerified = false,
                    glossId = card.glossId,
                    glossEn = card.glossEn,
                    exampleZh = card.exampleZh,
                    examplePinyin = card.examplePinyin,
                    exampleGloss = card.exampleGloss,
                    notes = card.notes,
                    source = if (card.filledByModel) "deepseek" else "notes",
                )
            )
        }
        _addState.value = AddState.Saved(cards.size)
        _addText.value = ""
        loadLibrary()
        refresh()
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

    fun startSession(size: Int = settings.sessionSize) = viewModelScope.launch {
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

    /** Follow a link from one word's detail page to another's. */
    fun openWordByHanzi(hanzi: String) = viewModelScope.launch {
        val match = repo.search(hanzi).firstOrNull { it.hanzi == hanzi } ?: return@launch
        _detail.value = match to repo.masteryFor(match.id)
    }

    fun closeWord() {
        _detail.value = null
    }

    override fun onCleared() {
        speaker.release()
        super.onCleared()
    }
}
