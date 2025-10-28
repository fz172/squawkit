package dev.fanfly.wingslog.userprofile.manager.impl

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.fanfly.wingslog.userprofile.manager.UserProfileManager

@Module
@InstallIn(SingletonComponent::class)
interface UserProfileManagerModule {

  @Binds
  fun bindsUserProfileManagerImpl(impl: UserProfileManagerImpl): UserProfileManager
}