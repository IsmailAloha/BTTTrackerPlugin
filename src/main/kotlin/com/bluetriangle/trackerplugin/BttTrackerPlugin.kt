package com.bluetriangle.trackerplugin

import com.android.build.api.variant.AndroidComponentsExtension
import com.bluetriangle.trackerplugin.tasks.BttTransformTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class BttTrackerPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "bttTracker",
            BttTrackerExtension::class.java
        )

        val androidComponents = project.extensions
            .findByType(AndroidComponentsExtension::class.java)

        if (androidComponents == null) {
            project.logger.warn(
                "[BttTracker] No Android extension found. " +
                        "Apply this plugin after 'com.android.application' or 'com.android.library'."
            )
            return
        }

        androidComponents.onVariants { variant ->
            if (!extension.enabled) return@onVariants

            val taskName = "bttTrack${variant.name.replaceFirstChar { it.uppercase() }}"

            val task = project.tasks.register(taskName, BttTransformTask::class.java) { task ->
                task.sourceDirectories.set(
                    variant.sources.java?.all?.map { dirs ->
                        dirs.map { it.asFile.absolutePath }
                    } ?: project.provider { emptyList() }
                )
                task.dryRun.set(extension.dryRun)
                task.trackFunctionName.set(extension.trackFunctionName)
                task.group = "btt"
                task.description =
                    "Injects ${extension.trackFunctionName}() into clickable Composables [${variant.name}]"
            }

            project.tasks.matching { t ->
                t.name.contains("compile", ignoreCase = true) &&
                        t.name.contains(variant.name, ignoreCase = true) &&
                        t.name.contains("kotlin", ignoreCase = true)
            }.configureEach { it.dependsOn(task) }
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
