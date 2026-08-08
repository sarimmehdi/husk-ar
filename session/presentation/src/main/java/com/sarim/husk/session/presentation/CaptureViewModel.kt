package com.sarim.husk.session.presentation

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarim.husk.ar.CameraSnapshot
import com.sarim.husk.ar.TracedEllipse
import com.sarim.husk.session.domain.fitting.FitRefusal
import com.sarim.husk.session.domain.fitting.ShellFit
import com.sarim.husk.session.domain.model.MeasurementConfidence
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Observation
import com.sarim.husk.session.domain.model.ObservationId
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.usecase.CaptureObservationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.time.Instant
import kotlin.math.abs

/** What the capture screen is telling the person to do. */
enum class CaptureMessage {
    /** Point the camera at the marker; nothing can be measured without it. */
    FIND_THE_MARKER,

    /** Outline the object to record a view of it. */
    OUTLINE_THE_OBJECT,

    /** The camera moved while the outline was being drawn, so it matches no single frame. */
    HOLD_STILL,

    /** Recorded, but not yet enough views to fit a shell. */
    NEED_MORE_VIEWS,

    /** Recorded, but every view is from nearly the same place. */
    MOVE_AROUND_THE_OBJECT,

    /** The outline was not usable as an ellipse. */
    OUTLINE_UNUSABLE,

    /** A shell was fitted. */
    MEASURED,
}

/** What the capture screen draws. */
@Parcelize
data class CaptureScreenState(
    /** The object being measured. */
    val label: String = "",
    /** How many views have been recorded. */
    val viewCount: Int = 0,
    /** Whether the marker is currently being tracked. */
    val isMarkerTracked: Boolean = false,
    /** What to tell the person. */
    val message: CaptureMessage = CaptureMessage.FIND_THE_MARKER,
    /** How far the measurement can be trusted so far. */
    val confidence: MeasurementConfidence = MeasurementConfidence.UNMEASURED,
) : Parcelable

/** What the capture screen can ask for. */
@Immutable
sealed interface CaptureScreenToViewModelEvents {
    /** The AR view reported a frame, or null when the marker is not tracked. */
    data class FrameAvailable(
        /** The frame, or null. */
        val snapshot: CameraSnapshot?,
    ) : CaptureScreenToViewModelEvents

    /** A drag began. */
    data object TraceStarted : CaptureScreenToViewModelEvents

    /** A drag finished and produced an outline. */
    data class TraceCommitted(
        /** The outline, in preview pixels. */
        val trace: TracedEllipse,
        /** Preview width the outline was drawn on. */
        val previewWidth: Int,
        /** Preview height the outline was drawn on. */
        val previewHeight: Int,
    ) : CaptureScreenToViewModelEvents
}

/** Everything the capture screen needs from the domain. */
data class CaptureScreenUseCase(
    /** Records a view and re-fits the shell. */
    val captureObservationUseCase: CaptureObservationUseCase,
)

/**
 * Drives outlining an object through the camera.
 *
 * Holds the most recent frame so that a committed outline can be paired with the pose and lens it
 * was drawn against. Those three are one fact; separated, they describe an object that was never
 * there.
 */
