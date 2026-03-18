package dev.fanfly.wingslog.feature.userprofile.database.impl

import dev.fanfly.wingslog.feature.userprofile.database.UserProfileManager
import org.koin.dsl.module

val userProfileDatabaseModule = module {
  single<UserProfileManager> { UserProfileManagerImpl(get(), get()) }
}
