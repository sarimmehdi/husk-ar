#include "core/lfd.h"

#include <algorithm>
#include <cmath>
#include <cstddef>

#include "core/linalg.h"

namespace husk {
namespace {

constexpr int kMinimumViews = 3;
constexpr int kQuadricUnknowns = 10;  // the upper triangle of a symmetric 4x4
constexpr int kConicEquations = 6;    // the upper triangle of a symmetric 3x3
constexpr double kNearlyZero = 1e-12;

// Upper-triangle index pairs, in the order the unknowns are stacked.
constexpr int kQuadricPairs[kQuadricUnknowns][2] = {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {1, 1},
                                                   {1, 2}, {1, 3}, {2, 2}, {2, 3}, {3, 3}};
constexpr int kConicPairs[kConicEquations][2] = {{0, 0}, {0, 1}, {0, 2}, {1, 1}, {1, 2}, {2, 2}};

using Matrix3 = std::array<double, 9>;
using Matrix4 = std::array<double, 16>;
using Projection = std::array<double, 12>;

EllipsoidEstimate Refuse(RefusalReason reason) {
  EllipsoidEstimate estimate;
  estimate.ok = false;
  estimate.refusal = reason;
  return estimate;
}

Matrix3 AsMatrix3(const DualConic& conic) {
  return {conic.m00, conic.m01, conic.m02, conic.m01, conic.m11,
          conic.m12, conic.m02, conic.m12, conic.m22};
}

double FrobeniusNorm(const Matrix3& m) {
  double sum = 0.0;
  for (double value : m) {
    sum += value * value;
  }
  return std::sqrt(sum);
}

// The similarity that recentres and rescales one view's image coordinates, so the ellipse sits at
// the origin at roughly unit size.
//
// Without this the assembled matrix mixes columns built from focal lengths with columns built from
// principal point offsets, and the null vector is read off a system spanning several orders of
// magnitude. The transform is applied to the conic and the camera together, which leaves
// C = P Q P-transpose untouched.
bool ImageNormaliser(const DualConic& conic, Matrix3* normaliser) {
  const Matrix3 c = AsMatrix3(conic);
  const double norm = FrobeniusNorm(c);
  if (norm < kNearlyZero || std::abs(c[8]) < kNearlyZero * norm) {
    return false;
  }

  // Fix the scale by driving the homogeneous corner to -1, the same normalisation the ellipsoid
  // itself uses, so the centre can be read straight out of the last column.
  const double inverse_scale = -1.0 / c[8];
  const double centre_x = -c[2] * inverse_scale;
  const double centre_y = -c[5] * inverse_scale;

  // Undo the outer product of the centre to recover the shape about the origin. Its trace is the
  // sum of the squared semi-axes, so half of it is a mean squared radius.
  const double shape_xx = c[0] * inverse_scale + centre_x * centre_x;
  const double shape_yy = c[4] * inverse_scale + centre_y * centre_y;
  const double mean_squared_radius = 0.5 * (shape_xx + shape_yy);
  if (!(mean_squared_radius > kNearlyZero)) {
    return false;
  }
  const double radius = std::sqrt(mean_squared_radius);

  *normaliser = {1.0 / radius, 0.0, -centre_x / radius, 0.0, 1.0 / radius,
                 -centre_y / radius, 0.0, 0.0,          1.0};
  return true;
}

Matrix3 CongruentTransform(const Matrix3& t, const Matrix3& m) {
  Matrix3 product{};  // m * t-transpose
  for (int row = 0; row < 3; ++row) {
    for (int col = 0; col < 3; ++col) {
      double sum = 0.0;
      for (int k = 0; k < 3; ++k) {
        sum += m[static_cast<std::size_t>(row * 3 + k)] * t[static_cast<std::size_t>(col * 3 + k)];
      }
      product[static_cast<std::size_t>(row * 3 + col)] = sum;
    }
  }
  Matrix3 result{};
  for (int row = 0; row < 3; ++row) {
    for (int col = 0; col < 3; ++col) {
      double sum = 0.0;
      for (int k = 0; k < 3; ++k) {
        sum += t[static_cast<std::size_t>(row * 3 + k)] *
               product[static_cast<std::size_t>(k * 3 + col)];
      }
      result[static_cast<std::size_t>(row * 3 + col)] = sum;
    }
  }
  return result;
}

Projection ApplyNormaliser(const Matrix3& t, const Projection& p) {
  Projection result{};
  for (int row = 0; row < 3; ++row) {
    for (int col = 0; col < 4; ++col) {
      double sum = 0.0;
      for (int k = 0; k < 3; ++k) {
        sum += t[static_cast<std::size_t>(row * 3 + k)] * p[static_cast<std::size_t>(k * 4 + col)];
      }
      result[static_cast<std::size_t>(row * 4 + col)] = sum;
    }
  }
  return result;
}

// P H, where H is the 4x4 that carries preconditioned world coordinates back to real ones.
Projection ApplyWorldTransform(const Projection& p, const Matrix4& h) {
  Projection result{};
  for (int row = 0; row < 3; ++row) {
    for (int col = 0; col < 4; ++col) {
      double sum = 0.0;
      for (int k = 0; k < 4; ++k) {
        sum += p[static_cast<std::size_t>(row * 4 + k)] * h[static_cast<std::size_t>(k * 4 + col)];
      }
      result[static_cast<std::size_t>(row * 4 + col)] = sum;
    }
  }
  return result;
}

// The camera centre: the world point that projects nowhere, P [c; 1] = 0.
bool CameraCentre(const Projection& p, std::array<double, 3>* centre) {
  const double a = p[0];
  const double b = p[1];
  const double c = p[2];
  const double d = p[4];
  const double e = p[5];
  const double f = p[6];
  const double g = p[8];
  const double h = p[9];
  const double i = p[10];

  const double determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
  if (std::abs(determinant) < kNearlyZero) {
    return false;
  }
  const Matrix3 inverse = {(e * i - f * h) / determinant, (c * h - b * i) / determinant,
                           (b * f - c * e) / determinant, (f * g - d * i) / determinant,
                           (a * i - c * g) / determinant, (c * d - a * f) / determinant,
                           (d * h - e * g) / determinant, (b * g - a * h) / determinant,
                           (a * e - b * d) / determinant};
  const std::array<double, 3> last_column = {p[3], p[7], p[11]};
  for (int row = 0; row < 3; ++row) {
    double sum = 0.0;
    for (int k = 0; k < 3; ++k) {
      sum += inverse[static_cast<std::size_t>(row * 3 + k)] *
             last_column[static_cast<std::size_t>(k)];
    }
    (*centre)[static_cast<std::size_t>(row)] = -sum;
  }
  return true;
}

// Moves the world origin to the middle of the cameras.
//
// The shape of an ellipsoid lives in the difference between the quadric's leading block and the
// outer product of its centre. When the centre is far from the origin that difference cancels two
// large numbers to leave a small one, and the axes lose precision to the subtraction. Solving near
// the origin avoids the cancellation entirely.
//
// Translation only. Rescaling by how far apart the cameras are looks like the natural companion but
// is backwards: as the views converge that spread collapses towards zero while the object stays put,
// so the transform blows the object's coordinates up without bound in precisely the ill-conditioned
// case the estimator most needs to survive.
Matrix4 WorldPreconditioner(const std::vector<CameraView>& views) {
  std::array<double, 3> centroid = {0.0, 0.0, 0.0};
  std::vector<std::array<double, 3>> centres;
  centres.reserve(views.size());
  for (const CameraView& view : views) {
    std::array<double, 3> centre{};
    if (!CameraCentre(view.projection, &centre)) {
      continue;
    }
    centres.push_back(centre);
    for (int axis = 0; axis < 3; ++axis) {
      centroid[static_cast<std::size_t>(axis)] += centre[static_cast<std::size_t>(axis)];
    }
  }

  Matrix4 h = {1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0};
  if (centres.empty()) {
    return h;
  }
  const double count = static_cast<double>(centres.size());
  for (int axis = 0; axis < 3; ++axis) {
    centroid[static_cast<std::size_t>(axis)] /= count;
  }

  h[3] = centroid[0];
  h[7] = centroid[1];
  h[11] = centroid[2];
  return h;
}

// Rows of the linear map from the quadric's unknowns to one view's projected conic.
//
// Entry (r, s) of P Q P-transpose is a sum over pairs of quadric entries. Symmetry lets the pair
// (a, b) and its mirror share one unknown, which is why the off-diagonal coefficients carry both
// products.
void AppendViewEquations(const Projection& p, const DualConic& conic, double conic_scale,
                         int view_index, Matrix* m) {
  const std::array<double, kConicEquations> conic_values = {conic.m00, conic.m01, conic.m02,
                                                            conic.m11, conic.m12, conic.m22};
  for (int equation = 0; equation < kConicEquations; ++equation) {
    const int r = kConicPairs[equation][0];
    const int s = kConicPairs[equation][1];
    const int row = view_index * kConicEquations + equation;

    for (int unknown = 0; unknown < kQuadricUnknowns; ++unknown) {
      const int a = kQuadricPairs[unknown][0];
      const int b = kQuadricPairs[unknown][1];
      double coefficient = p[static_cast<std::size_t>(r * 4 + a)] *
                           p[static_cast<std::size_t>(s * 4 + b)];
      if (a != b) {
        coefficient +=
            p[static_cast<std::size_t>(r * 4 + b)] * p[static_cast<std::size_t>(s * 4 + a)];
      }
      (*m)(row, unknown) = coefficient;
    }

    // Each view sees the conic only up to scale, so the unknown scale gets its own column. It is
    // the reason the system has 10 + n unknowns rather than 10.
    (*m)(row, kQuadricUnknowns + view_index) =
        -conic_values[static_cast<std::size_t>(equation)] * conic_scale;
  }
}

Matrix4 QuadricFromUnknowns(const std::vector<double>& unknowns) {
  Matrix4 quadric{};
  for (int index = 0; index < kQuadricUnknowns; ++index) {
    const int a = kQuadricPairs[index][0];
    const int b = kQuadricPairs[index][1];
    const double value = unknowns[static_cast<std::size_t>(index)];
    quadric[static_cast<std::size_t>(a * 4 + b)] = value;
    quadric[static_cast<std::size_t>(b * 4 + a)] = value;
  }
  return quadric;
}

// H Q H-transpose, undoing the world preconditioning.
Matrix4 UndoWorldPreconditioning(const Matrix4& h, const Matrix4& quadric) {
  Matrix4 product{};  // quadric * h-transpose
  for (int row = 0; row < 4; ++row) {
    for (int col = 0; col < 4; ++col) {
      double sum = 0.0;
      for (int k = 0; k < 4; ++k) {
        sum += quadric[static_cast<std::size_t>(row * 4 + k)] *
               h[static_cast<std::size_t>(col * 4 + k)];
      }
      product[static_cast<std::size_t>(row * 4 + col)] = sum;
    }
  }
  Matrix4 result{};
  for (int row = 0; row < 4; ++row) {
    for (int col = 0; col < 4; ++col) {
      double sum = 0.0;
      for (int k = 0; k < 4; ++k) {
        sum += h[static_cast<std::size_t>(row * 4 + k)] *
               product[static_cast<std::size_t>(k * 4 + col)];
      }
      result[static_cast<std::size_t>(row * 4 + col)] = sum;
    }
  }
  return result;
}

DualConic ProjectQuadric(const Projection& p, const Matrix4& quadric) {
  std::array<double, 12> qp{};  // quadric * p-transpose, 4x3
  for (int row = 0; row < 4; ++row) {
    for (int col = 0; col < 3; ++col) {
      double sum = 0.0;
      for (int k = 0; k < 4; ++k) {
        sum += quadric[static_cast<std::size_t>(row * 4 + k)] *
               p[static_cast<std::size_t>(col * 4 + k)];
      }
      qp[static_cast<std::size_t>(row * 3 + col)] = sum;
    }
  }
  Matrix3 c{};
  for (int row = 0; row < 3; ++row) {
    for (int col = 0; col < 3; ++col) {
      double sum = 0.0;
      for (int k = 0; k < 4; ++k) {
        sum += p[static_cast<std::size_t>(row * 4 + k)] * qp[static_cast<std::size_t>(k * 3 + col)];
      }
      c[static_cast<std::size_t>(row * 3 + col)] = sum;
    }
  }
  return DualConic{c[0], c[1], c[2], c[4], c[5], c[8]};
}

// Disagreement between two conics that are each only defined up to scale, so both are taken to unit
// norm and the sign is aligned before differencing.
double ConicDisagreement(const DualConic& expected, const DualConic& actual) {
  Matrix3 a = AsMatrix3(expected);
  Matrix3 b = AsMatrix3(actual);
  const double norm_a = FrobeniusNorm(a);
  const double norm_b = FrobeniusNorm(b);
  if (norm_a < kNearlyZero || norm_b < kNearlyZero) {
    return 0.0;
  }
  double alignment = 0.0;
  for (std::size_t i = 0; i < a.size(); ++i) {
    a[i] /= norm_a;
    b[i] /= norm_b;
    alignment += a[i] * b[i];
  }
  const double sign = alignment < 0.0 ? -1.0 : 1.0;

  double squared = 0.0;
  for (std::size_t i = 0; i < a.size(); ++i) {
    const double delta = a[i] - sign * b[i];
    squared += delta * delta;
  }
  return std::sqrt(squared);
}

// The widest angle between any two lines of sight to a point.
//
// Computed against the recovered centre rather than between camera positions, because that is what
// governs how well the views triangulate. Two cameras far apart but nearly in line with the object
// see almost the same thing.
double ViewSpread(const std::vector<CameraView>& views, const std::array<double, 3>& target) {
  std::vector<std::array<double, 3>> directions;
  directions.reserve(views.size());
  for (const CameraView& view : views) {
    std::array<double, 3> centre{};
    if (!CameraCentre(view.projection, &centre)) {
      continue;
    }
    std::array<double, 3> direction{};
    double length = 0.0;
    for (int axis = 0; axis < 3; ++axis) {
      const std::size_t a = static_cast<std::size_t>(axis);
      direction[a] = target[a] - centre[a];
      length += direction[a] * direction[a];
    }
    length = std::sqrt(length);
    if (length < kNearlyZero) {
      continue;
    }
    for (double& component : direction) {
      component /= length;
    }
    directions.push_back(direction);
  }

  double widest = 0.0;
  for (std::size_t i = 0; i + 1 < directions.size(); ++i) {
    for (std::size_t j = i + 1; j < directions.size(); ++j) {
      double dot = 0.0;
      for (int axis = 0; axis < 3; ++axis) {
        dot += directions[i][static_cast<std::size_t>(axis)] *
               directions[j][static_cast<std::size_t>(axis)];
      }
      widest = std::max(widest, std::acos(std::clamp(dot, -1.0, 1.0)));
    }
  }
  return widest;
}

}  // namespace

EllipsoidEstimate EstimateEllipsoid(const std::vector<CameraView>& views,
                                    const std::vector<DualConic>& conics,
                                    double minimum_null_space_margin) {
  if (views.size() != conics.size()) {
    return Refuse(RefusalReason::kInconsistentInput);
  }
  const int view_count = static_cast<int>(views.size());
  if (view_count < kMinimumViews) {
    return Refuse(RefusalReason::kTooFewViews);
  }

  const Matrix4 world = WorldPreconditioner(views);

  Matrix m(view_count * kConicEquations, kQuadricUnknowns + view_count);
  for (int index = 0; index < view_count; ++index) {
    const std::size_t i = static_cast<std::size_t>(index);
    Matrix3 normaliser{};
    if (!ImageNormaliser(conics[i], &normaliser)) {
      return Refuse(RefusalReason::kDegenerateConic);
    }
    const Matrix3 normalised_conic = CongruentTransform(normaliser, AsMatrix3(conics[i]));
    const Projection normalised_camera =
        ApplyWorldTransform(ApplyNormaliser(normaliser, views[i].projection), world);

    // The conic's own scale is arbitrary, so divide it out. Left in, a conic reported a thousand
    // times larger than its neighbours would dominate the least squares fit for no reason.
    const double conic_norm = FrobeniusNorm(normalised_conic);
    if (conic_norm < kNearlyZero) {
      return Refuse(RefusalReason::kDegenerateConic);
    }
    const DualConic scaled{normalised_conic[0], normalised_conic[1], normalised_conic[2],
                           normalised_conic[4], normalised_conic[5], normalised_conic[8]};
    AppendViewEquations(normalised_camera, scaled, 1.0 / conic_norm, index, &m);
  }

  const Svd svd = ComputeSvd(m);
  const double margin = svd.NullSpaceMargin();
  if (margin < minimum_null_space_margin) {
    EllipsoidEstimate refusal = Refuse(RefusalReason::kNullSpaceAmbiguous);
    refusal.null_space_margin = margin;
    return refusal;
  }

  const Matrix4 quadric =
      UndoWorldPreconditioning(world, QuadricFromUnknowns(svd.NullVector()));

  // Fix the arbitrary overall scale by driving the homogeneous corner to -1. A corner at zero means
  // the recovered surface has no finite centre, so it is not an ellipsoid.
  double largest = 0.0;
  for (double value : quadric) {
    largest = std::max(largest, std::abs(value));
  }
  if (largest < kNearlyZero || std::abs(quadric[15]) < kNearlyZero * largest) {
    return Refuse(RefusalReason::kNotAnEllipsoid);
  }
  const double normalisation = -1.0 / quadric[15];

  const std::array<double, 3> centre = {-quadric[3] * normalisation, -quadric[7] * normalisation,
                                        -quadric[11] * normalisation};

  // The leading block carries the shape displaced by the centre's outer product. Adding it back --
  // not subtracting it, which is the sign the reference implementation gets wrong -- leaves the
  // shape matrix about the origin.
  Matrix shape(3, 3);
  for (int row = 0; row < 3; ++row) {
    for (int col = 0; col < 3; ++col) {
      shape(row, col) = quadric[static_cast<std::size_t>(row * 4 + col)] * normalisation +
                        centre[static_cast<std::size_t>(row)] * centre[static_cast<std::size_t>(col)];
    }
  }

  // The shape matrix is symmetric, so its decomposition is an eigendecomposition: the singular
  // values are the squared semi-axes and the right vectors are the axis directions. The sign an SVD
  // discards matters here, so each direction is checked against the quadratic form -- a negative one
  // means a hyperboloid, which has no semi-axis to report.
  const Svd shape_svd = ComputeSvd(shape);
  EllipsoidEstimate estimate;
  for (int axis = 0; axis < 3; ++axis) {
    double form = 0.0;
    for (int row = 0; row < 3; ++row) {
      for (int col = 0; col < 3; ++col) {
        form += shape_svd.v(row, axis) * shape(row, col) * shape_svd.v(col, axis);
      }
    }
    if (form <= kNearlyZero) {
      return Refuse(RefusalReason::kNotAnEllipsoid);
    }
    estimate.radii[static_cast<std::size_t>(axis)] =
        std::sqrt(shape_svd.singular_values[static_cast<std::size_t>(axis)]);
    for (int row = 0; row < 3; ++row) {
      estimate.rotation[static_cast<std::size_t>(row * 3 + axis)] = shape_svd.v(row, axis);
    }
  }

  // The axes are only defined up to sign, and an SVD is free to hand back a reflection. Flipping one
  // column makes it a rotation, which is what a caller can hand to a renderer.
  const double determinant =
      estimate.rotation[0] * (estimate.rotation[4] * estimate.rotation[8] -
                              estimate.rotation[5] * estimate.rotation[7]) -
      estimate.rotation[1] * (estimate.rotation[3] * estimate.rotation[8] -
                              estimate.rotation[5] * estimate.rotation[6]) +
      estimate.rotation[2] * (estimate.rotation[3] * estimate.rotation[7] -
                              estimate.rotation[4] * estimate.rotation[6]);
  if (determinant < 0.0) {
    for (int row = 0; row < 3; ++row) {
      estimate.rotation[static_cast<std::size_t>(row * 3 + 2)] *= -1.0;
    }
  }

  Matrix4 normalised_quadric{};
  for (std::size_t i = 0; i < quadric.size(); ++i) {
    normalised_quadric[i] = quadric[i] * normalisation;
  }
  double residual = 0.0;
  for (int index = 0; index < view_count; ++index) {
    const std::size_t i = static_cast<std::size_t>(index);
    residual += ConicDisagreement(conics[i], ProjectQuadric(views[i].projection, normalised_quadric));
  }

  estimate.ok = true;
  estimate.refusal = RefusalReason::kNone;
  estimate.centre = centre;
  estimate.null_space_margin = margin;
  estimate.view_spread_radians = ViewSpread(views, centre);
  estimate.conic_residual = residual / static_cast<double>(view_count);
  return estimate;
}

}  // namespace husk
