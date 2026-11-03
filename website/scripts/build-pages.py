#!/usr/bin/env python3
"""Render self-contained static RideNow company/support pages from content/pages.json."""
from __future__ import annotations

import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGES = json.loads((ROOT / "content/pages.json").read_text())
ARTICLES = json.loads((ROOT / "content/newsroom.json").read_text())


def esc(value: str) -> str:
    return html.escape(str(value), quote=True)


def header(interior: bool = True) -> str:
    modifier = " site-header--interior" if interior else ""
    brand_mark = "ridenow-mark-light.png" if interior else "ridenow-mark-dark.png"
    return f'''<a class="skip-link" href="#main">Skip to content</a>
<header class="site-header site-header--global{modifier}" id="top">
  <a class="brand" href="/" aria-label="RideNow home"><img src="/assets/brand/{brand_mark}" alt="" width="42" height="36"><span class="brand__name">Ride<span>Now</span></span></a>
  <button class="menu-toggle" type="button" aria-label="Open navigation" aria-controls="site-nav" aria-expanded="false" data-menu-toggle><span></span><span></span></button>
  <nav class="site-nav" id="site-nav" aria-label="Main navigation" data-site-nav>
    <details class="nav-group"><summary>Ride</summary><div class="nav-menu"><span class="nav-menu__eyebrow">FOR YOUR NEXT TRIP</span><a href="/how-it-works/">How it works</a><a href="/fares/">Fares</a><a href="/cities/">Cities</a><a href="/download/">Get the app</a></div></details>
    <details class="nav-group"><summary>Drive</summary><div class="nav-menu"><span class="nav-menu__eyebrow">FOR THE PEOPLE BEHIND THE WHEEL</span><a href="/driver/">Driver experience</a><a href="/safety/">Safety</a><a href="/careers/">Careers</a><a href="/partners/">Partners</a></div></details>
    <details class="nav-group"><summary>Company</summary><div class="nav-menu"><span class="nav-menu__eyebrow">GET TO KNOW RIDENOW</span><a href="/about/">About RideNow</a><a href="/business/">Business</a><a href="/newsroom/">Newsroom</a><a href="/accessibility/">Accessibility</a></div></details>
    <details class="nav-group"><summary>Help</summary><div class="nav-menu"><span class="nav-menu__eyebrow">HERE TO HELP</span><a href="/help/">Help Center</a><a href="/faq/">FAQs</a><a href="/contact/">Contact</a><a href="/privacy/">Privacy</a><a href="/terms/">Terms</a></div></details>
    <a class="site-nav__cta" href="/download/">Explore RideNow <span aria-hidden="true">↗</span></a>
  </nav>
</header>'''


def footer() -> str:
    groups = {
        "Ride": [("How it works", "/how-it-works/"), ("Fares", "/fares/"), ("Cities", "/cities/"), ("Download", "/download/")],
        "Drive": [("Driver experience", "/driver/"), ("Safety", "/safety/"), ("Careers", "/careers/"), ("Partners", "/partners/")],
        "Company": [("About", "/about/"), ("Business", "/business/"), ("Newsroom", "/newsroom/"), ("Accessibility", "/accessibility/")],
        "Support": [("Help Center", "/help/"), ("FAQs", "/faq/"), ("Contact", "/contact/")],
    }
    columns = "".join(
        '<div class="global-footer__column"><h2>' + esc(title) + '</h2><ul>' + "".join(
            f'<li><a href="{esc(url)}">{esc(label)}</a></li>' for label, url in links
        ) + "</ul></div>" for title, links in groups.items()
    )
    return f'''<footer class="site-footer global-footer">
  <div class="page-wrap global-footer__main">
    <div class="global-footer__brand"><a class="brand brand--footer" href="/" aria-label="RideNow home"><img src="/assets/brand/ridenow-mark-dark.png" alt="" width="42" height="36"><span class="brand__name">Ride<span>Now</span></span></a><p>Your Ride. Right Now.</p><span class="global-footer__place">Sri Lanka · A work in motion</span></div>
    <nav class="global-footer__links" aria-label="Footer navigation">{columns}</nav>
  </div>
  <div class="page-wrap global-footer__bottom"><span>© <span data-year>2026</span> RideNow</span><nav aria-label="Legal links"><a href="/privacy/">Privacy</a><a href="/terms/">Terms</a></nav><a href="#top" class="footer-top">BACK TO TOP <span aria-hidden="true">↑</span></a></div>
</footer>'''


