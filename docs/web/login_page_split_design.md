# Design Doc: Splitting the Web Landing Page from the Login Page

**Status:** 📋 Proposed — mocks done, all open questions resolved (§9); ready to build
**Last updated:** 2026-09-09
**Scope:** `webApp` + a new static promo site; `feature/login` gains the redesigned card shared with native
**Mocks:** Claude Design project `03e5a809-b352-4b85-9979-5126024f10db` — *SquawkIt Login Split.dc.html*
(screens `1a`–`1e` light, `2a`–`2e` dark)
**Related:** [`promo_site_design.html`](promo_site_design.html) (the page this replaces),
[`web_target_expansion_plan.md`](web_target_expansion_plan.md),
[`../account/email_link_signin_design.html`](../account/email_link_signin_design.html)

---

## Implementation status

Nothing built. Mocks are done (see above); this doc covers architecture, routing, build wiring and
sequencing. Layout, type, spacing and color come from the mocks.

Decisions taken 2026-09-09: the site stays **`squawkit.fanfly.dev`** (the mocks' "squawkit.com" is
placeholder copy); **anonymous sign-in stays off on web**; the mocks' placeholder app icons resolve
to the two real brand marks in §3.1; **"Terms & Privacy" points at the existing `privacy.html`**,
which already covers both; and the **promo page carries no login form at all** — every sign-in
control on it is a link to `/login`.

---

## 1. Problem

