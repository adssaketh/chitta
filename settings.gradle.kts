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

rootProject.name = "chitta"

include(":app")
include(":core:design")
include(":core:money")
include(":core:model")
include(":core:importer")
include(":core:domain")
include(":core:intake")
include(":core:database")
