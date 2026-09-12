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
  //
  // Only the promo page skips ahead. The vertical pages (/aircraft, /car, ...) share this script
  // but are landing pages someone chose to open — from a search result, or from a link on this
  // site — and bouncing a signed-in reader to /login would make every one of those links a dead end.
  const isPromoPage = location.pathname === '/' || location.pathname === '/index.html';
  try {
    if (isPromoPage && localStorage.getItem('squawkit.hasSignedIn') === '1') {
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
})();
