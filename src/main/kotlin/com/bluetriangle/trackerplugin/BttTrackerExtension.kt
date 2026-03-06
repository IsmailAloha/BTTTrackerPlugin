package com.bluetriangle.trackerplugin

/**
 * Configuration DSL for the BTT Tracker plugin.
 *
 * Usage in the app's build.gradle.kts:
 * ```kotlin
 * bttTracker {
 *     enabled = true
 *     dryRun = false
 *     trackFunctionName = "bttTrackAction"
 * }
 * ```
 */
open class BttTrackerExtension {

    /**
     * Master switch. Set to false to disable the plugin without removing it.
     * Default: true
     */
    var enabled: Boolean = true

    /**
     * When true, prints a diff of every change without writing anything to disk.
     * Useful for reviewing what the plugin would do before committing.
     * Default: false
     */
    var dryRun: Boolean = false

    /**
     * The name of the modifier extension function to inject.
     * Must match the function name in your SDK exactly.
     * Default: "bttTrackAction"
     */
    var trackFunctionName: String = "bttTrackAction"
}
