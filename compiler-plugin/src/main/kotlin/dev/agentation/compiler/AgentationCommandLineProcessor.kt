package dev.agentation.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@AutoService(CommandLineProcessor::class)
@OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
class AgentationCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = OPTION_ENABLED,
            valueDescription = "<true|false>",
            description = "Enable Agentation source-info injection",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            OPTION_ENABLED -> configuration.put(KEY_ENABLED, value.toBoolean())
        }
    }

    companion object {
        const val PLUGIN_ID = "dev.agentation.compiler"
        const val OPTION_ENABLED = "enabled"
        val KEY_ENABLED = CompilerConfigurationKey<Boolean>(OPTION_ENABLED)
    }
}
