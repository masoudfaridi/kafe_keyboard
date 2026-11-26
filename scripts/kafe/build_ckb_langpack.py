#!/usr/bin/env python3
"""
Kafe Keyboard: build the Central Kurdish / Sorani (`ckb`) language pack,
src/main/assets/kafe/langpack-ckb.zip.

Sources (downloaded to ~/.cache/kafe-corpora/ckb/, never into the repo):
  * Frequencies: kww, the Kurdish Wikipedia word list with counts by Dolan Hêriş
    (https://github.com/0xdolan/kww, MIT; text from Kurdish Wikipedia, CC BY-SA 4.0).
  * Inclusion oracle: KurdishHunspell ckb-Arab by Sina Ahmadi
    (https://github.com/sinaahmadi/KurdishHunspell, CC BY-SA 4.0), read with spylls,
    so inflected forms (کتێبەکان، دەنووسم) are validated, not just lemmas.

Method, like the Persian pack (frequency source + spell-checker oracle):
  1. Normalise Arabic-script variants to Sorani letters (ي ى -> ی, ك -> ک, tatweel and
     harakat dropped); ە (U+06D5) and ه (U+0647) stay distinct, they are different letters
     in Sorani. Counts of spellings that normalise to the same word are summed.
  2. Keep only words made of Sorani letters, 2+ letters long except the conjunction و.
  3. Rank by count. A word is kept when Hunspell accepts it; the most frequent
     --band words are kept even without Hunspell (function words, place and person names
     the lexicon lacks), as long as they pass step 2.
  4. The first --limit survivors go to CleverKeys' own build_langpack.py (CKDT V2
     binary, unigrams, NOTICE and manifest attribution).

Kept in scripts/kafe/ so upstream CleverKeys merges never touch it.

Usage (needs `pip install spylls`):
    python scripts/kafe/build_ckb_langpack.py
"""
import argparse
import hashlib
import re
import subprocess
import sys
import urllib.request
from collections import Counter
from pathlib import Path

from spylls.hunspell import Dictionary

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "scripts"
CACHE = Path.home() / ".cache" / "kafe-corpora" / "ckb"

SOURCES = {
    "word_and_freqs.txt": "https://raw.githubusercontent.com/0xdolan/kww/main/txt-files/word_and_freqs.txt",
    "ckb-Arab.dic": "https://raw.githubusercontent.com/sinaahmadi/KurdishHunspell/main/ckb/ckb-Arab.dic",
    "ckb-Arab.aff": "https://raw.githubusercontent.com/sinaahmadi/KurdishHunspell/main/ckb/ckb-Arab.aff",
}

# Sorani alphabet (Arabic script) plus hamza.
SORANI = "ئابپتجچحخدرڕزژسشعغفڤقکگلڵمنهەوۆیێء"
SORANI_WORD = re.compile(f"^[{SORANI}]+$")
FOLD = str.maketrans({"ي": "ی", "ى": "ی", "ك": "ک", "ڪ": "ک", "ـ": None})
HARAKAT = re.compile("[ً-ْٰ]")
SINGLE_LETTER_WORDS = {"و"}

ATTRIBUTION = (
    "Word frequencies: kww Kurdish Wikipedia word list by Dolan Hêriş (MIT), counted from "
    "Kurdish Wikipedia (CC BY-SA 4.0); inclusion oracle: KurdishHunspell ckb-Arab lexicon and "
    "affix rules by Sina Ahmadi (CC BY-SA 4.0). Modified: normalised, filtered and converted to "
    "a CleverKeys frequency list. Full text: NOTICE.txt"
)
SOURCE_URLS = "https://github.com/0xdolan/kww https://github.com/sinaahmadi/KurdishHunspell https://ckb.wikipedia.org"


def fetch() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    for name, url in SOURCES.items():
        path = CACHE / name
        if not path.exists():
            print(f"downloading {url}")
            urllib.request.urlretrieve(url, path)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()[:16]
        print(f"  {name}: {path.stat().st_size:,} bytes, sha256 {digest}…")


def normalise(word: str) -> str:
    return HARAKAT.sub("", word.strip().translate(FOLD))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--limit", type=int, default=50_000, help="words to keep")
    ap.add_argument("--band", type=int, default=3_000, help="top words kept without Hunspell")
    ap.add_argument("--min-count", type=int, default=2, help="drop words seen fewer times")
    ap.add_argument("--version", type=int, default=1, help="pack version")
    ap.add_argument("--output", type=Path, default=ROOT / "src/main/assets/kafe/langpack-ckb.zip")
    args = ap.parse_args()

    fetch()
    counts: Counter = Counter()
    for line in (CACHE / "word_and_freqs.txt").read_text(encoding="utf-8").splitlines():
        word, _, n = line.rpartition(",")
        if not word or not n.strip().isdigit():
            continue
        w = normalise(word)
        if SORANI_WORD.match(w) and (len(w) >= 2 or w in SINGLE_LETTER_WORDS):
            counts[w] += int(n)
    ranked = [w for w, n in counts.most_common() if n >= args.min_count]
    print(f"candidates after normalising and filtering: {len(ranked):,}")

    hunspell = Dictionary.from_files(str(CACHE / "ckb-Arab"))
    kept, by_band, by_oracle = [], 0, 0
    for rank, w in enumerate(ranked):
        if rank < args.band:
            kept.append(w)
            by_band += 1
        elif hunspell.lookup(w):
            kept.append(w)
            by_oracle += 1
        if len(kept) >= args.limit:
            break
    print(f"kept {len(kept):,} words ({by_band:,} from the top band, {by_oracle:,} accepted by Hunspell)")

    out_dir = CACHE / "build"
    out_dir.mkdir(exist_ok=True)
    word_file = out_dir / "ckb_words.txt"
    word_file.write_text("".join(f"{w}\t{counts[w]}\n" for w in kept), encoding="utf-8")
    print(f"wrote {word_file}")
    print("sample:", " ".join(kept[:30]))

    cmd = [
        sys.executable, str(SCRIPTS / "build_langpack.py"),
        "--lang", "ckb", "--name", "Central Kurdish",
        "--input", str(word_file),
        "--version", str(args.version),
        "--license", "CC-BY-SA-4.0",
        "--attribution", ATTRIBUTION,
        "--source", SOURCE_URLS,
        "--output", str(args.output),
    ]
    return subprocess.call(cmd, cwd=str(SCRIPTS))


if __name__ == "__main__":
    sys.exit(main())
