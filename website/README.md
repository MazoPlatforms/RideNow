# RideNow — ridenow.lk

RideNow's public website is built with semantic HTML, CSS, and native JavaScript ES modules. It has no frontend framework or runtime dependency. The Rider landing page lives at `/`; `/drive` and `/driver` resolve to a separate Driver experience. Company, support, product, and legal pages are statically rendered from the structured content in `content/pages.json` and the article list in `content/newsroom.json`. Run `python3 apps/website/scripts/build-pages.py` from the repository root to regenerate them. The shared navigation and page components use the RideNow identity and licensed Plus Jakarta Sans font, with the original optimized cinematic artwork already in the site asset library.

## Run locally

From the repository root:

```sh
python3 -m http.server 4174 --directory apps/website
```

Open <http://localhost:4174>. The Driver experience is available at <http://localhost:4174/drive> and <http://localhost:4174/driver>. Product pages include `/about/`, `/how-it-works/`, `/fares/`, `/cities/`, `/business/`, `/partners/`, `/download/`, `/safety/`, and `/accessibility/`. Help is at `/help/` (with `/faq/` as its alias), contact at `/contact/`, and legal pages at `/privacy/` and `/terms/`. The Newsroom has a current product note at `/newsroom/the-preview/`; unknown article slugs use the host's 404 response. Store controls and the contact form are explicitly preview-only because no public store listings or message-delivery backend exist.

## Structure

- `index.html` — redesigned Rider landing page (illustrated, light-first design) and SEO metadata. It loads only `styles/home.css` and `src/home.mjs`; all artwork is inline SVG, so the page needs no photos or video.
- `styles/home.css`, `src/home.mjs` — landing-page-only styles and behaviour (floating nav, scroll reveal, hero parallax, concept phone tabs, preview dialog). Other pages still use the shared files below until they are redesigned.
- `driver/index.html` — Driver landing page and SEO metadata
- `drive/index.html` — route alias to the Driver page
- `privacy/index.html`, `terms/index.html` — Rider and Driver privacy notice and draft service terms
- `styles/legal.css` — quiet, readable legal-document layout on the shared cool-neutral RideNow palette
- `content/pages.json`, `content/newsroom.json` — maintainable route and editorial content sources
- `scripts/build-pages.py` — static page generation, sitemap, and custom 404 output
- `_redirects`, `robots.txt`, `sitemap.xml`, `404.html` — static-host route aliases and indexing support
- `styles/global.css`, `src/global.mjs` — shared multi-page navigation, footer, help search, contact-form preview, and walkthrough interactions
- `styles/pages.css` — distinct responsive visual compositions for company and support pages
- `styles/site.css` — shared one-theme design system and motion foundations
- `styles/rider.css`, `styles/driver.css` — page-specific responsive art direction
- `src/main.mjs` — navigation, reduced-motion-aware effects, app concepts, and preview dialogs
- `assets/brand/` — official RideNow marks and wordmarks
- `assets/fonts/` — Plus Jakarta Sans and its SIL Open Font License
- `assets/images/` — original generated, optimized Rider and Driver artwork
- `assets/videos/` — small silent WebM motion loops, progressively loaded after the poster image and omitted when reduced motion or data saving is enabled


## Landing page redesign notes

- Palette: warm pearl, ink, saffron `#ffc21a`, coral `#ff6a3d`, deep tea `#0d2b2a`. Type: Plus Jakarta Sans (weights 300 and 800 for contrast).
- Art is original SVG: the Colombo dawn scene with the Lotus Tower, the four day scenes, the map, banknote and dial illustrations. The hero pods use SMIL `animateMotion`; off-screen animation is paused and `prefers-reduced-motion` turns motion off.
- `assets/brand/ridenow-mark-ink-sm.webp` and `ridenow-mark-white-sm.webp` are small (about 7 KB) copies of the official marks for the new header and footer. `assets/images/ridenow-home-og.jpg` is the new social preview image.
- `styles/rider.css`, `src/main.mjs` and the rider hero video/photos are no longer used by `/`; they are left in place in case other pages or future work reference them.

- `driver/index.html` is redesigned in the same system (shares `styles/home.css` and `src/home.mjs`): steering-wheel dawn scene, custom SVG icons, zero-commission badge, driver app concept. `assets/images/ridenow-driver-og.jpg` is its social image. The old `styles/driver.css` and driver photos/video are unused but left in place.
- `download/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version, so skip its download page if you rerun it). Social image: `assets/images/ridenow-download-og.jpg`.
- `how-it-works/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version). Social image: `assets/images/ridenow-how-og.jpg`.
- `fares/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version). Social image: `assets/images/ridenow-fares-og.jpg`.
- `cities/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version). Social image: `assets/images/ridenow-cities-og.jpg`.
- `safety/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version). Social image: `assets/images/ridenow-safety-og.jpg`.
- `careers/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version). Social image: `assets/images/ridenow-careers-og.jpg`.
- `partners/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version). Social image: `assets/images/ridenow-partners-og.jpg`.
- `about/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version). Social image: `assets/images/ridenow-about-og.jpg`.
- `business/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version). Social image: `assets/images/ridenow-business-og.jpg`.
- `newsroom/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version). `newsroom/the-preview/` still uses the old design. Social image: `assets/images/ridenow-newsroom-og.jpg`.
- `newsroom/the-preview/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version).
- `accessibility/index.html` is redesigned in the same system (hand-authored; `scripts/build-pages.py` would regenerate the old version). Social image: `assets/images/ridenow-accessibility-og.jpg`.
- `help/index.html` is redesigned in the same system, with a live search filter in `src/help.mjs` (hand-authored; `scripts/build-pages.py` would regenerate the old version). Social image: `assets/images/ridenow-help-og.jpg`.
