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
include(":feature:userprofile:userprofilecard")
include(":feature:userprofile")
include(":feature:userprofile:database")
include(":feature:userprofile:sharedassets")
include(":feature:maintenance:datamanager")
include(":feature:maintenance:sharedassets")
include(":feature:maintenance:viewing")
include(":feature:maintenance:update")
include(":feature:inspection:update")
include(":feature:inspection:model")
include(":feature:inspection:viewing")
include(":feature:inspection:sharedassets")

include(":feature:inspection:datamanager")
include(":feature:fleet:datamanager")
include(":feature:fleet:model")
include(":feature:fleet:sharedassets")
include(":feature:fleet:viewing")
include(":feature:technician:datamanager")
include(":feature:technician:manage")
include(":feature:technician:sharedassets")
include(":core:attachments:model")
include(":core:attachments:datamanager")
include(":core:attachments:sharedassets")
include(":core:attachments:viewing")

