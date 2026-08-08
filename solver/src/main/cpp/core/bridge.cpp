#include "core/bridge.h"

#include <cstddef>
#include <vector>

#include "core/lfd.h"

namespace husk {
namespace {

void Write(const EllipsoidEstimate& estimate, double* out) {
  for (int axis = 0; axis < 3; ++axis) {
    const std::size_t i = static_cast<std::size_t>(axis);
    out[axis] = estimate.centre[i];
    out[3 + axis] = estimate.radii[i];
  }
  for (int cell = 0; cell < 9; ++cell) {
    out[6 + cell] = estimate.rotation[static_cast<std::size_t>(cell)];
  }
  out[15] = estimate.null_space_margin;
  out[16] = estimate.conic_residual;
  out[17] = estimate.view_spread_radians;
}

}  // namespace

int EstimateEllipsoidPacked(const double* projections, const double* conics, int view_count,
                            double* out) {
  if (out == nullptr) {
    return static_cast<int>(RefusalReason::kInconsistentInput);
  }
  for (int slot = 0; slot < kPackedEstimateSize; ++slot) {
    out[slot] = 0.0;
  }
  if (projections == nullptr || conics == nullptr || view_count < 0) {
    return static_cast<int>(RefusalReason::kInconsistentInput);
  }

  std::vector<CameraView> views(static_cast<std::size_t>(view_count));
  std::vector<DualConic> parsed(static_cast<std::size_t>(view_count));
  for (int index = 0; index < view_count; ++index) {
    const std::size_t view = static_cast<std::size_t>(index);
    const std::size_t projection_base = view * kPackedProjectionStride;
    for (int cell = 0; cell < kPackedProjectionStride; ++cell) {
      views[view].projection[static_cast<std::size_t>(cell)] =
          projections[projection_base + static_cast<std::size_t>(cell)];
    }
    const std::size_t conic_base = view * kPackedConicStride;
    parsed[view] = DualConic{conics[conic_base],     conics[conic_base + 1],
                             conics[conic_base + 2], conics[conic_base + 3],
                             conics[conic_base + 4], conics[conic_base + 5]};
  }

  const EllipsoidEstimate estimate = EstimateEllipsoid(views, parsed);
  Write(estimate, out);
  return static_cast<int>(estimate.refusal);
}

}  // namespace husk
