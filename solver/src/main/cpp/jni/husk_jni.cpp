#include <jni.h>

#include "core/bridge.h"

// The whole JNI surface: pin the arrays, call, release.
//
// Everything worth testing lives in core/bridge.h, which is ordinary C++ that the host tests cover
// in milliseconds. Logic added here instead could only be exercised on a device.

namespace {

// Whether an array exists and is long enough to hold what the caller promised.
//
// Kotlin sizes every one of these buffers, so a short array means a bug rather than bad user input.
// Checking anyway is what keeps that bug a refusal instead of a heap corruption, and the lengths are
// only knowable here.
bool LongEnough(JNIEnv* env, jdoubleArray array, jsize required) {
  return array != nullptr && env->GetArrayLength(array) >= required;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL Java_com_sarim_husk_solver_JniEstimator_estimate(
    JNIEnv* env, jobject /* self */, jdoubleArray projections, jdoubleArray conics, jint view_count,
    jdoubleArray out) {
  if (view_count < 0 || !LongEnough(env, out, husk::kPackedEstimateSize) ||
      !LongEnough(env, projections, view_count * husk::kPackedProjectionStride) ||
      !LongEnough(env, conics, view_count * husk::kPackedConicStride)) {
    return husk::kCodeInconsistentInput;
  }

  jdouble* pinned_projections = env->GetDoubleArrayElements(projections, nullptr);
  jdouble* pinned_conics = env->GetDoubleArrayElements(conics, nullptr);
  jdouble* pinned_out = env->GetDoubleArrayElements(out, nullptr);

  // A pin fails only when the VM is out of memory. Releasing what did succeed keeps that from
  // leaking on the way back out.
  if (pinned_projections == nullptr || pinned_conics == nullptr || pinned_out == nullptr) {
    if (pinned_projections != nullptr) {
      env->ReleaseDoubleArrayElements(projections, pinned_projections, JNI_ABORT);
    }
    if (pinned_conics != nullptr) {
      env->ReleaseDoubleArrayElements(conics, pinned_conics, JNI_ABORT);
    }
    if (pinned_out != nullptr) {
      env->ReleaseDoubleArrayElements(out, pinned_out, JNI_ABORT);
    }
    return husk::kCodeInconsistentInput;
  }

  const int code =
      husk::EstimateEllipsoidPacked(pinned_projections, pinned_conics, view_count, pinned_out);

  // The inputs are read only, so abort their copies rather than paying to write them back. Only the
  // result has to be committed.
  env->ReleaseDoubleArrayElements(projections, pinned_projections, JNI_ABORT);
  env->ReleaseDoubleArrayElements(conics, pinned_conics, JNI_ABORT);
  env->ReleaseDoubleArrayElements(out, pinned_out, 0);
  return code;
}
