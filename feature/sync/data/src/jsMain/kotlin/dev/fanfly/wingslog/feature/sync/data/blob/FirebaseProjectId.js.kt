package dev.fanfly.wingslog.feature.sync.data.blob

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app

internal actual fun firebaseProjectId(): String? = Firebase.app.options.projectId
