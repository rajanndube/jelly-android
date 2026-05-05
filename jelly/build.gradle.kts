plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
}

// Module coordinates so consumers can resolve the artifact as
// `dev.jelly:jelly:<version>` from mavenLocal, an internal repo, or a
// public Maven host.
group = "dev.jelly"
version = "0.1.0"

android {
    namespace = "dev.jelly"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        // consumer-rules.pro is bundled into the published .aar so the
        // consumer's R8 step preserves Jelly's reflective targets,
        // serialization machinery, and public API.
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        // Release variant is what gets published. Library minification is
        // off — consumers run R8 on the merged classpath and our
        // consumer-rules.pro travels with the .aar.
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            consumerProguardFiles("consumer-rules.pro")
        }
        getByName("debug") {
            // Same rules apply to debug for parity if an integrator ever
            // turns on isMinifyEnabled for debug in their host.
            consumerProguardFiles("consumer-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
        }
    }

    // Publish only the `release` variant. AGP packages the .aar plus a
    // sources jar and (auto-generated empty) javadoc jar suitable for
    // Maven Central / internal Artifactory uploads.
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // ui-tooling brings the inspectable node tree the SDK's Compose
    // previews use during development. ui-tooling-data (slot tree) is
    // reached purely via reflection from CompositionInspectorDebug — no
    // compile-time dependency required, and consumers who want
    // composable-name source attribution can opt in by adding
    //     debugImplementation("androidx.compose.ui:ui-tooling-data:<ver>")
    // to their app.
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// `afterEvaluate` is required because the Android `release` software
// component (`components["release"]`) is registered late by AGP — it
// doesn't exist at the time the top-level publishing { } block would be
// evaluated.
afterEvaluate {
    // GitHub `OWNER/REPO` that hosts the published artifacts. Read from the
    // `jelly.github.repo` Gradle property (set in gradle.properties or via
    // -P); also drives the POM `url` / `scm` fields so a single property
    // controls everything that references the GitHub project.
    val githubRepo = providers.gradleProperty("jelly.github.repo")
        .orElse("OWNER/REPO")
        .get()
    val githubUrl = "https://github.com/$githubRepo"

    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = project.group.toString()
                artifactId = "jelly"
                version = project.version.toString()

                pom {
                    name.set("Jelly Android")
                    description.set(
                        "QA-annotation toolbar for Android Compose apps. " +
                            "Long-press any element to capture a comment, " +
                            "screenshot, and structured markdown for AI " +
                            "coding agents.",
                    )
                    url.set(githubUrl)
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("jelly")
                            name.set("Jelly")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/$githubRepo.git")
                        developerConnection.set("scm:git:ssh://git@github.com/$githubRepo.git")
                        url.set(githubUrl)
                    }
                }
            }
        }

        repositories {
            // Local smoke test target: `./gradlew :jelly:publishToMavenLocal`.
            mavenLocal()

            // GitHub Packages — the canonical publish target. Credentials are
            // resolved in this order:
            //   1. Gradle properties `gpr.user` / `gpr.token` (e.g. set in
            //      `~/.gradle/gradle.properties` for local publishes — keeps
            //      tokens out of the repo).
            //   2. Env vars `GITHUB_ACTOR` / `GITHUB_TOKEN` (the standard
            //      names GitHub Actions exposes; CI publishes need no extra
            //      config).
            //
            // The PAT must have `write:packages` (and `repo` for private
            // repositories). Consumers fetching the artifact need a PAT
            // with `read:packages`.
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/$githubRepo")
                credentials {
                    username = providers.gradleProperty("gpr.user").orNull
                        ?: System.getenv("GITHUB_ACTOR")
                                ?: ""
                    password = providers.gradleProperty("gpr.token").orNull
                        ?: System.getenv("GITHUB_TOKEN")
                                ?: ""
                }
            }
        }
    }
}
