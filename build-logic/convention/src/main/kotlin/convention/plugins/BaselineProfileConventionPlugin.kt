package convention.plugins

import androidx.baselineprofile.gradle.producer.BaselineProfileProducerExtension
import com.android.build.api.dsl.ManagedVirtualDevice.PageAlignment
import com.android.build.api.dsl.TestExtension
import convention.utils.Config
import convention.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class BaselineProfileConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.test")
            pluginManager.apply("androidx.baselineprofile")

            extensions.configure<TestExtension> {
                namespace = Config.BASELINE_PROFILE_NAMESPACE
                compileSdk = Config.COMPILE_SDK

                defaultConfig {
                    minSdk = Config.BASELINE_PROFILE_MIN_SDK
                    targetSdk = Config.TARGET_SDK
                    testInstrumentationRunner =
                        Config.TEST_INSTRUMENTATION_RUNNER
                }

                compileOptions {
                    sourceCompatibility = Config.JAVA_VERSION
                    targetCompatibility = Config.JAVA_VERSION
                }

                targetProjectPath = ":app"

                configureManagedDevice()
            }

            extensions.configure<BaselineProfileProducerExtension> {
                managedDevices += Config.MANAGED_DEVICE_NAME
                useConnectedDevices = false
            }

            dependencies {
                add(
                    "implementation",
                    libs.benchmarkMacroJunit4Library,
                )
                add(
                    "implementation",
                    libs.androidxTestRunnerLibrary,
                )
                add(
                    "implementation",
                    libs.uiautomatorLibrary,
                )
                add(
                    "implementation",
                    libs.androidxJunitLibrary,
                )
            }
        }
    }

    @Suppress("UnstableApiUsage")
    private fun TestExtension.configureManagedDevice() {
        testOptions.managedDevices.localDevices.register(
            Config.MANAGED_DEVICE_NAME,
        ) {
            device = Config.MANAGED_DEVICE
            sdkVersion = Config.MANAGED_DEVICE_API
            systemImageSource = Config.SYSTEM_IMAGE_SOURCE
            pageAlignment = PageAlignment.FORCE_16KB_PAGES
        }
    }
}
