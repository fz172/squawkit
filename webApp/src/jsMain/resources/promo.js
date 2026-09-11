// @ts-check
/**
 * The promo page's only script. Deliberately small enough to type-check with `// @ts-check` and
 * jsconfig.json rather than a build step — if it outgrows that, see the design doc's §6.1 for the
 * esbuild escape hatch.
 *
 * Nothing here is required to read the page: it is a static document, and every section renders
 * without JavaScript.
 */
(function () {
  'use strict';

  // --- returning visitors -------------------------------------------------
  // Someone who has signed in on this device before has already read the pitch. Send them to the
  // login page before anything else runs — this fires in milliseconds, where the same redirect
  // inside the app would cost a multi-megabyte bundle download first.
  //
  // The flag is a hint, never auth state: it is read for this one decision and nothing else, so its
  // worst failure (cleared storage, a shared machine) is one extra page. Crawlers never have it,
  // so indexing is unaffected.
  try {
    if (localStorage.getItem('squawkit.hasSignedIn') === '1') {
      location.replace('/login');
      return;
    }
  } catch (e) {
    // Private mode, or storage blocked entirely. Show the page.
  }

  // --- mobile nav ---------------------------------------------------------
  const toggle = document.getElementById('nav-toggle');
  const nav = document.getElementById('site-nav');

  if (toggle && nav) {
    const setOpen = function (/** @type {boolean} */ open) {
      nav.setAttribute('data-open', String(open));
      toggle.setAttribute('aria-expanded', String(open));
      toggle.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
    };

    toggle.addEventListener('click', function () {
      setOpen(nav.getAttribute('data-open') !== 'true');
    });

    // Tapping a section link should close the sheet, not leave it covering the target.
    nav.addEventListener('click', function (event) {
      if (event.target instanceof Element && event.target.closest('a')) setOpen(false);
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && nav.getAttribute('data-open') === 'true') {
        setOpen(false);
        toggle.focus();
      }
    });

    // The sheet is a mobile-only affordance; leaving it open across the breakpoint strands it.
    const wide = window.matchMedia('(min-width: 861px)');
    wide.addEventListener('change', function (event) {
      if (event.matches) setOpen(false);
    });
  }

  // --- analytics ----------------------------------------------------------
  // Analytics lives inside the Kotlin bundle (main.kt), which this page never loads — without a
  // snippet of its own the promo half of the site is invisible to GA4. Design §6.3.
  //
  // Here rather than in the head so it runs after the returner redirect above: a visitor bounced
  // to /login never saw this page and must not count as having landed on it.
  //
  // Same measurement id as main.kt, deliberately — one stream and one first-party cookie, so a
  // visit that starts at / and signs in at /login stays one session. app.html must not repeat the
  // snippet; Firebase Analytics loads gtag.js there itself.
  const MEASUREMENT_ID = 'G-VPNQ92VG8F';

  // The device-local opt-out the app's diagnostics setting writes (JsAnalyticsPreferenceStore).
  // Absent means enabled, matching the app's default.
  try {
    if (localStorage.getItem('firebase_logging_enabled') === 'false') return;
  } catch (e) {
    // Storage blocked. Fall through to the default.
  }

  const w = /** @type {any} */ (window);
  const script = document.createElement('script');
  script.async = true;
  script.src = 'https://www.googletagmanager.com/gtag/js?id=' + MEASUREMENT_ID;
  document.head.appendChild(script);

  w.dataLayer = w.dataLayer || [];
  /** @type {(...args: any[]) => void} */
  const gtag = function () {
    w.dataLayer.push(arguments);
  };
  gtag('js', new Date());
  gtag('config', MEASUREMENT_ID);

  // Name this surface distinctly so landing traffic and sign-in starts stop being one number: the
  // app logs `login` for the card at /login (TrackRootScreenViews), this page logs `web_promo`.
  gtag('event', 'screen_view', { screen_name: 'web_promo' });
})();
