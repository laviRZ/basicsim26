"""Translate the English docs into Hebrew and Spanish with Claude.

English is the only source of truth: README.md and EXERCISES.md at the repository root. This
script writes generated translations to docs/i18n/<lang>/<FILE>.md, which nobody edits by hand;
to change a translation, change the English (or this script's context) and run it again.

Each page is split into sections at its level-2 headings. A section is translated only when its
English text changed since the last run (docs/i18n/<lang>/<FILE>.sections.json remembers a hash of
the English each translation came from), so a one-line edit retranslates one section, and the
review diff shows only what changed.

Every translation is checked before it is kept: code blocks, link and image targets, and the
heading and table structure must match the English exactly. A section that fails is retried with
the failure explained, and the run stops rather than write a broken page.

    python tools/translate_docs.py            # translate what changed (needs ANTHROPIC_API_KEY)
    python tools/translate_docs.py --check    # list stale sections, call nothing
    python tools/translate_docs.py --fake     # fill in placeholder "translations" for testing

The GitHub workflow .github/workflows/docs-translate.yml runs it on every push to master and opens
a pull request with the result.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ["README.md", "EXERCISES.md"]
LANGUAGES = {"he": "Hebrew", "es": "Spanish"}
I18N_DIR = ROOT / "docs" / "i18n"

MODEL = "claude-opus-5-5"
MAX_ATTEMPTS = 3

NO_CREDENTIALS = (
    "No Claude API credentials. Set ANTHROPIC_API_KEY: on GitHub, add a repository secret of that "
    "name under Settings > Secrets and variables > Actions."
)

FENCE = re.compile(r"^(```|~~~)")
LINK_TARGET = re.compile(r"\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")


# --- Splitting and comparing -------------------------------------------------------------------


def split_sections(text: str) -> list[str]:
    """Splits Markdown at level-2 headings outside code blocks; the first part is the title."""
    sections: list[list[str]] = [[]]
    in_fence = False
    for line in text.splitlines(keepends=True):
        if FENCE.match(line.strip()):
            in_fence = not in_fence
        if not in_fence and line.startswith("## ") and sections[-1]:
            sections.append([])
        sections[-1].append(line)
    return ["".join(lines) for lines in sections if lines]


def section_hash(section: str) -> str:
    return hashlib.sha256(section.encode("utf-8")).hexdigest()


def code_blocks(text: str) -> list[str]:
    blocks, current, in_fence = [], [], False
    for line in text.splitlines():
        if FENCE.match(line.strip()):
            if in_fence:
                blocks.append("\n".join(current))
                current = []
            in_fence = not in_fence
            continue
        if in_fence:
            current.append(line)
    return blocks


def prose(text: str) -> list[str]:
    """The lines outside code blocks."""
    lines, in_fence = [], False
    for line in text.splitlines():
        if FENCE.match(line.strip()):
            in_fence = not in_fence
            continue
        if not in_fence:
            lines.append(line)
    return lines


def problems(english: str, translated: str) -> list[str]:
    """What a translation got structurally wrong, in words the translator can act on."""
    found = []
    if code_blocks(english) != code_blocks(translated):
        found.append(
            "The code blocks differ from the English. Every fenced code block must be copied "
            "exactly, character for character, including its comments."
        )
    english_prose, translated_prose = prose(english), prose(translated)
    english_targets = sorted(LINK_TARGET.findall("\n".join(english_prose)))
    translated_targets = sorted(LINK_TARGET.findall("\n".join(translated_prose)))
    if english_targets != translated_targets:
        found.append(
            f"The link and image targets differ. The English has {english_targets}; the "
            f"translation has {translated_targets}. Keep every (target) exactly as it is."
        )
    for level in ("# ", "## ", "### ", "#### "):
        count_en = sum(line.startswith(level) for line in english_prose)
        count_tr = sum(line.startswith(level) for line in translated_prose)
        if count_en != count_tr:
            found.append(
                f"The English has {count_en} '{level.strip()}' headings and the translation has "
                f"{count_tr}. Keep one translated heading for each English heading, at the same "
                "level."
            )
    table_en = sum(line.lstrip().startswith("|") for line in english_prose)
    table_tr = sum(line.lstrip().startswith("|") for line in translated_prose)
    if table_en != table_tr:
        found.append(
            f"The English has {table_en} table rows and the translation has {table_tr}. Keep "
            "every table row, with the same number of columns."
        )
    return found


# --- The translator ----------------------------------------------------------------------------

CONTEXT = """\
You translate the documentation of an open-source teaching project for FIRST Robotics \
Competition (FRC) teams.

