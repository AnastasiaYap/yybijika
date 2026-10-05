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
import io.tr8.yybijika.data.CardState
import io.tr8.yybijika.data.Composition
import io.tr8.yybijika.data.CharacterState
import io.tr8.yybijika.data.Repo
import io.tr8.yybijika.data.CharacterCard
import io.tr8.yybijika.data.GlossLanguage
import io.tr8.yybijika.ui.theme.Palette
import io.tr8.yybijika.ui.theme.ThemeMode
import io.tr8.yybijika.data.Settings
import io.tr8.yybijika.data.UserWord
import io.tr8.yybijika.notes.DeepSeek
import io.tr8.yybijika.notes.NoteParser
import io.tr8.yybijika.exercise.WordBundle
import io.tr8.yybijika.exercise.Exercise
import io.tr8.yybijika.learn.CardDeck
import io.tr8.yybijika.learn.Composer
import io.tr8.yybijika.widget.WidgetNudge
import io.tr8.yybijika.learn.Diagnosis
import androidx.core.app.NotificationManagerCompat
import io.tr8.yybijika.learn.Vitality
import io.tr8.yybijika.remind.ReminderScheduler
import io.tr8.yybijika.learn.CardFilter
import io.tr8.yybijika.learn.Grade
import io.tr8.yybijika.learn.Scheduler
import io.tr8.yybijika.learn.SessionBuilder
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

/**
 * The swipe deck.
 *
 * [undo] holds the card's state from before the last swipe, which is the only
 * thing needed to put it back: a mis-swipe must not cost an interval.
 */
/**
 * The two decks the Cards screen can be grooming.
 *
 * Kept as one screen rather than two because the activity is identical — the
 * same three gestures, the same ladder, the same filters — and splitting it
 * would mean maintaining the swipe behaviour twice.
 */
enum class DeckKind(val label: String) { WORDS("Words"), CHARACTERS("Characters") }

/**
 * The character half of the Cards screen.
 *
 * A parallel state rather than a generic one: a word is addressed by id and a
 * character by itself, and a single state holding either would spend its life
 * being narrowed back down.
 */
data class CharacterCardsState(
    val order: List<CharacterState> = emptyList(),
    val window: Map<String, CharacterCard> = emptyMap(),
    val index: Int = 0,
    val counts: Map<CardFilter, Int> = emptyMap(),
    val undo: Pair<Int, CharacterState>? = null,
) {
    val size: Int get() = order.size
    val currentState: CharacterState? get() = order.getOrNull(index)
    val current: Pair<CharacterCard, CharacterState>?
        get() = currentState?.let { st -> window[st.hanzi]?.let { it to st } }
    val canUndo: Boolean get() = undo != null
}

data class CardsState(
    val filter: CardFilter = CardFilter.DUE,
    /** The whole slice as states; the words themselves arrive a window at a time. */
    val order: List<CardState> = emptyList(),
    val window: Map<Long, WordBundle> = emptyMap(),
    val index: Int = 0,
    val counts: Map<CardFilter, Int> = emptyMap(),
    val undo: Pair<Int, CardState>? = null,
) {
    val size: Int get() = order.size
    val currentState: CardState? get() = order.getOrNull(index)
    val current: Pair<WordBundle, CardState>?
        get() = currentState?.let { state -> window[state.wordId]?.let { it to state } }
    val canUndo: Boolean get() = undo != null
}

