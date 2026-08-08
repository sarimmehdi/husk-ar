package com.sarim.husk

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sarim.husk.ar.MarkerImage
import com.sarim.husk.ar.ShellPalette
import com.sarim.husk.nav.Navigator
import com.sarim.husk.nav.Route
import com.sarim.husk.session.presentation.CaptureScreen
import com.sarim.husk.session.presentation.CaptureViewModel
import com.sarim.husk.session.presentation.SessionDetailScreen
import com.sarim.husk.session.presentation.SessionDetailViewModel
import com.sarim.husk.session.presentation.SessionListScreen
import com.sarim.husk.session.presentation.SessionListViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The app shell: a list of sessions, and one session at a time.
 *
 * Destinations are read from the back stack rather than held here, so the visible screen and the
 * history cannot disagree.
 */
@Composable
fun StarterApp(modifier: Modifier = Modifier) {
    val navigator: Navigator = koinInject()
    val current = navigator.backStack.last()

    BackHandler(enabled = navigator.backStack.size > 1) { navigator.pop() }

    when (current) {
        Route.Home -> {
            val viewModel = koinViewModel<SessionListViewModel>()
            SessionListScreen(
                viewModel = viewModel,
                onOpenSession = { navigator.navigateTo(Route.SessionDetail(it)) },
                modifier = modifier,
            )
        }

        is Route.SessionDetail -> {
            // Keyed by session id, so opening a different session builds its own view model rather
            // than reusing one still pointed at the previous session.
            val viewModel =
                koinViewModel<SessionDetailViewModel>(
                    key = current.sessionId,
                    parameters = { parametersOf(current.sessionId) },
                )
            SessionDetailScreen(
                viewModel = viewModel,
                onSessionMissing = navigator::pop,
                onMeasureObject = { objectId, label ->
                    navigator.navigateTo(Route.Capture(current.sessionId, objectId, label))
                },
                modifier = modifier,
            )
        }

        is Route.Capture -> {
            val marker: MarkerImage = koinInject()
            val viewModel =
                koinViewModel<CaptureViewModel>(
                    key = current.objectId,
                    parameters = {
                        parametersOf(current.sessionId, current.objectId, current.label)
                    },
                )
            CaptureScreen(
                viewModel = viewModel,
                marker = marker,
                palette =
                    ShellPalette(
                        unselected = MaterialTheme.colorScheme.primary,
                        selected = MaterialTheme.colorScheme.tertiary,
                    ),
                modifier = modifier,
            )
        }
    }
}