The project is a simulated FRC robot, the "Block Bot". Students write real robot code for it \
in Java with WPILib 2026 (command-based programming, Commands v2) and AdvantageKit logging, run \
it in the WPILib simulator, and watch it move in AdvantageScope's 3D view. The README gets a \
student set up; the exercises take them through seven stages, from spinning a roller to swerve \
drive and odometry. The readers are high-school students, many of them new to programming, \
reading on their own or with a mentor. The English is written for them on purpose: short \
sentences, everyday words, each new term explained the first time it is needed. Keep that \
voice: friendly, direct, second person, plain. A translation that reads like a textbook or a \
manual has failed even if it is accurate.

Think about what the student will actually do with each piece of text:

- They type the code. Everything in code blocks and inline code (class and method names, \
constants, file names, log keys such as Roller/PositionRadians) must stay exactly as written, \
because it has to match the code and the logs character for character.
- They click through software whose interface is in English: VS Code and its WPILib commands, \
the WPILib simulator window, AdvantageScope's menus and tabs, keyboard keys. Quote those labels \
as the student will see them on screen, in English; explain them in the target language if that \
helps.
- They talk to teammates and mentors and search the web. FRC teams in each language have their \
own habits: many technical terms (subsystem, command, PID, swerve, odometry, feedforward...) are \
usually said in English, or in a well-known local form. Use the words a student on an FRC team \
in that language would really use and recognise, and when a term is normally kept in English, \
keep it, perhaps with a short translation the first time it is explained. Be consistent with \
the rest of the existing translation.

You receive the whole English document for context, the current translation of the rest of the \
document, and one section to translate. Translate only that section and return only its \
translated Markdown: no preamble, no notes, no wrapping code fence.

