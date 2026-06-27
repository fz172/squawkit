// Performance tuning for the production webpack bundle (the deploy artifact). Development builds
// are untouched.
if (config.mode === "production") {
    // Skip source-map generation: emitting source maps for the large Compose/Skiko bundle is a big
    // chunk of jsBrowserProductionWebpack's time/memory (the "Cannot rewrite paths… Too many
    // sources" work), and the deployed site doesn't ship them.
    config.devtool = false;

    // Skip Terser minification. Minifying the large bundle is the dominant cost of this task and is
    // pathologically slow on CI's small (~2 vCPU) runners — tens of minutes vs seconds locally.
    // The alpha preview channel doesn't need a minified bundle (Firebase serves it gzipped), so
    // trade bundle size for a fast, reliable build. Revisit (re-enable, or use a larger runner) if
    // we start deploying to the live channel.
    config.optimization = config.optimization || {};
    config.optimization.minimize = false;
}
