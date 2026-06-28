package com.bilimusic.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.palette.graphics.Palette
import com.bilimusic.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    primaryContainer = Purple80,
    onPrimaryContainer = Color(0xFF21005D),
    secondary = PurpleGrey40,
    onSecondary = Color.White,
    secondaryContainer = PurpleGrey80,
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Pink40,
    onTertiary = Color.White,
    tertiaryContainer = Pink80,
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFB3261E),
    onError = Color.White,
    outline = Color(0xFF79747E),
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Purple80,
    secondary = PurpleGrey80,
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = PurpleGrey80,
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFEFB8C8),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    outline = Color(0xFF938F99),
)

@Composable
fun BiliMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
    seedColor: Int = 0xFF6750A4.toInt(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val actualDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> darkTheme
    }

    val actualColorScheme = when {
        useDynamicColor && seedColor != 0xFF6750A4.toInt() -> {
            // Generate a scheme from the seed color using static builder
            val seed = Color(seedColor)
            if (actualDarkTheme) darkColorScheme(primary = seed)
            else lightColorScheme(primary = seed)
        }
        useDynamicColor -> {
            // System dynamic color based on wallpaper
            if (actualDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        else -> if (actualDarkTheme) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = actualColorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !actualDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = actualColorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * 从壁纸提取主题色
 */
suspend fun extractColorFromWallpaper(context: Context): Int? {
    return withContext(Dispatchers.IO) {
        try {
            val wallpaperManager = android.app.WallpaperManager.getInstance(context)
            val drawable = wallpaperManager.drawable ?: return@withContext null
            val bitmap = drawable.toBitmap(
                (drawable.intrinsicWidth).coerceAtLeast(100),
                (drawable.intrinsicHeight).coerceAtLeast(100)
            )
            val palette = Palette.from(bitmap).generate()
            palette?.getVibrantColor(palette.getMutedColor(0xFF6750A4.toInt()))
                ?: palette?.getMutedColor(0xFF6750A4.toInt())
        } catch (e: Exception) {
            null
        }
    }
}
