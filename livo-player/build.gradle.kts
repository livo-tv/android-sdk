plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

group = "tv.livo"
version = providers.gradleProperty("VERSION_NAME").get()

android {
    namespace = "tv.livo.sdk.player"
    compileSdk = 35
    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = true
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "UnsafeOptInUsageError")
    }
}

dependencies {
    api(project(":livo-api"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource)
    implementation(libs.media3.ui)
    implementation(libs.coil.compose)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

mavenPublishing {
    coordinates("tv.livo", "livo-player")
    pom {
        name.set("Livo Player")
        description.set("Jetpack Compose HLS player, waiting room, captions, and playback beacons.")
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