def breadcrumbs(slug: str, title: str) -> str:
    return f'<nav class="breadcrumbs" aria-label="Breadcrumb"><ol><li><a href="/">Home</a></li><li aria-current="page">{esc(title)}</li></ol></nav>'


def render_items(items: list[dict], ordered: bool = False) -> str:
    tag = "ol" if ordered else "ul"
    cls = "route-steps" if ordered else "route-principles"
    bits = []
    for index, item in enumerate(items, 1):
        number = f'<span class="route-principles__index">{index:02d}</span>' if not ordered else f'<span class="route-steps__index">0{index}</span>'
        bits.append(f'<li>{number}<div><h3>{esc(item["title"])}</h3><p>{esc(item["text"])}</p></div></li>')
    return f'<{tag} class="{cls}">{"".join(bits)}</{tag}>'


def render_section(section: dict, page: dict, slug: str, index: int) -> str:
    kind = section["kind"]
    number = f"{index + 1:02d}"
    eyebrow = f'<p class="kicker kicker--dark">{esc(section.get("eyebrow", ""))}</p>' if section.get("eyebrow") else ""
    text = f'<p>{esc(section.get("text", ""))}</p>' if section.get("text") else ""
    link = f'<a class="text-link text-link--dark" href="{esc(section["link"])}">{esc(section.get("linkText", "Explore"))}<span aria-hidden="true">↗</span></a>' if section.get("link") else ""
    title = esc(section.get("title", ""))
    if kind == "split":
        image = esc(section.get("image", page["image"]))
        alt = esc(section.get("imageAlt", page["imageAlt"]))
        return f'''<section class="route-section route-section--split" aria-labelledby="section-{slug}-{index}"><div class="page-wrap route-split"><div class="route-split__copy"><span class="route-section__index">{number} / {esc(section.get("eyebrow", ""))}</span><h2 id="section-{slug}-{index}">{title}</h2>{text}{link}</div><figure class="route-split__image"><img src="/assets/images/{image}" alt="{alt}" loading="lazy" width="1774" height="887"><figcaption>RIDENOW · SRI LANKA</figcaption></figure></div></section>'''
    if kind == "principles":
        return f'''<section class="route-section route-section--principles" aria-labelledby="section-{slug}-{index}"><div class="page-wrap"><div class="route-section__heading">{eyebrow}<h2 id="section-{slug}-{index}">{title}</h2></div>{render_items(section.get("items", []))}</div></section>'''
    if kind == "callout":
        return f'''<section class="route-section route-section--callout" aria-labelledby="section-{slug}-{index}"><div class="page-wrap route-callout"><div><span class="route-section__index">{number} / {esc(section.get("eyebrow", ""))}</span><h2 id="section-{slug}-{index}">{title}</h2>{text}{link}</div><svg viewBox="0 0 460 250" aria-hidden="true"><path d="M10 208c75-3 51-92 128-93 75-1 62 83 127 66 64-17 49-108 112-108 30 0 47 14 72-35"/><circle cx="10" cy="208" r="6"/><circle cx="449" cy="38" r="6"/></svg></div></section>'''
    if kind == "availability":
        return f'''<section class="route-section route-section--availability" aria-labelledby="section-{slug}-{index}"><div class="page-wrap availability"><div class="availability__mark" aria-hidden="true"><span>—</span><i></i></div><div class="availability__copy">{eyebrow}<h2 id="section-{slug}-{index}">{title}</h2>{text}{link}</div></div></section>'''
    if kind == "help":
        groups = []
        for group_index, group in enumerate(section.get("groups", [])):
            items = []
            for item in group.get("items", []):
                items.append(f'<details class="help-item" data-help-item data-search-text="{esc(item["q"] + " " + item["a"])}"><summary>{esc(item["q"])}<span aria-hidden="true">+</span></summary><p>{esc(item["a"])}</p></details>')
            groups.append(f'<section class="help-group" aria-labelledby="help-group-{group_index}"><h3 id="help-group-{group_index}">{esc(group["title"])}</h3>{"".join(items)}</section>')
        return f'''<section class="route-section route-section--help" aria-labelledby="section-{slug}-{index}"><div class="page-wrap help-center"><div class="help-center__heading">{eyebrow}<h2 id="section-{slug}-{index}">{title}</h2><label class="help-search"><span class="visually-hidden">Search help articles</span><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.3"/><path d="m15.4 15.4 5 5"/></svg><input type="search" name="q" placeholder="Search a question or topic" autocomplete="off" data-help-search><kbd>/</kbd></label><p class="help-search__status" role="status" aria-live="polite" data-help-status>Browse the answers below.</p></div><div class="help-center__groups">{"".join(groups)}</div><p class="help-center__more">Still looking? <a href="/contact/">Contact options</a> are available here; the form is a non-sending preview.</p></div></section>'''
    if kind == "contact":
        fields = []
        for field in section.get("fields", []):
            name = esc(field["name"]); label = esc(field["label"]); req = " required" if field.get("required") else ""
            maxlen = f' maxlength="{int(field["max"])}"' if field.get("max") else ""
            autocomplete = f' autocomplete="{esc(field["autocomplete"])}"' if field.get("autocomplete") else ""
            if field["type"] == "select":
                options = ''.join(f'<option value="{esc(option)}">{esc(option)}</option>' for option in field.get("options", []))
                control = f'<select id="contact-{name}" name="{name}"{req}><option value="" selected disabled>Select a topic</option>{options}</select>'
            elif field["type"] == "textarea":
                control = f'<textarea id="contact-{name}" name="{name}" rows="5"{req}{maxlen}></textarea>'
            else:
                control = f'<input id="contact-{name}" name="{name}" type="{esc(field["type"])}"{req}{maxlen}{autocomplete}>'
            fields.append(f'<div class="contact-field"><label for="contact-{name}">{label}</label>{control}</div>')
        return f'''<section class="route-section route-section--contact" aria-labelledby="section-{slug}-{index}"><div class="page-wrap contact-layout"><div class="contact-layout__intro">{eyebrow}<h2 id="section-{slug}-{index}">{title}</h2>{text}<div class="contact-preview-note"><span aria-hidden="true">i</span><p>Preview form only. No message is sent or stored.</p></div><p class="contact-small">RideNow has not published a monitored email address or office address. Please don’t include passwords, identity documents, or payment details.</p></div><form class="contact-form" method="post" data-contact-form><div class="contact-form__fields">{"".join(fields)}</div><button class="button button--yellow" type="submit">Check message <span aria-hidden="true">↗</span></button><p class="contact-form__status" role="status" aria-live="polite" data-contact-status></p></form></div></section>'''
    if kind == "walkthrough":
        steps = section.get("steps", [])
        tabs = ''.join(f'<button class="walkthrough-tab{" is-active" if i == 0 else ""}" type="button" role="tab" id="walkthrough-tab-{i}" aria-controls="walkthrough-panel-{i}" aria-selected="{"true" if i == 0 else "false"}" tabindex="{"0" if i == 0 else "-1"}" data-walkthrough-tab="{i}"><span>0{i + 1}</span>{esc(step["title"])}</button>' for i, step in enumerate(steps))
        panels = ''.join(f'<div class="walkthrough-panel" id="walkthrough-panel-{i}" role="tabpanel" aria-labelledby="walkthrough-tab-{i}"{" hidden" if i else ""} data-walkthrough-panel="{i}"><span>STEP 0{i + 1} / 0{len(steps)}</span><h3>{esc(step["title"])}</h3><p>{esc(step["text"])}</p></div>' for i, step in enumerate(steps))
        return f'''<section class="route-section route-section--walkthrough" aria-labelledby="section-{slug}-{index}"><div class="page-wrap walkthrough"><div class="walkthrough__intro">{eyebrow}<h2 id="section-{slug}-{index}">{title}</h2><p class="concept-label"><span class="concept-label__dot"></span> CONCEPT ONLY · NO LIVE BOOKINGS</p></div><div class="walkthrough__stage"><div class="walkthrough__route" aria-hidden="true"><svg viewBox="0 0 600 380"><path d="M38 320c105-18 59-140 180-142s108 116 206 55 53-122 143-195"/><circle cx="38" cy="320" r="9"/><circle cx="567" cy="38" r="9"/></svg></div><div class="walkthrough__content"><div class="walkthrough-tabs" role="tablist" aria-label="Ride steps">{tabs}</div>{panels}</div></div></div></section>'''
    if kind == "newsroom":
        slug_article = esc(section.get("articleSlug", "the-preview"))
        return f'''<section class="route-section route-section--newsroom" aria-labelledby="section-{slug}-{index}"><div class="page-wrap newsroom-feature"><span class="route-section__index">{esc(section.get("eyebrow", "PRODUCT NOTE"))}</span><article><div class="newsroom-feature__copy"><h2 id="section-{slug}-{index}">{title}</h2>{text}<a class="text-link text-link--dark" href="/newsroom/{slug_article}/">{esc(section.get("linkText", "Read"))}<span aria-hidden="true">↗</span></a></div><div class="newsroom-feature__art" aria-hidden="true"><span>RN</span><svg viewBox="0 0 420 250"><path d="M10 210c80-18 52-108 128-108s75 90 140 65 64-120 132-145"/><circle cx="10" cy="210" r="7"/><circle cx="410" cy="22" r="7"/></svg></div></article><p class="newsroom-feature__note">Product note · Current development preview · No launch date announced</p></div></section>'''
    if kind == "download":
        return f'''<section class="route-section route-section--download" aria-labelledby="section-{slug}-{index}"><div class="page-wrap download-panel"><div class="download-panel__copy">{eyebrow}<h2 id="section-{slug}-{index}">{title}</h2>{text}<div class="store-buttons"><button class="store-button" type="button" data-store="Google Play" aria-haspopup="dialog"><span class="store-button__icon" aria-hidden="true">▶</span><span><small>ANDROID PREVIEW</small><strong>Google Play</strong></span><b>NOT LIVE</b></button><button class="store-button" type="button" data-store="Apple App Store" aria-haspopup="dialog"><span class="store-button__icon" aria-hidden="true">●</span><span><small>NO iOS APP</small><strong>App Store</strong></span><b>NOT LIVE</b></button></div><p class="store-note">Preview controls only. There are no public app-store listings.</p></div><div class="download-panel__brand" aria-hidden="true"><img src="/assets/brand/ridenow-mark-dark.png" alt="" width="300" height="260"><svg viewBox="0 0 500 320"><path d="M12 264c122-33 61-158 196-163s112 104 180 64 40-91 100-142"/></svg><span>YOUR RIDE.<br>RIGHT NOW.</span></div></div></section>'''
    raise ValueError(f"Unknown section kind: {kind}")


