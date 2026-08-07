package convention.common

import com.android.build.api.dsl.CommonExtension
import convention.utils.Config
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

fun Project.configureAndroidCommon(extension: CommonExtension) {
    tasks.withType<Test>().configureEach {
        if (ENABLE_DYNAMIC_AGENT_LOADING !in jvmArgs) {
            jvmArgs(ENABLE_DYNAMIC_AGENT_LOADING)
        }
    }

    extension.apply {
        compileSdk = Config.COMPILE_SDK

        defaultConfig.apply {
            minSdk = Config.MIN_SDK
            testInstrumentationRunner = Config.TEST_INSTRUMENTATION_RUNNER
        }

        compileOptions.apply {
            sourceCompatibility = Config.JAVA_VERSION
            targetCompatibility = Config.JAVA_VERSION
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.android") {
            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain(Config.JVM_TOOLCHAIN)
            }
        }

        lint.apply {
            abortOnError = true
            checkReleaseBuilds = true
            warningsAsErrors = false
            file("lint-baseline.xml").takeIf { it.exists() }?.let {
                baseline = it
            }
        }
    }
}

private const val ENABLE_DYNAMIC_AGENT_LOADING = "-XX:+EnableDynamicAgentLoading"
