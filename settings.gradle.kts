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
include(":core:datetime")
include(":core:nav")
include(":core:sharedassets")
include(":core:ui")
include(":core:ui:adaptive")
include(":core:ui:theme")
include(":core:ui:widget:avataricon")
include(":core:appinfo")
include(":core:analytics")
include(":core:auth")
include(":core:storage")
include(":core:firebase")
include(":core:di")
include(":feature:settings")
include(":feature:login")
include(":feature:userprofile:userprofilecard")
include(":feature:userprofile:sharedassets")
include(":feature:logs:datamanager")
include(":feature:logs:sharedassets")
include(":feature:logs:viewing")
include(":feature:logs:update")
include(":feature:tasks:update")
include(":feature:tasks:model")
include(":feature:tasks:viewing")
include(":feature:tasks:sharedassets")

include(":feature:tasks:datamanager")

include(":feature:sharing:model")
include(":feature:sharing:datamanager")
include(":feature:sharing:sharedassets")
include(":feature:sharing:viewing")
include(":feature:sharing:update")

include(":feature:fleet:datamanager")
include(":feature:fleet:sharedassets")
include(":feature:fleet:viewing")
include(":feature:aircraft:dashboard")
include(":feature:aircraft:update")
include(":feature:sync:data")
include(":feature:sync:logging")
include(":feature:sync:settings")
include(":feature:sync:sharedassets")
include(":feature:technician:datamanager")
include(":feature:technician:manage")
include(":feature:technician:sharedassets")
include(":feature:attachment:model")
include(":feature:attachment:datamanager")
include(":feature:attachment:sharedassets")
include(":feature:attachment:viewing")
include(":feature:export:datamanager")
include(":feature:export:sharedassets")
include(":feature:export:update")
include(":feature:featurelab:datamanager")

include(":feature:squawk:model")
include(":feature:squawk:datamanager")
include(":feature:squawk:sharedassets")
include(":feature:squawk:viewing")
include(":feature:squawk:update")

include(":feature:shell")

include(":feature:stresstest")
include(":feature:stresstest:config")
include(":webApp")