Keep the Markdown structure exactly: the same headings at the same levels, the same lists, \
tables with the same rows and columns, and every fenced code block copied unchanged, comments \
included. Keep every link and image target (the part in parentheses) unchanged; translate the \
link text. Keep bold and italics on the corresponding words.\
"""

HEBREW_NOTES = """\
The translation is Hebrew and is shown right-to-left, with code shown left-to-right. Write \
natural Israeli Hebrew as students and mentors on Israeli FRC teams speak it.\
"""

SPANISH_NOTES = """\
The translation is Spanish. Write neutral Spanish that students in Spain and Latin America both \
read comfortably, as FRC teams in Spanish-speaking countries speak it; address the reader as tú.\
"""

LANGUAGE_NOTES = {"he": HEBREW_NOTES, "es": SPANISH_NOTES}


def translate_section(
    client, language: str, document: str, translation_so_far: str, section: str
) -> str:
    """Asks Claude for one section, checking and retrying until it keeps the structure."""
    import anthropic

    system = [
        {"type": "text", "text": CONTEXT},
        {"type": "text", "text": LANGUAGE_NOTES[language]},
    ]
    # The whole English document is the same for every section of a run, so it is cached.
    english_block = {
        "type": "text",
        "text": f"<english_document>\n{document}\n</english_document>",
        "cache_control": {"type": "ephemeral"},
    }
    request = (
        f"<current_{LANGUAGES[language].lower()}_translation>\n{translation_so_far}\n"
        f"</current_{LANGUAGES[language].lower()}_translation>\n\n"
        f"<section_to_translate>\n{section}</section_to_translate>\n\n"
        f"Translate the section into {LANGUAGES[language]}."
    )
    messages = [{"role": "user", "content": [english_block, {"type": "text", "text": request}]}]

    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            with client.beta.messages.stream(
                model=MODEL,
                max_tokens=64000,
                system=system,
                messages=messages,
                thinking={"type": "adaptive"},
                output_config={"effort": "high"},
                # If a safety classifier declines, the API reruns the request on a fallback
                # model chosen for that kind of decline.
                betas=["server-side-fallback-2026-07-01"],
                fallbacks="default",
            ) as stream:
                response = stream.get_final_message()
        except anthropic.RateLimitError as error:
            sys.exit(f"Rate limited by the Claude API; run again later. ({error.message})")
        except anthropic.APIStatusError as error:
            sys.exit(f"The Claude API returned {error.status_code}: {error.message}")
        except anthropic.APIConnectionError:
            sys.exit("Could not reach the Claude API.")
        except TypeError as error:
            # The SDK finds out it has no credentials only when it sends the first request.
            if "authentication" not in str(error):
                raise
            sys.exit(NO_CREDENTIALS)

        if response.stop_reason == "refusal":
            sys.exit(f"Claude declined to translate a section into {LANGUAGES[language]}.")
        if response.stop_reason == "max_tokens":
            sys.exit("A translation was cut off at max_tokens.")
        text = "".join(block.text for block in response.content if block.type == "text")
        translated = text.strip("\n") + "\n" + ("\n" if section.endswith("\n\n") else "")

        found = problems(section, translated)
        if not found:
            return translated
        print(f"    attempt {attempt} rejected: {' '.join(found)}")
        messages += [
            {"role": "assistant", "content": response.content},
            {
                "role": "user",
                "content": "That translation cannot be used:\n- "
                + "\n- ".join(found)
                + "\nTranslate the section again, fixing this. Return only the Markdown.",
            },
        ]
    sys.exit(f"Gave up on a {LANGUAGES[language]} section after {MAX_ATTEMPTS} attempts.")


def fake_section(language: str, section: str) -> str:
    """A placeholder translation with the right structure, for testing the pipeline offline."""
    out, in_fence = [], False
    for line in section.splitlines(keepends=True):
        if FENCE.match(line.strip()):
            in_fence = not in_fence
        if not in_fence and line.startswith("#"):
            hashes, _, title = line.partition(" ")
            line = f"{hashes} [{language}] {title}"
        out.append(line)
    return "".join(out)


# --- The cache ---------------------------------------------------------------------------------


def cache_path(language: str, source: str) -> Path:
    return I18N_DIR / language / f"{Path(source).stem}.sections.json"


def load_cache(language: str, source: str) -> dict[str, str]:
    """English section hash -> its translation."""
    path = cache_path(language, source)
    if not path.exists():
        return {}
    entries = json.loads(path.read_text(encoding="utf-8"))["sections"]
    return {entry["source_sha256"]: entry["translation"] for entry in entries}


def stale_sections(language: str, source: str) -> list[int]:
    """The indexes of the English sections with no up-to-date translation."""
    cache = load_cache(language, source)
    english = (ROOT / source).read_text(encoding="utf-8")
    return [
        i for i, section in enumerate(split_sections(english)) if section_hash(section) not in cache
    ]


def first_line(section: str) -> str:
    return section.strip().splitlines()[0][:70]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="list stale sections and exit")
    mode.add_argument("--fake", action="store_true", help="write placeholder translations")
    args = parser.parse_args()

    if args.check:
        stale = 0
        for language in LANGUAGES:
            for source in SOURCES:
                english = split_sections((ROOT / source).read_text(encoding="utf-8"))
                for i in stale_sections(language, source):
                    print(f"{language}/{source}: {first_line(english[i])}")
                    stale += 1
        print(f"{stale} stale section(s).")
        return

    client = None
    if not args.fake:
        import anthropic

        try:
            client = anthropic.Anthropic()
        except anthropic.AnthropicError as error:
            sys.exit(f"{NO_CREDENTIALS} ({error})")

    for language, name in LANGUAGES.items():
        for source in SOURCES:
            document = (ROOT / source).read_text(encoding="utf-8")
            sections = split_sections(document)
            cache = load_cache(language, source)
            translated = [cache.get(section_hash(section)) for section in sections]
            todo = [i for i, text in enumerate(translated) if text is None]
            print(f"{name} {source}: {len(todo)} of {len(sections)} section(s) to translate")

            for i in todo:
                print(f"  {first_line(sections[i])}")
                if args.fake:
                    translated[i] = fake_section(language, sections[i])
                    continue
                so_far = "".join(
                    text if text is not None else f"[{first_line(sections[j])}: not translated yet]\n\n"
                    for j, text in enumerate(translated)
                    if j != i
                )
                translated[i] = translate_section(client, language, document, so_far, sections[i])

            out_dir = I18N_DIR / language
            out_dir.mkdir(parents=True, exist_ok=True)
            # Only the current sections are kept, so retired English text drops out of the cache.
            entries = [
                {"source_sha256": section_hash(section), "translation": text}
                for section, text in zip(sections, translated)
            ]
            cache_path(language, source).write_text(
                json.dumps({"model": MODEL, "sections": entries}, ensure_ascii=False, indent=2)
                + "\n",
                encoding="utf-8",
                newline="\n",
            )
            (out_dir / source).write_text("".join(translated), encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
