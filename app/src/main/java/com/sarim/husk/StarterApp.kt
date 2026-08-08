package com.sarim.husk

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sarim.husk.ar.ShellPalette
import com.sarim.husk.nav.Navigator
import com.sarim.husk.nav.Route
import com.sarim.husk.session.presentation.CaptureScreen
import com.sarim.husk.session.presentation.CaptureViewModel
import com.sarim.husk.session.presentation.ReplayScreen
import com.sarim.husk.session.presentation.ReplayViewModel
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
        Route.Home -> SessionListDestination(navigator, modifier)
        is Route.SessionDetail -> SessionDetailDestination(current, navigator, modifier)
        is Route.Capture -> CaptureDestination(current, modifier)
        is Route.Replay -> ReplayDestination(current, modifier)
    }
}

@Composable
private fun SessionListDestination(
    navigator: Navigator,
    modifier: Modifier,
) {
    SessionListScreen(
        viewModel = koinViewModel<SessionListViewModel>(),
        onOpenSession = { navigator.navigateTo(Route.SessionDetail(it)) },
        modifier = modifier,
    )
}

@Composable
private fun SessionDetailDestination(
    route: Route.SessionDetail,
    navigator: Navigator,
    modifier: Modifier,
) {
    // Keyed by session id, so opening a different session builds its own view model rather than
    // reusing one still pointed at the previous session.
    val viewModel =
        koinViewModel<SessionDetailViewModel>(
            key = route.sessionId,
            parameters = { parametersOf(route.sessionId) },
        )
    SessionDetailScreen(
        viewModel = viewModel,
        onSessionMissing = navigator::pop,
        onMeasureObject = { objectId, label ->
            navigator.navigateTo(Route.Capture(route.sessionId, objectId, label))
        },
        onReplayObject = { objectId ->
            navigator.navigateTo(Route.Replay(route.sessionId, objectId))
        },
        modifier = modifier,
    )
}

@Composable
private fun CaptureDestination(
    route: Route.Capture,
    modifier: Modifier,
) {
    val viewModel =
        koinViewModel<CaptureViewModel>(
            key = route.objectId,
            parameters = { parametersOf(route.sessionId, route.objectId, route.label) },
        )
    CaptureScreen(
        viewModel = viewModel,
        marker = koinInject(),
        palette = shellPalette(),
        modifier = modifier,
    )
}

@Composable
private fun ReplayDestination(
    route: Route.Replay,
    modifier: Modifier,
) {
    val viewModel =
        koinViewModel<ReplayViewModel>(
            key = route.objectId,
            parameters = { parametersOf(route.sessionId, route.objectId) },
        )
    ReplayScreen(
        viewModel = viewModel,
        marker = koinInject(),
        palette = shellPalette(),
        modifier = modifier,
    )
}

/** Shell colours from the theme, so the AR layer holds no brand values of its own. */
@Composable
private fun shellPalette() =
    ShellPalette(
        unselected = MaterialTheme.colorScheme.primary,
        selected = MaterialTheme.colorScheme.tertiary,
    )
