#!/usr/bin/env python3
"""
Kafe Keyboard: build the Arabic (`ar`) language pack, src/main/assets/kafe/langpack-ar.zip.

Source: wordfreq `ar` (CC BY-SA 4.0), the same frequency data behind part of the shipped
Persian and Turkish packs. CleverKeys' own pipeline does the rest: build_langpack.py
(build_dictionary.py for the CKDT V2 binary, generate_unigrams.py, NOTICE.txt and the
manifest's attribution), so the pack has exactly the format the app imports.

Filtering (wordfreq's tokens, most frequent first):
  * Arabic letters only, U+0621..U+064A without the tatweel U+0640 (drops Latin, digits,
    diacritics, Persian/Urdu-only letters and mixed tokens);
  * at least 2 letters, except the conjunction و;
  * the first --limit survivors (default 50,000, like the Persian pack).

Kept in scripts/kafe/ so upstream CleverKeys merges never touch it.

Usage (needs `pip install wordfreq`):
    python scripts/kafe/build_ar_langpack.py
"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

import wordfreq

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "scripts"
OUT_DIR = SCRIPTS / "dictionaries" / "ar"
ARABIC_WORD = re.compile("^[ء-ؿف-ي]+$")
SINGLE_LETTER_WORDS = {"و"}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--depth", type=int, default=120_000, help="wordfreq candidates to read")
    ap.add_argument("--limit", type=int, default=50_000, help="words to keep")
    ap.add_argument("--version", type=int, default=1, help="pack version")
    ap.add_argument("--output", type=Path, default=ROOT / "src/main/assets/kafe/langpack-ar.zip")
    args = ap.parse_args()

    words = []
    for w in wordfreq.top_n_list("ar", args.depth):
        if not ARABIC_WORD.match(w):
            continue
        if len(w) < 2 and w not in SINGLE_LETTER_WORDS:
            continue
        words.append(w)
        if len(words) >= args.limit:
            break
    if len(words) < args.limit:
        print(f"warning: only {len(words):,} words survived from {args.depth:,} candidates", file=sys.stderr)

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    word_file = OUT_DIR / "ar_words.txt"
    word_file.write_text("\n".join(words) + "\n", encoding="utf-8")
    print(f"wrote {word_file} ({len(words):,} words)")

    cmd = [
        sys.executable, str(SCRIPTS / "build_langpack.py"),
        "--lang", "ar", "--name", "Arabic",
        "--input", str(word_file), "--use-wordfreq",
        "--version", str(args.version),
        "--data-source", "wordfreq",
        "--output", str(args.output),
    ]
    print("running:", " ".join(cmd))
    return subprocess.call(cmd, cwd=str(SCRIPTS))


if __name__ == "__main__":
    sys.exit(main())
