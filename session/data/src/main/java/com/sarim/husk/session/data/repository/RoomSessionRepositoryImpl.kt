package com.sarim.husk.session.data.repository

import com.sarim.husk.geometry.CameraIntrinsics
import com.sarim.husk.geometry.DualConic
import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Pose
import com.sarim.husk.geometry.Quaternion
import com.sarim.husk.geometry.Vector3
import com.sarim.husk.session.data.dao.MeasurementDao
import com.sarim.husk.session.data.dao.SessionDao
import com.sarim.husk.session.data.entity.IntrinsicsColumns
import com.sarim.husk.session.data.entity.MeasuredObjectEntity
import com.sarim.husk.session.data.entity.ObservationEntity
import com.sarim.husk.session.data.entity.OutlineColumns
import com.sarim.husk.session.data.entity.PoseColumns
import com.sarim.husk.session.data.entity.SessionEntity
import com.sarim.husk.session.data.entity.ShellColumns
import com.sarim.husk.session.domain.model.MarkerId
import com.sarim.husk.session.domain.model.MeasuredObject
import com.sarim.husk.session.domain.model.MeasurementQuality
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.Observation
import com.sarim.husk.session.domain.model.ObservationId
import com.sarim.husk.session.domain.model.Session
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.repository.SessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * [SessionRepository] backed by SQLite.
 *
 * Everything it promises is pinned by the same contract the in-memory store passes, which is the
 * point: ordering, replacement and absence are obvious in a map and easy to get wrong here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomSessionRepositoryImpl(
    private val sessions: SessionDao,
    private val measurements: MeasurementDao,
) : SessionRepository {
    override fun observeSessions(): Flow<List<Session>> =
        sessions
            .observeSessions()
            .flatMapLatest { stored ->
                if (stored.isEmpty()) {
                    // Answered immediately rather than left to the join below, so an empty database
                    // says there is nothing yet instead of leaving the list screen loading.
                    flowOf(emptyList())
                } else {
                    hydrate(stored)
                }
            }.distinctUntilChanged()

    override fun observeSession(id: SessionId): Flow<Session?> =
        sessions
            .observeSession(id.value)
            .flatMapLatest { session ->
                if (session == null) {
                    flowOf(null)
                } else {
                    hydrate(listOf(session)).map { it.firstOrNull() }
                }
            }.distinctUntilChanged()

    /**
     * Joins sessions to their objects and those to their views, as one live stream.
     *
     * Nested rather than combined, because the set of objects decides which views to watch: a new
     * object has to bring its own observations under observation too.
     */
    private fun hydrate(rows: List<SessionEntity>): Flow<List<Session>> =
        measurements.observeObjectsIn(rows.map { it.id }).flatMapLatest { objects ->
            observationsOf(objects).map { views ->
                val viewsByObject = views.groupBy { it.objectId }
                val objectsBySession = objects.groupBy { it.sessionId }
                rows.map { session ->
                    session.toDomain(
                        objectsBySession[session.id]
                            .orEmpty()
                            .map { it.toDomain(viewsByObject[it.id].orEmpty()) },
                    )
                }
            }
        }

    /** The views of the given objects, or an immediate empty answer when there are none. */
    private fun observationsOf(objects: List<MeasuredObjectEntity>): Flow<List<ObservationEntity>> =
        if (objects.isEmpty()) {
            flowOf(emptyList())
        } else {
            measurements.observeObservationsOf(objects.map { it.id })
        }

    override suspend fun putSession(session: Session) {
        sessions.upsert(session.toEntity())
        session.objects.forEachIndexed { index, measured ->
            writeObject(session.id, measured, index)
        }
    }

    override suspend fun renameSession(
        id: SessionId,
        name: String,
    ) {
        sessions.rename(id.value, name)
    }

    override suspend fun deleteSession(id: SessionId) {
        sessions.delete(id.value)
    }

    override suspend fun putObject(
        sessionId: SessionId,
        measured: MeasuredObject,
    ) {
        // Declining rather than letting the foreign key throw. The contract says naming an unknown
        // session does nothing, because a screen can act on one the moment after it was deleted.
        if (!sessions.exists(sessionId.value)) return
        writeObject(sessionId, measured, measurements.nextSortIndex(sessionId.value))
    }

    override suspend fun deleteObject(
        sessionId: SessionId,
        objectId: ObjectId,
    ) {
        measurements.deleteObject(sessionId.value, objectId.value)
    }

    /**
     * Writes one object and its views.
     *
     * An object already present keeps the position it had. Re-solving a shell would otherwise move
     * it to the end of the list, reshuffling what someone is looking at every time a new view
     * improves the fit.
     */
    private suspend fun writeObject(
        sessionId: SessionId,
        measured: MeasuredObject,
        fallbackSortIndex: Int,
    ) {
        val sortIndex = measurements.sortIndexOf(measured.id.value) ?: fallbackSortIndex
        measurements.replaceObject(
            measured = measured.toEntity(sessionId, sortIndex),
            observations =
                measured.observations.mapIndexed { index, observation ->
                    observation.toEntity(measured.id, index)
                },
        )
    }
}

