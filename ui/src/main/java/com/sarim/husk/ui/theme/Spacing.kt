package com.sarim.husk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Semantic spacing values shared by generated Compose UI. */
@Immutable
data class Spacing(
    /** Zero spacing for APIs that require an explicit value. */
    val default: Dp = 0.dp,
    /** Extra-small spacing between tightly related elements. */
    val extraSmall: Dp = 4.dp,
    /** Small spacing between related elements. */
    val small: Dp = 8.dp,
    /** Standard spacing between content groups. */
    val medium: Dp = 16.dp,
    /** Large spacing between distinct sections. */
    val large: Dp = 24.dp,
    /** Extra-large spacing for prominent separation. */
    val extraLarge: Dp = 32.dp,
    /** Maximum standard spacing for major layout regions. */
    val extremelyLarge: Dp = 48.dp,
    /** Horizontal padding applied to top-level screen content. */
    val screenPadding: Dp = 20.dp,
)

internal val LocalSpacing = staticCompositionLocalOf { Spacing() }

/** Spacing tokens supplied by the current [MaterialTheme]. */
val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