class CaptureViewModel(
    private val useCases: CaptureScreenUseCase,
    private val sessionId: SessionId,
    private val objectId: ObjectId,
    private val label: String,
    private val newId: () -> String,
    private val clock: () -> Instant,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CaptureScreenState(label = label))

    /** What the screen draws. */
    val state: StateFlow<CaptureScreenState> = mutableState.asStateFlow()

    private var latest: CameraSnapshot? = null
    private var atTraceStart: CameraSnapshot? = null

    /** Handles [event]. */
    fun onEvent(event: CaptureScreenToViewModelEvents) {
        when (event) {
            is CaptureScreenToViewModelEvents.FrameAvailable -> onFrame(event.snapshot)
            CaptureScreenToViewModelEvents.TraceStarted -> atTraceStart = latest
            is CaptureScreenToViewModelEvents.TraceCommitted ->
                onTraceCommitted(event.trace, event.previewWidth, event.previewHeight)
        }
    }

    private fun onFrame(snapshot: CameraSnapshot?) {
        latest = snapshot
        mutableState.update { current ->
            current.copy(
                isMarkerTracked = snapshot != null,
                message =
                    when {
                        snapshot == null -> CaptureMessage.FIND_THE_MARKER
                        current.message == CaptureMessage.FIND_THE_MARKER ->
                            CaptureMessage.OUTLINE_THE_OBJECT
                        else -> current.message
                    },
            )
        }
    }

    private fun onTraceCommitted(
        trace: TracedEllipse,
        previewWidth: Int,
        previewHeight: Int,
    ) {
        val snapshot = latest ?: return say(CaptureMessage.FIND_THE_MARKER)

        // An outline drawn while the phone was moving matches no single frame: the object slid
        // across the preview underneath the finger. Recording it would pair a correct-looking
        // ellipse with a pose it was never drawn against, and nothing afterwards could notice.
        if (movedDuring(atTraceStart, snapshot)) return say(CaptureMessage.HOLD_STILL)

        val observation =
            Observation(
                id = ObservationId(newId()),
                cameraInAnchor = snapshot.cameraInMarker,
                intrinsics = snapshot.intrinsics,
                outline = trace.toDualConic(snapshot.previewMapping(previewWidth, previewHeight)),
                capturedAt = clock(),
            )

        viewModelScope.launch {
            val fit = useCases.captureObservationUseCase(sessionId, objectId, observation)
            mutableState.update { current ->
                current.copy(
                    viewCount = current.viewCount + 1,
                    message = fit.toMessage(),
                    confidence =
                        (fit as? ShellFit.Fitted)?.quality?.confidence()
                            ?: MeasurementConfidence.UNMEASURED,
                )
            }
        }
    }

    private fun say(message: CaptureMessage) {
        mutableState.update { it.copy(message = message) }
    }

    /** Whether the camera moved enough between two frames to invalidate an outline drawn across them. */
    private fun movedDuring(
        start: CameraSnapshot?,
        end: CameraSnapshot,
    ): Boolean {
        val from = start ?: return false
        val shifted =
            (from.cameraInMarker.translation - end.cameraInMarker.translation).length() >
                MAXIMUM_SHIFT_METRES
        // Turning matters more than shifting at arm's length, so it is judged separately rather than
        // folded into one number.
        val turned = from.cameraInMarker.rotation.inverse() * end.cameraInMarker.rotation
        return shifted || abs(turned.w) < MAXIMUM_HALF_ANGLE_COSINE
    }

    private companion object {
        /** About a finger's width. Beyond it the object has visibly slid across the preview. */
        const val MAXIMUM_SHIFT_METRES = 0.02

        /** The cosine of half of roughly two degrees. A unit quaternion's w is that cosine. */
        const val MAXIMUM_HALF_ANGLE_COSINE = 0.99985
    }
}

private fun ShellFit.toMessage(): CaptureMessage =
    when (this) {
        is ShellFit.Fitted -> CaptureMessage.MEASURED
        is ShellFit.Refused ->
            when (reason) {
                FitRefusal.TOO_FEW_VIEWS -> CaptureMessage.NEED_MORE_VIEWS
                FitRefusal.VIEWS_TOO_CLOSE -> CaptureMessage.MOVE_AROUND_THE_OBJECT
                FitRefusal.UNUSABLE_OUTLINE -> CaptureMessage.OUTLINE_UNUSABLE
                // Neither is the person's doing, and asking them to move would send them chasing a
                // fault that is not theirs. Recording happened; the shell simply did not.
                FitRefusal.NOT_AN_ELLIPSOID, FitRefusal.UNKNOWN -> CaptureMessage.NEED_MORE_VIEWS
            }
    }
