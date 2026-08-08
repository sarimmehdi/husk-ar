package com.sarim.husk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sarim.husk.session.presentation.SessionListScreen
import com.sarim.husk.session.presentation.SessionListViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app's content: the list of measuring sessions.
 *
 * Opening a session does nothing yet — the session screen is the next milestone. The callback is
 * wired now so the list is not built around its absence.
 */
@Composable
fun StarterApp(modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<SessionListViewModel>()
    SessionListScreen(
        viewModel = viewModel,
        onOpenSession = {},
        modifier = modifier,
    )
}
