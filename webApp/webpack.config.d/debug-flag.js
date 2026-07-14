// Injects the __WINGSLOG_DEBUG__ constant into the bundle so the Kotlin code can branch on it
// (see main.kt -> BuildInfo.isDeveloperBuild). It is the web analogue of Android's debug vs release
// build type, and it drives AppCapability: Feature Lab, the stress test, and aircraft sharing (#134)
// are all developer-build surfaces.
//
// A development build IS a developer build. That used to need WINGSLOG_WEB_DEBUG=true set by hand,
// so `./gradlew :webApp:jsBrowserDevelopmentRun` — the command you use to develop — produced a
// RELEASE app with every developer surface missing, and nothing said so.
//
// webpack already knows which it is, so ask it. The env var still forces the flag on, which is what
// a dogfood web build wants: a production bundle with developer surfaces visible.
const webpack = require("webpack");

const isDeveloperBuild =
    process.env.WINGSLOG_WEB_DEBUG === "true" || config.mode === "development";

config.plugins.push(new webpack.DefinePlugin({
    __WINGSLOG_DEBUG__: JSON.stringify(isDeveloperBuild),
}));
