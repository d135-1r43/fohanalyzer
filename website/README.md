<p align="center">
  <img src="assets/img/logo.svg" alt="" width="112">
</p>

<h1 align="center">fohanalyzer.com</h1>

<p align="center">
  The website for <a href="https://github.com/d135-1r43/fohanalyzer">FOHanalyzer</a>,
  an open-source dual-channel RTA for front-of-house engineers.<br>
  Static HTML, no framework, no build step.
</p>

## What this is

Three pages and a 404, served by GitHub Pages from this directory of the application repository. There is no bundler, no
transpiler and no `package.json` — what is in this repository is byte-for-byte what is
served. Editing a file and reloading the browser is the whole development loop.

That is a deliberate match for the application it describes, which ships five runtime
dependencies and no config file of its own.

```
index.html          the product, the open-source case, the contribution pitch
contribute.html     the contributor guide
impressum.html      the German legal notice (§ 5 DDG, § 18 Abs. 2 MStV)
404.html            served by GitHub Pages for anything else
assets/css/         tokens · base · components
assets/js/          three custom elements, no dependencies
assets/fonts/       Barlow + IBM Plex Mono, subset to WOFF2
assets/img/         logo and screenshots, taken from the app repository
tools/make-og.py    regenerates the link-preview card
tools/csp-hash.py   re-hashes the inline analytics snippet for the CSP
```

## Running it locally

Any static file server will do:

```bash
python3 -m http.server 8099
# http://127.0.0.1:8099
```

GitHub Pages also resolves extensionless URLs (`/contribute` → `contribute.html`) and serves
`404.html` for unknown paths; a plain local server does neither.

## Deploying

`.github/workflows/pages.yml` in the repository root publishes this directory to GitHub
Pages on every push to `master` that touches `website/`. Pull requests run the same checks
without deploying.

Every run checks the inline-script CSP hash is current and that the Impressum is still
published with its address block, then uploads the pages, `assets/`, `robots.txt` and
`sitemap.xml`. This README and `tools/` are not published.

GitHub Pages cannot set response headers, so the security policy lives in a
`<meta http-equiv="Content-Security-Policy">` in every page's `<head>`, identical on all
four. A `<meta>` CSP cannot express `frame-ancestors`, and `X-Frame-Options`,
`X-Content-Type-Options` and `Permissions-Policy` have no `<meta>` form at all, so those are
not sent. The referrer policy is set with `<meta name="referrer">`. GitHub Pages caches
everything for ten minutes, so a deploy is live for everyone shortly after it lands.

## How the CSS is organised

Three files, loaded in this order, each with one job:

