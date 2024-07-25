// settings.gradle.kts
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

rootProject.name = "PcscLike"
include(":PcscLike")
include(":PcscLikeSample")
include(":PcscLikeSampleBle")
include(":PcscLikeSampleUsb")
