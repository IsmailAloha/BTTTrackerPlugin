package com.bluetriangle.trackerplugin.visitor

import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * PSI visitor that finds every @Composable function containing a clickable
 * signal, and records the exact source offset of the modifier expression at
 * each clickable call site.
 *
 * Two kinds of clickable signals are detected:
 *
 * 1. Named argument signal — a call with onClick/onLongClick/onCheckedChange etc:
 *    ```kotlin
 *    Button(onClick = { }, modifier = modifier)
 *    ```
 *    → records the `modifier` argument expression offset on that call.
 *
 * 2. Modifier chain signal — a .clickable / .combinedClickable chain:
 *    ```kotlin
 *    Box(modifier = Modifier.padding(8.dp).clickable { })
 *    ```
 *    → records the full chain expression offset (from `Modifier` to end of `}`).
 *
 * [BttCodeRewriter] then appends `.bttTrackAction("FunctionName")` at each
 * recorded offset end position.
 */
class ClickableComposableFinder : KtTreeVisitorVoid() {

    data class ComposableMatch(
        val functionName: String,
        /** Modifier sites sorted descending by offset — safe for reverse patching. */
        val modifierSites: List<ModifierSite>,
        val bodyStartOffset: Int,
        val bodyEndOffset: Int,
    )

    data class ModifierSite(
        val expressionText: String,
        val expressionStartOffset: Int,
        val expressionEndOffset: Int,
    )

    val results: MutableList<ComposableMatch> = mutableListOf()

    // ── Top-level function visitor ────────────────────────────────────────────
    override fun visitNamedFunction(function: KtNamedFunction) {
        // Recurse first so nested @Composable functions are also discovered.
        super.visitNamedFunction(function)

        if (!function.isComposable()) return
        val name = function.name ?: return
        val body = function.bodyBlockExpression ?: return

        val sites = mutableListOf<ModifierSite>()
        body.accept(ClickableSiteCollector(sites, function))

        if (sites.isEmpty()) return

        val uniqueSites = sites
            .distinctBy { it.expressionStartOffset }
            .sortedByDescending { it.expressionStartOffset }

        results += ComposableMatch(
            functionName    = name,
            modifierSites   = uniqueSites,
            bodyStartOffset = body.textRange.startOffset,
            bodyEndOffset   = body.textRange.endOffset,
        )
    }

    // ── Call site collector ───────────────────────────────────────────────────

    private inner class ClickableSiteCollector(
        private val sites: MutableList<ModifierSite>,
        private val enclosingFn: KtNamedFunction,
    ) : KtTreeVisitorVoid() {

        /**
         * Handles Button(onClick = { }, modifier = modifier) style calls.
         */
        override fun visitCallExpression(expression: KtCallExpression) {
            super.visitCallExpression(expression)

            if (!isDirectChildOf(expression, enclosingFn)) return

            val hasClickArg = expression.valueArguments.any { arg ->
                arg.getArgumentName()?.asName?.asString() in CLICK_ARG_NAMES
            }

            if (hasClickArg) {
                resolveModifierSite(expression)?.let { sites += it }
            }
        }

        /**
         * Handles Modifier.clickable { } / modifier.combinedClickable { } style chains.
         */
        override fun visitDotQualifiedExpression(
            expression: KtDotQualifiedExpression
        ) {
            super.visitDotQualifiedExpression(expression)

            val selectorName = (expression.selectorExpression as? KtCallExpression)
                ?.calleeExpression?.text ?: return

            if (selectorName !in CLICK_CALL_NAMES) return
            if (!isDirectChildOf(expression, enclosingFn)) return

            val outermost = outermostChain(expression)
            sites += ModifierSite(
                expressionText        = outermost.text,
                expressionStartOffset = outermost.textRange.startOffset,
                expressionEndOffset   = outermost.textRange.endOffset,
            )
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        /**
         * Given a call with a click arg, find the modifier argument expression.
         * Tries named arg first, then falls back to any positional arg that
         * looks like a Modifier expression.
         */
        private fun resolveModifierSite(call: KtCallExpression): ModifierSite? {
            // 1. Named modifier arg: modifier = ...
            val namedModifier = call.valueArguments.firstOrNull { arg ->
                arg.getArgumentName()?.asName?.asString()?.lowercase() == "modifier"
            }
            if (namedModifier != null) {
                val expr = namedModifier.getArgumentExpression() ?: return null
                return ModifierSite(
                    expressionText        = expr.text,
                    expressionStartOffset = expr.textRange.startOffset,
                    expressionEndOffset   = expr.textRange.endOffset,
                )
            }

            // 2. Positional arg starting with Modifier or modifier
            val positional = call.valueArguments.firstOrNull { arg ->
                val t = arg.getArgumentExpression()?.text ?: ""
                t.startsWith("Modifier") || t.startsWith("modifier")
            }
            if (positional != null) {
                val expr = positional.getArgumentExpression() ?: return null
                return ModifierSite(
                    expressionText        = expr.text,
                    expressionStartOffset = expr.textRange.startOffset,
                    expressionEndOffset   = expr.textRange.endOffset,
                )
            }

            return null
        }

        /**
         * Climbs up through parent KtDotQualifiedExpression nodes to find the
         * outermost one, so we capture the full chain expression rather than
         * just an intermediate link.
         */
        private fun outermostChain(node: KtExpression): KtExpression {
            var cur: KtExpression = node
            while (true) {
                val parent = cur.parent as? KtDotQualifiedExpression ?: break
                if (parent.receiverExpression == cur) cur = parent else break
            }
            return cur
        }

        /**
         * Returns true if expr lives directly inside enclosingFn's body,
         * not inside a nested named function declared in a lambda.
         */
        private fun isDirectChildOf(expr: KtElement, fn: KtNamedFunction): Boolean =
            expr.getParentOfType<KtNamedFunction>(strict = true) == fn
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private val COMPOSABLE_ANNOTATIONS = setOf("Composable", "Preview")

        val CLICK_CALL_NAMES = setOf(
            "clickable",
            "combinedClickable",
            "toggleable",
            "selectable",
            "triStateToggleable",
        )

        val CLICK_ARG_NAMES = setOf(
            "onClick",
            "onLongClick",
            "onDoubleClick",
            "onCheckedChange",
        )
    }

    private fun KtNamedFunction.isComposable(): Boolean =
        annotationEntries.any { it.shortName?.asString() in COMPOSABLE_ANNOTATIONS }
}