**`tokens.css`** — custom properties only, no selectors. It is a port of the app's own
[`theme.css`](https://github.com/d135-1r43/fohanalyzer/blob/master/src/main/resources/com/fohanalyzer/theme.css),
keeping the JavaFX names (`-panel`, `-txt-dim`, `-cyan`) as `--panel`, `--txt-dim`,
`--cyan`, so a colour can be traced between the two files without translating it. Change a
colour here and it changes everywhere.

**`base.css`** — `@font-face`, the reset, element defaults, layout primitives
(`.container`, `.section`, `.grid`) and a small closed set of utilities.

**`components.css`** — the named blocks, BEM-style: `.btn`, `.card`, `.chip`, `.readout`,
`.callout`, `.steps`, `.band`, `.site-header`, `.site-footer`. Each is self-contained and
reads only from tokens, so it can be moved to a new page without dragging rules behind it.

Components take arguments through custom properties rather than through variant classes
that repeat themselves. `--accent` is the main one: set it on a `.card` and the dot, the
icon, the hover border and the trailing arrow all follow, because custom properties
inherit.

```html
<article class="card acc-lime">…</article>   <!-- one class recolours the whole block -->
```

The accent classes (`.acc-cyan`, `.acc-lime`, …) exist so that **no page contains an inline
`style` attribute**, which is what lets the CSP ship `style-src 'self'` with no
`'unsafe-inline'`. If you add an inline style, it will silently stop being applied in
production — add a class instead.

The asset filenames are not content-hashed, so when a stylesheet changes, bump the `?v=` on
its `<link>` in all four pages. GitHub Pages only caches for ten minutes, but a returning
visitor whose browser is mid-revalidation can otherwise briefly pair the old CSS with the
new HTML.

## The JavaScript

`assets/js/components.js` is nearly the entire client-side story: one file, three custom
elements, no dependencies, no network calls, no cookies.

| Element | Does | Without JS |
|---|---|---|
| `<foh-spectrum>` | The animated analyser bars in the hero | Nothing renders; it is decorative and `aria-hidden` |
| `<foh-copy>` | Adds a copy button to a `<pre>` | The code block is still there and still selectable |
| `<foh-reveal>` | Fades a block in on scroll | Content is simply visible |

None of them create content. Every page is complete, readable and navigable with the file
blocked, which is also why they are indexable. The spectrum suspends when scrolled out of
view or when the tab is hidden, and paints a single static frame under
`prefers-reduced-motion`.

Their `display: block` is declared in `components.css`, not injected from script — an
unknown element defaults to `display: inline`, and setting it from JS would mislay the
layout until the script ran.

## Analytics

The one thing the site loads from another origin is the tracker of a self-hosted
[Plausible](https://plausible.io) instance at `plausible.herhoffer.net`. It sets no cookies,
stores no identifier, and posts an aggregate pageview to `/api/event`; the counted domain is
configured on the instance, not in the page. Every page carries the same two-tag snippet in
its `<head>` — an `async` tag for the tracker and an inline stub that queues calls made
before it arrives.

That inline stub is the only inline script on the site, so instead of `'unsafe-inline'` the
CSP allow-lists it by hash. **Edit the snippet and the hash stops matching, which silently
kills tracking on every page** — the pages still render, nothing errors visibly. After any
change to it:

```bash
tools/csp-hash.py   # verifies all four pages match, prints the new sha256-… for the CSP <meta>
```

Removing analytics again means deleting the snippet from the four pages and putting
`script-src 'self'; … connect-src 'none'` back in the CSP `<meta>` of all four pages — plus the `Datenschutz`
section of `impressum.html` and the footer line, which both describe what is running.

## Fonts

Barlow and IBM Plex Mono, the same two families the app bundles, taken from the same
OFL-licensed files and subset to Latin plus box-drawing:

```
486 KB of TTF  →  87 KB of WOFF2
```

They are self-hosted. Nothing on this site is fetched from a third party, so there is no
consent banner to show, nothing leaked to a CDN, and nothing to break when someone else's
service has an outage. Both licence texts are in `assets/fonts/`.

Regenerating them needs `fonttools` and `brotli`:

```bash
pyftsubset Barlow-Regular.ttf \
  --unicodes="U+0000-00FF,U+2000-206F,U+2190-21FF,U+2500-257F,U+25A0-25FF" \
  --layout-features='*' --flavor=woff2 --output-file=Barlow-Regular.woff2
```

## The link preview

Every page carries a full Open Graph and Twitter card set: type, site name,
locale, canonical URL, title, description, and an image with explicit dimensions
and alt text. `impressum.html` declares `de_DE`, the rest `en_GB`.

They all share one image, `assets/img/og.png`, at the 1200×630 that Open Graph
actually wants — the app screenshot used to stand in for it, and at 1366×820 it
was cropped awkwardly by every consumer that assumes 1.91∶1.

The card is generated, not drawn by hand, so it cannot drift from the site:
`tools/make-og.py` reads the same palette, the same `Logo.java` geometry and the
same cyan→lime→amber ramp the hero uses. Change the wording or a colour and
re-run it:

```bash
python3 -m venv .venv && .venv/bin/pip install Pillow
.venv/bin/python tools/make-og.py
```

Pillow cannot read the subset WOFF2 files in `assets/fonts`, so the text is set
from the original TTFs in the application repository, expected as a sibling
checkout under either `fohanalyzer` or its pre-rename name `fohanalyzer-java`.
Point `FOH_FONTS` elsewhere if yours lives somewhere else. The script
is deterministic — the same inputs give a byte-identical PNG.

## Editing the content

The pages share the same header and footer markup, duplicated rather than templated —
with no build step there is nowhere to compile a partial, and a few copies of thirty lines
is a smaller cost than adding a toolchain. **If you change the nav or the footer, change it in
`index.html`, `contribute.html`, `impressum.html` and `404.html`.**

That is now four copies, which was the point at which this trade was going to stop being
worth it. It still just about holds, because the footer changes rarely. A fifth page should
come with a decision instead: most likely accepting a static site generator, which GitHub
Pages can run in the workflow.

`404.html` uses root-absolute asset paths (`/assets/…`) because it is served at whatever URL
was requested; the other pages use relative paths so they also work opened straight from
disk.

## Legal

`impressum.html` is the operator identification German law requires (§ 5 DDG) together with
the responsible-person entry (§ 18 Abs. 2 MStV). It is written in German because that is the
language the obligation is owed in.

It also carries the data-protection statement, which names GitHub Pages as the host. If the
hosting moves again, that paragraph has to move with it.

## Licence

The site's content and code are **GPL-3.0-or-later**, matching the application.

The bundled fonts are not covered by that and keep their own: IBM Plex Mono (© IBM Corp.)
and Barlow (© The Barlow Project Authors) are both under the
[SIL Open Font License 1.1](https://openfontlicense.org), with the licence texts included in
`assets/fonts/`.
