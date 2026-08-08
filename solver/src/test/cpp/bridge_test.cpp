#include "core/bridge.h"

#include "synthetic.h"

#include <gtest/gtest.h>

#include <array>
#include <vector>

namespace husk {
namespace {

using namespace synthetic;  // NOLINT(build/namespaces) -- fixtures read better unqualified

std::vector<double> PackViews(const std::vector<CameraView>& views) {
  std::vector<double> packed;
  packed.reserve(views.size() * 12);
  for (const CameraView& view : views) {
    packed.insert(packed.end(), view.projection.begin(), view.projection.end());
  }
  return packed;
}

std::vector<double> PackConics(const std::vector<DualConic>& conics) {
  std::vector<double> packed;
  packed.reserve(conics.size() * 6);
  for (const DualConic& conic : conics) {
    packed.insert(packed.end(), {conic.m00, conic.m01, conic.m02, conic.m11, conic.m12, conic.m22});
  }
  return packed;
}

struct Fixture {
  std::vector<double> views;
  std::vector<double> conics;
  int count;
};

Fixture WellSeparatedFixture(const TestEllipsoid& original) {
  const std::vector<CameraView> views = WellSeparatedViews(original.centre, 2.0);
  const std::vector<DualConic> conics = ProjectAll(views, DualQuadricOf(original));
  return {PackViews(views), PackConics(conics), static_cast<int>(views.size())};
}

TEST(EstimateEllipsoidPacked, AgreesWithTheStructuredEstimateSlotForSlot) {
  // The packed entry point exists only so the JNI layer can stay trivial. If it ever disagrees with
  // the structured one, the app is being handed a different answer than the solver produced.
  const TestEllipsoid original{{0.4, 1.2, -0.3}, {0.15, 0.09, 0.2}, Identity3()};
  const std::vector<CameraView> views = WellSeparatedViews(original.centre, 2.0);
  const std::vector<DualConic> conics = ProjectAll(views, DualQuadricOf(original));
  const EllipsoidEstimate expected = EstimateEllipsoid(views, conics);

  const std::vector<double> packed_views = PackViews(views);
  const std::vector<double> packed_conics = PackConics(conics);
  std::array<double, kPackedEstimateSize> out{};
  const int code = EstimateEllipsoidPacked(packed_views.data(), packed_conics.data(),
                                           static_cast<int>(views.size()), out.data());

  ASSERT_EQ(code, 0);
  for (int i = 0; i < 3; ++i) {
    const std::size_t axis = static_cast<std::size_t>(i);
    EXPECT_DOUBLE_EQ(out[axis], expected.centre[axis]) << "centre slot " << i;
    EXPECT_DOUBLE_EQ(out[3 + axis], expected.radii[axis]) << "radii slot " << i;
  }
  for (int i = 0; i < 9; ++i) {
    EXPECT_DOUBLE_EQ(out[static_cast<std::size_t>(6 + i)],
                     expected.rotation[static_cast<std::size_t>(i)])
        << "rotation slot " << i;
  }
  EXPECT_DOUBLE_EQ(out[15], expected.null_space_margin);
  EXPECT_DOUBLE_EQ(out[16], expected.conic_residual);
  EXPECT_DOUBLE_EQ(out[17], expected.view_spread_radians);
}

TEST(EstimateEllipsoidPacked, ReadsEachViewFromItsOwnTwelveDoubleStride) {
  // A stride mistake would still produce a plausible looking ellipsoid, just the wrong one, so this
  // checks against the known answer rather than merely checking that something came back.
  const TestEllipsoid original{{0.4, 1.2, -0.3}, {0.15, 0.09, 0.2}, Identity3()};
  const Fixture fixture = WellSeparatedFixture(original);

  std::array<double, kPackedEstimateSize> out{};
  ASSERT_EQ(EstimateEllipsoidPacked(fixture.views.data(), fixture.conics.data(), fixture.count,
                                    out.data()),
            0);

  EXPECT_NEAR(out[0], 0.4, 1e-6);
  EXPECT_NEAR(out[1], 1.2, 1e-6);
  EXPECT_NEAR(out[2], -0.3, 1e-6);
}

TEST(EstimateEllipsoidPacked, ReportsRefusalsAsTheirReasonCode) {
  const TestEllipsoid original{{0.0, 1.0, 0.0}, {0.1, 0.1, 0.1}, Identity3()};
  const std::vector<CameraView> views = ViewsAround(original.centre, 2.0, {0.0, 1.5}, 0.25);
  const std::vector<double> packed_views = PackViews(views);
  const std::vector<double> packed_conics = PackConics(ProjectAll(views, DualQuadricOf(original)));

  std::array<double, kPackedEstimateSize> out{};
  const int code = EstimateEllipsoidPacked(packed_views.data(), packed_conics.data(), 2, out.data());

  EXPECT_EQ(code, static_cast<int>(RefusalReason::kTooFewViews));
  EXPECT_NE(code, 0) << "zero has to mean success, so no refusal may share it";
}

TEST(EstimateEllipsoidPacked, StillReportsTheMarginWhenItRefusesOnConditioning) {
  // The debug overlay shows why a capture was rejected. A refusal that zeroed its diagnostics would
  // leave nothing to show.
  const TestEllipsoid original{{0.0, 1.0, 0.0}, {0.1, 0.1, 0.1}, Identity3()};
  const std::vector<CameraView> views =
      ViewsAround(original.centre, 2.0, {0.0, 0.00025, 0.0005, 0.00075, 0.001}, 0.001);
  const std::vector<double> packed_views = PackViews(views);
  const std::vector<double> packed_conics = PackConics(ProjectAll(views, DualQuadricOf(original)));

  std::array<double, kPackedEstimateSize> out{};
  const int code = EstimateEllipsoidPacked(packed_views.data(), packed_conics.data(), 5, out.data());

  EXPECT_EQ(code, static_cast<int>(RefusalReason::kNullSpaceAmbiguous));
  EXPECT_GT(out[15], 0.0);
  EXPECT_LT(out[15], kDefaultNullSpaceMargin);
}

TEST(EstimateEllipsoidPacked, HoldsTheRefusalCodesKotlinMirrors) {
  // Kotlin repeats these numbers, because an enum crosses JNI as nothing richer than an integer.
  // Reordering RefusalReason silently relabels every refusal the app reports, so the mapping is
  // pinned to literals on both sides rather than derived from declaration order.
  EXPECT_EQ(static_cast<int>(RefusalReason::kNone), kCodeResolved);
  EXPECT_EQ(static_cast<int>(RefusalReason::kTooFewViews), kCodeTooFewViews);
  EXPECT_EQ(static_cast<int>(RefusalReason::kInconsistentInput), kCodeInconsistentInput);
  EXPECT_EQ(static_cast<int>(RefusalReason::kDegenerateConic), kCodeDegenerateConic);
  EXPECT_EQ(static_cast<int>(RefusalReason::kNullSpaceAmbiguous), kCodeNullSpaceAmbiguous);
  EXPECT_EQ(static_cast<int>(RefusalReason::kNotAnEllipsoid), kCodeNotAnEllipsoid);
}

TEST(EstimateEllipsoidPacked, RefusesNullBuffersRatherThanDereferencingThem) {
  // Reached from JNI, where a failed array pin hands back null. Crashing the host process is not an
  // acceptable way to report that.
  std::array<double, kPackedEstimateSize> out{};
  const std::vector<double> values(60, 0.0);

  EXPECT_EQ(EstimateEllipsoidPacked(nullptr, values.data(), 5, out.data()),
            static_cast<int>(RefusalReason::kInconsistentInput));
  EXPECT_EQ(EstimateEllipsoidPacked(values.data(), nullptr, 5, out.data()),
            static_cast<int>(RefusalReason::kInconsistentInput));
  EXPECT_EQ(EstimateEllipsoidPacked(values.data(), values.data(), 5, nullptr),
            static_cast<int>(RefusalReason::kInconsistentInput));
}

TEST(EstimateEllipsoidPacked, RefusesANegativeViewCount) {
  std::array<double, kPackedEstimateSize> out{};
  const std::vector<double> values(60, 0.0);

  EXPECT_EQ(EstimateEllipsoidPacked(values.data(), values.data(), -1, out.data()),
            static_cast<int>(RefusalReason::kInconsistentInput));
}

TEST(EstimateEllipsoidPacked, LeavesTheOutputZeroedWhenItRefuses) {
  std::array<double, kPackedEstimateSize> out{};
  out.fill(7.0);
  const std::vector<double> values(60, 0.0);

  EXPECT_NE(EstimateEllipsoidPacked(values.data(), values.data(), -1, out.data()), 0);
  for (std::size_t i = 0; i < out.size(); ++i) {
    EXPECT_DOUBLE_EQ(out[i], 0.0) << "stale slot " << i << " could be read as a real answer";
  }
}

}  // namespace
}  // namespace husk
