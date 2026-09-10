plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

group = "tv.livo"
version = providers.gradleProperty("VERSION_NAME").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.ktor.client.core)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.client.websockets)
    api(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.cio)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    coordinates("tv.livo", "livo-api")
    pom {
        name.set("Livo API")
        description.set("Pure Kotlin client for Livo media, auth, notifications, metrics, and realtime.")
        inceptionYear.set("2026")
        url.set("https://github.com/livo-tv/android-sdk")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("livo")
                name.set("Livo")
                url.set("https://livo.tv")
            }
        }
        scm {
            url.set("https://github.com/livo-tv/android-sdk")
            connection.set("scm:git:git://github.com/livo-tv/android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/livo-tv/android-sdk.git")
        }
    }
}
