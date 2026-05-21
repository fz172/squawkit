package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.gitlive.firebase.storage.Data
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun ByteArray.toFirebaseData(): Data =
  NSData.create(bytes = usePinned { it.addressOf(0) }, length = size.toULong())
