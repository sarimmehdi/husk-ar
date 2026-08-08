import convention.utils.ModuleType

plugins {
    alias(libs.plugins.conventionLibraryPluginId)
    alias(libs.plugins.conventionJacocoPluginId)
}

android {
    namespace = "com.sarim.husk.solver"

    // Pinned rather than left to whatever the machine happens to have. The host test build resolves
    // GoogleTest out of this same NDK, so the two stay in step.
    ndkVersion = "29.0.14206865"

    defaultConfig {
        // The library convention leaves instrumentation to applications, but this module has one
        // thing that cannot be proven any other way: whether the JNI boundary actually binds.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64 is every ARCore-capable phone; x86_64 is the emulator on an Intel host. Dropping
            // armeabi-v7a keeps the native build to two passes, and 32-bit ARM is long gone from
            // devices that can run ARCore at all.
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                // Statically linked because the app ships exactly one native library. c++_shared
                // would mean packaging libc++ alongside it to no benefit.
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.4"
        }
    }
}

libraryConfig {
    moduleType.set(ModuleType.PURE)
}

dependencies {
    // api rather than implementation: Pose, Ellipsoid and DualConic are all over this module's
    // public surface, so anything calling the solver needs them on its compile classpath too.
    api(project(":geometry"))

    androidTestImplementation(libs.androidxJunitLibrary)
    androidTestImplementation(libs.androidxTestRunnerLibrary)
}
