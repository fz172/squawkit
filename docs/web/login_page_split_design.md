# Design Doc: Splitting the Web Landing Page from the Login Page

**Status:** 📋 Proposed
**Last updated:** 2026-09-09
**Scope:** `webApp` only — Android and iOS are untouched
**Related:** [`promo_site_design.html`](promo_site_design.html) (the landing page this splits),
[`web_target_expansion_plan.md`](web_target_expansion_plan.md),
[`../account/email_link_signin_design.html`](../account/email_link_signin_design.html)

---

## Implementation status

Nothing built. Visual design is **owned by separate mocks** (in progress) — this doc deliberately
specifies routing, state and sequencing only, and defers layout, type and spacing to those mocks.
§3 names the two surfaces and what each must contain; it does not say what they look like.

---

## 1. Problem

`squawkit.fanfly.dev` is both the marketing site and the app. One Compose destination
(`Screen.Login`, the web host's `WebLoginLandingScreen`) has to be both the SEO landing page and the
sign-in form, and at narrow widths those two jobs conflict.

Measured on the current build at Chrome's minimum window width (500 CSS px):

| | Desktop (1440 px) | Minimum width (500 px) |
|---|---|---|
| Page height | ~2,460 CSS px | ~5,600 CSS px |
| Login card top | ~110 px — in the hero, top-right | ~505 px |
| Card fully visible | immediately | ~1,000 px down |

On desktop the card sits in the hero beside the headline and there is no problem. At 500 px the
two-column hero stacks, so the headline, the subhead and six thing-type chips all come first; on a
typical phone viewport (~700 px tall) the card's heading is at the fold and its buttons are below
it. Every fix that raises the card on mobile pushes the marketing story beneath it, which defeats
the page's other job — so this is structural, not a spacing bug.

The traffic argument is stronger than the layout one. `WebLoginLandingScreen` calls
`loginViewModel.silentLogin()` on mount and skips straight through for anyone with a live session,
so **signed-in users never see the marketing page at all**. The people who do see it are signed-out
returners — cleared storage, a new browser, a second device, someone who just signed out — and they
are made to scroll a 5,600 px pitch to reach a form they have already decided to use.

## 2. Goals

- A returning, signed-out user reaches a sign-in control without scrolling, at any width.
- The landing page keeps its full marketing and SEO payload, and stops competing with a form.
- Sign-in has a **stable, linkable, bookmarkable URL**.
- No change to the shared `AuthFlow`, to `LoginScreen`, or to Android/iOS behaviour.

### Non-goals

- Redesigning the marketing content itself (the sections, copy, and their order stay as they are).
- Changing the auth providers, the onboarding tail, or `feature/login` in any way.
- Server-side rendering or prerendering. The landing page stays a Compose canvas.

## 3. The two surfaces

**`/` — the landing page.** Everything `WebLoginLandingScreen` renders today, minus the login card:
hero, features, how-it-works, FAQ, get-the-app, final CTA, footer. It gains a primary **Log in**
control in the sticky header (today the header's only button is "Get the app"), and every existing
sign-in CTA on the page routes to `/login` instead of scrolling.

**`/login` — the login page.** The card alone, centred at every width: the four provider buttons,
the "Now on iOS and Android" tile, the disclaimer, and a thin footer (Privacy Notice · Support). No
page nav, no marketing sections, no scroll on a phone. Email sign-in and the whole onboarding tail
(`AuthStep.EmailSignIn` → `NameEntry` → `Welcome` → `NotificationPrimer` → `AdsConsentExplainer`)
stay exactly where they are inside `AuthFlow`.

Layout, type, spacing and the responsive behaviour of both come from the mocks, not from here.

## 4. Routing

### 4.1 The key point: `Screen.Login` already *is* the login page

`Screen.Login` is a shared route in `core/nav`, and shared code navigates to it —
`NavigateToLoginOnSignOut` sends every host there when `authStateChanged` emits null. So the split
must **not** add a "login route". It adds a *web-only landing route* and lets `Screen.Login` become
what its name says.

| Route | Destination | URL |
|---|---|---|
| `WebScreen.Landing` (new, `webApp` only) | marketing page | `/` |
| `Screen.Login` (existing, shared) | `AuthFlow` with the card as `loginContent` | `/login` |

`WebLoginLandingScreen` splits into two composables in `webApp/src/jsMain/.../web/`:
`WebLandingScreen` (the marketing sections; keeps the section anchors and in-page scrolling) and
`WebLoginCard` (the card, centred). Both keep reading `WebLandingAssets`.

`AuthFlow`'s `loginContent` slot now receives `WebLoginCard` rather than the whole page — a smaller
override than today's, and `AuthFlow` itself is unchanged.

### 4.2 Start destination

`WebApp`'s `NavHost` currently hardcodes `startDestination = Screen.Login.route`. It becomes a
decision made once at composition from the path and one stored flag:

| Path | Has returned before? | Start destination |
|---|---|---|
| `/login` | either | `Screen.Login` |
| `/` | no | `WebScreen.Landing` |
| `/` | yes | `Screen.Login` |
| anything else | either | `WebScreen.Landing` |

Reading the URL at startup is an established pattern here: `main.kt` already branches on
`window.location.href` twice before the app composes — once for
`Firebase.auth.isSignInWithEmailLink(href)` and once to park a `/share#…` invite through
`ThingShareDeepLinks.deliver(href)`. The catch-all Hosting rewrite (`"source": "**"` →
`/index.html`) already serves any path, which is how `/share` works today, so `/login` needs no
hosting change to *resolve*.

Signed-in users are unaffected either way: `silentLogin()` runs on the login card as it does on the
landing page today, and completes the flow before either surface matters.

### 4.3 The returning-visitor flag

Without this the split just costs returners one extra tap, so it is part of the feature, not a
follow-up.

On a successful sign-in, write a flag to `localStorage` (`squawkit.hasSignedIn`). A later visit to
`/` with the flag set and no live session starts at `Screen.Login`. A visitor without the flag gets
the landing page. The flag is a hint, not auth state — it is never read for anything but this
routing choice, so its worst failure (a cleared storage, a shared machine) is one extra page.

Escape hatch: the login page's footer carries a quiet link back to `/` ("What is SquawkIt?"), so a
returner who wants the pitch can still reach it.

### 4.4 Sign-out and deep links

- **Sign-out** already works: `NavigateToLoginOnSignOut` → `Screen.Login` → `/login`, which is the
  clean card. This is strictly better than today, where sign-out drops you at the top of a 5,600 px
  marketing page.
- **Invite links** (`/share#{thingId}.{secret}`, parked in `main.kt`, redeemed by `RedeemHost` above
  the nav graph) are unaffected — `RedeemHost` sits at the app root, not inside a login destination.
  A signed-out invitee should start at `Screen.Login`, not the landing page: they arrived with
  intent. Add `/share` to the "has intent" set in the §4.2 table.
- **Email-link sign-in** completes in its own tab via `EmailLinkCompletionScreen`, returning before
  `WebApp` composes at all. Untouched.

### 4.5 The `appAddress` trap

`BrowserHistoryBinding` pins its URL base once, at construction:

```kotlin
private val appAddress = with(window.location) { origin + pathname }
```

Today `pathname` is always `/` when the binding starts, because history binding only switches on
**after** sign-in (`browserNavigationBound = true` in `AuthFlow`'s `onComplete`). Once `/login`
exists, a user who signs in from `/login` would pin the base to `/login` and every in-app URL would
become `/login#fleet…`.

Fix: pin the base to the app root explicitly (`origin + "/"`) rather than to whatever path the
session happened to start on. Worth a comment saying why, since the current form reads like
deliberate subpath support.

## 5. SEO

The landing page keeps every meta tag, the `SoftwareApplication` and `FAQPage` JSON-LD, and the
canonical URL it has now — and improves, because its `<h1>` and its primary action finally agree.

`/login` should not be indexed. There is exactly one `index.html`, and it declares
`<meta content="index, follow" name="robots">`, so a per-route directive needs one of:

1. **A second HTML file** (`login.html`, same bundle script tags, `noindex` in the head) plus a
   Hosting rewrite for `/login` ahead of the catch-all. Static, correct for crawlers that do not run
   JS, and the recommended option.
2. **Patching the meta tag from Kotlin** at startup when the path is `/login`. One line, but
   crawlers that read the pre-JS HTML see `index, follow`.

Option 1 costs one file and one rewrite entry; take it. The web app ships no `robots.txt` of its own today (only
`fanfly.dev` has one), so add one alongside `index.html` with `Disallow: /login`.

## 6. Analytics

`TrackRootScreenViews` logs every root route, and `BrowserTitleAnalytics` retitles the tab per
screen view, so the split produces two distinct screen views where there is one today — which is
the point: landing-page traffic and sign-in starts stop being the same number. Name them so the
funnel reads cleanly (`web_landing` and `login`), and keep the existing login events unchanged so
the sign-in success rate stays comparable across the change.

## 7. Files touched

| File | Change |
|---|---|
| `web/WebLoginLandingScreen.kt` | split into `WebLandingScreen` + `WebLoginCard`; header gains the Log in button; CTAs route instead of scrolling |
| `web/WebApp.kt` | `startDestination` decided by path + flag; new `WebScreen.Landing` composable; `loginContent` = `WebLoginCard` |
| `web/ShellBrowserHistory.kt` | pin `appAddress` to `origin + "/"` |
| `main.kt` | (only if the flag read moves earlier than composition) |
| `webApp/src/jsMain/resources/index.html` | unchanged |
| `webApp/src/jsMain/resources/login.html` | new — `noindex` shell for the same bundle |
| `firebase.json` | rewrite `/login` → `/login.html` ahead of the catch-all |
| `webApp/src/jsMain/resources/robots.txt` | new — the web app ships no `robots.txt` today; add one with `Disallow: /login` |

Nothing in `feature/login`, `core/nav`, or `feature/shell` changes. `Screen.Login` keeps its route
string; only what the web host renders for it changes.

## 8. Sequencing

Three PRs, each shippable on its own:

1. **Split the composable.** `WebLandingScreen` + `WebLoginCard` out of one file, still on one
   route, still rendering exactly what it renders today. No behaviour change, no URL change — a
   pure refactor that makes the next PR small.
2. **Add the route.** `WebScreen.Landing`, the path-based start destination, the header Log in
   button, CTA routing, the `appAddress` fix, `login.html` + the rewrite + `robots.txt`. This is the
   PR that changes what users see.
3. **Remember returners.** The `localStorage` flag and the `/` → `/login` decision, plus the
   "What is SquawkIt?" link back. Deliberately last: it is the piece most likely to want tuning once
   the first two are live, and the split is still an improvement without it.

The mocks gate PR 2's layout, not PR 1.

## 9. Open questions

1. **Does `/` keep a login card at all on desktop?** The hero card works well at 1440 px, and
   removing it costs a click for desktop visitors who are ready. Keeping it means the card lives in
   two places; dropping it makes the two surfaces genuinely single-purpose. Leaning toward dropping
   it — the header button is always visible and the page has three other CTAs — but this is a mock
   decision.
2. **Does `/login` need a back-to-marketing affordance beyond the footer link?** Depends on how the
   mocks handle the header.
3. **Should the landing page redirect signed-in users, or keep silently completing?** Today
   `silentLogin()` lands them in the app from wherever they were. Keeping that means a signed-in
   user who deliberately visits `/` to read the FAQ gets bounced into the app. Worth deciding
   explicitly rather than inheriting.
