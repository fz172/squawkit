pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
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

rootProject.name = "wingslog"
include(":app")
include(":core:model")
include(":core:ui")
include(":core:auth")
include(":core:database")
include(":feature:settings")
include(":feature:aircraft")
include(":feature:userprofile:userprofilecard")
include(":feature:userprofile")
include(":feature:userprofile:database")
include(":feature:aircraft:database")
include(":feature:fleet:database")
include(":feature:fleet")
