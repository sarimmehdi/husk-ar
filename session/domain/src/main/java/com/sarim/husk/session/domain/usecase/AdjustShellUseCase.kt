package com.sarim.husk.session.domain.usecase

import com.sarim.husk.geometry.Ellipsoid
import com.sarim.husk.geometry.Vector3
import com.sarim.husk.session.domain.model.ObjectId
import com.sarim.husk.session.domain.model.SessionId
import com.sarim.husk.session.domain.model.ShellAdjustment
import com.sarim.husk.session.domain.repository.SessionRepository
import kotlinx.coroutines.flow.first

/**
 * Adjusts a shell by hand.
 *
 * The result stops carrying the solver's confidence. That number was earned by the views the shell
 * was fitted to, and once it has been moved they no longer describe it; continuing to report it
 * would be the app vouching for a figure someone typed in.
 */
class AdjustShellUseCase(
    private val repository: SessionRepository,
) {
    /**
     * Applies [adjustment] to an object's shell. Does nothing when either id is unknown.
     *
     * A scale of zero or less is ignored on that axis. Zero would collapse the shell to a plane and
     * a negative would turn it inside out, and neither is anything a person dragging a slider means.
     */
    suspend operator fun invoke(
        sessionId: SessionId,
        objectId: ObjectId,
        adjustment: ShellAdjustment,
    ) {
        val measured =
            repository
                .observeSession(sessionId)
                .first()
                ?.objects
                ?.firstOrNull { it.id == objectId }
                ?: return

        val shell = measured.shell
        repository.putObject(
            sessionId,
            measured.copy(
                shell =
                    Ellipsoid(
                        centre = shell.centre + adjustment.centreOffsetMetres,
                        radii =
                            Vector3(
                                scaled(shell.radii.x, adjustment.extentScale.x),
                                scaled(shell.radii.y, adjustment.extentScale.y),
                                scaled(shell.radii.z, adjustment.extentScale.z),
                            ),
                        rotation = shell.rotation,
                    ),
                isHandAdjusted = true,
            ),
        )
    }

    private fun scaled(
        radius: Double,
        scale: Double,
    ): Double = if (scale > 0.0) radius * scale else radius
}
