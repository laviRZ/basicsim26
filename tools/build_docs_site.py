"""Build the docs website: the English pages and their Hebrew and Spanish translations.

The English pages are README.md and EXERCISES.md at the repository root, used as they are. The
translations come from the cache files in docs/i18n/<lang>/ (see tools/translate_docs.py). This
script lays them out for MkDocs in build/docs-site-src/ and builds build/docs-site/:

    python tools/build_docs_site.py           # build
    python tools/build_docs_site.py --serve   # build and preview at http://127.0.0.1:8000

A translation that is behind the English (a run that stopped part-way, or no API key) gets a note
at the top pointing to the English page. A page with no translation at all shows the English.

The GitHub workflow .github/workflows/docs-site.yml translates, runs this, and publishes the
result to GitHub Pages on every push to master.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

from translate_docs import I18N_DIR, LANGUAGES, ROOT, SOURCES, load_cache, section_hash, split_sections

SITE_SRC = ROOT / "build" / "docs-site-src"
IMAGES = ROOT / "docs" / "images"

# Each source file's page in the site, and that page's English address under the site URL.
PAGE_NAMES = {"README.md": "index.md", "EXERCISES.md": "EXERCISES.md"}
ENGLISH_PATHS = {"README.md": "", "EXERCISES.md": "EXERCISES/"}

STALE_NOTE = {
    "he": "התרגום של העמוד הזה עדיין לא כולל את השינויים האחרונים. "
    "[לגרסה העדכנית באנגלית]({english}).",
    "es": "La traducción de esta página todavía no incluye los últimos cambios. "
    "[Ver la versión actualizada en inglés]({english}).",
}

# Code reads left to right inside right-to-left pages, including code inside a sentence.
EXTRA_CSS = """\
[dir="rtl"] .md-typeset pre,
[dir="rtl"] .md-typeset code,
[dir="rtl"] .md-typeset kbd {
  direction: ltr;
  unicode-bidi: isolate;
}
[dir="rtl"] .md-typeset pre {
  text-align: left;
}
"""


def site_url() -> str:
    """The published address, from mkdocs.yml; MkDocs rewrites relative links across languages."""
    for line in (ROOT / "mkdocs.yml").read_text(encoding="utf-8").splitlines():
        if line.startswith("site_url:"):
            return line.split(":", 1)[1].strip().rstrip("/") + "/"
    sys.exit("mkdocs.yml has no site_url.")


def adapt(markdown: str) -> str:
    """Points the repository's own links at their places in the site."""
    return markdown.replace("](README.md", "](index.md").replace("](docs/images/", "](images/")


def lay_out() -> None:
    if SITE_SRC.exists():
        shutil.rmtree(SITE_SRC)
    english = SITE_SRC / "en"
    english.mkdir(parents=True)
    shutil.copytree(IMAGES, english / "images")
    (english / "stylesheets").mkdir()
    (english / "stylesheets" / "extra.css").write_text(EXTRA_CSS, encoding="utf-8")

    for source in SOURCES:
        text = (ROOT / source).read_text(encoding="utf-8")
        (english / PAGE_NAMES[source]).write_text(adapt(text), encoding="utf-8")

    for language in LANGUAGES:
        for source in SOURCES:
            # The page is put together from the cache, so it needs nothing but the cache files the
            # workflow downloads: each English section's translation, or the English itself.
            cache = load_cache(language, source)
            if not cache:
                continue
            english = split_sections((ROOT / source).read_text(encoding="utf-8"))
            parts = [cache.get(section_hash(section)) for section in english]
            text = adapt("".join(part or section for part, section in zip(parts, english)))
            if None in parts:
                note = STALE_NOTE[language].format(english=site_url() + ENGLISH_PATHS[source])
                title, _, rest = text.partition("\n")
                text = f"{title}\n\n!!! warning \"\"\n    {note}\n{rest}"
            page = SITE_SRC / language / PAGE_NAMES[source]
            page.parent.mkdir(parents=True, exist_ok=True)
            page.write_text(text, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--serve", action="store_true", help="preview the site locally")
    args = parser.parse_args()

    lay_out()
    command = ["serve"] if args.serve else ["build", "--strict"]
    result = subprocess.run(
        [sys.executable, "-m", "mkdocs", *command, "-f", str(ROOT / "mkdocs.yml")], cwd=ROOT
    )
    sys.exit(result.returncode)


if __name__ == "__main__":
    main()
