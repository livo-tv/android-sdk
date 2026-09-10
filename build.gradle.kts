import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
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
    }

    pluginManager.withPlugin("com.android.library") {
        pluginManager.withPlugin("com.vanniktech.maven.publish") {
            extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
                configure(
                    AndroidSingleVariantLibrary(
                        variant = "release",
                        sourcesJar = true,
                        publishJavadocJar = false,
                    ),
                )
            }
            val javadocJar =
                tasks.register<Jar>("emptyJavadocJar") {
                    archiveClassifier.set("javadoc")
                }
            extensions
                .getByType<PublishingExtension>()
                .publications
                .withType<MavenPublication>()
                .configureEach { artifact(javadocJar) }
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
        ":livo-player:spotlessCheck",
        ":livo-player:lint",
        ":livo-player:testDebugUnitTest",
        ":livo-studio:spotlessCheck",
        ":livo-studio:lint",
        ":livo-studio:testDebugUnitTest",
        ":livo-community:spotlessCheck",
        ":livo-community:lint",
        ":example:assembleDebug",
    )
}
