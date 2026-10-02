package dev.fanfly.wingslog.core.firebase.functions

import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.details

// The Android SDK decodes the error's JSON `details` into a Map.
actual fun FirebaseFunctionsException.detailsString(key: String): String? =
  (details as? Map<*, *>)?.get(key) as? String
