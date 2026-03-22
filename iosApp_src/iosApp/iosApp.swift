import SwiftUI
import ComposeApp

@main
struct iosApp: App {
    init() {
        MainViewControllerKt.initKoinIos()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
