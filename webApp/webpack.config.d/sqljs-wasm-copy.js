// The @cashapp/sqldelight-sqljs-worker worker loads sql.js, which fetches `sql-wasm.wasm` at
// runtime. Webpack doesn't bundle that binary, so copy it into the output root where the worker
// can fetch it (otherwise the dev server returns index.html and WebAssembly.instantiate fails on
// the "<!DO…" magic word).
const CopyWebpackPlugin = require("copy-webpack-plugin");

config.plugins.push(
    new CopyWebpackPlugin({
        patterns: [
            { from: require.resolve("sql.js/dist/sql-wasm.wasm"), to: "." },
        ],
    })
);
