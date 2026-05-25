// sql.js references Node modules that its browser/WASM path does not use. Okio also probes `os`
// while constructing Coil's browser image-loader fallback, and requires a working `tmpdir()`.
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback, {
    fs: false,
    path: false,
    crypto: false,
    os: require.resolve("os-browserify/browser"),
});