`squawkit.fanfly.dev` is both the marketing site and the app. One Compose destination
(`Screen.Login`, rendered by the web host's `WebLoginLandingScreen`) is both the SEO landing page and
the sign-in form, and the two jobs conflict in two separate ways.

**Layout.** Measured on the current build:

| | Desktop (1440 px) | Minimum width (500 px) |
|---|---|---|
| Page height | ~2,460 CSS px | ~5,600 CSS px |
| Login card top | ~110 px — in the hero, top-right | ~505 px |
| Card fully visible | immediately | ~1,000 px down |

At 1440 px the card sits in the hero beside the headline and there is no problem. At 500 px the
two-column hero stacks, so headline, subhead and six thing-type chips all come first; on a typical
phone viewport (~700 px tall) the card's heading is at the fold and its buttons are below it. Any
fix that raises the card on mobile pushes the marketing story beneath it, so this is structural.

**Audience.** `WebLoginLandingScreen` calls `loginViewModel.silentLogin()` on mount and skips
straight through for anyone with a live session, so signed-in users never see the marketing page.
The people who do see it are signed-out returners — cleared storage, a new browser, a second device,
someone who just signed out — made to scroll a 5,600 px pitch to reach a form they already chose.

**Crawlability.** This one is invisible in a browser and is arguably the biggest cost. Compose
Multiplatform renders into a WebGL `<canvas>` inside a shadow root — the page has **no DOM text at
all**. Everything a crawler can read today lives in `index.html`'s `<head>`: the meta tags, the
`SoftwareApplication` and `FAQPage` JSON-LD, and a one-line `<noscript>`. The six feature cards, the
three how-it-works steps, the six FAQ answers and every heading are pixels. The JSON-LD is doing all
the work, and it is asserting content that no crawler can corroborate on the page.

## 2. Approach

Split by **technology**, not just by route:

- **`/` — a hand-written static promo site.** Plain HTML, CSS and a little JavaScript. Real DOM
  text, crawlable, no Kotlin bundle, fast first paint.
- **`/login` and the app — the KMP bundle**, entered directly at the login card.

This is not a new pattern in the repo: `support.html`, `privacy.html` and
`account_delete_request.html` are already hand-written static pages living in
`webApp/src/jsMain/resources/` and served from the same origin next to the Compose bundle. The promo
page is a bigger one of those.

### 2.1 Goals

- A signed-out user reaches sign-in without scrolling, at any width.
- The promo page is fully readable by crawlers and by a browser with JS disabled.
- Sign-in has a stable, linkable, bookmarkable URL.
- The login card is **one design on web, Android and iOS** (the mocks show it shared — see §4).
- **All sign-in logic lives on `/login`.** The promo page holds no form, no provider buttons and no
  auth SDK — its header button and footer link are ordinary links. That is what keeps it a static
  file, and it is why the desktop hero card of today's page does not survive the split.

### 2.2 Non-goals

- A framework. No React, no bundler-heavy setup; see §6.
- Server-side rendering of the app itself. The app stays a Compose canvas.
- Redesigning the app shell, the auth providers, or the onboarding tail.

## 3. The mocks

Ten artboards, five screens in light (`1a`–`1e`) and dark (`2a`–`2e`):

| | Screen | Notes |
|---|---|---|
| `a` | **Promo page — desktop** | Header: Features · Get the app · **Log in**. Hero with a product preview (meters `1243.5` / `987.2` / `412.0`, fleet cards, a NEXT 90 DAYS list, an "Oil change logged" toast). Six feature cards. Mobile-app band with both store badges. Footer: Log in · Terms & Privacy · Support · Mobile app. |
| `b` | **Promo page — mobile** | Same content; hamburger + Log in in the header. |
| `c` | **Login — desktop** | "← Back to squawkit.com", "Need an account? Signing in creates one.", logo + "Track the important stuff", provider buttons, disclaimer, Terms & Privacy · Support. Second state: the email step — "← All log-in options", "Log in with email", "We'll send a link that signs you in. No password to remember.", email field, "Send login link", "The link expires in 15 minutes and can only be used once." |
| `d` | **Login — mobile web** | Same card; back link reads "← squawkit.com". |
| `e` | **Native app login — iOS** | The same card, native. This is what makes the login screen shared rather than web-only. |

Both promo and login have full dark variants, so the static page needs a real
`prefers-color-scheme` palette, not an afterthought.

Deltas the mocks introduce beyond the split itself — each is new work, not a port:

1. The hero **product preview** does not exist today. It is the largest single piece of new markup.
2. Footer says **"Terms & Privacy"** — the page already exists. `privacy.html` is titled "Terms of
   Use & Privacy Policy" and runs 18 numbered sections covering both, so this is a **label** change,
   not a new page: the shared string `privacy_notice` ("Privacy Notice", in
   `feature/login/.../strings.xml`) becomes "Terms & Privacy" and every host picks it up.
3. The login card's subtitle is **"Track the important stuff"** — new copy.
4. Back-links read **"squawkit.com"** — placeholder. The site stays `squawkit.fanfly.dev`, which is
   what every canonical URL, the App Store listing and `promo_site_design.html` already use, so the
   copy reads "← Back to squawkit.fanfly.dev" (desktop) / "← squawkit.fanfly.dev" (mobile).
5. The login card shows **"Continue anonymously"** — web hides that row, and needs no work to do it
   (§4.1).
6. Both surfaces use a **placeholder app icon**; the real marks differ per surface (§3.1).

### 3.1 Brand marks

The mocks stand in a placeholder app icon on both surfaces. They take **different real marks**,
and neither is the one the web header uses today.

**Promo page — the coloured airplane, static.** The launcher artwork in colour:
`core/sharedassets/src/commonMain/composeResources/drawable/ic_launcher_foreground.xml` is the
multiplatform vector, `docs/product/store_assets/appstore/app_icon_1024.png` the raster master, and
`favicon-192.png` / `apple-touch-icon.png` already ship beside `index.html`. Export an SVG from the
launcher foreground for the static page — crisp at any size, a couple of KB, no Kotlin involved.

Do **not** reach for `BrandPlane` here. It is deliberately single-colour — the same artwork cropped
tight for `Icon()` tinting — which is why today's web header mark is monochrome. Its docstring is
worth reading before anyone "fixes" the colour by tinting it.

**Login card — the motion hero.** `LoginPlaneArt()` in `feature/login/LoginCommon.kt`, which renders
`ThingHero`: five Thing glyphs fly into a crate, the crate morphs into the plane, the glyphs fan out
behind it for a beat and drift away, and the plane bobs alone. `animate = false` gives the resting
state, which is what surfaces reached *from* the login page should use so the sequence does not
replay — the email step included.

This falls out of §4 rather than costing anything: because web drops its `loginContent` override and
falls back to the shared `LoginScreen`, **the motion hero arrives for free** — it is already what
Android and iOS render. The coloured SVG for the promo page is the only new brand asset in this doc.

## 4. The login card is shared, not web-only

Artboard `e` shows the same card running natively on iOS. That changes the shape of the work in a
way that *reduces* it: rather than building a web-only login card, redesign the **shared**
`LoginScreen` in `feature/login` to the mocks, and have the web host **drop its `loginContent`
override entirely** so `AuthFlow` falls back to its default:

```kotlin
loginContent: @Composable (onLoginSuccess: () -> Unit, onChooseEmail: () -> Unit) -> Unit =
  { onLoginSuccess, onChooseEmail -> LoginScreen(...) }   // AuthFlow's existing default
```

So `WebLoginLandingScreen.kt` is **deleted**, not split — the static site takes its marketing half
and the shared `LoginScreen` takes its login half. `WebLandingAssets.kt` goes with it once its
colors are ported to CSS custom properties.

The only web-specific addition is the "← Back to squawkit.fanfly.dev" affordance, which is a link
out of the SPA. Gate it on a capability rather than a platform check, in the spirit of
`AppCapability`.

### 4.1 Anonymous sign-in needs no work

`LoginScreen` already wraps that row in `if (appCapability.isAnonymousLoginSupported)`, and
`AppCapability.js.kt` sets it `false` while Android and iOS set it `true`. Artboards `c` and `d` are
showing the native variant of the card; web hides the row on its own. Nothing to build, and nothing
to add to the redesign beyond keeping the existing gate.

This does mean the Android and iOS login screens change visually. That is what the mocks ask for,
and it is the reason the total work here is smaller than "build a web login page".

## 5. Routing and hosting

### 5.1 Entry points

Today `index.html` is the Compose bundle host and `firebase.json` has a single catch-all rewrite
(`"source": "**"` → `/index.html`). Static files are served before rewrites, which is why
`/support.html` resolves. The split inverts which HTML is which:

| Path | Served by | Contents |
|---|---|---|
| `/` | `index.html` (**rewritten** — now static) | the promo page; loads no Kotlin |
| `/login` | `app.html` via catch-all | KMP bundle, starts at `Screen.Login` |
| `/share#…` | `app.html` via catch-all | unchanged; `main.kt` parks the invite |
| `/support.html`, `/privacy.html` | themselves | unchanged |
| anything else | `app.html` via catch-all | the app |

`app.html` is today's `index.html` minus the SEO payload, plus `<meta name="robots"
content="noindex">`. All the marketing meta, Open Graph, Twitter card and JSON-LD move to the new
static `index.html`, where for the first time they describe content that is actually on the page.

```jsonc
"rewrites": [
  { "source": "**", "destination": "/app.html" }
]
```

No rewrite entry is needed for `/` — the static `index.html` wins as a file. This also means the
`noindex` problem from the earlier draft disappears: the app's HTML host is a different file, so it
simply carries a different robots tag. No second rewrite, no JS meta patching.

### 5.2 Start destination

`WebApp`'s `NavHost` hardcodes `startDestination = Screen.Login.route`, and with `/` no longer part
of the SPA that stays correct as-is. Every path that boots the bundle wants the login card first
(the app itself is unreachable signed-out anyway), so **no start-destination logic is needed** —
another simplification over the earlier draft.

### 5.3 The returner redirect

A signed-out returner should not have to click through the pitch, and a *signed-in* user visiting
`/` should not be stranded on a marketing page — today `silentLogin()` carries them into the app.

Both are handled by the static page, before any Kotlin loads:

```js
if (localStorage.getItem('squawkit.hasSignedIn')) location.replace('/login');
```

The flag is written by the app on first successful sign-in. It is a hint, never auth state: its
worst failure (cleared storage, a shared machine) is one extra page. Crawlers never have it set, so
indexing is unaffected. Doing it in static JS rather than in Kotlin means the redirect fires in
milliseconds instead of after a multi-megabyte bundle download.

### 5.4 The `appAddress` trap

`BrowserHistoryBinding` pins its URL base once, at construction:

```kotlin
private val appAddress = with(window.location) { origin + pathname }
```

That is safe today only because history binding switches on **after** sign-in
(`browserNavigationBound = true` in `AuthFlow`'s `onComplete`), when `pathname` is always `/`. Once
sign-in happens at `/login`, every in-app URL would become `/login#fleet…`. Pin the base to the app
root explicitly (`origin + "/"`), with a comment — the current form reads like deliberate subpath
support.

### 5.5 Unaffected

- **Email-link sign-in** completes in its own tab: `main.kt` checks
  `Firebase.auth.isSignInWithEmailLink(href)` and renders `EmailLinkCompletionScreen` before `WebApp`
  composes. Reached through the catch-all like any other app path.
- **Invite redemption**: `RedeemHost` sits above the nav graph at the app root, not inside a login
  destination.
- **Sign-out**: `NavigateToLoginOnSignOut` → `Screen.Login` → the clean card. Strictly better than
  today, which drops you at the top of a 5,600 px marketing page.

## 6. Building the static site

### 6.1 Where the source lives

`webApp/src/jsMain/resources/` is copied verbatim into the distribution, which is how the existing
static pages ship. Two options:

1. **Author directly in `resources/`** — plain `.html`, `.css`, `.js`. Zero build wiring, matches
   `support.html` exactly, works with the existing `./gradlew :webApp:jsBrowserDistribution` and the
   deploy workflow untouched.
2. **A `webPromo/` source tree** with `package.json` + esbuild, output copied into the distribution
   by a Gradle task wired ahead of `jsBrowserDistribution`, plus one step in the deploy workflow.

**Recommendation: start with (1).** The page needs very little script — theme handling, the mobile
menu, the returner redirect, smooth scroll — and inline `<style>` is already the house pattern for
these pages. Type-check it without a build step by adding `// @ts-check` at the top of the JS and a
`jsconfig.json`; you get editor and CI type errors with no toolchain. Move to (2) only if the script
grows past roughly a hundred lines or wants real modules. The repo already runs npm/TypeScript in
`backend/firebase/functions`, so (2) is available, just not yet earned.

The one thing not to do is inline everything into a single file: the promo page's CSS is
substantially larger than `support.html`'s, and the dark palette doubles it. A separate
`promo.css` keeps it reviewable.

### 6.2 Fonts

The mocks use Space Grotesk and JetBrains Mono, which the app already loads as brand faces
(`rememberBrandHeadlineFamily` / `rememberBrandMonoFamily`). Serve the same files from the promo
page rather than pulling Google Fonts, so the two surfaces cannot drift and the page keeps one
fewer third-party origin.

### 6.3 Analytics

Worth calling out because the split silently removes it. Web analytics today is Firebase Analytics
initialized inside the Kotlin bundle (`measurementId` in `main.kt`), and `TrackRootScreenViews` logs
the login route as a screen view. A static page loads none of that, so **promo-page traffic would
disappear from analytics entirely** unless a `gtag.js` snippet with the same measurement ID is added
to `index.html`. Add it as part of the build, not as a follow-up — otherwise the split's own effect
on sign-in conversion is unmeasurable.

Name the two surfaces distinctly (`web_promo` vs the existing login screen view) so landing traffic
and sign-in starts stop being one number.

## 7. Files touched

| File | Change |
|---|---|
| `webApp/src/jsMain/resources/index.html` | **rewritten** — the static promo page, carrying all SEO meta + JSON-LD |
| `webApp/src/jsMain/resources/promo.css` | new — promo styles, light + dark |
| `webApp/src/jsMain/resources/promo.js` | new — theme, menu, returner redirect (`// @ts-check`) |
| `webApp/src/jsMain/resources/app.html` | new — today's `index.html` minus SEO, plus `noindex` |
| `webApp/src/jsMain/resources/robots.txt` | new — the web app ships none today; `Disallow: /app.html` |
| `firebase.json` | catch-all rewrite retargeted to `/app.html` |
| `web/WebLoginLandingScreen.kt` | **deleted** |
| `web/WebLandingAssets.kt` | deleted once its palette is ported to CSS custom properties |
| `web/WebApp.kt` | drops the `loginContent` override; `AuthFlow` falls back to `LoginScreen` |
| `web/ShellBrowserHistory.kt` | pin `appAddress` to `origin + "/"` |
| `feature/login/.../LoginScreen.kt` | redesigned to the mocks; shared by all three platforms |
| `feature/login/.../EmailSignInScreen.kt` | restyled to the mocks' email step |
| `core/appinfo/.../AppCapability*.kt` | a capability for the "back to the promo site" link (web only) |
| `feature/login/.../composeResources/values/strings.xml` | `privacy_notice` relabelled "Terms & Privacy" |
| `webApp/src/jsMain/resources/brand-plane.svg` | new — the coloured launcher mark for the promo page (§3.1) |

`core/nav` and `feature/shell` are untouched: `Screen.Login` keeps its route string, and everything
that navigates to it keeps working.

## 8. Sequencing

Four PRs, each shippable on its own:

1. **The static promo page.** New `index.html` + `promo.css` + `promo.js` + `app.html` + the rewrite
   + `robots.txt` + the gtag snippet. At the end of this PR `/` is static and crawlable and `/login`
   boots the app into the existing (old-looking) card. The biggest visible win, and it does not
   touch Kotlin at all beyond the HTML host.
2. **Redesign the shared login card.** `LoginScreen` + `EmailSignInScreen` to the mocks, on all
   three platforms.
3. **Delete the web landing screen.** Drop the `loginContent` override and `WebLoginLandingScreen`
   / `WebLandingAssets`; fix `appAddress`. Pure removal once (1) and (2) have landed.
4. **Remember returners.** Write the `localStorage` flag on sign-in; the redirect in `promo.js`.
   Last because it is the piece most likely to want tuning once the rest is live.

PR 1 and PR 2 are independent and can run in parallel. PR 3 depends on both.

## 9. Decisions

Every question this doc opened was settled on 2026-09-09; recorded here so the reasoning is not
re-litigated during implementation.

| Question | Decision |
|---|---|
| Domain — the mocks say "squawkit.com" | Placeholder. Stays `squawkit.fanfly.dev`; a domain move is its own project (canonicals, OAuth redirect domains, Hosting, both store listings) and must not ride along. |
| "Continue anonymously" on web | Stays off. The existing `isAnonymousLoginSupported` gate already handles it (§4.1) — the artboards show the native variant. |
| Brand marks | Coloured launcher SVG on the promo page, the `ThingHero` motion mark on the login card (§3.1). |
| "Terms & Privacy" | Points at the existing `privacy.html`, already titled "Terms of Use & Privacy Policy". A string relabel, not a new page. |
| A login form on the promo page | None. Every sign-in control there is a link to `/login`; the desktop hero card does not survive the split. |
| Bookmarks of `/` | Landing on the promo page is fine. The §5.3 flag carries returners onward; no `/app` alias is advertised. |
