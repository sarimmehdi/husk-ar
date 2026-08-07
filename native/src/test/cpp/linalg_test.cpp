#include "core/linalg.h"

#include <gtest/gtest.h>

#include <cmath>

namespace husk {
namespace {

constexpr double kTolerance = 1e-9;

TEST(Matrix, StoresValuesInRowMajorOrder) {
  Matrix m(2, 3);
  m(0, 0) = 1.0;
  m(0, 2) = 3.0;
  m(1, 1) = 5.0;

  EXPECT_EQ(m.rows(), 2);
  EXPECT_EQ(m.cols(), 3);
  EXPECT_DOUBLE_EQ(m(0, 0), 1.0);
  EXPECT_DOUBLE_EQ(m(0, 2), 3.0);
  EXPECT_DOUBLE_EQ(m(1, 1), 5.0);
  EXPECT_DOUBLE_EQ(m(0, 1), 0.0) << "a fresh matrix starts at zero";
}

TEST(Svd, RecoversSingularValuesOfADiagonalMatrix) {
  Matrix m(3, 3);
  m(0, 0) = 5.0;
  m(1, 1) = 3.0;
  m(2, 2) = 1.0;

  const Svd svd = ComputeSvd(m);

  EXPECT_NEAR(svd.singular_values[0], 5.0, kTolerance);
  EXPECT_NEAR(svd.singular_values[1], 3.0, kTolerance);
  EXPECT_NEAR(svd.singular_values[2], 1.0, kTolerance);
}

TEST(Svd, OrdersSingularValuesFromLargestToSmallest) {
  Matrix m(3, 3);
  m(0, 0) = 1.0;
  m(1, 1) = 7.0;
  m(2, 2) = 4.0;

  const Svd svd = ComputeSvd(m);

  EXPECT_GE(svd.singular_values[0], svd.singular_values[1]);
  EXPECT_GE(svd.singular_values[1], svd.singular_values[2]);
}

TEST(Svd, FindsTheNullVectorOfARankDeficientMatrix) {
  // The solver's whole method is: build a matrix whose null vector is the quadric, then read it off.
  // Here the third column is unused, so the null vector must be the third basis vector.
  Matrix m(3, 3);
  m(0, 0) = 2.0;
  m(1, 1) = 3.0;

  const Svd svd = ComputeSvd(m);
  const std::vector<double> null_vector = svd.NullVector();

  EXPECT_NEAR(std::abs(null_vector[2]), 1.0, 1e-7);
  EXPECT_NEAR(null_vector[0], 0.0, 1e-7);
  EXPECT_NEAR(null_vector[1], 0.0, 1e-7);
}

TEST(Svd, FindsTheNullVectorWhenTheMatrixIsTallerThanItIsWide) {
  // Every real call is overdetermined: 6 rows per view against 10 + n unknowns.
  Matrix m(6, 3);
  for (int row = 0; row < 6; ++row) {
    m(row, 0) = static_cast<double>(row + 1);
    m(row, 1) = static_cast<double>(2 * (row + 1));
  }

  const std::vector<double> null_vector = ComputeSvd(m).NullVector();

  EXPECT_NEAR(std::abs(null_vector[2]), 1.0, 1e-7);
}

TEST(Svd, ReconstructsTheOriginalMatrixFromItsFactors) {
  // A decomposition that produces plausible singular values but inconsistent factors would still
  // pass the tests above, so check that the pieces multiply back together.
  Matrix m(4, 3);
  double value = 0.0;
  for (int row = 0; row < 4; ++row) {
    for (int col = 0; col < 3; ++col) {
      value += 1.3;
      m(row, col) = std::sin(value) * 3.0;
    }
  }

  const Svd svd = ComputeSvd(m);

  for (int row = 0; row < 4; ++row) {
    for (int col = 0; col < 3; ++col) {
      double reconstructed = 0.0;
      for (int k = 0; k < 3; ++k) {
        reconstructed += svd.u(row, k) * svd.singular_values[k] * svd.v(col, k);
      }
      EXPECT_NEAR(reconstructed, m(row, col), 1e-9) << "at (" << row << ", " << col << ")";
    }
  }
}

TEST(Svd, ReportsTheSecondSmallestSingularValueRelativeToTheLargest) {
  // This ratio is the guard. One near-null direction among otherwise healthy ones is a determined
  // system; a second direction that is also near null means the answer is an arbitrary pick from a
  // family, and the estimator has to refuse.
  Matrix one_null_direction(3, 3);
  one_null_direction(0, 0) = 4.0;
  one_null_direction(1, 1) = 2.0;
  one_null_direction(2, 2) = 0.001;

  Matrix two_null_directions(3, 3);
  two_null_directions(0, 0) = 4.0;
  two_null_directions(1, 1) = 0.001;
  two_null_directions(2, 2) = 0.001;

  EXPECT_NEAR(ComputeSvd(one_null_direction).NullSpaceMargin(), 0.5, 1e-9);
  EXPECT_NEAR(ComputeSvd(two_null_directions).NullSpaceMargin(), 0.00025, 1e-9);
}

TEST(Svd, ScoresConditioningIndependentlyOfHowExactlyTheSystemWasSolved) {
  // The reason the margin is measured against the largest singular value and not against the
  // smallest. Both matrices below are equally ambiguous -- two directions near null -- but their
  // smallest singular values differ by nine orders of magnitude. A ratio between the two smallest
  // would call the second one vastly better conditioned, which is exactly backwards.
  Matrix ambiguous(3, 3);
  ambiguous(0, 0) = 4.0;
  ambiguous(1, 1) = 1e-9;
  ambiguous(2, 2) = 1e-9;

  Matrix ambiguous_solved_more_exactly(3, 3);
  ambiguous_solved_more_exactly(0, 0) = 4.0;
  ambiguous_solved_more_exactly(1, 1) = 1e-9;
  ambiguous_solved_more_exactly(2, 2) = 1e-18;

  EXPECT_NEAR(ComputeSvd(ambiguous).NullSpaceMargin(),
              ComputeSvd(ambiguous_solved_more_exactly).NullSpaceMargin(), 1e-15);
}

}  // namespace
}  // namespace husk
