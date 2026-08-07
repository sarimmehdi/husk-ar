package com.sarim.husk.starter.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarim.husk.ui.theme.spacing

/** Connects [StarterViewModel] state and actions to the starter UI. */
@Composable
fun StarterScreen(
    viewModel: StarterViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StarterContent(
        uiState = uiState,
        onNameChanged = viewModel::onNameChanged,
        onSaveGreeting = viewModel::onSaveGreeting,
        modifier = modifier,
    )
}

/** Renders the stateless, previewable starter feature content. */
@Composable
fun StarterContent(
    uiState: StarterUiState,
    onNameChanged: (String) -> Unit,
    onSaveGreeting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StarterScreenTemplate(modifier = modifier) { contentPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            ) {
                Text(
                    text = stringResource(R.string.starter_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.starter_architecture_description),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = uiState.greeting,
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.starter_name_label)) },
                    singleLine = true,
                )
                Button(
                    onClick = onSaveGreeting,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.starter_save_action))
                }
            }
        }
    }
}

/** Provides the edge-aware scaffold shared by the starter screen. */
@Composable
fun StarterScreenTemplate(
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            content = content,
            contentWindowInsets = WindowInsets.safeDrawing,
        )
    }
}
