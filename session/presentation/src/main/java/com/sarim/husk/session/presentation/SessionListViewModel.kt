package com.sarim.husk.session.presentation

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.usecase.CreateSessionUseCase
import com.sarim.husk.session.domain.usecase.DeleteSessionUseCase
import com.sarim.husk.session.domain.usecase.ObserveSessionsUseCase
import com.sarim.husk.session.domain.usecase.RenameSessionUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

/** One session as the list draws it. */
@Parcelize
data class SessionRow(
    /** The session's id, as a plain string so the state stays parcelable. */
    val id: String,
    /** What to show as the title. */
    val name: String,
    /** How much has been measured in it. */
    val objectCount: Int,
    /** When it was started, as epoch milliseconds. */
    val createdAtEpochMillis: Long,
) : Parcelable

/** What the session list draws. */
@Parcelize
data class SessionListScreenState(
    /** The sessions, newest first. */
    val sessions: List<SessionRow> = emptyList(),
    /** Whether the store has yet to answer. Distinguishes "no sessions" from "not known yet". */
    val isLoading: Boolean = true,
) : Parcelable

/** What the session list can ask for. */
@Immutable
sealed interface SessionListScreenToViewModelEvents {
    /** Start a session called [name]; blank takes a dated default. */
    data class Create(
        /** The requested title. */
        val name: String,
    ) : SessionListScreenToViewModelEvents

    /** Retitle a session. */
    data class Rename(
        /** Which session. */
        val id: String,
        /** The requested title. */
        val name: String,
    ) : SessionListScreenToViewModelEvents

    /** Remove a session and everything measured in it. */
    data class Delete(
        /** Which session. */
        val id: String,
    ) : SessionListScreenToViewModelEvents
}

/** Everything the session list needs from the domain. */
data class SessionListScreenUseCase(
    /** Supplies the rows. */
    val observeSessionsUseCase: ObserveSessionsUseCase,
    /** Starts a session. */
    val createSessionUseCase: CreateSessionUseCase,
    /** Retitles one. */
    val renameSessionUseCase: RenameSessionUseCase,
    /** Removes one. */
    val deleteSessionUseCase: DeleteSessionUseCase,
)

/**
 * Drives the session list.
 *
 * [marker] is the frame new sessions are anchored to. It is a constructor argument rather than
 * something chosen per session because the marker library does not exist yet; when it does, this
 * becomes the picker's answer and nothing else here changes.
 */
class SessionListViewModel(
    private val useCases: SessionListScreenUseCase,
    private val marker: MarkerId,
) : ViewModel() {
    /** What the screen draws. */
    val state: StateFlow<SessionListScreenState> =
        useCases
            .observeSessionsUseCase()
            .map { sessions ->
                SessionListScreenState(sessions = sessions.map(Session::toRow), isLoading = false)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SessionListScreenState(),
            )

    /** Handles [event]. */
    fun onEvent(event: SessionListScreenToViewModelEvents) {
        viewModelScope.launch {
            when (event) {
                is SessionListScreenToViewModelEvents.Create ->
                    useCases.createSessionUseCase(event.name, marker)

                is SessionListScreenToViewModelEvents.Rename ->
                    useCases.renameSessionUseCase(SessionId(event.id), event.name)

                is SessionListScreenToViewModelEvents.Delete ->
                    useCases.deleteSessionUseCase(SessionId(event.id))
            }
        }
    }
}

private fun Session.toRow() =
    SessionRow(
        id = id.value,
        name = name,
        objectCount = objects.size,
        createdAtEpochMillis = createdAt.toEpochMilli(),
    )

private const val STOP_TIMEOUT_MILLIS = 5_000L
