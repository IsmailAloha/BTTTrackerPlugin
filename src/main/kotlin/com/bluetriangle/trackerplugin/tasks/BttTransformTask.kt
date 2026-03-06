package com.bluetriangle.trackerplugin.tasks

import com.bluetriangle.trackerplugin.transform.BttSourceTransformer
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class BttTransformTask : DefaultTask() {

    @get:Input
    abstract val sourceDirectories: ListProperty<String>

    @get:Input
    abstract val dryRun: Property<Boolean>

    @get:Input
    abstract val trackFunctionName: Property<String>

    @TaskAction
    fun transform() {
        val transformer = BttSourceTransformer(trackFunctionName.get())
        var filesScanned = 0
        var filesModified = 0

        sourceDirectories.get().forEach { dirPath ->
            val dir = File(dirPath)
            if (!dir.exists()) return@forEach

            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    filesScanned++
                    val original = file.readText()
                    val transformed = transformer.transform(original, file.name)

                    if (transformed != original) {
                        filesModified++
                        if (dryRun.get()) {
                            printDiff(file, original, transformed)
                        } else {
                            file.writeText(transformed)
                            logger.lifecycle("[BttTracker] ✔ Modified: ${file.relativeTo(dir)}")
                        }
                    }
                }
        }

        val mode = if (dryRun.get()) " [DRY-RUN]" else ""
        logger.lifecycle(
            "[BttTracker]$mode Scanned $filesScanned file(s), modified $filesModified file(s)."
        )
    }

    private fun printDiff(file: File, original: String, modified: String) {
        logger.lifecycle("\n[BttTracker] ── diff: ${file.name} ──")
        val origLines = original.lines()
        val modLines = modified.lines()
        val maxLen = maxOf(origLines.size, modLines.size)
        for (i in 0 until maxLen) {
            val o = origLines.getOrNull(i)
            val m = modLines.getOrNull(i)
            when {
                o == null -> logger.lifecycle("+ $m")
                m == null -> logger.lifecycle("- $o")
                o != m -> {
                    logger.lifecycle("- $o")
                    logger.lifecycle("+ $m")
                }
            }
        }
        logger.lifecycle("[BttTracker] ── end diff ──\n")
    }
}
