// Production bundling (the deploy artifact) skips source-map generation. Emitting source maps for
// the large Compose/Skiko bundle is a big chunk of jsBrowserProductionWebpack's time and memory
// (it's the "Cannot rewrite paths in JavaScript source maps: Too many sources" work), and the
// deployed site doesn't ship them. Development builds keep their source maps for debugging.
if (config.mode === "production") {
    config.devtool = false;
}
