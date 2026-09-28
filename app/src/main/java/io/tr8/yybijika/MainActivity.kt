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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tr8.yybijika.ui.AddNotesScreen
import io.tr8.yybijika.ui.AppViewModel
import io.tr8.yybijika.ui.BrowseScreen
import io.tr8.yybijika.ui.CardsScreen
import io.tr8.yybijika.ui.HomeScreen
import io.tr8.yybijika.ui.QuizScreen
import io.tr8.yybijika.ui.ReadScreen
import io.tr8.yybijika.ui.SessionScreen
import io.tr8.yybijika.ui.SettingsScreen
import io.tr8.yybijika.ui.WordDetailScreen
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
private enum class Overlay { NONE, SESSION, QUIZ_RUN, BROWSE, DETAIL, SETTINGS, ADD }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { YybijikaTheme { App() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: AppViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }

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

    // Granting install permission sends the user to system settings, so the only
    // way to learn they came back with it is to look again on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.recheckInstallPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun back() {
        when (overlay) {
            Overlay.DETAIL -> { vm.closeWord(); overlay = Overlay.BROWSE }
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
                        onDone = { overlay = Overlay.NONE },
                    )

                    Overlay.BROWSE -> BrowseScreen(
                        words = browse,
                        onSearch = vm::search,
                        onOpen = { vm.openWord(it); overlay = Overlay.DETAIL },
                    )

                    Overlay.DETAIL -> detail?.let { (word, mastery) ->
                        WordDetailScreen(
                            word = word,
                            mastery = mastery,
                            onSpeak = vm::speak,
                            onOpenRelated = vm::openWordByHanzi,
                        )
                    }

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
                        onDownloadUpdate = vm::downloadUpdate,
                        onInstallUpdate = vm::installUpdate,
                        onGrantInstallPermission = vm::grantInstallPermission,
                        onDismissUpdate = vm::dismissUpdate,
                    )

                    Tab.CARDS -> CardsScreen(
                        state = cards,
                        onSwipe = vm::swipe,
                        onUndo = vm::undoSwipe,
                        onFilter = { vm.loadCards(it) },
                        onRevive = vm::reviveCard,
                        onSpeak = vm::speak,
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
                        onSpeak = vm::speak,
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
