package dev.fanfly.wingslog.core.firebase.data

import dev.gitlive.firebase.storage.Data
import org.khronos.webgl.Uint8Array

actual fun ByteArray.toFirebaseData(): Data = Data(Uint8Array(this.toTypedArray()))
