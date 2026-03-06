package com.bluetriangle.trackerplugin

import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.bluetriangle.trackerplugin.tasks.BttTransformTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class BttTrackerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "bttTracker",
            BttTrackerExtension::class.java
        )

        project.afterEvaluate {
            if (!extension.enabled) {
                project.logger.lifecycle("[BttTracker] Plugin disabled — skipping.")
                return@afterEvaluate
            }

            val androidApp = project.extensions.findByType(AppExtension::class.java)
            val androidLib = project.extensions.findByType(LibraryExtension::class.java)

            when {
                androidApp != null -> registerTasksForApp(project, androidApp, extension)
                androidLib != null -> registerTasksForLib(project, androidLib, extension)
                else -> project.logger.warn(
                    "[BttTracker] No Android extension found. " +
                        "Apply this plugin after 'com.android.application' or 'com.android.library'."
                )
            }
        }
    }

    private fun registerTasksForApp(
        project: Project,
        android: AppExtension,
        extension: BttTrackerExtension,
    ) {
        android.applicationVariants.all { variant ->
            val sourceDirs = variant.sourceSets
                .flatMap { it.javaDirectories }
                .map { it.absolutePath }
            registerTransformTask(project, variant.name, sourceDirs, extension)
        }
    }

    private fun registerTasksForLib(
        project: Project,
        android: LibraryExtension,
        extension: BttTrackerExtension,
    ) {
        android.libraryVariants.all { variant ->
            val sourceDirs = variant.sourceSets
                .flatMap { it.javaDirectories }
                .map { it.absolutePath }
            registerTransformTask(project, variant.name, sourceDirs, extension)
        }
    }

    private fun registerTransformTask(
        project: Project,
        variantName: String,
        sourceDirs: List<String>,
        extension: BttTrackerExtension,
    ) {
        val taskName = "bttTrack${variantName.replaceFirstChar { it.uppercase() }}"

        val task = project.tasks.register(taskName, BttTransformTask::class.java) { task ->
            task.sourceDirectories.set(sourceDirs)
            task.dryRun.set(extension.dryRun)
            task.trackFunctionName.set(extension.trackFunctionName)
            task.group = "btt"
            task.description =
                "Injects ${extension.trackFunctionName}() into clickable Composables [$variantName]"
        }

        // Run before Kotlin compilation so modified sources get compiled
        project.tasks.matching { t ->
            t.name.contains("compile", ignoreCase = true) &&
                t.name.contains(variantName, ignoreCase = true) &&
                t.name.contains("kotlin", ignoreCase = true)
        }.configureEach { compileTask ->
            compileTask.dependsOn(task)
        }
    }
}
