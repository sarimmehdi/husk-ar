package convention.common

import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import java.io.File

fun Project.configurePreCommitLinting() {
    if (!providers.gradleProperty("precommit").isPresent) return

    afterEvaluate {
        val filesToLint = getPreCommitFilesForCurrentProject()

        logger.lifecycle(
            "Pre-commit files for $path: ${
                filesToLint.joinToString(
                    separator = ", ",
                    transform = File::getPath,
                ).ifBlank { "none" }
            }",
        )

        if (filesToLint.isEmpty()) {
            disablePreCommitLintTasks()
            return@afterEvaluate
        }

        configurePreCommitLintTaskSources(filesToLint)
    }
}

private fun Project.disablePreCommitLintTasks() {
    tasks.configureEach {
        val shouldDisable =
            name.contains(
                other = "ktlint",
                ignoreCase = true,
            ) ||
                name.contains(
                    other = "detekt",
                    ignoreCase = true,
                )

        if (shouldDisable) {
            enabled = false
        }
    }
}

private fun Project.configurePreCommitLintTaskSources(filesToLint: List<File>) {
    val lintFileCollection = files(filesToLint)

    tasks.withType<Detekt>().configureEach {
        setSource(lintFileCollection)
        exclude("**/*.gradle.kts")
    }

    tasks.withType<BaseKtLintCheckTask>().configureEach {
        setSource(lintFileCollection)
    }

    tasks.withType<KtLintFormatTask>().configureEach {
        setSource(lintFileCollection)
    }
}

private fun Project.getPreCommitFilesForCurrentProject(): List<File> {
    val currentProjectPath =
        projectDir
            .toPath()
            .toAbsolutePath()
            .normalize()

    return getPreCommitFiles()
        .filter { file -> file.exists() }
        .filter { file -> file.isFile }
        .filter { file -> file.extension == KOTLIN_FILE_EXTENSION }
        .filter { file ->
            file
                .toPath()
                .toAbsolutePath()
                .normalize()
                .startsWith(currentProjectPath)
        }
}

private fun Project.getPreCommitFiles(): List<File> {
    val precommitFilesPath =
        providers
            .gradleProperty("precommitFiles")
            .orNull

    if (precommitFilesPath != null) {
        return rootProject
            .file(precommitFilesPath)
            .takeIf { file -> file.exists() }
            ?.readLines()
            ?.asSequence()
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.map { path -> rootProject.file(path) }
            ?.filter { file -> file.exists() }
            ?.toList()
            .orEmpty()
    }

    return providers
        .exec {
            workingDir(rootProject.rootDir)
            commandLine(
                "git",
                "--no-pager",
                "diff",
                "--name-only",
                "--cached",
                "--diff-filter=ACMR",
                "--",
                "*.kt",
            )
        }.standardOutput.asText
        .get()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { path -> rootProject.file(path) }
        .filter { file -> file.exists() }
        .toList()
}

private const val KOTLIN_FILE_EXTENSION = "kt"
