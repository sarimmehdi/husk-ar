#ifndef HUSK_CORE_LINALG_H_
#define HUSK_CORE_LINALG_H_

#include <cstddef>
#include <vector>

namespace husk {

// A dense, row-major matrix of doubles.
//
// Deliberately minimal. The solver needs one decomposition of one modestly sized matrix, so a
// linear algebra dependency would cost more in build fragility than it saves in code. Doubles
// throughout: the assembly multiplies camera parameters together and float precision is visibly
// lossy by the time the null vector is read off.
class Matrix {
 public:
  Matrix(int rows, int cols);

  int rows() const { return rows_; }
  int cols() const { return cols_; }

  double& operator()(int row, int col) { return values_[Index(row, col)]; }
  double operator()(int row, int col) const { return values_[Index(row, col)]; }

 private:
  std::size_t Index(int row, int col) const {
    return static_cast<std::size_t>(row) * static_cast<std::size_t>(cols_) +
           static_cast<std::size_t>(col);
  }

  int rows_;
  int cols_;
  std::vector<double> values_;
};

// A singular value decomposition, ordered from the largest singular value to the smallest.
//
// Columns of u and v are the left and right singular vectors respectively, so the original matrix
// is u * diag(singular_values) * v-transpose.
struct Svd {
  Matrix u;
  Matrix v;
  std::vector<double> singular_values;

  // The right singular vector belonging to the smallest singular value.
  //
  // This is the solution of the homogeneous system the estimator builds: the quadric is whatever
  // the assembled matrix maps closest to zero.
  std::vector<double> NullVector() const;

  // How clearly the null direction stands apart, as the second smallest singular value relative to
  // the largest.
  //
  // A healthy value means exactly one direction is near null, so the quadric it encodes is the only
  // one consistent with the views. A value near zero means a second direction is just as close to
  // null and the recovered quadric is an arbitrary pick from a family. That is what happens when the
  // camera barely moves between captures, and it is the signal the estimator refuses on rather than
  // returning a confident answer built on nothing.
  //
  // Measured against the largest singular value rather than against the smallest. The smallest sits
  // at rounding noise whenever the input is close to exact, which makes a ratio between the two
  // smallest enormous even for degenerate geometry -- it reports how precisely the system was
  // solved, not whether the answer was determined.
  double NullSpaceMargin() const;
};

// Decomposes a matrix with at least as many rows as columns.
//
// Uses one-sided Jacobi rotations, which work on the matrix itself rather than on its normal
// equations. Forming the normal equations would square the condition number, and conditioning is
// precisely what the caller is trying to measure.
Svd ComputeSvd(const Matrix& matrix);

}  // namespace husk

#endif  // HUSK_CORE_LINALG_H_