data class SessionState(
    /**
     * The working queue, not a fixed list: a failed item is pushed back in a
     * few places further on, so the session can grow as it goes.
     */
    val items: List<SessionItem> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val chosen: Int? = null,
    val typed: String = "",
    val assembled: List<Int> = emptyList(),
    val combo: Int = 0,
    /** A written sentence is away being marked; the button says so. */
    val checking: Boolean = false,
    val critique: Composer.Critique? = null,
    val earned: Int = 0,
    val correct: Int = 0,
    val finished: Boolean = false,
    /**
     * How many questions were planned, before any retries were added.
     *
     * Progress is measured against this rather than against the live queue, or
     * failing a question would make the bar jump backwards — which reads as
     * punishment for being wrong, exactly when it should not.
     */
    val planned: Int = 0,
    /** Questions settled first time, so progress never counts a retry twice. */
    val settled: Int = 0,
    /**
     * Whether the last answer was objectively right.
     *
     * Null where the app cannot tell — a flashcard has no answer to check, so
     * that one still asks the learner.
     */
    val wasCorrect: Boolean? = null,
) {
    val current: SessionItem? get() = items.getOrNull(index)
    val total: Int get() = maxOf(planned, items.size)
    val progress: Float get() =
        if (planned == 0) 0f else (settled.toFloat() / planned).coerceIn(0f, 1f)

    /** True when the app graded this itself and is only waiting to be let on. */
    val selfGraded: Boolean get() = wasCorrect != null
}

/**
 * Whether the app may make a sound, and whether it could if allowed.
 *
 * Both halves are shown because they fail differently. Muted is something the
 * learner did and can undo from the toggle; no voice installed is something only
 * the system settings can fix, and a toggle that does nothing would be a lie.
 */
/** The palette, and whether it follows the phone. */
data class ThemeChoice(val palette: Palette, val mode: ThemeMode)

data class AudioState(
    val voiceInstalled: Boolean = false,
    val enabled: Boolean = true,
) {
    val on: Boolean get() = voiceInstalled && enabled
}

