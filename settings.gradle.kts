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
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
  repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
  }
}

rootProject.name = "wingslog"
include(":app")
include(":composeApp")
include(":core:model")
include(":core:ui")
include(":core:auth")
include(":core:database")
include(":feature:settings")
include(":feature:maintenance")
include(":feature:userprofile:userprofilecard")
include(":feature:userprofile")
include(":feature:userprofile:database")
include(":feature:userprofile:sharedassets")
include(":feature:maintenance:database")
include(":feature:inspection")
include(":feature:fleet:database")
include(":feature:fleet")

