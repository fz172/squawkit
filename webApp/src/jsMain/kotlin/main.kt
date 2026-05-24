import androidx.compose.ui.window.CanvasBasedWindow
import dev.fanfly.wingslog.web.WebApp

fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "Hopply") {
        WebApp()
    }
}
