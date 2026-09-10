plugins {
    `java-platform`
    alias(libs.plugins.maven.publish)
}

group = "tv.livo"
version = providers.gradleProperty("VERSION_NAME").get()

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":livo-api"))
        api(project(":livo-player"))
        api(project(":livo-studio"))
        api(project(":livo-community"))
    }
}

mavenPublishing {
    coordinates("tv.livo", "livo-bom")
    pom {
        name.set("Livo BOM")
        description.set("Bill of materials for the Livo Android SDK.")
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
