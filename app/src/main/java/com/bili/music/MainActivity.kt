package com.bili.music

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bili.music.data.preferences.AppPreferences
import com.bili.music.player.MusicPlayer
import com.bili.music.ui.components.MiniPlayerBar
import com.bili.music.ui.navigation.Screen
import com.bili.music.ui.screens.downloads.DownloadsScreen
import com.bili.music.ui.screens.player.PlayerScreen
import com.bili.music.ui.screens.player.PlayerViewModel
import com.bili.music.ui.screens.playlist.PlaylistScreen
import com.bili.music.ui.screens.search.SearchScreen
import com.bili.music.ui.screens.settings.SettingsScreen
import com.bili.music.ui.screens.settings.SettingsViewModel
import com.bili.music.ui.theme.BiliMusicTheme
import com.bili.music.ui.theme.extractColorFromWallpaper
import com.bili.music.data.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var musicPlayer: MusicPlayer

    @Inject
    lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        setContent {
            val context = LocalContext.current

            val themeMode by preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val useDynamicColor by preferences.useDynamicColor.collectAsState(initial = true)
            val seedColor by preferences.seedColor.collectAsState(initial = 0xFF6750A4.toInt())
            val isFirstLaunch by preferences.isFirstLaunch.collectAsState(initial = true)

            LaunchedEffect(isFirstLaunch) {
                if (isFirstLaunch) {
                    val wallpaperColor = extractColorFromWallpaper(context)
                    if (wallpaperColor != null) {
                        preferences.setSeedColor(wallpaperColor)
                        preferences.setUseDynamicColor(true)
                    }
                    preferences.setFirstLaunchDone()
                }
            }

            BiliMusicTheme(
                themeMode = themeMode,
                useDynamicColor = useDynamicColor,
                seedColor = seedColor
            ) {
                MainScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        musicPlayer.release()
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val playerUiState by playerViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    // Back button: player open → minimize; not on search → go to search; on search → exit
    BackHandler(enabled = showPlayer && playerUiState.currentSong != null) {
        showPlayer = false
        playerViewModel.minimize()
    }
    BackHandler(enabled = !showPlayer && selectedTab != 0) {
        selectedTab = 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content layer
        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayerBar(
                        currentSong = playerUiState.currentSong,
                        isPlaying = playerUiState.isPlaying,
                        onPlayPauseClick = { playerViewModel.togglePlayPause() },
                        onNextClick = { playerViewModel.playNext() },
                        onClick = {
                            playerViewModel.restore()
                            showPlayer = true
                        }
                    )

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    ) {
                        Screen.items.forEachIndexed { index, screen ->
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == index)
                                            screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = stringResource(screen.titleRes)
                                    )
                                },
                                label = { Text(stringResource(screen.titleRes)) },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        when (settingsUiState.pageTransition) {
                            "fade" -> (fadeIn() togetherWith fadeOut())
                            "scale" -> (scaleIn(initialScale = 0.9f) + fadeIn() togetherWith scaleOut(targetScale = 0.9f) + fadeOut())
                            "default" -> (fadeIn() togetherWith fadeOut())
                            else -> {
                                val direction = if (targetState > initialState) 1 else -1
                                (slideInHorizontally { width -> width * direction } + fadeIn() togetherWith
                                 slideOutHorizontally { width -> -width * direction } + fadeOut())
                            }
                        }
                    },
                    label = "tab_animation"
                ) { tab ->
                    when (tab) {
                        0 -> SearchScreen()
                        1 -> PlaylistScreen()
                        2 -> DownloadsScreen()
                        3 -> SettingsScreen(viewModel = settingsViewModel)
                    }
                }
            }
        }

        // Full screen player overlay (on top of EVERYTHING)
        AnimatedVisibility(
            visible = showPlayer && playerUiState.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}  // Block clicks from passing through
                    )
            ) {
                PlayerScreen(
                    viewModel = playerViewModel,
                    onMinimize = { showPlayer = false }
                )
            }
        }
    }
}
