package com.assem.scrollbreak.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SbWhite,
    onPrimary = SbBlack,
    secondary = SbAccent,
    onSecondary = SbBlack,
    tertiary = SbAccent,
    background = SbBlack,
    onBackground = SbWhite,
    surface = SbBlack,
    onSurface = SbWhite,
    surfaceVariant = SbSurfaceDark,
    onSurfaceVariant = SbMutedDark,
    surfaceContainer = SbSurfaceDark,
    surfaceContainerHigh = SbSurfaceHighDark,
    surfaceContainerHighest = SbSurfaceHighDark,
    surfaceContainerLow = SbSurfaceDark,
    outline = SbMutedDark,
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = SbWhite,
    primaryContainer = Color(0xFF3A3A3A),
    onPrimaryContainer = SbWhite,
    error = SbAccent
)

private val LightColorScheme = lightColorScheme(
    primary = SbBlack,
    onPrimary = SbWhite,
    secondary = SbAccent,
    onSecondary = SbWhite,
    tertiary = SbAccent,
    background = SbWhite,
    onBackground = SbInk,
    surface = SbWhite,
    onSurface = SbInk,
    surfaceVariant = SbSurfaceLight,
    onSurfaceVariant = SbMutedLight,
    surfaceContainer = SbSurfaceLight,
    surfaceContainerHigh = SbSurfaceHighLight,
    surfaceContainerHighest = SbSurfaceHighLight,
    surfaceContainerLow = SbSurfaceLight,
    outline = SbMutedLight,
    secondaryContainer = Color(0xFFD9D9D9),
    onSecondaryContainer = SbInk,
    primaryContainer = Color(0xFFD9D9D9),
    onPrimaryContainer = SbInk,
    error = Color(0xFFC62F0A)
)

@Composable
fun ScrollBreakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
    ) {
        // The app's text is English, so keep it left-to-right even on an Arabic (RTL) phone;
        // mirroring English text jumbles punctuation and numbers ("DAYS 7", ".friend still plays").
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
    }
}