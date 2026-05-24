// sql.js (pulled in by @cashapp/sqldelight-sqljs-worker) ships a dual Node/browser build that
// references Node core modules. In a browser bundle these aren't available and webpack 5 no longer
// polyfills them automatically — the browser/WASM path doesn't use them, so stub them out.
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback, {
    fs: false,
    path: false,
    crypto: false,
    os: false,
});