private fun Session.toEntity() =
    SessionEntity(
        id = id.value,
        name = name,
        markerId = markerId.value,
        createdAtEpochMillis = createdAt.toEpochMilli(),
    )

private fun SessionEntity.toDomain(objects: List<MeasuredObject>) =
    Session(
        id = SessionId(id),
        name = name,
        markerId = MarkerId(markerId),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        objects = objects,
    )

private fun MeasuredObject.toEntity(
    sessionId: SessionId,
    sortIndex: Int,
) = MeasuredObjectEntity(
    id = id.value,
    sessionId = sessionId.value,
    label = label,
    sortIndex = sortIndex,
    shell =
        ShellColumns(
            centreX = shell.centre.x,
            centreY = shell.centre.y,
            centreZ = shell.centre.z,
            radiusX = shell.radii.x,
            radiusY = shell.radii.y,
            radiusZ = shell.radii.z,
            rotationX = shell.rotation.x,
            rotationY = shell.rotation.y,
            rotationZ = shell.rotation.z,
            rotationW = shell.rotation.w,
        ),
    viewSpreadRadians = quality?.viewSpreadRadians,
    conicResidual = quality?.conicResidual,
    nullSpaceMargin = quality?.nullSpaceMargin,
    isHandAdjusted = isHandAdjusted,
)

private fun MeasuredObjectEntity.toDomain(observations: List<ObservationEntity>) =
    MeasuredObject(
        id = ObjectId(id),
        label = label,
        shell =
            Ellipsoid(
                centre = Vector3(shell.centreX, shell.centreY, shell.centreZ),
                radii = Vector3(shell.radiusX, shell.radiusY, shell.radiusZ),
                rotation =
                    Quaternion(shell.rotationX, shell.rotationY, shell.rotationZ, shell.rotationW),
            ),
        observations = observations.map(ObservationEntity::toDomain),
        // All three are written together or not at all, so one being present means the fit
        // succeeded. Rebuilding a partial quality would invent numbers nothing measured.
        quality =
            if (viewSpreadRadians == null || conicResidual == null || nullSpaceMargin == null) {
                null
            } else {
                MeasurementQuality(viewSpreadRadians, conicResidual, nullSpaceMargin)
            },
        isHandAdjusted = isHandAdjusted,
    )

private fun Observation.toEntity(
    objectId: ObjectId,
    sortIndex: Int,
) = ObservationEntity(
    id = id.value,
    objectId = objectId.value,
    sortIndex = sortIndex,
    pose =
        PoseColumns(
            translationX = cameraInAnchor.translation.x,
            translationY = cameraInAnchor.translation.y,
            translationZ = cameraInAnchor.translation.z,
            rotationX = cameraInAnchor.rotation.x,
            rotationY = cameraInAnchor.rotation.y,
            rotationZ = cameraInAnchor.rotation.z,
            rotationW = cameraInAnchor.rotation.w,
        ),
    intrinsics =
        IntrinsicsColumns(
            focalLengthX = intrinsics.focalLengthX,
            focalLengthY = intrinsics.focalLengthY,
            principalPointX = intrinsics.principalPointX,
            principalPointY = intrinsics.principalPointY,
        ),
    outline =
        OutlineColumns(
            m00 = outline.m00,
            m01 = outline.m01,
            m02 = outline.m02,
            m11 = outline.m11,
            m12 = outline.m12,
            m22 = outline.m22,
        ),
    capturedAtEpochMillis = capturedAt.toEpochMilli(),
)

private fun ObservationEntity.toDomain() =
    Observation(
        id = ObservationId(id),
        cameraInAnchor =
            Pose(
                translation = Vector3(pose.translationX, pose.translationY, pose.translationZ),
                rotation = Quaternion(pose.rotationX, pose.rotationY, pose.rotationZ, pose.rotationW),
            ),
        intrinsics =
            CameraIntrinsics(
                focalLengthX = intrinsics.focalLengthX,
                focalLengthY = intrinsics.focalLengthY,
                principalPointX = intrinsics.principalPointX,
                principalPointY = intrinsics.principalPointY,
            ),
        outline =
            DualConic(
                m00 = outline.m00,
                m01 = outline.m01,
                m02 = outline.m02,
                m11 = outline.m11,
                m12 = outline.m12,
                m22 = outline.m22,
            ),
        capturedAt = Instant.ofEpochMilli(capturedAtEpochMillis),
    )
