#include "core/linalg.h"

#include <algorithm>
#include <cmath>
#include <limits>
#include <numeric>

namespace husk {
namespace {

// One-sided Jacobi sweeps stop once every column pair is orthogonal to this relative precision.
constexpr double kOrthogonalityTolerance = 1e-15;
constexpr int kMaxSweeps = 60;

}  // namespace

Matrix::Matrix(int rows, int cols)
    : rows_(rows),
      cols_(cols),
      values_(static_cast<std::size_t>(rows) * static_cast<std::size_t>(cols), 0.0) {}

std::vector<double> Svd::NullVector() const {
  const int last = v.cols() - 1;
  std::vector<double> null_vector(static_cast<std::size_t>(v.rows()));
  for (int row = 0; row < v.rows(); ++row) {
    null_vector[static_cast<std::size_t>(row)] = v(row, last);
  }
  return null_vector;
}

double Svd::NullSpaceMargin() const {
  const std::size_t count = singular_values.size();
  if (count < 2) {
    return std::numeric_limits<double>::infinity();
  }
  const double largest = singular_values[0];
  if (largest <= 0.0) {
    return 0.0;
  }
  return singular_values[count - 2] / largest;
}

Svd ComputeSvd(const Matrix& matrix) {
  const int rows = matrix.rows();
  const int cols = matrix.cols();

  // Work on a copy of the columns; each rotation orthogonalises a pair of them in place. When the
  // sweeps finish, the column norms are the singular values and the accumulated rotations are v.
  Matrix work = matrix;
  Matrix v(cols, cols);
  for (int i = 0; i < cols; ++i) {
    v(i, i) = 1.0;
  }

  for (int sweep = 0; sweep < kMaxSweeps; ++sweep) {
    bool rotated = false;
    for (int p = 0; p < cols - 1; ++p) {
      for (int q = p + 1; q < cols; ++q) {
        double alpha = 0.0;
        double beta = 0.0;
        double gamma = 0.0;
        for (int row = 0; row < rows; ++row) {
          alpha += work(row, p) * work(row, p);
          beta += work(row, q) * work(row, q);
          gamma += work(row, p) * work(row, q);
        }
        if (std::abs(gamma) <= kOrthogonalityTolerance * std::sqrt(alpha * beta)) {
          continue;
        }
        rotated = true;

        const double zeta = (beta - alpha) / (2.0 * gamma);
        const double t = (zeta >= 0.0 ? 1.0 : -1.0) / (std::abs(zeta) + std::sqrt(1.0 + zeta * zeta));
        const double c = 1.0 / std::sqrt(1.0 + t * t);
        const double s = c * t;

        for (int row = 0; row < rows; ++row) {
          const double wp = work(row, p);
          const double wq = work(row, q);
          work(row, p) = c * wp - s * wq;
          work(row, q) = s * wp + c * wq;
        }
        for (int row = 0; row < cols; ++row) {
          const double vp = v(row, p);
          const double vq = v(row, q);
          v(row, p) = c * vp - s * vq;
          v(row, q) = s * vp + c * vq;
        }
      }
    }
    if (!rotated) {
      break;
    }
  }

  std::vector<double> norms(static_cast<std::size_t>(cols), 0.0);
  for (int col = 0; col < cols; ++col) {
    double sum = 0.0;
    for (int row = 0; row < rows; ++row) {
      sum += work(row, col) * work(row, col);
    }
    norms[static_cast<std::size_t>(col)] = std::sqrt(sum);
  }

  std::vector<int> order(static_cast<std::size_t>(cols));
  std::iota(order.begin(), order.end(), 0);
  std::sort(order.begin(), order.end(), [&norms](int a, int b) {
    return norms[static_cast<std::size_t>(a)] > norms[static_cast<std::size_t>(b)];
  });

  Svd result{Matrix(rows, cols), Matrix(cols, cols), std::vector<double>(static_cast<std::size_t>(cols))};
  for (int col = 0; col < cols; ++col) {
    const int source = order[static_cast<std::size_t>(col)];
    const double norm = norms[static_cast<std::size_t>(source)];
    result.singular_values[static_cast<std::size_t>(col)] = norm;

    // A vanishing column carries no direction, so leave the corresponding left vector at zero
    // rather than dividing by it. The right vector still carries the null direction, which is the
    // one the caller wants.
    if (norm > 0.0) {
      for (int row = 0; row < rows; ++row) {
        result.u(row, col) = work(row, source) / norm;
      }
    }
    for (int row = 0; row < cols; ++row) {
      result.v(row, col) = v(row, source);
    }
  }
  return result;
}

}  // namespace husk