def page_html(slug: str, page: dict) -> str:
    title = page["title"]
    lines = page["heading"]
    hero_heading = "".join(f'<span>{esc(line)}</span>' if i == 0 else f'<em>{esc(line)}</em>' for i, line in enumerate(lines))
    img = esc(page["image"])
    image_alt = esc(page["imageAlt"])
    sections = "".join(render_section(section, page, slug, i) for i, section in enumerate(page.get("sections", [])))
    intro = f'''<section class="route-hero route-hero--{esc(page["layout"])}" style="--route-image:url('/assets/images/{img}')" aria-labelledby="page-title">
  <div class="route-hero__wash" aria-hidden="true"></div><div class="route-hero__route" aria-hidden="true"><svg viewBox="0 0 800 430"><path d="M5 350c130-30 80-165 225-168s120 135 246 88 95-133 202-202"/><circle cx="5" cy="350" r="7"/><circle cx="678" cy="68" r="7"/></svg></div>
  <div class="page-wrap route-hero__content">{breadcrumbs(slug, title)}<p class="kicker route-kicker"><span class="pulse-dot" aria-hidden="true"></span>{esc(page["eyebrow"])}</p><h1 id="page-title">{hero_heading}</h1><p class="route-hero__lede">{esc(page["lede"])}</p></div><span class="route-hero__index" aria-hidden="true">RN / {esc(slug.upper().replace('-', ' '))}</span>
</section>'''
    canonical = f"https://ridenow.lk/{slug}/"
    page_type = "help" if slug == "help" else slug
    return f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="theme-color" content="#111513"><meta name="description" content="{esc(page['description'])}"><meta name="robots" content="index,follow"><link rel="canonical" href="{esc(canonical)}"><link rel="icon" type="image/png" href="/assets/brand/ridenow-mark-light.png"><link rel="preload" href="/assets/fonts/plus-jakarta-sans-variable.ttf" as="font" type="font/ttf" crossorigin><link rel="preload" href="/assets/images/{img}" as="image" fetchpriority="high"><link rel="stylesheet" href="/styles/site.css"><link rel="stylesheet" href="/styles/global.css"><link rel="stylesheet" href="/styles/pages.css"><title>{esc(title)}</title><meta property="og:type" content="website"><meta property="og:url" content="{esc(canonical)}"><meta property="og:title" content="{esc(title)}"><meta property="og:description" content="{esc(page['description'])}"><meta property="og:image" content="https://ridenow.lk/assets/images/{img}"></head>
