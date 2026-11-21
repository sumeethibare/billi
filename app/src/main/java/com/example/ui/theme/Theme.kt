package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.preferences.AppThemeMode
import com.example.ui.components.LocalReduceMotion

private val BilliLightColorScheme = lightColorScheme(
    primary = ForestPine,
    onPrimary = CrispIvory,
    primaryContainer = ForestPineContainer,
    onPrimaryContainer = OnForestPineContainer,
    secondary = Terracotta,
    onSecondary = CrispIvory,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = OnTerracottaContainer,
    tertiary = WarmBrass,
    onTertiary = CrispIvory,
    tertiaryContainer = WarmBrassContainer,
    onTertiaryContainer = OnWarmBrassContainer,
    background = PaperAlabaster,
    onBackground = InkPrimary,
    surface = PaperAlabaster,
    onSurface = InkPrimary,
    surfaceVariant = WarmLinen,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = CrispIvory,
    surfaceContainerLow = CrispIvory,
    surfaceContainer = WarmLinen,
    surfaceContainerHigh = SandDivider,
    outline = SoftStone,
    outlineVariant = SandDivider,
    error = NegativeRust,
    onError = CrispIvory,
    errorContainer = NegativeRustBg,
    onErrorContainer = OnTerracottaContainer
)

private val BilliDarkColorScheme = darkColorScheme(
    primary = SageLuminous,
    onPrimary = OnSageLuminous,
    primaryContainer = SageDarkContainer,
    onPrimaryContainer = OnSageDarkContainer,
    secondary = CoralWarm,
    onSecondary = OnCoralWarm,
    secondaryContainer = CoralDarkContainer,
    onSecondaryContainer = OnCoralDarkContainer,
    tertiary = WarmBrass,
    onTertiary = DarkCharcoal,
    tertiaryContainer = DarkSurfaceHigh,
    onTertiaryContainer = WarmBrassContainer,
    background = DarkCharcoal,
    onBackground = DarkInkPrimary,
    surface = DarkCharcoal,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = DarkInkSecondary,
    surfaceContainerLowest = DarkCharcoal,
    surfaceContainerLow = DarkSurfaceCard,
    surfaceContainer = DarkSurfaceHigh,
    surfaceContainerHigh = DarkDivider,
    outline = DarkDivider,
    outlineVariant = DarkSurfaceHigh,
    error = CoralWarm,
    onError = OnCoralWarm,
    errorContainer = CoralDarkContainer,
    onErrorContainer = OnCoralDarkContainer
)

private val BilliAmoledColorScheme = darkColorScheme(
    primary = SageLuminous,
    onPrimary = OnSageLuminous,
    primaryContainer = SageDarkContainer,
    onPrimaryContainer = OnSageDarkContainer,
    secondary = CoralWarm,
    onSecondary = OnCoralWarm,
    secondaryContainer = CoralDarkContainer,
    onSecondaryContainer = OnCoralDarkContainer,
    tertiary = WarmBrass,
    onTertiary = AmoledBlack,
    tertiaryContainer = AmoledSurfaceHigh,
    onTertiaryContainer = WarmBrassContainer,
    background = AmoledBlack,
    onBackground = DarkInkPrimary,
    surface = AmoledBlack,
    onSurface = DarkInkPrimary,
    surfaceVariant = AmoledSurface,
    onSurfaceVariant = DarkInkSecondary,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurfaceLow,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceHigh,
    outline = AmoledDivider,
    outlineVariant = AmoledSurfaceHigh,
    error = CoralWarm,
    onError = OnCoralWarm,
    errorContainer = CoralDarkContainer,
    onErrorContainer = OnCoralDarkContainer
)

val BilliShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
private fun animateSchemeColor(target: Color, reduceMotion: Boolean): Color {
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 320),
        label = "theme_color_morph"
    )
    return color
}

@Composable
private fun ColorScheme.animated(reduceMotion: Boolean): ColorScheme = copy(
    primary = animateSchemeColor(primary, reduceMotion),
    onPrimary = animateSchemeColor(onPrimary, reduceMotion),
    primaryContainer = animateSchemeColor(primaryContainer, reduceMotion),
    onPrimaryContainer = animateSchemeColor(onPrimaryContainer, reduceMotion),
    secondary = animateSchemeColor(secondary, reduceMotion),
    onSecondary = animateSchemeColor(onSecondary, reduceMotion),
    secondaryContainer = animateSchemeColor(secondaryContainer, reduceMotion),
    onSecondaryContainer = animateSchemeColor(onSecondaryContainer, reduceMotion),
    tertiary = animateSchemeColor(tertiary, reduceMotion),
    onTertiary = animateSchemeColor(onTertiary, reduceMotion),
    tertiaryContainer = animateSchemeColor(tertiaryContainer, reduceMotion),
    onTertiaryContainer = animateSchemeColor(onTertiaryContainer, reduceMotion),
    background = animateSchemeColor(background, reduceMotion),
    onBackground = animateSchemeColor(onBackground, reduceMotion),
    surface = animateSchemeColor(surface, reduceMotion),
    onSurface = animateSchemeColor(onSurface, reduceMotion),
    surfaceVariant = animateSchemeColor(surfaceVariant, reduceMotion),
    onSurfaceVariant = animateSchemeColor(onSurfaceVariant, reduceMotion),
    surfaceContainerLowest = animateSchemeColor(surfaceContainerLowest, reduceMotion),
    surfaceContainerLow = animateSchemeColor(surfaceContainerLow, reduceMotion),
    surfaceContainer = animateSchemeColor(surfaceContainer, reduceMotion),
    surfaceContainerHigh = animateSchemeColor(surfaceContainerHigh, reduceMotion),
    outline = animateSchemeColor(outline, reduceMotion),
    outlineVariant = animateSchemeColor(outlineVariant, reduceMotion),
    error = animateSchemeColor(error, reduceMotion),
    onError = animateSchemeColor(onError, reduceMotion),
    errorContainer = animateSchemeColor(errorContainer, reduceMotion),
    onErrorContainer = animateSchemeColor(onErrorContainer, reduceMotion)
)

@Composable
fun BilliTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    amoledDark: Boolean = false,
    useSystemFont: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val targetScheme = when {
        !useDark -> BilliLightColorScheme
        amoledDark -> BilliAmoledColorScheme
        else -> BilliDarkColorScheme
    }
    val animatedScheme = targetScheme.animated(reduceMotion)
    val typography = remember(useSystemFont) { billiTypography(useSystemFont) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDark
                controller.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(
        LocalUseSystemFont provides useSystemFont,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = typography,
            shapes = BilliShapes,
            content = content
        )
    }
}
