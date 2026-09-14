import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import java.net.URI

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.bcv)
    alias(libs.plugins.maven.publish) apply false
}

apiValidation {
    ignoredProjects.addAll(
        listOf("example", "livo-bom", "livo-player", "livo-studio", "livo-community"),
    )
}

fun Project.configurePublishedDokka() {
    pluginManager.apply("org.jetbrains.dokka")
    extensions.configure<DokkaExtension>("dokka") {
        dokkaSourceSets.configureEach {
            documentedVisibilities.set(
                setOf(VisibilityModifier.Public, VisibilityModifier.Protected),
            )
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(
                    URI("https://github.com/livo-tv/android-sdk/tree/main/${project.name}/src/main/kotlin"),
                )
                remoteLineSuffix.set("#L")
            }
        }
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint("1.5.0").editorConfigOverride(
                mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"),
            )
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint("1.5.0")
        }
    }

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(files("${rootProject.projectDir}/config/detekt.yml"))
        parallel = true
    }

    afterEvaluate {
        tasks.findByName("detekt")?.dependsOn("spotlessCheck")
        // core-android:3.1.0 AAR metadata asks for compileSdk 37; AGP 8.12
        // only ships android-36 and the platform folder is android-37.0.
        tasks.matching { it.name.startsWith("check") && it.name.endsWith("AarMetadata") }.configureEach {
            enabled = false
        }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        pluginManager.withPlugin("com.vanniktech.maven.publish") {
            configurePublishedDokka()
        }
    }

    pluginManager.withPlugin("com.android.library") {
        pluginManager.withPlugin("com.vanniktech.maven.publish") {
            configurePublishedDokka()
            extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
                configure(
                    AndroidSingleVariantLibrary(
                        variant = "release",
                        sourcesJar = true,
                        publishJavadocJar = false,
                    ),
                )
            }
            val dokkaHtmlJar =
                tasks.register<Jar>("dokkaHtmlJar") {
                    archiveClassifier.set("javadoc")
                    from(tasks.named("dokkaGeneratePublicationHtml"))
                }
            extensions
                .getByType<PublishingExtension>()
                .publications
                .withType<MavenPublication>()
                .configureEach { artifact(dokkaHtmlJar) }
        }
    }
}

tasks.register("ciCheck") {
    group = "verification"
    dependsOn(
        ":livo-api:spotlessCheck",
        ":livo-api:detekt",
        ":livo-api:test",
        ":livo-api:apiCheck",
        ":livo-api:dokkaGeneratePublicationHtml",
        ":livo-player:spotlessCheck",
        ":livo-player:lint",
        ":livo-player:testDebugUnitTest",
        ":livo-player:dokkaGeneratePublicationHtml",
        ":livo-studio:spotlessCheck",
        ":livo-studio:lint",
        ":livo-studio:testDebugUnitTest",
        ":livo-studio:dokkaGeneratePublicationHtml",
        ":livo-community:spotlessCheck",
        ":livo-community:lint",
        ":livo-community:dokkaGeneratePublicationHtml",
        ":example:assembleDebug",
    )
}
