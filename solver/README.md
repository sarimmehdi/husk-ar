# solver

The ellipsoid solver: portable C++17 with no third-party dependencies, a thin JNI shim, and a Kotlin
API over it.

Given several camera poses and the ellipse the object's silhouette traces in each image, it recovers
the 3D ellipsoid — closed form, no initialisation, no iteration. It either resolves or refuses.

## Running the tests on the host

The tests build and run on the development machine in well under a second, which is the reason this
layer exists separately from the Android build. Nothing here needs a device or an emulator.

```bash
ANDROID_NDK_ROOT=~/Library/Android/sdk/ndk/29.0.14206865 \
  ~/Library/Android/sdk/cmake/3.31.4/bin/cmake \
  -S solver/src/test/cpp -B build/native-host -DCMAKE_BUILD_TYPE=Debug
```

```bash
~/Library/Android/sdk/cmake/3.31.4/bin/cmake --build build/native-host && ./build/native-host/husk_core_tests
```

`ANDROID_NDK_ROOT` is how GoogleTest is located — the NDK bundles a copy, so the tests need no
network and no vendored dependency. Point `-DHUSK_GOOGLETEST_DIR` at a checkout instead if you would
rather supply your own. The library builds with `-Wall -Wextra -Wpedantic -Werror`.

## Layout

| Path | Contents |
| --- | --- |
| `src/main/cpp/core/linalg.*` | Dense matrix and a one-sided Jacobi SVD |
| `src/main/cpp/core/lfd.*` | The estimator |
| `src/main/cpp/core/bridge.*` | The estimator behind a flat, pointer-and-length signature |
| `src/main/cpp/jni/` | The JNI shim, built only for Android |
| `src/main/java/` | `EllipsoidSolver`, and the conversion to and from geometry types |
| `src/test/cpp/` | Host tests and their synthetic fixtures |
| `src/test/java/` | JVM tests for the conversion, with the native call replaced |
| `src/androidTest/java/` | The one test that needs a device |

## What is tested where

Three layers, and the split is deliberate — each covers what the layer below cannot.

The **C++ host tests** cover the arithmetic. They need no device and no emulator and run in
milliseconds, which is what makes it practical to develop the solver by measurement.

The **JVM tests** cover the conversion on either side of the native call, with the call itself
replaced by a fake. Building projection matrices, packing outlines, unpacking a quadric into a centre
and radii — that is where marshalling bugs live, and none of it needs a device.

The **instrumented test** covers exactly one thing the other two cannot reach: whether the JNI
boundary actually binds. `System.loadLibrary`, the exported symbol name, and whether pinned arrays
carry their values across. Four tests, one emulator boot.

```bash
./gradlew :solver:connectedDebugAndroidTest
```

## Why there is a hand-written SVD

The solver needs one decomposition of one modestly sized matrix. A linear algebra dependency would
cost more in build fragility — cross-compiling to four Android ABIs — than it saves in code.

The choice of algorithm is not arbitrary. Reading the null vector off the normal equations would be
shorter, but it squares the condition number, and conditioning is precisely what the estimator has to
measure to know whether the answer means anything. One-sided Jacobi works on the matrix itself.

## Judging a result

Three numbers come back with every estimate, and they answer different questions.

`null_space_margin` asks *was this answerable at all*. It collapses by three orders of magnitude when
the views converge on a single line of sight, and the estimator refuses below
`kDefaultNullSpaceMargin` rather than returning an arbitrary member of a family of fitting
ellipsoids.

`conic_residual` asks *does the answer explain what was traced*. It catches gross mistakes — a view
of a different object, a badly mistraced ellipse. It is **not** an accuracy measure, and it is worth
being precise about why: poorly triangulated views yield an ellipsoid that reprojects onto every
observed ellipse closely and is still the wrong size. Measured across a sweep of capture geometries,
the residual stayed flat at ~1.5e-3 while the radius error ran from 1% to 5000%.

`view_spread_radians` asks *was this a good capture*, and is the only one of the three that tracks
accuracy. With a pixel of tracing error, radius error runs about 2% at a radian of view spread, 4% at
0.4 rad, and degrades sharply below 0.2 rad. Capture guidance should be built on this number.

## Provenance

The algorithm is from Rubino, Crocco and Del Bue, *3D Object Localisation from Multi-View Image
Detections* (TPAMI 2017). This implementation was written from the paper and the authors'
MIT-licensed Python release. It is not a translation of their MATLAB code, which carries no licence.

It departs from the reference in one respect that matters. The reference reads the ellipsoid's centre
straight out of the dual quadric's last column, which places it at its own negation; the sign is
corrected here, and `PlacesTheCentreOnTheObjectRatherThanItsReflection` fails loudly if it ever comes
back.
