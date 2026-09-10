pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-sdk"

include(
    ":livo-bom",
    ":livo-api",
    ":livo-player",
    ":livo-studio",
    ":livo-community",
    ":example",
)
