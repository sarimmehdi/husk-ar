package convention.utils

import org.gradle.api.JavaVersion

object Config {
    const val APPLICATION_ID = "com.sarim.husk"
    const val NAMESPACE = "com.sarim.husk"
    const val VERSION_CODE = 1
    const val VERSION_NAME = "1.0"
    const val VERSION_CODE_ENVIRONMENT_VARIABLE = "ANDROID_VERSION_CODE"
    const val VERSION_NAME_ENVIRONMENT_VARIABLE = "ANDROID_VERSION_NAME"

    const val UPLOAD_KEYSTORE_PATH_ENVIRONMENT_VARIABLE = "ANDROID_UPLOAD_KEYSTORE_PATH"
    const val UPLOAD_KEYSTORE_PASSWORD_ENVIRONMENT_VARIABLE = "ANDROID_UPLOAD_KEYSTORE_PASSWORD"
    const val UPLOAD_KEY_ALIAS_ENVIRONMENT_VARIABLE = "ANDROID_UPLOAD_KEY_ALIAS"
    const val UPLOAD_KEY_PASSWORD_ENVIRONMENT_VARIABLE = "ANDROID_UPLOAD_KEY_PASSWORD"

    const val SENTRY_DSN_ENVIRONMENT_VARIABLE = "SENTRY_DSN"
    const val SENTRY_AUTH_TOKEN_ENVIRONMENT_VARIABLE = "SENTRY_AUTH_TOKEN"
    const val SENTRY_ORG_ENVIRONMENT_VARIABLE = "SENTRY_ORG"
    const val SENTRY_PROJECT_ENVIRONMENT_VARIABLE = "SENTRY_PROJECT"
    const val SENTRY_TRACES_SAMPLE_RATE = 0.1

    const val COMPILE_SDK = 37
    const val MIN_SDK = 30
    const val BASELINE_PROFILE_MIN_SDK = 28
    const val TARGET_SDK = 36
    const val TEST_INSTRUMENTATION_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
    const val INTEGRATION_TEST_RUNNER = "com.sarim.husk.test.IntegrationTestRunner"
    const val BASELINE_PROFILE_NAMESPACE = "com.sarim.husk.baselineprofile"
    val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_21
    const val JVM_TOOLCHAIN = 21

    const val MANAGED_DEVICE = "Pixel 6"
    const val MANAGED_DEVICE_NAME = "pixel6Api34"
    const val MANAGED_DEVICE_API = 37
    const val SYSTEM_IMAGE_SOURCE = "google"

    const val JACOCO_TOOL_VERSION = "0.8.14"
    const val MUTATION_MAX_THREADS = 4
    const val MUTATION_MAX_HEAP = "-Xmx2g"
    val MUTATION_EXCLUSIONS =
        setOf(
            "**.R",
            "**.R$*",
            "**.BuildConfig",
            "**.*Screen*",
            "**.*Preview*",
            "**.*ParameterProvider*",
            "**.*ComposableSingletons*",
            "**.*Module*",
        )
    val JACOCO_EXCLUSIONS =
        listOf(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/*_HiltModules*.*",
            "**/*Hilt_*.*",
            "**/dagger/**",
            "**/hilt_aggregated_deps/**",
            "**/*Module_*.*",
            "**/*Module.*",
            "**/*Module$*.*",
            "**/databinding/**",
            "**/BR.*",
            "**/*Directions*.*",
            "**/*Args*.*",
            "**/*ComposableSingletons*.*",
            "**/*PreviewKt*.*",
            "**/*ParameterProvider*.*",
            "**/*_Impl*.*",
            "**/*Dao_Impl*.*",
        )
}
