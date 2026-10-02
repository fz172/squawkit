package dev.fanfly.wingslog.core.firebase.functions

import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.details

// The JS SDK leaves `details` as the decoded JSON object.
actual fun FirebaseFunctionsException.detailsString(key: String): String? {
  val d = details ?: return null
  return d.asDynamic()[key] as? String
}
