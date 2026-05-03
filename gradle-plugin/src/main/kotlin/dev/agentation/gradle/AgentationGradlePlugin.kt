package dev.agentation.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Gradle plugin that registers the Agentation Kotlin compiler plugin against
 * Kotlin compilations.
 *
 * Host integration:
 * ```
 * plugins {
 *     id("dev.agentation")
 * }
 * ```
 *
 * The plugin auto-injects source location info into every `@Composable`
 * function in the host's source set so QA annotation popups can report
 * `Source: Foo.kt:42` without per-screen plumbing.
 */
class AgentationGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        // KotlinCompilerPluginSupportPlugin's apply is auto-called by the
        // Kotlin Gradle plugin when our plugin is on the classpath.
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>,
    ): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider {
            listOf(
                SubpluginOption(key = "enabled", value = "true"),
            )
        }
    }

    override fun getCompilerPluginId(): String = "dev.agentation.compiler"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "dev.agentation",
        artifactId = "compiler-plugin",
        version = "0.1.0",
    )
}
