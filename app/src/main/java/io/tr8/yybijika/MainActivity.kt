package io.tr8.yybijika

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tr8.yybijika.ui.AddNotesScreen
import io.tr8.yybijika.ui.AddSongScreen
import io.tr8.yybijika.ui.AppViewModel
import io.tr8.yybijika.ui.BrowseScreen
import io.tr8.yybijika.ui.CardsScreen
import io.tr8.yybijika.ui.CharacterScreen
import io.tr8.yybijika.ui.DiagnosisScreen
import io.tr8.yybijika.ui.FlagsScreen
import io.tr8.yybijika.ui.HomeScreen
import io.tr8.yybijika.ui.QuizScreen
import io.tr8.yybijika.ui.ReadScreen
import io.tr8.yybijika.ui.SessionScreen
import io.tr8.yybijika.ui.SettingsScreen
import io.tr8.yybijika.ui.WordDetailScreen
import io.tr8.yybijika.ui.WritingScreen
import io.tr8.yybijika.widget.PinWidget
import io.tr8.yybijika.ui.theme.YybijikaTheme

/**
 * The five places the app can be, plus the screens you reach from them.
 *
 * A flat bottom bar rather than a drawer: five destinations is exactly what a
 * bar holds comfortably, and the whole point of the sections is that switching
 * between reading, drilling and reviewing should take one tap.
 */
private enum class Tab(val label: String, val zh: String, val icon: ImageVector) {
    HOME("Home", "家", Icons.Outlined.CheckCircle),
    CARDS("Cards", "卡", Icons.Filled.Style),
    QUIZ("Quiz", "测", Icons.Filled.Quiz),
    REVIEW("Review", "复", Icons.Outlined.School),
    READ("Read", "读", Icons.AutoMirrored.Filled.MenuBook),
}

/** Screens pushed on top of a tab rather than being one. */
private enum class Overlay { NONE, SESSION, QUIZ_RUN, BROWSE, DETAIL, CHARACTER,
    SETTINGS, ADD, WRITING, ADD_SONG, DIAGNOSIS, FLAGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // The view model is created here rather than inside App() so the
            // palette is available to the theme that wraps it — a theme read
            // from below its own content would apply one tap late.
            val vm: AppViewModel = viewModel()
            val theme by vm.theme.collectAsState()
            YybijikaTheme(palette = theme.palette, mode = theme.mode) { App(vm) }
        }
    }
}

/**
 * Ask for notification permission, which Android 13 and later require.
 *
 * Called when the reminder is switched on rather than at startup. Below API 33
 * there is nothing to ask for and the call does nothing.
 */
private fun askNotificationPermission(context: android.content.Context) {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val activity = context as? android.app.Activity ?: return
    if (androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        androidx.core.app.ActivityCompat.requestPermissions(
            activity, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9001,
        )
    }
}

/**
 * Send the user to the system's text-to-speech settings.
 *
 * Installing a Chinese voice is something only the system can do, so the honest
 * move is to hand the user straight to the screen that does it rather than
 * describe where it lives.
 */
