"""
Print the CSP source expression for the one inline <script> the site ships:
the Plausible queue stub in every page's <head>.

`script-src` in the pages' CSP <meta> carries no 'unsafe-inline', so that snippet is
allow-listed by the SHA-256 of its contents instead. A browser hashes the text
node exactly as written — every space and both surrounding newlines included —
so this reads the bytes straight out of the file and does not reformat them.

    python3 tools/csp-hash.py

Paste the result into the script-src of the CSP <meta> on every page. All four pages have to
carry the identical snippet, because one hash covers all of them; that is
checked here rather than left to be discovered in production, where a stale
hash means the tracker is blocked and nothing looks wrong on the page.

Exits non-zero if the pages disagree or if the hash in any page's CSP is stale, so
it doubles as a CI check.
"""

import base64
import hashlib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PAGES = ["index.html", "404.html", "impressum.html", "contribute.html"]

INLINE = re.compile(r"<script>(.*?)</script>", re.S)
CSP_META = re.compile(r'<meta http-equiv="Content-Security-Policy" content="([^"]*)">')


def inline_script(page):
    """The contents of the page's single src-less <script>."""
    blocks = INLINE.findall((ROOT / page).read_text(encoding="utf-8"))
    if len(blocks) != 1:
        sys.exit(f"{page}: expected exactly 1 inline script, found {len(blocks)}")
    return blocks[0]


def main():
    reference = inline_script(PAGES[0])
    for page in PAGES[1:]:
        if inline_script(page) != reference:
            sys.exit(f"{page}: inline script differs from {PAGES[0]}; "
                     f"one hash cannot cover both")

    digest = base64.b64encode(hashlib.sha256(reference.encode("utf-8")).digest())
    source = f"'sha256-{digest.decode()}'"
    print(source)

    policies = {}
    for page in PAGES:
        found = CSP_META.findall((ROOT / page).read_text(encoding="utf-8"))
        if len(found) != 1:
            sys.exit(f"{page}: expected exactly 1 CSP <meta>, found {len(found)}")
        policies[page] = found[0]
    if len(set(policies.values())) != 1:
        sys.exit("the CSP <meta> differs between pages; keep them identical")
    if source not in policies[PAGES[0]]:
        sys.exit("the CSP <meta> does not carry this hash — the tracker is blocked "
                 "on every page until you paste it into script-src")

if __name__ == "__main__":
    main()
