#include "core/lfd.h"

#include "synthetic.h"

#include <gtest/gtest.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <vector>

namespace husk {
namespace {

using namespace synthetic;  // NOLINT(build/namespaces) -- fixtures read better unqualified

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
