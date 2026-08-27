#!/usr/bin/env python3
"""Classify local screenshots with Gemini Flash and score against folder/CSV labels.

Usage (from repo root):
  set GEMINI_API_KEY=your-key
  python scripts/eval_gemini_flash.py

Optional:
  python scripts/eval_gemini_flash.py --locate
  python scripts/eval_gemini_flash.py --model gemini-2.5-flash
"""

from __future__ import annotations

import argparse
import base64
import csv
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TESTDATA = ROOT / "testdata"
ADS_DIR = TESTDATA / "ads"
NOT_ADS_DIR = TESTDATA / "not_ads"
LABELS_CSV = TESTDATA / "labels.csv"
QUADRANT_ENUM = (
    "top-left",
    "top-right",
    "bottom-left",
    "bottom-right",
    "not_found",
)

CLASSIFY_PROMPT = """You are classifying a single mobile screenshot.

Return whether this is a FULL-SCREEN interstitial advertisement (or rewarded-ad overlay)
that is blocking the app/game, not the game/app itself.

is_ad=true examples: video/playable interstitial, "tap to install", large X/close,
countdown skip, store-style ad overlay covering most of the screen.

is_ad=false examples: actual gameplay, menus, HUD, small banner ads that do not
take over the screen, system UI.

Do not guess remaining countdown seconds.
"""

LOCATE_PROMPT = """This screenshot is a full-screen mobile ad.

Locate the close (X) control and any Skip (or equivalent) control.
Pick one quadrant for each. If it is absent or too uncertain, use not_found.
Do not invent a control that is not visible.
"""


def load_api_key() -> str:
    env = os.environ.get("GEMINI_API_KEY", "").strip()
    if env:
        return env
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line.startswith("GEMINI_API_KEY="):
                return line.split("=", 1)[1].strip()
    sys.exit(
        "GEMINI_API_KEY missing. Put it in the environment or in local.properties."
    )


def collect_images() -> list[tuple[Path, bool]]:
    items: list[tuple[Path, bool]] = []
    for folder, is_ad in ((ADS_DIR, True), (NOT_ADS_DIR, False)):
        if not folder.exists():
            continue
        for path in sorted(folder.iterdir()):
            if path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}:
                items.append((path, is_ad))
    return items


def load_csv_labels() -> dict[str, dict]:
    if not LABELS_CSV.exists():
        return {}
    out: dict[str, dict] = {}
    with LABELS_CSV.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            name = (row.get("filename") or "").strip()
            if not name:
                continue
            out[name] = row
    return out


def image_part(path: Path) -> dict:
    mime = mimetypes.guess_type(path.name)[0] or "image/jpeg"
    data = base64.b64encode(path.read_bytes()).decode("ascii")
    return {"inline_data": {"mime_type": mime, "data": data}}


def gemini_json(
    api_key: str,
    model: str,
    prompt: str,
    image_paths: list[Path],
    schema: dict,
) -> dict:
    url = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        f"{model}:generateContent?key={api_key}"
    )
    parts = [{"text": prompt}]
    for path in image_paths:
        parts.append(image_part(path))
    body = {
        "contents": [{"role": "user", "parts": parts}],
        "generationConfig": {
            "temperature": 0,
            "responseMimeType": "application/json",
            "responseSchema": schema,
        },
    }
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code}: {detail}") from exc
    text = payload["candidates"][0]["content"]["parts"][0]["text"]
    return json.loads(text)


def classify(api_key: str, model: str, path: Path) -> bool:
    schema = {
        "type": "OBJECT",
        "properties": {"is_ad": {"type": "BOOLEAN"}},
        "required": ["is_ad"],
    }
    result = gemini_json(api_key, model, CLASSIFY_PROMPT, [path], schema)
    return bool(result["is_ad"])


def locate(api_key: str, model: str, path: Path) -> dict:
    schema = {
        "type": "OBJECT",
        "properties": {
            "close_button": {"type": "STRING", "enum": list(QUADRANT_ENUM)},
            "skip_indicator": {"type": "STRING", "enum": list(QUADRANT_ENUM)},
        },
        "required": ["close_button", "skip_indicator"],
    }
    return gemini_json(api_key, model, LOCATE_PROMPT, [path], schema)


def parse_bool(raw: str | None) -> bool | None:
    if raw is None or raw.strip() == "":
        return None
    return raw.strip().lower() in {"1", "true", "yes", "ad"}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=os.environ.get("GEMINI_MODEL", "gemini-2.5-flash"))
    parser.add_argument(
        "--locate",
        action="store_true",
        help="If classified as ad, also ask for close/skip quadrants",
    )
    args = parser.parse_args()

    images = collect_images()
    if not images:
        sys.exit(
            f"No images found. Copy screenshots into:\n  {ADS_DIR}\n  {NOT_ADS_DIR}"
        )

    api_key = load_api_key()
    csv_labels = load_csv_labels()
    correct = 0
    locate_checked = 0
    locate_correct = 0

    print(f"model={args.model}  n={len(images)}")
    for path, folder_is_ad in images:
        expected = folder_is_ad
        row = csv_labels.get(path.name)
        if row:
            csv_is_ad = parse_bool(row.get("is_ad"))
            if csv_is_ad is not None:
                expected = csv_is_ad
        try:
            predicted = classify(api_key, args.model, path)
        except Exception as exc:  # noqa: BLE001
            print(f"{path.name:40} ERROR  {exc}")
            continue

        ok = predicted == expected
        correct += int(ok)
        mark = "OK" if ok else "FAIL"
        extra = ""
        if args.locate and predicted:
            try:
                loc = locate(api_key, args.model, path)
                extra = f"  close={loc.get('close_button')} skip={loc.get('skip_indicator')}"
                if row:
                    locate_checked += 1
                    want_close = (row.get("close_button") or "").strip()
                    want_skip = (row.get("skip_indicator") or "").strip()
                    if want_close and want_skip:
                        if loc.get("close_button") == want_close and loc.get(
                            "skip_indicator"
                        ) == want_skip:
                            locate_correct += 1
            except Exception as exc:  # noqa: BLE001
                extra = f"  locate_error={exc}"
        print(
            f"{path.name:40} {mark:4}  expected={expected} predicted={predicted}{extra}"
        )

    total = len(images)
    print(f"\nclassify accuracy: {correct}/{total}")
    if args.locate and locate_checked:
        print(f"locate accuracy:   {locate_correct}/{locate_checked} (rows with CSV labels)")


if __name__ == "__main__":
    main()
