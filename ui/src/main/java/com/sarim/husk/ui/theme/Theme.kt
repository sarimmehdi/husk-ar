package com.sarim.husk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme =
    darkColorScheme(
        primary = PRIMARY_GREEN_LIGHT,
        onPrimary = ON_PRIMARY_DARK,
        primaryContainer = PRIMARY_CONTAINER_DARK,
        onPrimaryContainer = PRIMARY_CONTAINER_LIGHT,
        secondary = SECONDARY_GREEN_LIGHT,
        onSecondary = ON_SECONDARY_DARK,
        secondaryContainer = SECONDARY_CONTAINER_DARK,
        onSecondaryContainer = SECONDARY_CONTAINER_LIGHT,
        background = BACKGROUND_DARK,
        onBackground = TEXT_PRIMARY_DARK,
        surface = SURFACE_DARK,
        onSurface = TEXT_PRIMARY_DARK,
        surfaceVariant = SURFACE_VARIANT_DARK,
        onSurfaceVariant = TEXT_SECONDARY_DARK,
        outline = OUTLINE_DARK,
        error = ERROR_RED,
        onError = WHITE,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PRIMARY_GREEN,
        onPrimary = WHITE,
        primaryContainer = PRIMARY_CONTAINER_LIGHT,
        onPrimaryContainer = ON_PRIMARY_CONTAINER_LIGHT,
        secondary = SECONDARY_GREEN,
        onSecondary = WHITE,
        secondaryContainer = SECONDARY_CONTAINER_LIGHT,
        onSecondaryContainer = ON_SECONDARY_CONTAINER_LIGHT,
        background = BACKGROUND_LIGHT,
        onBackground = TEXT_PRIMARY_LIGHT,
        surface = SURFACE_LIGHT,
        onSurface = TEXT_PRIMARY_LIGHT,
        surfaceVariant = SURFACE_VARIANT_LIGHT,
        onSurfaceVariant = TEXT_SECONDARY_LIGHT,
        outline = OUTLINE_LIGHT,
        error = ERROR_RED,
        onError = WHITE,
    )

/** Applies the generated Material theme and semantic spacing to [content]. */
@Composable
fun HuskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = HuskShapes,
            content = content,
        )
    }
}
