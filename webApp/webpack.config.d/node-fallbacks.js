// Some deps reference Node core modules their browser/WASM path doesn't use. Okio probes `os`
// while constructing Coil's browser image-loader fallback and requires a working `tmpdir()`, so
// map it to a shim; stub the rest out.
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback, {
    fs: false,
    path: false,
    crypto: false,
    os: require.resolve("os-browserify/browser"),
});
