package dev.agentation.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.fileEntry
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName

/**
 * Compiler plugin that finds every `@Composable` function in the host app's
 * source set and tags it with source-location info readable at runtime.
 *
 * Runtime contract: the plugin injects a call equivalent to:
 *
 * ```
 * @Composable
 * fun MyButton(modifier: Modifier = Modifier, ...) {
 *     val modifier = modifier.agentationSource("MyButton.kt", 42)  // injected
 *     // ... original body
 * }
 * ```
 *
 * Implementation status:
 *  - Detection of `@Composable` functions: working
 *  - Source location capture: working (from IR file entry)
 *  - Modifier wrapping: TODO (iterative refinement against a real build)
 *
 * Until modifier wrapping lands, this plugin is a no-op transform — it logs
 * which composables it would have tagged. The runtime registry already accepts
 * manual `Modifier.agentationSource(...)` calls so apps can opt in per-screen
 * while the auto-injection matures.
 */
class AgentationIrGenerationExtension : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val transformer = AgentationIrTransformer(pluginContext)
        moduleFragment.transformChildrenVoid(transformer)
        // Build-time visibility — use println so we don't need to wrestle with
        // K2's MessageCollector API which moved between Kotlin minor versions.
        println("[agentation] processed ${transformer.composablesFound} @Composable functions")
    }
}

private class AgentationIrTransformer(
    private val pluginContext: IrPluginContext,
) : IrElementTransformerVoid() {

    var composablesFound: Int = 0
        private set

    private val composableFqn = FqName("androidx.compose.runtime.Composable")

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (!declaration.hasAnnotation(composableFqn)) {
            return super.visitFunction(declaration)
        }

        composablesFound++

        val fileEntry = declaration.fileEntry
        val file = fileEntry?.name?.substringAfterLast('/') ?: "<unknown>"
        val line = fileEntry?.getLineNumber(declaration.startOffset) ?: -1
        val name = declaration.name.asString()

        println("[agentation] would tag @Composable $name at $file:$line")

        // TODO: inject `modifier = modifier.agentationSource(file, line)` at
        // function entry. Steps:
        //   1. Find the `modifier: Modifier` value parameter.
        //   2. Look up `dev.agentation.capture.agentationSource` via
        //      `pluginContext.referenceFunctions(CallableId(...))`.
        //   3. Build an IrCall for `modifier.agentationSource(file, line)`.
        //   4. Insert at the start of the function body, replacing the
        //      modifier param with a local `val` that shadows it.
        //
        // The reason this isn't done yet: each step requires testing against
        // a real build — IR construction errors only surface at the consumer's
        // compile step, not in this plugin's compile step.

        return super.visitFunction(declaration)
    }
}
