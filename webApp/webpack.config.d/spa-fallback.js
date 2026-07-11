// SPA history fallback for the dev server: serve index.html for client-side routes (e.g. a
// full-page load of a /share#... deep link) instead of 404 "Cannot GET /share". The app is a
// single-page app — main.kt reads window.location (incl. the fragment) and routes client-side.
// Production (Firebase Hosting, firebase.json) already rewrites ** -> /index.html.
if (config.devServer) {
    config.devServer.historyApiFallback = true;
}
