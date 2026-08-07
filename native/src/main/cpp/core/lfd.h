#ifndef HUSK_CORE_LFD_H_
#define HUSK_CORE_LFD_H_

#include <array>
#include <vector>

namespace husk {

// The dual of an ellipse in the image: the symmetric 3x3 matrix of its tangent lines, stored as its
// upper triangle.
//
// Dual rather than point form because the projection of a dual quadric is a dual conic --
// C = P Q P-transpose exactly, with no inversion in the way. That is what makes recovering the
// ellipsoid a linear problem.
struct DualConic {
  double m00;
  double m01;
  double m02;
  double m11;
  double m12;
  double m22;
};

// A pinhole camera as its 3x4 projection matrix P = K [R | t], row-major.
//
// Computer vision convention: +X right, +Y down, +Z forward. Callers holding a rendering-convention
// pose must convert before building this.
struct CameraView {
  std::array<double, 12> projection;
};

// Why an estimate was refused. Each maps to something the app can tell the user to do differently.
enum class RefusalReason {
  kNone,
  // Fewer than three views. Two ellipses do not constrain an ellipsoid.
  kTooFewViews,
  // The caller passed a different number of views and conics.
  kInconsistentInput,
  // One of the conics is not a proper ellipse, so it has no centre to normalise around.
  kDegenerateConic,
  // More than one quadric fits the views. The usual cause is a camera that barely moved.
  kNullSpaceAmbiguous,
  // The recovered quadric is a hyperboloid or a plane pair rather than a bounded ellipsoid.
  kNotAnEllipsoid,
};

struct EllipsoidEstimate {
  bool ok = false;
  RefusalReason refusal = RefusalReason::kNone;

  std::array<double, 3> centre{};
  // Semi-axis lengths. radii[k] belongs to column k of rotation.
  std::array<double, 3> radii{};
  // Row-major 3x3 rotation taking ellipsoid axes to world axes. Column k is the axis of radii[k].
  std::array<double, 9> rotation{};

  // How clearly the solution stood apart from the alternatives. See Svd::NullSpaceMargin.
  double null_space_margin = 0.0;

  // Mean disagreement between the observed conics and the ones the recovered ellipsoid projects to,
  // after normalising both to unit Frobenius norm. Unitless and scale free, so it is comparable
  // across captures at different distances. Zero for exact input.
  //
  // Worth watching for gross mistakes -- a view of a different object, a badly mistraced ellipse --
  // but it is not a measure of accuracy. Poorly triangulated views produce an ellipsoid that fits
  // every observed ellipse closely and is still the wrong size, so this stays small while the answer
  // goes wrong. Use view_spread_radians to judge whether a capture was good enough.
  double conic_residual = 0.0;

  // The widest angle between any two lines of sight to the recovered object.
  //
  // This is the honest measure of capture quality, because it is what accuracy actually tracks:
  // roughly a percent of radius error per pixel of tracing error at a radian of spread, degrading
  // sharply below about a fifth of a radian. Reported rather than enforced -- how much error is
  // tolerable is the caller's decision, not the solver's.
  double view_spread_radians = 0.0;
};

// Refuse below this margin.
//
// Set from measurement rather than taste. Sound geometry sits near 9e-4 and drops only slowly as
// views converge, while genuinely coincident views fall through 1e-5; this sits below the observed
// floor for healthy captures with several times headroom, and above everything unanswerable.
//
// Deliberately a catastrophe detector and nothing more. It separates "these views cannot determine
// an ellipsoid" from "these views can", and does not attempt to separate good captures from mediocre
// ones -- the margin barely moves across that range, which is what view_spread_radians is for.
constexpr double kDefaultNullSpaceMargin = 1e-4;

// Recovers the ellipsoid whose silhouette in each view is the given ellipse.
//
// Closed form, following Rubino, Crocco and Del Bue, "3D Object Localisation from Multi-View Image
// Detections" (TPAMI 2017). Each view contributes C = P Q P-transpose up to an unknown scale;
// stacking those constraints over n views gives a 6n by 10+n homogeneous system whose null vector
// holds the quadric. No initialisation and no iteration, so it either resolves or refuses.
//
// Both inputs must describe the same object in the same world frame, in the same order.
EllipsoidEstimate EstimateEllipsoid(const std::vector<CameraView>& views,
                                    const std::vector<DualConic>& conics,
                                    double minimum_null_space_margin = kDefaultNullSpaceMargin);

}  // namespace husk

#endif  // HUSK_CORE_LFD_H_
