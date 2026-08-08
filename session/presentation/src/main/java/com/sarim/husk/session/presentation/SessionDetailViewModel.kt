package com.sarim.husk.session.presentation

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.MeasurementConfidence
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.model.ShellAdjustment
import com.sarim.husk.session.domain.usecase.AdjustShellUseCase
import com.sarim.husk.session.domain.usecase.DeleteObjectUseCase
import com.sarim.husk.session.domain.usecase.ObserveSessionUseCase
import com.sarim.husk.session.domain.usecase.StartObjectUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.math.roundToInt

/** One measured object as the session screen draws it. */
@Parcelize
data class MeasuredObjectRow(
    /** The object's id, as a plain string so the state stays parcelable. */
    val id: String,
    /** What to show as the title. */
    val label: String,
    /** How many views have been traced of it. */
    val viewCount: Int,
    /** Full width across each axis, in millimetres, sorted largest first. */
    val extentMillimetres: List<Int>,
    /** How far the measurement can be trusted. */
    val confidence: MeasurementConfidence,
    /** Whether this row matches the current search. */
    val matchesSearch: Boolean = false,
) : Parcelable

/** What the session screen draws. */
@Parcelize
data class SessionDetailScreenState(
    /** The session's title. */
    val name: String = "",
    /** What has been measured, in capture order. */
    val objects: List<MeasuredObjectRow> = emptyList(),
    /** Whether the store has yet to answer. */
    val isLoading: Boolean = true,
    /** Whether the session is gone, having been deleted from somewhere else. */
    val isMissing: Boolean = false,
    /** What is being searched for. */
    val search: String = "",
    /** Whether the debug overlay is on. */
    val isDebugVisible: Boolean = false,
) : Parcelable

/** What the session screen can ask for. */
@Immutable
sealed interface SessionDetailScreenToViewModelEvents {
    /** Begin measuring something new. */
    data class StartObject(
        /** What to call it. */
        val label: String,
    ) : SessionDetailScreenToViewModelEvents

    /** Remove a measured object. */
    data class DeleteObject(
        /** Which object. */
        val id: String,
    ) : SessionDetailScreenToViewModelEvents

    /** Narrow the list, and highlight what matches in the scene. */
    data class SearchChanged(
        /** What to look for. */
        val query: String,
    ) : SessionDetailScreenToViewModelEvents

    /** Turn the debug overlay on or off. */
    data object DebugToggled : SessionDetailScreenToViewModelEvents

    /** Correct a fitted shell by hand. */
    data class AdjustObject(
        /** Which object. */
        val id: String,
        /** What to change. */
        val adjustment: ShellAdjustment,
    ) : SessionDetailScreenToViewModelEvents
}

/** Everything the session screen needs from the domain. */
data class SessionDetailScreenUseCase(
    /** Supplies the session and its contents. */
    val observeSessionUseCase: ObserveSessionUseCase,
    /** Begins a new measurement. */
    val startObjectUseCase: StartObjectUseCase,
    /** Removes one. */
    val deleteObjectUseCase: DeleteObjectUseCase,
    /** Corrects one by hand. */
    val adjustShellUseCase: AdjustShellUseCase,
)

/** Drives the screen for one session. */
class SessionDetailViewModel(
    private val useCases: SessionDetailScreenUseCase,
    private val sessionId: SessionId,
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val debugVisible = MutableStateFlow(false)

    /** What the screen draws. */
    val state: StateFlow<SessionDetailScreenState> =
        combine(
            useCases.observeSessionUseCase(sessionId),
            search,
            debugVisible,
        ) { session, query, debug ->
            (session?.toState(query) ?: MISSING).copy(search = query, isDebugVisible = debug)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SessionDetailScreenState(),
        )

    /** Handles [event]. */
    fun onEvent(event: SessionDetailScreenToViewModelEvents) {
        // Neither of these touches storage, so they answer at once rather than waiting on a
        // coroutine — typing into a search box that lags a frame behind reads as broken.
        when (event) {
            is SessionDetailScreenToViewModelEvents.SearchChanged -> {
                search.value = event.query
                return
            }
            SessionDetailScreenToViewModelEvents.DebugToggled -> {
                debugVisible.value = !debugVisible.value
                return
            }
            else -> Unit
        }

        viewModelScope.launch {
            when (event) {
                is SessionDetailScreenToViewModelEvents.StartObject ->
                    useCases.startObjectUseCase(sessionId, event.label)

                is SessionDetailScreenToViewModelEvents.DeleteObject ->
                    useCases.deleteObjectUseCase(sessionId, ObjectId(event.id))

                is SessionDetailScreenToViewModelEvents.AdjustObject ->
                    useCases.adjustShellUseCase(sessionId, ObjectId(event.id), event.adjustment)

                // Both were dealt with above, without touching storage.
                is SessionDetailScreenToViewModelEvents.SearchChanged,
                SessionDetailScreenToViewModelEvents.DebugToggled,
                -> Unit
            }
        }
    }

    private companion object {
        /** A session that no longer exists: answered, empty, and flagged. */
        val MISSING =
            SessionDetailScreenState(isLoading = false, isMissing = true)
    }
}

private fun Session.toState(query: String) =
    SessionDetailScreenState(
        name = name,
        // Matches are marked rather than filtered out. A list that empties as you type hides how
        // much is there, and the scene needs to know which shells to highlight and which to arrow
        // towards — both of which need the ones that did not match as well.
        objects = objects.map { it.toRow(query) },
        isLoading = false,
        isMissing = false,
    )

private fun MeasuredObject.toRow(query: String): MeasuredObjectRow {
    val extent = shell.extent()
    return MeasuredObjectRow(
        id = id.value,
        label = label,
        viewCount = observations.size,
        // Millimetres because that is the precision the method can honestly claim, and sorted so
        // the same object reads the same way however its axes happened to come back.
        extentMillimetres =
            listOf(extent.x, extent.y, extent.z)
                .map { (it * MILLIMETRES_PER_METRE).roundToInt() }
                .sortedDescending(),
        // A hand-adjusted shell reports as adjusted whatever the solver once said about it. The
        // number described views that no longer match what is on screen.
        confidence =
            when {
                isHandAdjusted -> MeasurementConfidence.ADJUSTED
                else -> quality?.confidence() ?: MeasurementConfidence.UNMEASURED
            },
        // Case folded, because nobody searching for a mug types a capital M and means it.
        matchesSearch = query.isNotBlank() && label.contains(query.trim(), ignoreCase = true),
    )
}

private const val MILLIMETRES_PER_METRE = 1000.0
private const val STOP_TIMEOUT_MILLIS = 5_000L
