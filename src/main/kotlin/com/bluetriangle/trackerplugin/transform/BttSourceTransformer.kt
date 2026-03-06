package com.bluetriangle.trackerplugin.transform

import com.bluetriangle.trackerplugin.visitor.ClickableComposableFinder
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

/**
 * Parses a Kotlin source file using the embedded Kotlin compiler's PSI layer
 * (no full compilation needed), finds all qualifying @Composable functions via
 * [ClickableComposableFinder], and delegates text patching to [BttCodeRewriter].
 *
 * Returns the original source string unchanged (same reference) if no
 * modifications are needed — safe for identity comparison.
 */
class BttSourceTransformer(private val trackFunctionName: String) {

    private val environment: KotlinCoreEnvironment by lazy {
        val configuration = CompilerConfiguration()
        configuration.messageCollector = MessageCollector.NONE  // ← property syntax
        KotlinCoreEnvironment.createForProduction(
            Disposer.newDisposable(),
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    }

    fun transform(source: String, fileName: String = "Unknown.kt"): String {
        val psiFile = PsiFileFactory.getInstance(environment.project)
            .createFileFromText(fileName, KotlinFileType.INSTANCE, source) as? KtFile
            ?: return source

        val finder = ClickableComposableFinder()
        finder.visitKtFile(psiFile)

        if (finder.results.isEmpty()) return source

        return BttCodeRewriter(source, finder.results, trackFunctionName).rewrite()
    }
}
