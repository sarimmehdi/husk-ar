package com.sarim.husk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sarim.husk.starter.presentation.StarterScreen
import com.sarim.husk.starter.presentation.StarterViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Renders the generated clean-architecture demo. */
@Composable
fun StarterApp(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<StarterViewModel>()
    StarterScreen(viewModel = viewModel, modifier = modifier)
}
