plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin.api)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

gradlePlugin {
    plugins {
        create("agentation") {
            id = "dev.agentation"
            implementationClass = "dev.agentation.gradle.AgentationGradlePlugin"
            displayName = "Agentation"
            description = "Compose source-location injection for QA annotation"
        }
    }
}