private const val WINDOW = 12

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

    private val _cards = MutableStateFlow(CardsState())
    val cards: StateFlow<CardsState> = _cards.asStateFlow()

    private val _passages = MutableStateFlow<List<Passage>>(emptyList())
    val passages: StateFlow<List<Passage>> = _passages.asStateFlow()

    private val _patterns = MutableStateFlow<List<GrammarPattern>>(emptyList())
    val patterns: StateFlow<List<GrammarPattern>> = _patterns.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    private val _quiz = MutableStateFlow(QuizSetup())
    val quiz: StateFlow<QuizSetup> = _quiz.asStateFlow()

    /**
     * How the app looks. Held here so the choice takes effect on the tap rather
     * than on the next launch.
     */
    private val _theme = MutableStateFlow(
        ThemeChoice(settings.palette, settings.themeMode)
    )
    val theme: StateFlow<ThemeChoice> = _theme.asStateFlow()

    private val _audio = MutableStateFlow(AudioState(enabled = settings.audioEnabled))
    val audio: StateFlow<AudioState> = _audio.asStateFlow()

    /**
     * The passage currently open, if any.
     *
     * Held here rather than inside ReadScreen because tapping a word in a
     * passage opens the word card as an overlay, which takes the Read tab out
     * of composition entirely — and a reader that loses your place every time
     * you look a word up is worse than one with no links at all.
     */
    /** A passage is being written; null when nothing is happening. */
    private val _vitality = MutableStateFlow(Vitality.of(held = 0, due = 0, daysAway = 0))
    val vitality: StateFlow<Vitality.State> = _vitality.asStateFlow()

    private val _diagnosis = MutableStateFlow<Diagnosis?>(null)
    val diagnosis: StateFlow<Diagnosis?> = _diagnosis.asStateFlow()

    private val _songs = MutableStateFlow<List<Passage>>(emptyList())
    val songs: StateFlow<List<Passage>> = _songs.asStateFlow()

    private val _songTitle = MutableStateFlow("")
    val songTitle: StateFlow<String> = _songTitle.asStateFlow()

    private val _songLyrics = MutableStateFlow("")
    val songLyrics: StateFlow<String> = _songLyrics.asStateFlow()

    private val _songWorking = MutableStateFlow(false)
    val songWorking: StateFlow<Boolean> = _songWorking.asStateFlow()

    private val _songError = MutableStateFlow<String?>(null)
    val songError: StateFlow<String?> = _songError.asStateFlow()

    private val _writingPassage = MutableStateFlow(false)
    val writingPassage: StateFlow<Boolean> = _writingPassage.asStateFlow()

    private val _passageError = MutableStateFlow<String?>(null)
    val passageError: StateFlow<String?> = _passageError.asStateFlow()

    private val _pendingWriting = MutableStateFlow(0)
    val pendingWriting: StateFlow<Int> = _pendingWriting.asStateFlow()

    private val _markingPending = MutableStateFlow(false)
    val markingPending: StateFlow<Boolean> = _markingPending.asStateFlow()

    private val _passagePinyin = MutableStateFlow(settings.passagePinyin)
    val passagePinyin: StateFlow<Boolean> = _passagePinyin.asStateFlow()

    private val _writing = MutableStateFlow<List<Composition>>(emptyList())
    val writing: StateFlow<List<Composition>> = _writing.asStateFlow()

    private val _openPassage = MutableStateFlow<Long?>(null)
    val openPassage: StateFlow<Long?> = _openPassage.asStateFlow()

    private val _deckKind = MutableStateFlow(DeckKind.WORDS)
    val deckKind: StateFlow<DeckKind> = _deckKind.asStateFlow()

    private val _charCards = MutableStateFlow(CharacterCardsState())
    val charCards: StateFlow<CharacterCardsState> = _charCards.asStateFlow()

    private val _character =
        MutableStateFlow<Pair<CharacterCard, List<Triple<Long, String, String?>>>?>(null)
    val character: StateFlow<Pair<CharacterCard, List<Triple<Long, String, String?>>>?> =
        _character.asStateFlow()

    private val _addText = MutableStateFlow("")
    val addText: StateFlow<String> = _addText.asStateFlow()

    private val _addState = MutableStateFlow<AddState>(AddState.Editing)
    val addState: StateFlow<AddState> = _addState.asStateFlow()

    init {
        repo.setAudioEnabled(settings.audioEnabled)
        // Self-healing: alarms are lost on reboot, on a force-stop, and when
        // the system decides to clear them. Re-syncing on every launch costs
        // nothing and means a reminder cannot quietly stop for ever.
        ReminderScheduler.sync(app)
        repo.setGlossLanguage(settings.glossLanguage)
        speaker.whenReady { available ->
            repo.setTtsAvailable(available)
            _home.update { it.copy(ttsAvailable = available) }
            _audio.update { it.copy(voiceInstalled = available) }
            // The voice check finishes after this block has already asked for
            // the quiz counts, and three of the types are only offerable once
            // it comes back. Recount rather than leave listening hidden on a
            // phone that can in fact speak.
            loadQuizSetup()
        }
        refresh()
        loadLibrary()
        loadQuizSetup()
        if (settings.autoCheckUpdates) checkForUpdate()
    }

    private fun loadLibrary() = viewModelScope.launch {
        loadCards(_cards.value.filter)
        // Written-for-you passages first: they are about what is going
        // wrong right now, and the shipped ones will keep.
        _passages.value = repo.madePassages() + repo.passages()
        _songs.value = repo.songs()
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
            writtenCount = repo.compositionCount(),
            deckWords = repo.deckStats().words,
            glossLanguage = settings.glossLanguage,
            audioEnabled = settings.audioEnabled,
            voiceReport = speaker.report.summary,
            voiceWorks = speaker.available,
            remindEnabled = settings.remindEnabled,
            remindHour = settings.remindHour,
            remindMinute = settings.remindMinute,
            notificationsAllowed = NotificationManagerCompat
                .from(getApplication()).areNotificationsEnabled(),
            palette = settings.palette,
            themeMode = settings.themeMode,
        )
    }

    /**
     * Say something out loud on demand, ignoring the mute setting.
     *
     * The point of the button is to find out whether the phone can speak at all,
     * so honouring mute would answer a different question.
     */
    fun testSound() = viewModelScope.launch {
        speaker.speak("你好，这是中文。")
        refreshSettings()
    }

    /**
     * Re-check for a Chinese voice, after a trip to the system settings.
     *
     * An engine bound before the voice was installed keeps saying no, so the
     * check has to be able to start over rather than be asked again.
     */
    fun recheckVoice() = viewModelScope.launch {
        speaker.recheck { available ->
            repo.setTtsAvailable(available)
            _home.update { it.copy(ttsAvailable = available) }
            _audio.update { it.copy(voiceInstalled = available) }
            loadQuizSetup()
            viewModelScope.launch { refreshSettings() }
        }
    }

    // ---- quiz -------------------------------------------------------------

    private fun loadQuizSetup() = viewModelScope.launch {
        _quiz.value = _quiz.value.copy(
            types = io.tr8.yybijika.exercise.Registry.all,
            available = repo.quizAvailability(),
        )
    }

    fun selectQuizType(id: String?) { _quiz.value = _quiz.value.copy(selected = id) }

    fun setQuizLength(n: Int) { _quiz.value = _quiz.value.copy(length = n) }

    fun startQuiz() = viewModelScope.launch {
        val setup = _quiz.value
        _session.value = SessionState()
        val items = repo.buildSession(setup.length, setup.selected)
        _session.value = SessionState(
            items = items, planned = items.size, finished = items.isEmpty(),
        )
    }

    // ---- the swipe deck ---------------------------------------------------

    fun loadCards(filter: CardFilter = _cards.value.filter) = viewModelScope.launch {
        val order = repo.cardOrder(filter)
        _cards.value = CardsState(
            filter = filter,
            order = order,
            index = 0,
            counts = tallies(),
            undo = null,
        )
        fillWindow(0)
    }

    /**
     * Load the words around the current position.
     *
     * A window rather than the whole slice, because the screen shows one card
     * and the next one only has to be ready by the time a swipe lands.
     */
    private fun fillWindow(index: Int) = viewModelScope.launch {
        val state = _cards.value
        val ids = state.order
            .subList(index.coerceAtMost(state.size), (index + WINDOW).coerceAtMost(state.size))
            .map { it.wordId }
        if (ids.isEmpty()) return@launch
        val fetched = repo.cardWindow(ids.filterNot { it in state.window })
        if (fetched.isEmpty()) return@launch
        _cards.value = _cards.value.copy(window = _cards.value.window + fetched)
    }

    private suspend fun tallies(): Map<CardFilter, Int> {
        val raw = repo.cardCounts()
        return mapOf(
            CardFilter.DUE to (raw["due"] ?: 0),
            CardFilter.STRUGGLING to (raw[CardState.STATE_STRUGGLING] ?: 0),
            CardFilter.LEARNING to (raw[CardState.STATE_LEARNING] ?: 0),
            CardFilter.KNOWN to (raw[CardState.STATE_KNOWN] ?: 0),
            CardFilter.RETIRED to (raw[CardState.STATE_RETIRED] ?: 0),
        )
    }

    fun swipe(swipe: CardDeck.Swipe) = viewModelScope.launch {
        val state = _cards.value
        val (word, before) = state.current ?: return@launch
        repo.swipe(word.id, swipe)

        // Advancing rather than removing keeps the list stable under the finger;
        // the slice is only rebuilt when the filter changes or the pass ends.
        // The tallies do refresh every swipe though — a chip that still reads
        // zero after you have just swiped a card into that bucket is a lie.
        _cards.value = state.copy(
            index = state.index + 1,
            undo = state.index to before,
            counts = tallies(),
        )
        if (state.index + 1 >= state.size) loadCards(state.filter)
        else fillWindow(state.index + 1)
    }

    fun undoSwipe() = viewModelScope.launch {
        val state = _cards.value
        val (index, before) = state.undo ?: return@launch
        repo.restoreCardState(before)
        _cards.value = state.copy(index = index, undo = null, counts = tallies())
    }

    fun reviveCard(wordId: Long) = viewModelScope.launch {
        repo.revive(wordId)
        loadCards(_cards.value.filter)
    }

    // ---- the character deck ----------------------------------------------

    fun setPassagePinyin(show: Boolean) {
        settings.passagePinyin = show
        _passagePinyin.value = show
    }

    fun loadDiagnosis() = viewModelScope.launch {
        _diagnosis.value = repo.diagnose()
    }

    fun loadWriting() = viewModelScope.launch {
        _writing.value = repo.compositions()
        _pendingWriting.value = repo.pendingCount()
    }

    /**
     * Send the unmarked pile off to be marked.
     *
     * Explicit rather than automatic: it costs API calls, and spending them
     * without being asked is not a decision the app gets to make.
     */
    fun markPendingWriting() = viewModelScope.launch {
        _markingPending.value = true
        repo.markPending(settings.deepseekKey, settings.glossLanguage)
        _markingPending.value = false
        loadWriting()
    }

    fun openPassage(id: Long?) { _openPassage.value = id }

    /**
     * Write a passage from whatever she is currently losing.
     *
     * Always explicit. It costs an API call and takes half a minute, and doing
     * that on its own schedule rather than hers would be presumptuous with
     * somebody else's key.
     */
    fun makePassage() = viewModelScope.launch {
        if (_writingPassage.value) return@launch
        _writingPassage.value = true
        _passageError.value = null
        repo.writePassage(settings.deepseekKey, settings.glossLanguage)
            .onSuccess { loadLibrary() }
            .onFailure { _passageError.value = it.message ?: "Could not write it." }
        _writingPassage.value = false
    }

    fun dismissPassageError() { _passageError.value = null }

    fun setSongTitle(text: String) { _songTitle.value = text }

    fun setSongLyrics(text: String) { _songLyrics.value = text }

    /**
     * Annotate the pasted lines and keep them.
     *
     * Clears the form only on success, so a failed attempt does not cost the
     * paste — retyping a song because the network dropped would be its own
     * small punishment.
     */
    fun addSong() = viewModelScope.launch {
        if (_songWorking.value) return@launch
        _songWorking.value = true
        _songError.value = null
        repo.addSong(
            title = _songTitle.value,
            lyrics = _songLyrics.value,
            apiKey = settings.deepseekKey,
            language = settings.glossLanguage,
        ).onSuccess {
            _songTitle.value = ""
            _songLyrics.value = ""
            loadLibrary()
        }.onFailure { _songError.value = it.message ?: "Could not read it." }
        _songWorking.value = false
    }

    fun dismissSongError() { _songError.value = null }

    fun deletePassage(id: Long) = viewModelScope.launch {
        repo.deletePassage(id)
        if (_openPassage.value == id) _openPassage.value = null
        loadLibrary()
    }

    fun setDeckKind(kind: DeckKind) {
        _deckKind.value = kind
        if (kind == DeckKind.CHARACTERS && _charCards.value.order.isEmpty()) {
            loadCharacterCards(_cards.value.filter)
        }
    }

    fun loadCharacterCards(filter: CardFilter = _cards.value.filter) =
        viewModelScope.launch {
            val order = repo.characterOrder(filter)
            _charCards.value = CharacterCardsState(
                order = order,
                index = 0,
                counts = repo.characterCounts(),
                undo = null,
            )
            fillCharacterWindow(0)
        }

    private fun fillCharacterWindow(index: Int) = viewModelScope.launch {
        val state = _charCards.value
        val ids = state.order
            .subList(index.coerceAtMost(state.size), (index + WINDOW).coerceAtMost(state.size))
            .map { it.hanzi }
            .filterNot { it in state.window }
        if (ids.isEmpty()) return@launch
        val fetched = repo.characterWindow(ids)
        if (fetched.isEmpty()) return@launch
        _charCards.value = _charCards.value.copy(window = _charCards.value.window + fetched)
    }

    fun swipeCharacter(swipe: CardDeck.Swipe) = viewModelScope.launch {
        val state = _charCards.value
        val (_, before) = state.current ?: return@launch
        repo.swipeCharacter(before, swipe)
        _charCards.value = state.copy(
            index = state.index + 1,
            undo = state.index to before,
            counts = repo.characterCounts(),
        )
        if (state.index + 1 >= state.size) loadCharacterCards(_cards.value.filter)
        else fillCharacterWindow(state.index + 1)
    }

    fun undoCharacterSwipe() = viewModelScope.launch {
        val state = _charCards.value
        val (index, before) = state.undo ?: return@launch
        repo.restoreCharacterState(before)
        _charCards.value = state.copy(
            index = index, undo = null, counts = repo.characterCounts(),
        )
    }

    fun reviveCharacter(hanzi: String) = viewModelScope.launch {
        repo.reviveCharacter(hanzi)
        loadCharacterCards(_cards.value.filter)
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
        // The plant is part of the home screen's state, so it refreshes with it
        // — and that is also what makes it revive the moment a session ends.
        _vitality.value = repo.vitality()
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
        _session.value = SessionState(
            items = items, planned = items.size, finished = items.isEmpty(),
        )
    }

    /**
     * Whether the app can mark this itself.
     *
     * A flashcard cannot be marked: there is no answer to compare against, only
     * the learner's own sense of whether they knew it. Everything else has a
     * right answer on file, and asking somebody to rate themselves a second
     * after seeing it collects a judgement that is mostly noise — and that noise
     * then sets their review intervals.
     */
    private fun objectiveResult(state: SessionState): Boolean? {
        val item = state.current ?: return null
        return when (val ex = item.exercise) {
            is Exercise.MultipleChoice -> state.chosen == ex.answerIndex
            is Exercise.Cloze -> state.chosen == ex.answerIndex
            is Exercise.Typing -> state.typed.trim() == ex.answer
            is Exercise.TileBuilder ->
                state.assembled.map { ex.tiles[it] } == ex.solution
            // Writing is marked by Composer and DeepSeek, not here.
            is Exercise.Compose -> null
            is Exercise.Flashcard -> null
        }
    }

    /**
     * Reveal, and mark it where the app can.
     *
     * Used by the types where the learner assembles or types an answer and then
     * submits it; the multiple choices come through [choose] instead.
     */
    fun reveal() {
        val state = _session.value
        val correct = objectiveResult(state.copy(revealed = true))
        _session.value = state.copy(revealed = true, wasCorrect = correct)
        if (correct == true) advanceAfterPause()
    }

    fun choose(index: Int) {
        val state = _session.value
        if (state.revealed) return
        val answered = state.copy(chosen = index, revealed = true)
        val correct = objectiveResult(answered)
        _session.value = answered.copy(wasCorrect = correct)
        if (correct == true) advanceAfterPause()
    }

    /**
     * Move on by itself when the answer was right.
     *
     * The rule is meant to be learnable in one sentence: right moves on, wrong
     * waits for you. It matches what a patient tutor does — no ceremony for the
     * ones you know, all the time in the world for the ones you do not — and it
     * halves the taps in a session for anybody doing well.
     *
     * Long enough to see the answer turn green, short enough not to feel like
     * waiting.
     */
    private fun advanceAfterPause() = viewModelScope.launch {
        val at = _session.value.index
        kotlinx.coroutines.delay(850)
        // Only if nothing else has moved in the meantime — the learner may have
        // tapped on themselves, or left the session entirely.
        if (_session.value.index == at && _session.value.wasCorrect == true) {
            grade(Grade.GOOD)
        }
    }

    fun type(text: String) = _session.update { it.copy(typed = text) }

    /**
     * Send a written sentence to be marked.
     *
     * Revealing only once the answer is back, so the grade buttons cannot be
     * pressed before the feedback they are a response to has arrived.
     */
    fun submitWriting() = viewModelScope.launch {
        val state = _session.value
        val ex = state.current?.exercise as? Exercise.Compose ?: return@launch
        if (state.typed.isBlank() || state.checking) return@launch

        _session.update { it.copy(checking = true) }
        val critique = repo.mark(ex, state.typed, settings.deepseekKey, settings.glossLanguage)
        _session.update {
            it.copy(checking = false, critique = critique, revealed = true)
        }
        loadWriting()
        // The Home button carries the count, and a count that only updates on
        // the next launch makes a sentence you just wrote look unsaved.
        refreshSettings()
    }

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

        // What was answered, so a lapse can later be described rather than
        // merely counted. Only the exercises that have a discrete answer.
        val chose = when (val ex = item.exercise) {
            is Exercise.MultipleChoice -> state.chosen?.let { ex.choices.getOrNull(it) }
            is Exercise.Cloze -> state.chosen?.let { ex.choices.getOrNull(it) }
            is Exercise.Typing -> state.typed.trim().takeIf { it.isNotEmpty() }
            else -> null
        }
        val expected = when (val ex = item.exercise) {
            is Exercise.MultipleChoice -> ex.choices.getOrNull(ex.answerIndex)
            is Exercise.Cloze -> ex.answer
            is Exercise.Typing -> ex.answer
            else -> null
        }
        val points = repo.answer(item, grade, state.combo, chose, expected)
        val combo = Xp.nextCombo(state.combo, grade)

        // A failure goes back into the queue a few questions further on, so the
        // correction is actually produced rather than merely read.
        val queue = if (grade == Grade.AGAIN) {
            SessionBuilder.requeue(state.items, state.index, item)
        } else {
            state.items
        }
        val atEnd = state.index + 1 >= queue.size

        _session.value = state.copy(
            items = queue,
            index = state.index + 1,
            revealed = false,
            chosen = null,
            typed = "",
            checking = false,
            critique = null,
            wasCorrect = null,
            assembled = emptyList(),
            combo = combo,
            earned = state.earned + points,
            correct = state.correct + if (grade == Grade.AGAIN) 0 else 1,
            // Only a first-time pass advances the bar. A retry that succeeds
            // has already been counted, and counting it twice would let a bad
            // session finish early.
            settled = state.settled + if (grade != Grade.AGAIN && !item.isRetry) 1 else 0,
            finished = atEnd,
        )
        if (atEnd) refresh()
        // The plant revives the moment the work is done, not six hours later.
        WidgetNudge.refresh(getApplication())
    }

    /** The interval each button will schedule, shown on the button itself. */
    fun previews(): Map<Grade, Int> {
        val item = _session.value.current ?: return emptyMap()
        val state = Scheduler.State(box = item.box)
        return Grade.entries.associateWith { Scheduler.previewDays(state, it) }
    }

    /**
     * Say something, unless the learner has muted the app.
     *
     * Checked here rather than in [Speaker] so the engine keeps reporting what
     * the phone can actually do — muting is a preference, and a muted app must
     * not start claiming there is no Chinese voice installed.
     */
    fun speak(text: String) {
        if (_audio.value.on) speaker.speak(text)
    }

    /**
     * Mute or unmute.
     *
     * The quiz counts are recomputed because three question types need sound:
     * muting should take them off the menu rather than leave them there to
     * produce an empty quiz.
     */
    /**
     * Switch the question language.
     *
     * Everything on screen is rebuilt rather than left to refresh on its own:
     * a half-Indonesian, half-English session is worse than either.
     */
    fun setGlossLanguage(language: GlossLanguage) = viewModelScope.launch {
        settings.glossLanguage = language
        repo.setGlossLanguage(language)
        refreshSettings()
        loadCards(_cards.value.filter)
        loadLibrary()
        _detail.value?.let { (word, _) -> openWordByHanzi(word.hanzi) }
    }

    /**
     * Turn the daily reminder on or off.
     *
     * The alarm is synced immediately rather than at next launch, because
     * somebody who just switched this on will check tonight, and "it starts
     * working after you reopen the app" is indistinguishable from broken.
     */
    fun setRemind(on: Boolean) = viewModelScope.launch {
        settings.remindEnabled = on
        ReminderScheduler.sync(getApplication())
        refreshSettings()
    }

    fun setRemindTime(hour: Int, minute: Int) = viewModelScope.launch {
        settings.remindHour = hour
        settings.remindMinute = minute
        ReminderScheduler.sync(getApplication())
        refreshSettings()
    }

    fun setPalette(palette: Palette) = viewModelScope.launch {
        settings.palette = palette
        _theme.value = _theme.value.copy(palette = palette)
        refreshSettings()
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settings.themeMode = mode
        _theme.value = _theme.value.copy(mode = mode)
        refreshSettings()
    }

    fun setAudioEnabled(enabled: Boolean) {
        settings.audioEnabled = enabled
        repo.setAudioEnabled(enabled)
        _audio.update { it.copy(enabled = enabled) }
        loadQuizSetup()
    }

    fun openCharacter(hanzi: String) = viewModelScope.launch {
        _character.value = repo.character(hanzi)
    }

    fun closeCharacter() { _character.value = null }

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
