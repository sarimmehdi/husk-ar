package com.sarim.husk.utils.errorui

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sarim.husk.utils.R
import com.sarim.husk.utils.UiText
import kotlinx.parcelize.Parcelize

/** How prominently a persistent message should be presented. */
enum class MessageSeverity {
    /** Neutral information. */
    INFO,

    /** A condition that needs attention while the app remains usable. */
    WARNING,

    /** A failure that requires user action. */
    ERROR,
}

/** A banner that remains visible until its underlying cause is resolved. */
@Immutable
@Parcelize
data class PersistentMessage(
    /** Text displayed by the banner. */
    val message: UiText,
    /** Visual prominence of the message. */
    val severity: MessageSeverity,
    /** Label for the optional action button. */
    val actionLabel: UiText? = null,
) : Parcelable

/** Renders [state] as a persistent banner above screen content. */
@Composable
fun PersistentMessageBanner(
    state: PersistentMessage,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    val colors = bannerColors(state.severity)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.container,
        contentColor = colors.content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = colors.icon,
                contentDescription = stringResource(colors.iconContentDescription),
            )
            Text(
                text = state.message.asString(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(text = state.actionLabel.asString())
                }
            }
        }
    }
}

/** Renders a failure inline without replacing surrounding content. */
@Composable
fun InlineErrorMessage(
    message: UiText,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message.asString(),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

@Composable
private fun bannerColors(severity: MessageSeverity): BannerColors =
    when (severity) {
        MessageSeverity.INFO ->
            BannerColors(
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                icon = Icons.Outlined.Info,
                iconContentDescription = R.string.information_icon_content_description,
            )
        MessageSeverity.WARNING ->
            BannerColors(
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                icon = Icons.Outlined.WarningAmber,
                iconContentDescription = R.string.warning_icon_content_description,
            )
        MessageSeverity.ERROR ->
            BannerColors(
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                icon = Icons.Outlined.ErrorOutline,
                iconContentDescription = R.string.error_icon_content_description,
            )
    }

private data class BannerColors(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val icon: ImageVector,
    @StringRes val iconContentDescription: Int,
)
