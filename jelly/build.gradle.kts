import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

// Module coordinates so consumers can resolve the artifact as
// `com.rajandube:jelly:<version>` from Maven Central, mavenLocal, or any
// composite build. The Maven groupId is independent of the Android
// `namespace` / Kotlin package below — keeping `dev.jelly` as the runtime
// package means consumers' import lines stay short while the Maven
// coordinate matches the publisher's namespace.
group = "com.rajandube"
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

// ─── Maven Central publication ──────────────────────────────────────────
//
// Uses the Sonatype Central Portal (the post-March-2024 system that
// replaced OSSRH). The vanniktech plugin handles:
//   • bundling the .aar + sources + javadoc + .module + .pom
//   • GPG signing every artifact (Central rejects unsigned uploads)
//   • uploading to https://central.sonatype.com via the Portal API
//
// Required Gradle properties (set in `~/.gradle/gradle.properties` or via
// env vars — see gradle.properties at the project root for the full list):
//   • mavenCentralUsername / mavenCentralPassword — Sonatype Central token
//   • signingInMemoryKey / signingInMemoryKeyPassword — base64 GPG private key
//
// The plugin is a no-op for `publishToMavenLocal`, so existing local
// smoke tests keep working without credentials.
mavenPublishing {
    // CENTRAL_PORTAL is the only valid host for namespaces registered after
    // March 2024. `automaticRelease = false` keeps the first publish in
    // staging so it can be reviewed in the UI; flip to true once the
    // pipeline is trusted.
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)

    // Signing is mandatory for Maven Central but a no-op for local
    // smoke tests — gate it on the GPG key being present so
    // `publishToMavenLocal` works without credentials. The Central upload
    // path still rejects unsigned artifacts; the plugin surfaces a clear
    // "no signatory" error if you try to publish there without keys set.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(
        groupId = project.group.toString(),
        artifactId = "jelly",
        version = project.version.toString(),
    )

    // Single-variant publication with sources and javadoc jars. AGP
    // generates an empty javadoc jar by default; the plugin's Dokka
    // integration can replace it later if richer API docs are desired.
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        ),
    )

    pom {
        name.set("Jelly Android")
        description.set(
            "QA-annotation toolbar for Android Compose apps. " +
                "Long-press any element to capture a comment, screenshot, " +
                "and structured markdown for AI coding agents.",
        )
        inceptionYear.set("2026")

        // Central Portal validates that POM url + scm + at least one
        // license + at least one developer are present and non-empty.
        // Read the GitHub OWNER/REPO from a Gradle property so a single
        // value drives every external link.
        val githubRepo = providers.gradleProperty("jelly.github.repo")
            .orElse("OWNER/REPO")
            .get()
        val githubUrl = "https://github.com/$githubRepo"
        url.set(githubUrl)

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set(
                    providers.gradleProperty("jelly.developer.id").orElse("jelly").get(),
                )
                name.set(
                    providers.gradleProperty("jelly.developer.name").orElse("Jelly").get(),
                )
                email.set(
                    providers.gradleProperty("jelly.developer.email").orElse("").get(),
                )
            }
        }
        scm {
            connection.set("scm:git:git://github.com/$githubRepo.git")
            developerConnection.set("scm:git:ssh://git@github.com/$githubRepo.git")
            url.set(githubUrl)
        }
    }
}
