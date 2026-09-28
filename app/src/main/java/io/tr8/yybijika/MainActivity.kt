package io.tr8.yybijika

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import io.tr8.yybijika.ui.AppViewModel
import io.tr8.yybijika.ui.BrowseScreen
import io.tr8.yybijika.ui.HomeScreen
import io.tr8.yybijika.ui.SessionScreen
import io.tr8.yybijika.ui.WordDetailScreen
import io.tr8.yybijika.ui.theme.YybijikaTheme

private enum class Screen { HOME, SESSION, BROWSE, DETAIL }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YybijikaTheme {
                App()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: AppViewModel = viewModel()) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    val home by vm.home.collectAsState()
    val session by vm.session.collectAsState()
    val browse by vm.browse.collectAsState()
    val detail by vm.detail.collectAsState()

    fun back() {
        when (screen) {
            Screen.DETAIL -> { vm.closeWord(); screen = Screen.BROWSE }
            Screen.BROWSE, Screen.SESSION -> screen = Screen.HOME
            Screen.HOME -> Unit
        }
    }

    BackHandler(enabled = screen != Screen.HOME) { back() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (screen != Screen.HOME) {
                TopAppBar(
                    title = {
                        Text(
                            when (screen) {
                                Screen.SESSION -> "Study"
                                Screen.BROWSE -> "Deck"
                                Screen.DETAIL -> detail?.first?.hanzi.orEmpty()
                                Screen.HOME -> ""
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
    ) { inner ->
        val content = Modifier.padding(inner)
        when (screen) {
            Screen.HOME -> androidx.compose.foundation.layout.Box(content) {
                HomeScreen(
                    state = home,
                    onStudy = { vm.startSession(); screen = Screen.SESSION },
                    onBrowse = { screen = Screen.BROWSE },
                )
            }

            Screen.SESSION -> androidx.compose.foundation.layout.Box(content) {
                SessionScreen(
                    state = session,
                    previews = vm.previews(),
                    onReveal = vm::reveal,
                    onChoose = vm::choose,
                    onType = vm::type,
                    onTapTile = vm::tapTile,
                    onUndoTile = vm::undoTile,
                    onGrade = vm::grade,
                    onSpeak = vm::speak,
                    onDone = { screen = Screen.HOME },
                )
            }

            Screen.BROWSE -> androidx.compose.foundation.layout.Box(content) {
                BrowseScreen(
                    words = browse,
                    onSearch = vm::search,
                    onOpen = { vm.openWord(it); screen = Screen.DETAIL },
                )
            }

            Screen.DETAIL -> androidx.compose.foundation.layout.Box(content) {
                detail?.let { (word, mastery) ->
                    WordDetailScreen(word, mastery, vm::speak)
                }
            }
        }
    }
}
