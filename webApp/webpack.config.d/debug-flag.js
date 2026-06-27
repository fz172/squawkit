// Injects the __WINGSLOG_DEBUG__ constant into the bundle so the Kotlin code can branch on it
// (see main.kt -> BuildInfo.isDeveloperBuild). Set WINGSLOG_WEB_DEBUG=true at build time to produce
// the debug web app (developer surfaces like Feature Lab visible); unset/false produces the release
// web app. This is the web analogue of Android's debug vs release build types.
const webpack = require("webpack");
config.plugins.push(new webpack.DefinePlugin({
    __WINGSLOG_DEBUG__: JSON.stringify(process.env.WINGSLOG_WEB_DEBUG === "true"),
}));
