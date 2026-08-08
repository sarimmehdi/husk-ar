package com.sarim.husk.ar

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.Ellipsoid
import io.github.sceneview.NodeScope
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.RuntimeAugmentedImageDatabase
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/** An ellipsoid the scene should draw, and the identity the app knows it by. */
data class PlacedEllipsoid(
    /** Stable identity, so a selection survives the shell being re-solved. */
    val id: String,
    /** The shell itself, in the marker anchor's frame. */
    val shell: Ellipsoid,
)

/** The marker image a session is anchored to. */
data class MarkerImage(
    /** Name ARCore reports back when it recognises the image. */
    val name: String,
    /** The image itself. */
    val bitmap: Bitmap,
    /** Its printed width in metres. Wrong here means every measurement is wrong by that ratio. */
    val widthMetres: Float,
)

/**
 * How shells are coloured.
 *
 * Supplied by the caller rather than fixed here, because these are theme values and this module has
 * no business holding them.
 */
data class ShellPalette(
    /** Every shell that is not selected. */
    val unselected: Color,
    /** The selected shell. */
    val selected: Color,
)

/** What the scene draws, and which of it is selected. */
data class SceneContent(
    /** Shells to draw, in the marker anchor's frame. */
    val ellipsoids: List<PlacedEllipsoid>,
    /** Colours for the two states. */
    val palette: ShellPalette,
    /** The selected shell, if any. */
    val selectedId: String? = null,
)

/**
 * What the scene reports back each frame.
 *
 * Grouped rather than passed separately: both describe the same thing, whether the marker is in
 * view and what the camera is doing relative to it, and callers almost always want either both or
 * neither.
 */
data class SceneObserver(
    /** Called when the marker starts or stops being tracked. */
    val onMarkerTrackingChanged: (Boolean) -> Unit = {},
    /** Called every frame with the camera against the marker, or null while it is not tracked. */
    val onFrame: (CameraSnapshot?) -> Unit = {},
)

/**
 * The AR view: a tracked marker with ellipsoids drawn in its frame.
 *
 * Everything is positioned relative to the marker rather than to the session origin. That is what
 * lets a session recorded today mean the same thing when the marker is found again next week, and it
 * is the same frame the solver works in, so no transform sits between the two.
 */
@Composable
fun HuskArScene(
    marker: MarkerImage,
    content: SceneContent,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    observer: SceneObserver = SceneObserver(),
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val imageDatabase = remember { RuntimeAugmentedImageDatabase() }
    var trackedMarker by remember { mutableStateOf<AugmentedImage?>(null) }

    LaunchedEffect(marker) {
        imageDatabase.addImage(marker.name, marker.bitmap, marker.widthMetres)
    }

    ARSceneView(
        modifier = modifier,
        engine = engine,
        materialLoader = materialLoader,
        // Husk anchors to a marker, never to a detected surface. Plane finding would cost every
        // frame and earn nothing.
        planeFindingMode = Config.PlaneFindingMode.DISABLED,
        sessionConfiguration = { session, config ->
            imageDatabase.applyTo(config, session)
            // The solver needs the camera pose belonging to the frame an outline was traced on.
            // Blocking keeps frames and poses in step; latest-camera-image would let them drift.
            config.updateMode = Config.UpdateMode.BLOCKING
            config.focusMode = Config.FocusMode.AUTO
        },
        onSessionUpdated = { session, frame ->
            val found = session.trackedMarkerNamed(marker.name)
            if ((found != null) != (trackedMarker != null)) {
                observer.onMarkerTrackingChanged(found != null)
            }
            trackedMarker = found
            // Reported every frame rather than fetched when a trace is committed. By then the frame
            // that was outlined has already been replaced, and the pose would belong to a later one.
            observer.onFrame(found?.let { frame.snapshotAgainst(it) })
        },
        // A tap the shells did not consume clears the selection. Node taps are handled per node and
        // return true, so anything arriving here missed every shell. Without this the only way out
        // of a selection would be to select something else.
        onTouchEvent = { event, _ ->
            if (event.action == MotionEvent.ACTION_UP && content.selectedId != null) {
                onSelect(null)
            }
            false
        },
    ) {
        trackedMarker?.let { image ->
            AugmentedImageNode(augmentedImage = image) {
                Shells(content, materialLoader, onSelect)
            }
        }
    }
}

/**
 * The shells themselves, as children of whatever node this is called inside.
 *
 * Being a [NodeScope] extension is what parents them to the marker: NodeScope inherits these node
 * builders from the scene scope but overrides where they attach. Called against the enclosing scene
 * scope instead, the shells would sit in session coordinates and drift away from the object as
 * ARCore refined its tracking.
 */
@Composable
private fun NodeScope.Shells(
    content: SceneContent,
    materialLoader: MaterialLoader,
    onSelect: (String?) -> Unit,
) {
    content.ellipsoids.forEach { placed ->
        key(placed.id) {
            val isSelected = placed.id == content.selectedId
            // Remembered because a MaterialInstance is a native handle. Building one per
            // recomposition would leak an engine resource every frame.
            val material =
                remember(isSelected, content.palette) {
                    materialLoader.createColorInstance(
                        if (isSelected) content.palette.selected else content.palette.unselected,
                    )
                }
            val placement = placed.shell.toPlacement()
            SphereNode(
                radius = UNIT_SPHERE_RADIUS,
                materialInstance = material,
                position = placement.position,
                scale = placement.scale,
                apply = {
                    // Set as a quaternion rather than through the composable's Euler parameter, so
                    // a shell standing on end cannot hit gimbal lock.
                    quaternion = placement.rotation
                    isTouchable = true
                    onSingleTapConfirmed = {
                        onSelect(placed.id)
                        true
                    }
                },
            )
        }
    }
}

/**
 * This frame's camera, expressed against the marker.
 *
 * imageIntrinsics rather than textureIntrinsics: an outline is mapped into camera image coordinates
 * before it reaches the solver, so the lens has to describe that same image.
 */
private fun Frame.snapshotAgainst(marker: AugmentedImage): CameraSnapshot {
    val lens = camera.imageIntrinsics
    val focalLength = lens.focalLength
    val principalPoint = lens.principalPoint
    val dimensions = lens.imageDimensions
    return CameraSnapshot(
        cameraInMarker =
            cameraInMarkerFrame(
                cameraInWorld = poseFrom(camera.pose.translation, camera.pose.rotationQuaternion),
                markerInWorld =
                    poseFrom(marker.centerPose.translation, marker.centerPose.rotationQuaternion),
            ),
        intrinsics =
            CameraIntrinsics(
                focalLengthX = focalLength[0].toDouble(),
                focalLengthY = focalLength[1].toDouble(),
                principalPointX = principalPoint[0].toDouble(),
                principalPointY = principalPoint[1].toDouble(),
            ),
        imageWidth = dimensions[0],
        imageHeight = dimensions[1],
    )
}

/** The marker with this name, if ARCore is currently tracking it. */
private fun Session.trackedMarkerNamed(name: String): AugmentedImage? =
    getAllTrackables(AugmentedImage::class.java)
        .firstOrNull { it.name == name && it.trackingState == TrackingState.TRACKING }
