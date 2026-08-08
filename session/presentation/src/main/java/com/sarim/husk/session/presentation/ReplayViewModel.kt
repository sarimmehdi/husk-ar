package com.sarim.husk.session.presentation

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarim.husk.ar.CameraSnapshot
import com.sarim.husk.ar.Nudge
import com.sarim.husk.ar.PreviewMapping
import com.sarim.husk.ar.guidanceTo
import com.sarim.husk.ar.toTracedEllipse
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Observation
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.usecase.ObserveSessionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlin.math.roundToInt

/** An outline to draw over the preview, in preview pixels. */
@Parcelize
data class HintOutline(
    /** Centre x. */
    val centreX: Float,
    /** Centre y. */
    val centreY: Float,
    /** Longer semi-axis. */
    val semiMajor: Float,
    /** Shorter semi-axis. */
    val semiMinor: Float,
    /** Angle of the major axis. */
    val rotationRadians: Float,
) : Parcelable

/** What the replay screen draws. */
@Parcelize
data class ReplayScreenState(
    /** The object being reviewed. */
    val label: String = "",
    /** Which view is showing, counting from one. Zero when there are none. */
    val position: Int = 0,
    /** How many views there are. */
    val total: Int = 0,
    /** The outline that was traced, placed on the current preview. */
    val hint: HintOutline? = null,
    /** Which way to move to get back to where it was taken from. */
    val move: Nudge = Nudge.NONE,
    /** How far away that is, in centimetres, so the screen need not format a fraction. */
    val distanceCentimetres: Int = 0,
    /** Whether the camera is back on the spot. */
    val isAligned: Boolean = false,
    /** Whether the marker is currently being tracked. */
    val isMarkerTracked: Boolean = false,
) : Parcelable

/** What the replay screen can ask for. */
@Immutable
sealed interface ReplayScreenToViewModelEvents {
    /** Show the next view. */
    data object Next : ReplayScreenToViewModelEvents

    /** Show the previous one. */
    data object Previous : ReplayScreenToViewModelEvents

    /** The AR view reported a frame, or null while the marker is not tracked. */
    data class FrameAvailable(
        /** The frame. */
        val snapshot: CameraSnapshot?,
    ) : ReplayScreenToViewModelEvents

    /** The preview was laid out. */
    data class PreviewResized(
        /** Its width in pixels. */
        val width: Int,
        /** Its height in pixels. */
        val height: Int,
    ) : ReplayScreenToViewModelEvents
}

/** Everything the replay screen needs from the domain. */
data class ReplayScreenUseCase(
    /** Supplies the session the object belongs to. */
    val observeSessionUseCase: ObserveSessionUseCase,
)

/**
 * Walks back through the views an object was measured from.
 *
 * Each step guides the camera back to where that view was taken and draws the outline that was
 * traced there. The point is not nostalgia: standing in the same place is how a measurement gets
 * checked, and how a bad view gets found and replaced.
 */
class ReplayViewModel(
    private val useCases: ReplayScreenUseCase,
    private val sessionId: SessionId,
    private val objectId: ObjectId,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReplayScreenState())

    /** What the screen draws. */
    val state: StateFlow<ReplayScreenState> = mutableState.asStateFlow()

    private var observations: List<Observation> = emptyList()
    private var index = 0
    private var previewWidth = 0
    private var previewHeight = 0

    init {
        viewModelScope.launch {
            val measured =
                useCases
                    .observeSessionUseCase(sessionId)
                    .first()
                    ?.objects
                    ?.firstOrNull { it.id == objectId }
            observations = measured?.observations.orEmpty()
            mutableState.update {
                it.copy(
                    label = measured?.label.orEmpty(),
                    position = if (observations.isEmpty()) 0 else 1,
                    total = observations.size,
                )
            }
        }
    }

    /** Handles [event]. */
    fun onEvent(event: ReplayScreenToViewModelEvents) {
        when (event) {
            // Wrapping rather than stopping at the ends. Reviewing means going round a few times,
            // and a disabled button at each end turns that into a chore.
            ReplayScreenToViewModelEvents.Next -> step(1)
            ReplayScreenToViewModelEvents.Previous -> step(-1)
            is ReplayScreenToViewModelEvents.FrameAvailable -> onFrame(event.snapshot)
            is ReplayScreenToViewModelEvents.PreviewResized -> {
                previewWidth = event.width
                previewHeight = event.height
            }
        }
    }

    private fun step(by: Int) {
        if (observations.isEmpty()) return
        index = Math.floorMod(index + by, observations.size)
        mutableState.update { it.copy(position = index + 1, hint = null) }
    }

    private fun onFrame(snapshot: CameraSnapshot?) {
        val recorded = observations.getOrNull(index)
        if (snapshot == null || recorded == null) {
            mutableState.update { it.copy(isMarkerTracked = false, hint = null) }
            return
        }

        val guidance = guidanceTo(target = recorded.cameraInAnchor, current = snapshot.cameraInMarker)
        // The hint is placed with the live preview's mapping rather than the one it was recorded
        // with, because it has to land on this screen as it is now.
        val mapping =
            PreviewMapping(
                imageWidth = snapshot.imageWidth,
                imageHeight = snapshot.imageHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
            )
        val outline = recorded.outline.toTracedEllipse(mapping)

        mutableState.update {
            it.copy(
                isMarkerTracked = true,
                move = guidance.move,
                distanceCentimetres = (guidance.distanceMetres * CENTIMETRES_PER_METRE).roundToInt(),
                isAligned = guidance.isAligned,
                hint =
                    HintOutline(
                        centreX = outline.centreX,
                        centreY = outline.centreY,
                        semiMajor = outline.semiMajor,
                        semiMinor = outline.semiMinor,
                        rotationRadians = outline.rotationRadians,
                    ),
            )
        }
    }

    private companion object {
        const val CENTIMETRES_PER_METRE = 100.0
    }
}
