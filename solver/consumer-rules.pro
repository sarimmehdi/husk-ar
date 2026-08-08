# The JNI entry point is bound by name at runtime, not by any reference R8 can follow.
#
# libhusk_jni exports Java_com_sarim_husk_solver_JniEstimator_estimate, so both the class name and
# the method name have to survive shrinking. Without this the app links fine, ships fine, and throws
# UnsatisfiedLinkError the first time anyone solves an ellipsoid in a release build.
#
# AGP's default configuration happens to carry an equivalent rule, but relying on that leaves the
# app one default-file change away from a crash that only shows up in release.
-keepclasseswithmembernames class com.sarim.husk.solver.JniEstimator {
    native <methods>;
}
