package com.sarim.husk.solver

/**
 * The one call that crosses into C++.
 *
 * An interface rather than a direct `external fun` so the conversion either side of it — building
 * projection matrices, packing outlines, unpacking a quadric into an [com.sarim.husk.geometry.Pose]
 * and radii — can be tested on the JVM. That marshalling is where the mistakes live, and pinning it
 * to a device test would mean an emulator boot per run.
 */
internal interface NativeEstimator {
    /**
     * Runs the solver over [viewCount] views.
     *
     * [projections] holds twelve doubles per view and [conics] six, both in view order. [out]
     * receives eighteen doubles. Returns zero when an ellipsoid resolved, otherwise the refusal
     * code.
     */
    fun estimate(
        projections: DoubleArray,
        conics: DoubleArray,
        viewCount: Int,
        out: DoubleArray,
    ): Int
}

/** [NativeEstimator] backed by `libhusk_jni`. */
internal object JniEstimator : NativeEstimator {
    init {
        System.loadLibrary("husk_jni")
    }

    external override fun estimate(
        projections: DoubleArray,
        conics: DoubleArray,
        viewCount: Int,
        out: DoubleArray,
    ): Int
}
