package com.sarim.husk.starter.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sarim.husk.ui.theme.HuskTheme

@Preview(showBackground = true)
@Composable
private fun StarterContentPreview() {
    HuskTheme {
        StarterContent(
            uiState =
                StarterUiState(
                    greeting = PREVIEW_GREETING,
                    name = PREVIEW_NAME,
                ),
            onNameChanged = {},
            onSaveGreeting = {},
        )
    }
}

private const val PREVIEW_GREETING = "Hello, Android!"
private const val PREVIEW_NAME = "Android"
