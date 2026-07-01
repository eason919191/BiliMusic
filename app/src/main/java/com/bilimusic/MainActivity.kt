package com.bilimusic

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bilimusic.data.preferences.AppPreferences
import com.bilimusic.player.MusicPlayer
import com.bilimusic.ui.components.FrostedBackground
import com.bilimusic.ui.components.MiniPlayerBar
import com.bilimusic.ui.navigation.Screen
import com.bilimusic.ui.screens.downloads.DownloadsScreen
import com.bilimusic.ui.screens.player.PlayerScreen
import com.bilimusic.ui.screens.player.PlayerViewModel
import com.bilimusic.ui.screens.playlist.PlaylistScreen
import com.bilimusic.ui.screens.search.SearchScreen
import com.bilimusic.ui.screens.settings.SettingsScreen
import com.bilimusic.ui.screens.settings.SettingsViewModel
import com.bilimusic.ui.theme.BiliMusicTheme
import com.bilimusic.ui.theme.extractColorFromWallpaper
import com.bilimusic.data.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

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
                MainScreen(musicPlayer = musicPlayer)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        musicPlayer.release()
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(musicPlayer: MusicPlayer) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }

    val playerViewModel: PlayerViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val playerUiState by playerViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    // Haze state for behind-content blur on mini player only
    val hazeState = remember { HazeState() }

    // [NeriPlayer] Keep screen on during fullscreen playback
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity, showPlayer) {
        val window = activity?.window
        val keepScreenOnFlag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val shouldKeepScreenOn = showPlayer
        val wasKeepScreenOn = window?.attributes?.flags?.and(keepScreenOnFlag) == keepScreenOnFlag
        if (shouldKeepScreenOn) {
            window?.addFlags(keepScreenOnFlag)
        }
        onDispose {
            if (shouldKeepScreenOn && !wasKeepScreenOn) {
                window?.clearFlags(keepScreenOnFlag)
            }
        }
    }

    // Back button: player open → minimize; not on search → go to search; on search → exit
    BackHandler(enabled = showPlayer && playerUiState.currentSong != null) {
        showPlayer = false
        playerViewModel.minimize()
    }
    BackHandler(enabled = !showPlayer && selectedTab != 0) {
        selectedTab = 0
    }

    SharedTransitionLayout {
        val sharedCoverState = rememberSharedContentState(key = "cover_transition")

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tab content - marks blur source for mini player
                Box(modifier = Modifier.fillMaxSize().haze(state = hazeState)) {
                    // Inner content area — bottom padding is applied here to keep
                    // scroll content out from behind the NavigationBar/MiniPlayer.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = if (playerUiState.currentSong != null && !showPlayer) 136.dp else 80.dp)
                    ) {
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
                            0 -> SearchScreen(musicPlayer = musicPlayer)
                            1 -> PlaylistScreen()
                            2 -> DownloadsScreen()
                            3 -> SettingsScreen(viewModel = settingsViewModel)
                        }
                    }
                    }
                }

                // Bottom bars with frosted glass blur
                Column(modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                    AnimatedVisibility(visible = !showPlayer, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
                        MiniPlayerBar(
                            currentSong = playerUiState.currentSong,
                            isPlaying = playerUiState.isPlaying,
                            onPlayPauseClick = { playerViewModel.togglePlayPause() },
                            onNextClick = { playerViewModel.playNext() },
                            onPreviousClick = { playerViewModel.playPrevious() },
                            onClick = { playerViewModel.restore(); showPlayer = true },
                            coverSharedModifier = Modifier.sharedElement(
                                state = sharedCoverState,
                                animatedVisibilityScope = this@AnimatedVisibility
                            ),
                            hazeState = hazeState,
                            alpha = settingsUiState.miniPlayerAlpha
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
                            Screen.items.forEachIndexed { index, screen ->
                                    NavigationBarItem(
                                        icon = {
                                            Crossfade(
                                                targetState = selectedTab == index,
                                                animationSpec = tween(200),
                                                label = "nav_icon"
                                            ) { isSelected ->
                                                Icon(
                                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                                    contentDescription = screen.title
                                                )
                                            }
                                        },
                                        label = { Text(screen.title) },
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index }
                                    )
                                }
                            }
                        }
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
            MaterialTheme(colorScheme = darkColorScheme()) {
                // BackHandler inside player — highest priority to close player first
                BackHandler {
                    showPlayer = false
                    playerViewModel.minimize()
                }
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
                        onMinimize = { showPlayer = false },
                        coverSharedModifier = Modifier.sharedElement(
                            state = sharedCoverState,
                            animatedVisibilityScope = this@AnimatedVisibility
                        )
                    )
                }
            }
        }
    }
}

