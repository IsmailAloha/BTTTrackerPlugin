package com.bluetriangle.trackerplugin.transform

import com.bluetriangle.trackerplugin.visitor.ClickableComposableFinder.ComposableMatch

/**
 * Applies text patches to the source file based on [ComposableMatch] records.
 *
 * For each modifier site, appends `.<trackFunctionName>("<FunctionName>")` to
 * the end of the modifier expression. Works in reverse source order so that
 * patches at later offsets don't shift the positions of earlier ones.
 *
 * Idempotent: skips any site that already contains the tracker call.
 *
 * Example:
 * ```
 * // Before
 * Box(modifier = modifier.padding(8.dp).clickable { doAction() })
 *
 * // After
 * Box(modifier = modifier.padding(8.dp).clickable { doAction() }.bttTrackAction("MyCard"))
 * ```
 */
class BttCodeRewriter(
    private val source: String,
    private val matches: List<ComposableMatch>,
    private val trackFunctionName: String,
) {

    fun rewrite(): String {
        // Process matches in reverse declaration order so offsets stay valid.
        val sortedMatches = matches.sortedByDescending { it.bodyStartOffset }

        var result = source

        for (match in sortedMatches) {
            // Sites within each match are already sorted descending by offset.
            for (site in match.modifierSites) {
                // Idempotency check — look ahead of the expression end to see
                // if we already injected the tracker here.
                val checkEnd = minOf(site.expressionEndOffset + 80, result.length)
                val lookahead = result.substring(site.expressionStartOffset, checkEnd)
                if (lookahead.contains("$trackFunctionName(\"${match.functionName}\")")) continue

                // Append the tracker call right after the modifier expression.
                result = result.substring(0, site.expressionEndOffset) +
                    ".$trackFunctionName(\"${match.functionName}\")" +
                    result.substring(site.expressionEndOffset)
            }
        }

        return result
    }
}
