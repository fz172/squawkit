package dev.fanfly.wingslog.core.ui.common.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

actual fun LocalDate.toDisplayFormat(): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    return this.toJavaLocalDate().format(formatter)
}
