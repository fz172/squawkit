package dev.fanfly.wingslog.core.ui.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

sealed class UiText {
    data class DynamicString(val value: String) : UiText()
    data class StringRes(val res: StringResource, val args: List<Any> = emptyList()) : UiText()

    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is StringRes -> stringResource(res, *args.toTypedArray())
        }
    }
}
