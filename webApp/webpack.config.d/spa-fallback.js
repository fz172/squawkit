// SPA history fallback for the dev server: serve the app's HTML host for client-side routes
// (/login, a full-page load of a /share#... deep link) instead of 404 "Cannot GET /login". The app
// is a single-page app — main.kt reads window.location (incl. the fragment) and routes client-side.
//
// It must be app.html, not index.html: index.html is the static promo page and loads no Kotlin, so
// falling back to it renders the marketing page at /login rather than the sign-in card. This
// mirrors what production does — firebase.json rewrites ** -> /app.html — and `/` still resolves to
// the promo page ahead of the fallback, because that file exists.
//
// The per-vertical landing pages (#908) are the exception: firebase.json rewrites /aircraft ->
// /aircraft.html and so on ahead of the ** fallback, and without the same rule here the dev server
// would serve the app host — and its sign-in card — at localhost:8080/car. Keep this list in step
// with the rewrites in firebase.json.
const verticalPages = ["aircraft", "car", "boat", "bike", "home"];

if (config.devServer) {
    config.devServer.historyApiFallback = {
        index: "/app.html",
        rewrites: verticalPages.map((page) => ({
            from: new RegExp(`^/${page}/?$`),
            to: `/${page}.html`,
        })),
    };
}
