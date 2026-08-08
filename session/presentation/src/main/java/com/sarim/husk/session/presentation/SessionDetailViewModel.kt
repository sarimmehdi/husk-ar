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
import com.sarim.husk.session.domain.usecase.DeleteObjectUseCase
import com.sarim.husk.session.domain.usecase.ObserveSessionUseCase
import com.sarim.husk.session.domain.usecase.StartObjectUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
}

/** Everything the session screen needs from the domain. */
data class SessionDetailScreenUseCase(
    /** Supplies the session and its contents. */
    val observeSessionUseCase: ObserveSessionUseCase,
    /** Begins a new measurement. */
    val startObjectUseCase: StartObjectUseCase,
    /** Removes one. */
    val deleteObjectUseCase: DeleteObjectUseCase,
)

/** Drives the screen for one session. */
class SessionDetailViewModel(
    private val useCases: SessionDetailScreenUseCase,
    private val sessionId: SessionId,
) : ViewModel() {
    /** What the screen draws. */
    val state: StateFlow<SessionDetailScreenState> =
        useCases
            .observeSessionUseCase(sessionId)
            .map { session -> session?.toState() ?: MISSING }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SessionDetailScreenState(),
            )

    /** Handles [event]. */
    fun onEvent(event: SessionDetailScreenToViewModelEvents) {
        viewModelScope.launch {
            when (event) {
                is SessionDetailScreenToViewModelEvents.StartObject ->
                    useCases.startObjectUseCase(sessionId, event.label)

                is SessionDetailScreenToViewModelEvents.DeleteObject ->
                    useCases.deleteObjectUseCase(sessionId, ObjectId(event.id))
            }
        }
    }

    private companion object {
        /** A session that no longer exists: answered, empty, and flagged. */
        val MISSING =
            SessionDetailScreenState(isLoading = false, isMissing = true)
    }
}

private fun Session.toState() =
    SessionDetailScreenState(
        name = name,
        objects = objects.map(MeasuredObject::toRow),
        isLoading = false,
        isMissing = false,
    )

private fun MeasuredObject.toRow(): MeasuredObjectRow {
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
        confidence = quality?.confidence() ?: MeasurementConfidence.UNMEASURED,
    )
}

private const val MILLIMETRES_PER_METRE = 1000.0
private const val STOP_TIMEOUT_MILLIS = 5_000L