<body class="global-page" data-route="{esc(page_type)}">{header()}<main id="main">{intro}{sections}</main>{footer()}
<dialog class="preview-dialog" aria-labelledby="preview-title" data-preview-dialog><form method="dialog"><button class="preview-dialog__close" value="close" aria-label="Close preview notice">×</button></form><span class="preview-dialog__mark"><img src="/assets/brand/ridenow-mark-light.png" alt="" width="32" height="28"></span><p class="kicker kicker--dark">A NOTE BEFORE YOU TAP</p><h2 id="preview-title">The listing isn’t live yet.</h2><p data-preview-message>RideNow app listings will be linked here when they’re published.</p><button class="button button--black" type="button" data-dialog-dismiss>Got it</button></dialog>
<script type="module" src="/src/global.mjs"></script></body></html>'''


def write(slug: str, markup: str) -> None:
    folder = ROOT / slug
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "index.html").write_text(markup + "\n")


for route, data in PAGES.items():
    write(route, page_html(route, data))

def article_html(article: dict) -> str:
    slug = article["slug"]
    canonical = f"https://ridenow.lk/newsroom/{slug}/"
    lines = article.get("heading", [article["title"]])
    title_markup = "".join(f'<span>{esc(line)}</span>' if i == 0 else f'<em>{esc(line)}</em>' for i, line in enumerate(lines))
    sections = "".join(f'<h2>{esc(section["title"])}</h2><p>{esc(section["text"])}</p>' for section in article.get("sections", []))
    return f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="theme-color" content="#111513"><meta name="description" content="{esc(article["description"])}"><meta name="robots" content="index,follow"><link rel="canonical" href="{esc(canonical)}"><link rel="icon" type="image/png" href="/assets/brand/ridenow-mark-light.png"><link rel="preload" href="/assets/fonts/plus-jakarta-sans-variable.ttf" as="font" type="font/ttf" crossorigin><link rel="stylesheet" href="/styles/site.css"><link rel="stylesheet" href="/styles/global.css"><link rel="stylesheet" href="/styles/pages.css"><title>{esc(article["title"])} | RideNow</title><meta property="og:type" content="article"><meta property="og:url" content="{esc(canonical)}"><meta property="og:title" content="{esc(article["title"])} | RideNow"><meta property="og:description" content="{esc(article["description"])}"><meta property="og:image" content="https://ridenow.lk/assets/images/ridenow-colombo-bluehour.webp"></head><body class="global-page" data-route="newsroom-article">{header()}<main id="main"><section class="route-hero route-hero--article" style="--route-image:url('/assets/images/ridenow-colombo-bluehour.webp')" aria-labelledby="page-title"><div class="route-hero__wash" aria-hidden="true"></div><div class="page-wrap route-hero__content"><nav class="breadcrumbs" aria-label="Breadcrumb"><ol><li><a href="/">Home</a></li><li><a href="/newsroom/">Newsroom</a></li><li aria-current="page">{esc(article["title"])}</li></ol></nav><p class="kicker route-kicker">{esc(article["category"])}</p><h1 id="page-title">{title_markup}</h1><p class="route-hero__lede">{esc(article["lede"])}</p></div></section><article class="article-body page-wrap"><p class="article-body__intro">RideNow is an original Sri Lankan ride-hailing project in development. This website shares the direction of its Rider and Driver experiences; it is not a live transport service.</p>{sections}<p class="article-body__back"><a class="text-link text-link--dark" href="/newsroom/">Back to the Newsroom <span aria-hidden="true">↗</span></a></p></article></main>{footer()}<script type="module" src="/src/global.mjs"></script></body></html>'''

