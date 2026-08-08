#ifndef HUSK_CORE_BRIDGE_H_
#define HUSK_CORE_BRIDGE_H_

namespace husk {

// Slots in the packed result, in order:
//   0..2    centre
//   3..5    radii
//   6..14   rotation, row-major
//   15      null space margin
//   16      conic residual
//   17      view spread, radians
constexpr int kPackedEstimateSize = 18;

// Doubles per view in the packed input.
constexpr int kPackedProjectionStride = 12;  // a 3x4 projection matrix, row-major
constexpr int kPackedConicStride = 6;        // the upper triangle of a symmetric 3x3

// Refusal codes as they cross into Kotlin.
//
// These numbers are duplicated in RefusalReason on the Kotlin side, because an enum cannot cross JNI
// as anything richer than an integer. Both sides pin the literals in their own tests, so reordering
// RefusalReason without updating Kotlin breaks a test here rather than silently relabelling every
// refusal the app reports.
constexpr int kCodeResolved = 0;
constexpr int kCodeTooFewViews = 1;
constexpr int kCodeInconsistentInput = 2;
constexpr int kCodeDegenerateConic = 3;
constexpr int kCodeNullSpaceAmbiguous = 4;
constexpr int kCodeNotAnEllipsoid = 5;

// EstimateEllipsoid behind a flat, pointer-and-length signature.
//
// Exists so the JNI layer holds nothing but a pin, a call, and a release. Marshalling that lives in
// a JNI function can only be tested on a device; here it is ordinary C++ that the host tests cover
// in milliseconds, which is the whole reason for the split.
//
// `projections` holds view_count * kPackedProjectionStride doubles and `conics` holds
// view_count * kPackedConicStride. `out` receives kPackedEstimateSize doubles.
//
// Returns 0 when the estimate resolved, otherwise the RefusalReason as an integer. Diagnostics are
// still written for a refusal that got far enough to compute them, so the caller can show why. Any
// null buffer or negative count is refused rather than dereferenced, because a failed array pin on
// the JNI side arrives as null.
int EstimateEllipsoidPacked(const double* projections, const double* conics, int view_count,
                            double* out);

}  // namespace husk

#endif  // HUSK_CORE_BRIDGE_H_
