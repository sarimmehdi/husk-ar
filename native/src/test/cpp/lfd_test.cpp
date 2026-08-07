#include "core/lfd.h"

#include <gtest/gtest.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <vector>

namespace husk {
namespace {

// The fixtures below are built here rather than loaded from a dataset. The projections are exact, so
// a correct solver has to reproduce the ellipsoid to machine precision, and there is no third-party
// data to redistribute from a public repository.

struct TestEllipsoid {
  std::array<double, 3> centre;
  std::array<double, 3> radii;
  std::array<double, 9> rotation;  // row-major, columns are the axes
};

std::array<double, 9> Identity3() { return {1, 0, 0, 0, 1, 0, 0, 0, 1}; }

std::array<double, 9> RotationAboutY(double radians) {
  const double c = std::cos(radians);
  const double s = std::sin(radians);
  return {c, 0, s, 0, 1, 0, -s, 0, c};
}

// Q = T diag(a^2, b^2, c^2, -1) T', with T = [[R, t], [0, 1]].
std::array<double, 16> DualQuadricOf(const TestEllipsoid& e) {
  std::array<double, 16> q{};
  for (int row = 0; row < 3; ++row) {
    for (int col = 0; col < 3; ++col) {
      double sum = 0.0;
      for (int axis = 0; axis < 3; ++axis) {
        const double radius = e.radii[static_cast<std::size_t>(axis)];
        sum += radius * radius * e.rotation[static_cast<std::size_t>(row * 3 + axis)] *
               e.rotation[static_cast<std::size_t>(col * 3 + axis)];
      }
      q[static_cast<std::size_t>(row * 4 + col)] =
          sum - e.centre[static_cast<std::size_t>(row)] * e.centre[static_cast<std::size_t>(col)];
    }
    q[static_cast<std::size_t>(row * 4 + 3)] = -e.centre[static_cast<std::size_t>(row)];
    q[static_cast<std::size_t>(3 * 4 + row)] = -e.centre[static_cast<std::size_t>(row)];
  }
  q[15] = -1.0;
  return q;
}

std::array<double, 3> Normalise(std::array<double, 3> v) {
  const double length = std::sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
  return {v[0] / length, v[1] / length, v[2] / length};
}

std::array<double, 3> Cross(const std::array<double, 3>& a, const std::array<double, 3>& b) {
  return {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
}

// A pinhole camera at `eye` looking at `target`, in the computer vision convention: +Z forward,
// +Y down, +X right. Returns the 3x4 projection matrix P = K [R | t].
CameraView LookAt(const std::array<double, 3>& eye, const std::array<double, 3>& target) {
  constexpr double kFocalLength = 800.0;
  constexpr double kPrincipalX = 640.0;
  constexpr double kPrincipalY = 360.0;

  const std::array<double, 3> forward =
      Normalise({target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]});
  const std::array<double, 3> world_up = {0.0, 1.0, 0.0};
  const std::array<double, 3> right = Normalise(Cross(forward, world_up));
  const std::array<double, 3> down = Cross(forward, right);
  const std::array<std::array<double, 3>, 3> r = {right, down, forward};

  CameraView view{};
  for (int row = 0; row < 3; ++row) {
    double translation = 0.0;
    for (int col = 0; col < 3; ++col) {
      translation -= r[static_cast<std::size_t>(row)][static_cast<std::size_t>(col)] *
                     eye[static_cast<std::size_t>(col)];
    }
    const double focal = row < 2 ? kFocalLength : 1.0;
    const double principal = row == 0 ? kPrincipalX : (row == 1 ? kPrincipalY : 0.0);
    const double forward_offset = -(r[2][0] * eye[0] + r[2][1] * eye[1] + r[2][2] * eye[2]);
    for (int col = 0; col < 3; ++col) {
      view.projection[static_cast<std::size_t>(row * 4 + col)] =
          focal * r[static_cast<std::size_t>(row)][static_cast<std::size_t>(col)] +
          principal * r[2][static_cast<std::size_t>(col)];
    }
    view.projection[static_cast<std::size_t>(row * 4 + 3)] =
        focal * translation + principal * forward_offset;
  }
  return view;
}

// C = P Q P', the exact projection of a dual quadric.
DualConic Project(const CameraView& view, const std::array<double, 16>& quadric) {
  std::array<double, 12> qp{};  // Q * P', 4x3
  for (int row = 0; row < 4; ++row) {
    for (int col = 0; col < 3; ++col) {
      double sum = 0.0;
      for (int k = 0; k < 4; ++k) {
        sum += quadric[static_cast<std::size_t>(row * 4 + k)] *
               view.projection[static_cast<std::size_t>(col * 4 + k)];
      }
      qp[static_cast<std::size_t>(row * 3 + col)] = sum;
    }
  }
  std::array<double, 9> c{};
  for (int row = 0; row < 3; ++row) {
    for (int col = 0; col < 3; ++col) {
      double sum = 0.0;
      for (int k = 0; k < 4; ++k) {
        sum += view.projection[static_cast<std::size_t>(row * 4 + k)] *
               qp[static_cast<std::size_t>(k * 3 + col)];
      }
      c[static_cast<std::size_t>(row * 3 + col)] = sum;
    }
  }
  return DualConic{c[0], c[1], c[2], c[4], c[5], c[8]};
}

// Cameras spaced around the target. `elevation_swing` has to shrink along with the azimuths for the
// views to genuinely converge: holding it fixed while narrowing the azimuths leaves the cameras a
// quarter radian apart vertically, which is well separated geometry wearing a degenerate label.
std::vector<CameraView> ViewsAround(const std::array<double, 3>& target, double distance,
                                    const std::vector<double>& azimuths, double elevation_swing) {
  std::vector<CameraView> views;
  views.reserve(azimuths.size());
  for (std::size_t i = 0; i < azimuths.size(); ++i) {
    const double azimuth = azimuths[i];
    const double elevation = elevation_swing * std::sin(static_cast<double>(i));
    const std::array<double, 3> eye = {
        target[0] + distance * std::cos(elevation) * std::cos(azimuth),
        target[1] + distance * std::sin(elevation),
        target[2] + distance * std::cos(elevation) * std::sin(azimuth),
    };
    views.push_back(LookAt(eye, target));
  }
  return views;
}

std::vector<CameraView> WellSeparatedViews(const std::array<double, 3>& target, double distance) {
  return ViewsAround(target, distance, {0.0, 1.1, 2.3, 3.6, 4.9}, 0.25);
}

std::vector<DualConic> ProjectAll(const std::vector<CameraView>& views,
                                  const std::array<double, 16>& quadric) {
  std::vector<DualConic> conics;
  conics.reserve(views.size());
  for (const CameraView& view : views) {
    conics.push_back(Project(view, quadric));
  }
  return conics;
}

struct Ellipse {
  double centre_x;
  double centre_y;
  double major;
  double minor;
  double angle;
};

Ellipse EllipseOf(const DualConic& c) {
  const double scale = -1.0 / c.m22;
  const double centre_x = -c.m02 * scale;
  const double centre_y = -c.m12 * scale;
  const double xx = c.m00 * scale + centre_x * centre_x;
  const double xy = c.m01 * scale + centre_x * centre_y;
  const double yy = c.m11 * scale + centre_y * centre_y;
  const double mean = 0.5 * (xx + yy);
  const double gap = std::sqrt(0.25 * (xx - yy) * (xx - yy) + xy * xy);
  return {centre_x, centre_y, std::sqrt(mean + gap), std::sqrt(std::max(mean - gap, 0.0)),
          0.5 * std::atan2(2.0 * xy, xx - yy)};
}

DualConic ConicOf(const Ellipse& e) {
  const double c = std::cos(e.angle);
  const double s = std::sin(e.angle);
  const double major = e.major * e.major;
  const double minor = e.minor * e.minor;
  const double xx = major * c * c + minor * s * s;
  const double xy = (major - minor) * c * s;
  const double yy = major * s * s + minor * c * c;
  return {xx - e.centre_x * e.centre_x, xy - e.centre_x * e.centre_y, -e.centre_x,
          yy - e.centre_y * e.centre_y, -e.centre_y,                  -1.0};
}

// Displaces every traced ellipse by about a pixel, the way a fingertip would.
std::vector<DualConic> WithTracingError(std::vector<DualConic> conics) {
  int step = 0;
  for (DualConic& conic : conics) {
    Ellipse ellipse = EllipseOf(conic);
    ellipse.centre_x += std::sin(3.7 * static_cast<double>(++step));
    ellipse.centre_y += std::sin(3.7 * static_cast<double>(++step));
    ellipse.major += 0.5 * std::sin(3.7 * static_cast<double>(++step));
    ellipse.minor += 0.5 * std::sin(3.7 * static_cast<double>(++step));
    conic = ConicOf(ellipse);
  }
  return conics;
}

void ExpectRadiiMatch(const std::array<double, 3>& expected, const std::array<double, 3>& actual,
                      double tolerance) {
  std::array<double, 3> a = expected;
  std::array<double, 3> b = actual;
  std::sort(a.begin(), a.end());
  std::sort(b.begin(), b.end());
  for (int i = 0; i < 3; ++i) {
    EXPECT_NEAR(a[static_cast<std::size_t>(i)], b[static_cast<std::size_t>(i)], tolerance);
  }
}

TEST(EstimateEllipsoid, RecoversAKnownEllipsoidFromExactProjections) {
  const TestEllipsoid original{{0.4, 1.2, -0.3}, {0.15, 0.09, 0.2}, Identity3()};
  const std::vector<CameraView> views = WellSeparatedViews(original.centre, 2.0);

  const EllipsoidEstimate estimate =
      EstimateEllipsoid(views, ProjectAll(views, DualQuadricOf(original)));

  ASSERT_TRUE(estimate.ok) << "an exact, well separated set of views must resolve";
  EXPECT_NEAR(estimate.centre[0], original.centre[0], 1e-6);
  EXPECT_NEAR(estimate.centre[1], original.centre[1], 1e-6);
  EXPECT_NEAR(estimate.centre[2], original.centre[2], 1e-6);
  ExpectRadiiMatch(original.radii, estimate.radii, 1e-6);
}

TEST(EstimateEllipsoid, PlacesTheCentreOnTheObjectRatherThanItsReflection) {
  // The reference implementation reads the quadric's last column directly, which puts the centre at
  // its own negation. With a centre well away from the origin the two answers are far apart, so this
  // fails loudly if the sign is ever reintroduced.
  const TestEllipsoid original{{1.5, -2.0, 3.0}, {0.1, 0.1, 0.1}, Identity3()};
  const std::vector<CameraView> views = WellSeparatedViews(original.centre, 2.5);

  const EllipsoidEstimate estimate =
      EstimateEllipsoid(views, ProjectAll(views, DualQuadricOf(original)));

  ASSERT_TRUE(estimate.ok);
  EXPECT_NEAR(estimate.centre[0], 1.5, 1e-6);
  EXPECT_NEAR(estimate.centre[1], -2.0, 1e-6);
  EXPECT_NEAR(estimate.centre[2], 3.0, 1e-6);
}

TEST(EstimateEllipsoid, RecoversARotatedEllipsoid) {
  const TestEllipsoid original{{0.0, 0.5, 0.0}, {0.25, 0.08, 0.14}, RotationAboutY(0.7)};
  const std::vector<CameraView> views = WellSeparatedViews(original.centre, 2.0);

  const EllipsoidEstimate estimate =
      EstimateEllipsoid(views, ProjectAll(views, DualQuadricOf(original)));

  ASSERT_TRUE(estimate.ok);
  ExpectRadiiMatch(original.radii, estimate.radii, 1e-6);
}

TEST(EstimateEllipsoid, RecoversTheAxisDirectionsOfARotatedEllipsoid) {
  // Radii alone would pass even if the axes came back pointing anywhere, so check the frame itself.
  // The longest axis of this ellipsoid is its local x, turned 0.7 radians about y.
  const TestEllipsoid original{{0.0, 0.5, 0.0}, {0.25, 0.08, 0.14}, RotationAboutY(0.7)};
  const std::vector<CameraView> views = WellSeparatedViews(original.centre, 2.0);

  const EllipsoidEstimate estimate =
      EstimateEllipsoid(views, ProjectAll(views, DualQuadricOf(original)));

  ASSERT_TRUE(estimate.ok);
  int longest = 0;
  for (int axis = 1; axis < 3; ++axis) {
    if (estimate.radii[static_cast<std::size_t>(axis)] >
        estimate.radii[static_cast<std::size_t>(longest)]) {
      longest = axis;
    }
  }
  // An axis is a direction without a preferred end, so compare the line and not the arrow.
  const std::size_t column = static_cast<std::size_t>(longest);
  const double alignment = std::abs(estimate.rotation[column] * std::cos(0.7) +
                                    estimate.rotation[6 + column] * (-std::sin(0.7)));
  EXPECT_NEAR(alignment, 1.0, 1e-6);
}

TEST(EstimateEllipsoid, ReturnsAProperRotationRatherThanAReflection) {
  const TestEllipsoid original{{0.0, 0.5, 0.0}, {0.25, 0.08, 0.14}, RotationAboutY(0.7)};
  const std::vector<CameraView> views = WellSeparatedViews(original.centre, 2.0);

  const std::array<double, 9> r =
      EstimateEllipsoid(views, ProjectAll(views, DualQuadricOf(original))).rotation;

  const double determinant = r[0] * (r[4] * r[8] - r[5] * r[7]) -
                             r[1] * (r[3] * r[8] - r[5] * r[6]) +
                             r[2] * (r[3] * r[7] - r[4] * r[6]);
  EXPECT_NEAR(determinant, 1.0, 1e-9) << "a renderer handed a reflection would draw a mirrored pose";
}

TEST(EstimateEllipsoid, ReportsANegligibleResidualForExactInput) {
  const TestEllipsoid original{{0.1, 0.9, 0.2}, {0.12, 0.12, 0.18}, Identity3()};
  const std::vector<CameraView> views = WellSeparatedViews(original.centre, 2.0);

  const EllipsoidEstimate estimate =
      EstimateEllipsoid(views, ProjectAll(views, DualQuadricOf(original)));

  ASSERT_TRUE(estimate.ok);
  EXPECT_LT(estimate.conic_residual, 1e-6) << "exact conics must reproject onto themselves";
}

TEST(EstimateEllipsoid, StaysWithinAFewPercentWhenTheTracingIsOffByAPixel) {
  // Every real capture is traced by hand. Well separated views absorb that: a pixel of error moves
  // the radii by a couple of percent rather than by a factor.
  const TestEllipsoid original{{0.0, 1.0, 0.0}, {0.1, 0.1, 0.1}, Identity3()};
  const std::vector<CameraView> views = WellSeparatedViews(original.centre, 2.0);

  const EllipsoidEstimate estimate =
      EstimateEllipsoid(views, WithTracingError(ProjectAll(views, DualQuadricOf(original))));

  ASSERT_TRUE(estimate.ok);
  for (int axis = 0; axis < 3; ++axis) {
    EXPECT_NEAR(estimate.radii[static_cast<std::size_t>(axis)], 0.1, 0.005);
  }
}

TEST(EstimateEllipsoid, RefusesFewerThanThreeViews) {
  const TestEllipsoid original{{0.0, 1.0, 0.0}, {0.1, 0.1, 0.1}, Identity3()};
  const std::vector<CameraView> views = ViewsAround(original.centre, 2.0, {0.0, 1.5}, 0.25);

  const EllipsoidEstimate estimate =
      EstimateEllipsoid(views, ProjectAll(views, DualQuadricOf(original)));

  EXPECT_FALSE(estimate.ok);
  EXPECT_EQ(estimate.refusal, RefusalReason::kTooFewViews);
}

TEST(EstimateEllipsoid, RefusesWhenTheCameraBarelyMovedBetweenViews) {
  // Five captures from within a couple of millimetres of each other, two metres out. More than one
  // ellipsoid fits, and the app has to say so rather than draw whichever one the arithmetic happened
  // to land on. The input here is exact, so the geometry alone is what makes this unanswerable.
  const TestEllipsoid original{{0.0, 1.0, 0.0}, {0.1, 0.1, 0.1}, Identity3()};
  const std::vector<CameraView> views =
      ViewsAround(original.centre, 2.0, {0.0, 0.00025, 0.0005, 0.00075, 0.001}, 0.001);

  const EllipsoidEstimate estimate =
      EstimateEllipsoid(views, ProjectAll(views, DualQuadricOf(original)));

  EXPECT_FALSE(estimate.ok);
  EXPECT_EQ(estimate.refusal, RefusalReason::kNullSpaceAmbiguous);
}

TEST(EstimateEllipsoid, LeavesHealthyGeometryClearOfTheRefusalThreshold) {
  // The threshold is only useful if ordinary captures sit well above it. This pins the headroom, so
  // a change that quietly degrades conditioning surfaces here rather than as refusals in the field.
  const TestEllipsoid original{{0.0, 1.0, 0.0}, {0.1, 0.1, 0.1}, Identity3()};
  const std::vector<CameraView> views = WellSeparatedViews(original.centre, 2.0);

  const EllipsoidEstimate estimate =
      EstimateEllipsoid(views, ProjectAll(views, DualQuadricOf(original)));

  EXPECT_GT(estimate.null_space_margin, 5.0 * kDefaultNullSpaceMargin);
}

TEST(EstimateEllipsoid, ReportsHowFarTheViewsSpreadAroundTheObject) {
  // The margin catches geometry that is unanswerable; it does not grade geometry that is merely
  // poor, and neither does the residual. This angle does, and it is what capture guidance is built
  // on.
  const TestEllipsoid original{{0.0, 1.0, 0.0}, {0.1, 0.1, 0.1}, Identity3()};

  const std::vector<CameraView> wide = WellSeparatedViews(original.centre, 2.0);
  const EllipsoidEstimate spread_out =
      EstimateEllipsoid(wide, ProjectAll(wide, DualQuadricOf(original)));

  const std::vector<CameraView> narrow =
      ViewsAround(original.centre, 2.0, {0.0, 0.02, 0.04, 0.06, 0.08}, 0.02);
  const EllipsoidEstimate bunched =
      EstimateEllipsoid(narrow, ProjectAll(narrow, DualQuadricOf(original)));

  ASSERT_TRUE(spread_out.ok);
  ASSERT_TRUE(bunched.ok);
  EXPECT_GT(spread_out.view_spread_radians, 1.0);
  EXPECT_LT(bunched.view_spread_radians, 0.15)
      << "views this bunched resolve numerically but are barely triangulated";
}

TEST(EstimateEllipsoid, RefusesWhenTheViewAndConicCountsDisagree) {
  const std::vector<CameraView> views = ViewsAround({0.0, 1.0, 0.0}, 2.0, {0.0, 1.2, 2.4}, 0.25);

  const EllipsoidEstimate estimate = EstimateEllipsoid(views, {});

  EXPECT_FALSE(estimate.ok);
  EXPECT_EQ(estimate.refusal, RefusalReason::kInconsistentInput);
}

}  // namespace
}  // namespace husk