for article in ARTICLES:
    write("newsroom/" + article["slug"], article_html(article))

faq_alias = '''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex,follow"><link rel="canonical" href="https://ridenow.lk/help/"><meta http-equiv="refresh" content="0;url=/help/"><title>RideNow FAQs — Help Center</title><script>location.replace('/help/'+location.search+location.hash)</script></head><body><main><h1>RideNow Help Center</h1><p>FAQs are part of the Help Center. <a href="/help/">Go to Help Center</a>.</p></main></body></html>'''
write("faq", faq_alias)

not_found = '''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="theme-color" content="#111513"><meta name="robots" content="noindex,follow"><link rel="stylesheet" href="/styles/site.css"><link rel="stylesheet" href="/styles/global.css"><link rel="stylesheet" href="/styles/pages.css"><title>Page not found | RideNow</title></head><body class="global-page"><!-- header inserted below -->''' + header() + '''<main id="main"><section class="route-hero route-hero--not-found"><div class="page-wrap route-hero__content"><p class="kicker route-kicker">404 · NOT FOUND</p><h1><span>This road</span><em>ends here.</em></h1><p class="route-hero__lede">That page isn’t available. Check the address or head back to RideNow.</p><a class="button button--yellow" href="/">Return to RideNow <span aria-hidden="true">↗</span></a></div></section></main>''' + footer() + '''<script type="module" src="/src/global.mjs"></script></body></html>'''
(ROOT / "404.html").write_text(not_found + "\n")

routes = ["", "driver", "privacy", "terms", "help", "about", "contact", "how-it-works", "fares", "cities", "safety", "newsroom", "business", "partners", "careers", "accessibility", "download"] + [f"newsroom/{item['slug']}" for item in ARTICLES]
sitemap = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">" + "".join(f"<url><loc>https://ridenow.lk/{route + '/' if route else ''}</loc></url>" for route in routes) + "</urlset>\n"
(ROOT / "sitemap.xml").write_text(sitemap)
print(f"Rendered {len(PAGES)} pages, {len(ARTICLES)} article(s), an FAQ alias, a sitemap, and a 404 page.")
