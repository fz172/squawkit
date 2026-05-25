// The OPFS worker (src/jsMain/resources/sqlite-wasm-opfs.worker.js) loads @sqlite.org/sqlite-wasm,
// whose Emscripten runtime fetches `sqlite3.wasm` at runtime (via locateFile -> "/sqlite3.wasm").
// Webpack doesn't bundle that binary, so copy it into the output root where the worker can fetch it
// (otherwise the dev server returns index.html and WebAssembly.instantiate fails on the "<!DO…" magic
// word). The package's `exports` map exposes the wasm at "@sqlite.org/sqlite-wasm/sqlite3.wasm".
const CopyWebpackPlugin = require("copy-webpack-plugin");

config.plugins.push(
    new CopyWebpackPlugin({
        patterns: [
            { from: require.resolve("@sqlite.org/sqlite-wasm/sqlite3.wasm"), to: "." },
        ],
    })
);