private fun openVoiceSettings(context: android.content.Context) {
    val intent = android.content.Intent("com.android.settings.TTS_SETTINGS")
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        // Not every phone exposes that screen. Accessibility settings is the
        // reliable parent of it on the skins that do not.
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/**
 * Shown where an entry was expected and none was found.
 *
 * Nothing in the app should reach this: links are only offered for words and
 * characters the deck actually holds. It exists because the alternative, when
 * something does slip through, is a blank screen — and a blank screen tells the
 * reader neither what happened nor that anything went wrong at all.
 */
@Composable
private fun MissingEntry(kind: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "That $kind is not in your deck yet. You can add it under " +
                "Home → Add notes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tab by remember { mutableStateOf(Tab.HOME) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    // Where a word card was opened from. A word tapped inside a passage has to
    // go back to that passage, not to the search screen it has never seen.
    var detailFrom by remember { mutableStateOf(Overlay.BROWSE) }

    val home by vm.home.collectAsState()
    val session by vm.session.collectAsState()
    val browse by vm.browse.collectAsState()
    val detail by vm.detail.collectAsState()
    val update by vm.update.collectAsState()
    val cards by vm.cards.collectAsState()
    val passages by vm.passages.collectAsState()
    val patterns by vm.patterns.collectAsState()
    val settings by vm.settingsState.collectAsState()
    val addText by vm.addText.collectAsState()
    val addState by vm.addState.collectAsState()
    val quiz by vm.quiz.collectAsState()
    val audio by vm.audio.collectAsState()
    val character by vm.character.collectAsState()
    val charCards by vm.charCards.collectAsState()
    val openPassage by vm.openPassage.collectAsState()
    val writing by vm.writing.collectAsState()
    val passagePinyin by vm.passagePinyin.collectAsState()
    val pendingWriting by vm.pendingWriting.collectAsState()
    val markingPending by vm.markingPending.collectAsState()
    val writingPassage by vm.writingPassage.collectAsState()
    val passageError by vm.passageError.collectAsState()
    val songs by vm.songs.collectAsState()
    val songTitle by vm.songTitle.collectAsState()
    val songLyrics by vm.songLyrics.collectAsState()
    val songWorking by vm.songWorking.collectAsState()
    val songError by vm.songError.collectAsState()
    val diagnosis by vm.diagnosis.collectAsState()
    val flags by vm.flags.collectAsState()
    val vitality by vm.vitality.collectAsState()
    val deckKind by vm.deckKind.collectAsState()

    // Granting install permission sends the user to system settings, so the only
    // way to learn they came back with it is to look again on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.recheckInstallPermission()
                vm.recheckVoice()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun back() {
        when (overlay) {
            // A character was opened from a word, so back goes to that word
            // rather than all the way out: following 学院 → 院 → 商学院 should
            // walk back the way it came.
            Overlay.CHARACTER -> {
                vm.closeCharacter()
                overlay = if (detail != null) Overlay.DETAIL else Overlay.BROWSE
            }
            Overlay.DETAIL -> { vm.closeWord(); overlay = detailFrom }
            Overlay.NONE -> if (tab != Tab.HOME) tab = Tab.HOME
            else -> overlay = Overlay.NONE
        }
    }

    BackHandler(enabled = overlay != Overlay.NONE || tab != Tab.HOME) { back() }

    val showingOverlay = overlay != Overlay.NONE

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (showingOverlay) {
                TopAppBar(
                    title = {
                        Text(
                            when (overlay) {
                                Overlay.SESSION -> "Review"
                                Overlay.QUIZ_RUN -> "Quiz"
                                Overlay.ADD -> "Add notes"
                                Overlay.BROWSE -> "Search"
                                Overlay.DETAIL -> detail?.first?.hanzi.orEmpty()
                                Overlay.CHARACTER -> character?.first?.hanzi.orEmpty()
                                Overlay.WRITING -> "Your writing"
                                Overlay.DIAGNOSIS -> "How you are doing"
                                Overlay.FLAGS -> "Cards you flagged"
                                Overlay.ADD_SONG -> "Add a song"
                                Overlay.SETTINGS -> "Settings"
                                Overlay.NONE -> ""
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!showingOverlay) {
                NavigationBar {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, entry.label) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner)) {
            if (showingOverlay) {
                when (overlay) {
                    Overlay.SESSION, Overlay.QUIZ_RUN -> SessionScreen(
                        state = session,
                        previews = vm.previews(),
                        onReveal = vm::reveal,
                        onChoose = vm::choose,
                        onType = vm::type,
                        onTapTile = vm::tapTile,
                        onUndoTile = vm::undoTile,
                        onGrade = vm::grade,
                        onSpeak = vm::speak,
                        onSubmit = vm::submitWriting,
                        onFlag = vm::flagCurrent,
                        onKeepGoing = vm::keepGoing,
                        onStopHere = vm::stopHere,
                        onDone = { overlay = Overlay.NONE },
                        audio = audio,
                        onToggleAudio = vm::setAudioEnabled,
                    )

                    Overlay.BROWSE -> BrowseScreen(
                        words = browse,
                        onSearch = vm::search,
                        onOpen = {
                            vm.openWord(it)
                            detailFrom = Overlay.BROWSE
                            overlay = Overlay.DETAIL
                        },
                    )

                    // The null branch should be unreachable — nothing offers a
                    // link to a word the deck does not have. It is handled all
                    // the same, because the failure it replaces was a blank
                    // screen with no way to tell what had gone wrong.
                    Overlay.DETAIL -> detail?.let { (word, mastery) ->
                        WordDetailScreen(
                            word = word,
                            mastery = mastery,
                            onSpeak = vm::speak,
                            onOpenRelated = vm::openWordByHanzi,
                            onOpenCharacter = {
                                vm.openCharacter(it); overlay = Overlay.CHARACTER
                            },
                        )
                    } ?: MissingEntry("word")

                    Overlay.CHARACTER -> character?.let { (card, words) ->
                        CharacterScreen(
                            character = card,
                            words = words,
                            onSpeak = vm::speak,
                            onOpenWord = {
                                vm.closeCharacter()
                                vm.openWordByHanzi(it)
                                overlay = Overlay.DETAIL
                            },
                        )
                    } ?: MissingEntry("character")

                    Overlay.ADD -> AddNotesScreen(
                        text = addText,
                        state = addState,
                        hasKey = settings.hasKey,
                        onTextChange = vm::setAddText,
                        onParse = vm::parseNotes,
                        onFill = vm::fillBlanks,
                        onToggleCard = vm::toggleCard,
                        onSave = vm::saveCards,
                        onReset = vm::resetAdd,
                        onOpenSettings = { overlay = Overlay.SETTINGS },
                    )

                    Overlay.DIAGNOSIS -> DiagnosisScreen(
                        diagnosis = diagnosis,
                        onOpenWord = {
                            vm.openWordByHanzi(it)
                            detailFrom = Overlay.DIAGNOSIS
                            overlay = Overlay.DETAIL
                        },
                        onOpenCharacter = {
                            vm.openCharacter(it); overlay = Overlay.CHARACTER
                        },
                    )

                    Overlay.FLAGS -> FlagsScreen(
                        flags = flags,
                        onFixed = vm::flagFixed,
                        onRemove = vm::unflag,
                        onOpenWord = {
                            vm.openWordByHanzi(it)
                            detailFrom = Overlay.FLAGS
                            overlay = Overlay.DETAIL
                        },
                    )

                    Overlay.ADD_SONG -> AddSongScreen(
                        title = songTitle,
                        lyrics = songLyrics,
                        working = songWorking,
                        error = songError,
                        hasKey = settings.hasKey,
                        onTitle = vm::setSongTitle,
                        onLyrics = vm::setSongLyrics,
                        onAdd = vm::addSong,
                        onOpenSettings = { overlay = Overlay.SETTINGS },
                    )

                    Overlay.WRITING -> WritingScreen(
                        compositions = writing,
                        pending = pendingWriting,
                        marking = markingPending,
                        hasKey = settings.hasKey,
                        onMarkPending = vm::markPendingWriting,
                    )

                    Overlay.SETTINGS -> SettingsScreen(
                        state = settings,
                        update = update,
                        onSaveKey = vm::saveKey,
                        onClearKey = vm::clearKey,
                        onSessionSize = vm::setSessionSize,
                        onNewPerSession = vm::setNewPerSession,
                        onAutoCheck = vm::setAutoCheck,
                        onCheckNow = vm::checkNow,
                        onDownloadUpdate = vm::downloadUpdate,
                        onInstallUpdate = vm::installUpdate,
                        onGrantInstallPermission = vm::grantInstallPermission,
                        onDismissUpdate = vm::dismissUpdate,
                        onGlossLanguage = vm::setGlossLanguage,
                        onToggleAudio = vm::setAudioEnabled,
                        onTestSound = vm::testSound,
                        onOpenVoiceSettings = { openVoiceSettings(context) },
                        onRemind = { on ->
                            // The permission is asked for at the moment it is
                            // needed, not at launch: a notification prompt
                            // before the app has shown its worth is the fastest
                            // route to a permanent no.
                            if (on) askNotificationPermission(context)
                            vm.setRemind(on)
                        },
                        onRemindTime = vm::setRemindTime,
                        onAddWidget = { PinWidget.request(context) },
                        onOpenFlags = { vm.loadFlags(); overlay = Overlay.FLAGS },
                        canPinWidget = PinWidget.isSupported(context),
                        onPalette = vm::setPalette,
                        onThemeMode = vm::setThemeMode,
                    )

                    Overlay.NONE -> Unit
                }
            } else {
                when (tab) {
                    Tab.HOME -> HomeScreen(
                        state = home,
                        update = update,
                        onStudy = { vm.startSession(); overlay = Overlay.SESSION },
                        onBrowse = { overlay = Overlay.BROWSE },
                        onSettings = { overlay = Overlay.SETTINGS },
                        onAddNotes = { overlay = Overlay.ADD },
                        onWriting = { vm.loadWriting(); overlay = Overlay.WRITING },
                        onDiagnosis = {
                            vm.loadDiagnosis(); overlay = Overlay.DIAGNOSIS
                        },
                        vitality = vitality,
                        writtenCount = settings.writtenCount,
                        onDownloadUpdate = vm::downloadUpdate,
                        onInstallUpdate = vm::installUpdate,
                        onGrantInstallPermission = vm::grantInstallPermission,
                        onDismissUpdate = vm::dismissUpdate,
                    )

                    Tab.CARDS -> CardsScreen(
                        state = cards,
                        characters = charCards,
                        kind = deckKind,
                        audio = audio,
                        onSwipe = vm::swipe,
                        onUndo = vm::undoSwipe,
                        // One filter drives both decks, so switching between
                        // them keeps you in the same slice.
                        onFilter = { vm.loadCards(it); vm.loadCharacterCards(it) },
                        onRevive = vm::reviveCard,
                        onSpeak = vm::speak,
                        onToggleAudio = vm::setAudioEnabled,
                        onDeckKind = vm::setDeckKind,
                        onSwipeCharacter = vm::swipeCharacter,
                        onUndoCharacter = vm::undoCharacterSwipe,
                        onReviveCharacter = vm::reviveCharacter,
                    )

                    Tab.REVIEW -> {
                        // Review is a session, not a browsable screen, so entering
                        // the tab starts one rather than showing a landing page.
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            vm.startSession()
                            overlay = Overlay.SESSION
                            tab = Tab.HOME
                        }
                    }

                    Tab.READ -> ReadScreen(
                        passages = passages,
                        patterns = patterns,
                        openPassageId = openPassage,
                        onOpenPassage = vm::openPassage,
                        showPinyin = passagePinyin,
                        onShowPinyin = vm::setPassagePinyin,
                        writing = writingPassage,
                        error = passageError,
                        onMakePassage = vm::makePassage,
                        onDismissError = vm::dismissPassageError,
                        onDeletePassage = vm::deletePassage,
                        songs = songs,
                        onAddSong = { vm.dismissSongError(); overlay = Overlay.ADD_SONG },
                        onSpeak = vm::speak,
                        // A word tapped in a passage opens its own card, and
                        // Back returns to the passage rather than out of it.
                        onOpenWord = {
                            vm.openWordByHanzi(it)
                            // NONE, so Back drops straight out of the word card
                            // and onto the passage the reader is still holding.
                            detailFrom = Overlay.NONE
                            overlay = Overlay.DETAIL
                        },
                    )

                    Tab.QUIZ -> QuizScreen(
                        setup = quiz,
                        onSelectType = vm::selectQuizType,
                        onLength = vm::setQuizLength,
                        onStart = { vm.startQuiz(); overlay = Overlay.QUIZ_RUN },
                    )
                }
            }
        }
    }
}
