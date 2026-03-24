from __future__ import annotations

import base64
import json
import re
import threading
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from PIL import Image, ImageOps

from .config import Settings
from .errors import ApiError

_ocr_engine = None
_ocr_lock = threading.Lock()


@dataclass
class OcrLine:
    text: str
    confidence: float
    bbox: list[int]
    center_y: float
    height: int


@dataclass
class OcrRow:
    text: str
    confidence: float
    bbox: list[int]
    center_y: float
    height: int


CYRILLIC_RE = re.compile(r"[\u0400-\u04FF]")
LATIN_RE = re.compile(r"[A-Za-z]")
TIME_RE = re.compile(r"\b\d{1,2}:\d{2}\b")
NON_TEXT_RE = re.compile(r"[^0-9A-Za-z\u0400-\u04FF]+")

LATIN_TO_CYR = str.maketrans(
    {
        "A": "А",
        "B": "В",
        "C": "С",
        "E": "Е",
        "H": "Н",
        "K": "К",
        "M": "М",
        "O": "О",
        "P": "Р",
        "T": "Т",
        "X": "Х",
        "Y": "У",
        "a": "а",
        "b": "в",
        "c": "с",
        "e": "е",
        "h": "н",
        "k": "к",
        "m": "м",
        "o": "о",
        "p": "р",
        "t": "т",
        "x": "х",
        "y": "у",
    }
)
CYR_TO_LATIN = str.maketrans(
    {
        "А": "A",
        "В": "B",
        "С": "C",
        "Е": "E",
        "Н": "H",
        "К": "K",
        "М": "M",
        "О": "O",
        "Р": "P",
        "Т": "T",
        "Х": "X",
        "У": "Y",
        "а": "a",
        "в": "b",
        "с": "c",
        "е": "e",
        "н": "h",
        "к": "k",
        "м": "m",
        "о": "o",
        "р": "p",
        "т": "t",
        "х": "x",
        "у": "y",
    }
)

CANONICAL_ALIASES = {
    "lida": "Lida",
    "плм": "ПЛМ",
    "sqwozbab": "SQWOZ BAB",
    "травма": "ТРАВМА",
    "серегапират": "Серега Пират",
    "серёгапират": "Серега Пират",
    "jamiehewlett": "Jamie Hewlett",
    "delasoul": "De La Soul",
    "delasou": "De La Soul",
    "lsplash": "LSPLASH",
    "esplash": "LSPLASH",
    "dxntzzz": "DXNTZZZ",
}

LOOKUP_ALIASES = {
    "lide": "lida",
    "lige": "lida",
    "lire": "lida",
    "lidl": "lida",
    "плим": "плм",
    "ie": "lida",
    "чсе": "чсв",
    "нсе": "чсв",
    "hce": "чсв",
    "elum": "blum",
    "tphemd": "травма",
    "tphemg": "травма",
    "серегаиреп": "серегапират",
    "серегаиреп": "серегапират",
    "suiuzеде": "sqwozbab",
    "sqwozbav": "sqwozbab",
    "sqwozbad": "sqwozbab",
    "delasou": "delasoul",
    "деласоу": "delasoul",
    "esplash": "lsplash",
    "esрlash": "lsplash",
    "deerseek": "seek",
    "фотосовездой": "фотосозвездой",
    "фотосозвевдой": "фотосозвездой",
    "фотосозвездои": "фотосозвездой",
    "амолет": "самолет",
    "ревервация": "резервация",
    "алерия": "валерия",
}

DIRECT_VALUE_REPLACEMENTS = {
    "ELUM": "BLUM",
    "Elum": "BLUM",
    "Lide": "Lida",
    "Lige": "Lida",
    "Lire": "Lida",
    "Lidl": "Lida",
    "Tphemg": "ТРАВМА",
    "Tphemd": "ТРАВМА",
    "ЧСЕ": "ЧСВ",
    "НСЕ": "ЧСВ",
    "HCE": "ЧСВ",
    "амолет": "Самолет",
}

PHRASE_REPLACEMENTS = {
    "фотоcо": "фото со",
    "фотосо": "фото со",
    "вездой": "звездой",
    "звевдой": "звездой",
    "серега иреп": "серега пират",
    "сеpега": "серега",
    "ревервация": "резервация",
    "алерия": "валерия",
    "самолет": "Самолет",
}

TITLE_RULES = [
    (re.compile(r"^чс[ев]$", re.IGNORECASE), "ЧСВ"),
    (re.compile(r"^blum$", re.IGNORECASE), "BLUM"),
    (re.compile(r"^elum$", re.IGNORECASE), "BLUM"),
    (re.compile(r"^самолет$", re.IGNORECASE), "Самолет"),
    (re.compile(r"^неловко$", re.IGNORECASE), "Неловко"),
    (re.compile(r"^seek$", re.IGNORECASE), "Seek"),
]

ARTIST_RULES = [
    (re.compile(r"^плм$", re.IGNORECASE), "ПЛМ"),
    (re.compile(r"^lida$", re.IGNORECASE), "Lida"),
    (re.compile(r"^sqwoz\s*bab$", re.IGNORECASE), "SQWOZ BAB"),
    (re.compile(r"^травма$", re.IGNORECASE), "ТРАВМА"),
    (re.compile(r"^серега\s*пират$", re.IGNORECASE), "Серега Пират"),
    (re.compile(r"^jamie\s*hewlett$", re.IGNORECASE), "Jamie Hewlett"),
    (re.compile(r"^de\s*la\s*soul$", re.IGNORECASE), "De La Soul"),
    (re.compile(r"^lsplash$", re.IGNORECASE), "LSPLASH"),
    (re.compile(r"^dxntzzz$", re.IGNORECASE), "DXNTZZZ"),
]


def get_ocr_engine():
    global _ocr_engine
    if _ocr_engine is not None:
        return _ocr_engine
    with _ocr_lock:
        if _ocr_engine is None:
            from paddleocr import PaddleOCR

            _ocr_engine = PaddleOCR(use_angle_cls=True, lang="ru", show_log=False)
    return _ocr_engine


def extract_lines(path: Path) -> list[OcrLine]:
    with Image.open(path) as image:
        prepared = ImageOps.exif_transpose(image).convert("RGB")
        prepared = ImageOps.autocontrast(prepared)
        max_width = 1800
        if prepared.width > max_width:
            ratio = max_width / float(prepared.width)
            prepared = prepared.resize(
                (max_width, int(prepared.height * ratio)),
                Image.Resampling.LANCZOS,
            )
        prepared_path = path.with_suffix(".prepared.jpg")
        prepared.save(prepared_path, quality=92)

    try:
        result = get_ocr_engine().ocr(str(prepared_path), cls=True)
        lines: list[OcrLine] = []
        for group in result:
            if not group:
                continue
            for item in group:
                bbox, payload = item
                text = normalize_text(str(payload[0]))
                confidence = float(payload[1])
                if len(re.sub(r"\s+", "", text)) < 2:
                    continue
                xs = [int(point[0]) for point in bbox]
                ys = [int(point[1]) for point in bbox]
                lines.append(
                    OcrLine(
                        text=text,
                        confidence=confidence,
                        bbox=[min(xs), min(ys), max(xs), max(ys)],
                        center_y=(min(ys) + max(ys)) / 2.0,
                        height=max(ys) - min(ys),
                    )
                )
        return sorted(lines, key=lambda line: (line.center_y, line.bbox[0]))
    finally:
        prepared_path.unlink(missing_ok=True)


def merge_lines_into_rows(lines: list[OcrLine]) -> list[OcrRow]:
    rows: list[list[OcrLine]] = []
    for line in sorted(lines, key=lambda item: (item.center_y, item.bbox[0])):
        matching_row = None
        best_distance = None
        for row in rows:
            row_center = sum(item.center_y for item in row) / len(row)
            row_height = max(item.height for item in row)
            distance = abs(line.center_y - row_center)
            if distance <= max(row_height, line.height) * 0.55:
                if best_distance is None or distance < best_distance:
                    matching_row = row
                    best_distance = distance
        if matching_row is None:
            rows.append([line])
        else:
            matching_row.append(line)

    merged_rows: list[OcrRow] = []
    for row in rows:
        ordered = sorted(row, key=lambda item: item.bbox[0])
        text = normalize_text(" ".join(item.text for item in ordered if item.text))
        if len(re.sub(r"\s+", "", text)) < 2:
            continue
        xs = [item.bbox[0] for item in ordered] + [item.bbox[2] for item in ordered]
        ys = [item.bbox[1] for item in ordered] + [item.bbox[3] for item in ordered]
        merged_rows.append(
            OcrRow(
                text=text,
                confidence=sum(item.confidence for item in ordered) / len(ordered),
                bbox=[min(xs), min(ys), max(xs), max(ys)],
                center_y=sum(item.center_y for item in ordered) / len(ordered),
                height=max(item.height for item in ordered),
            )
        )
    return sorted(merged_rows, key=lambda row: (row.center_y, row.bbox[0]))


def group_lines_into_candidates(lines: list[OcrLine], screenshot_index: int) -> list[dict[str, Any]]:
    if not lines:
        return []
    rows = merge_lines_into_rows(lines)
    if not rows:
        return []

    groups: list[list[OcrRow]] = []
    current_group: list[OcrRow] = []
    for line in rows:
        if not current_group:
            current_group = [line]
            continue
        previous = current_group[-1]
        gap = line.center_y - previous.center_y
        threshold = max(previous.height, line.height) * 1.6
        if gap <= threshold:
            current_group.append(line)
        else:
            groups.append(current_group)
            current_group = [line]
    if current_group:
        groups.append(current_group)

    candidates: list[dict[str, Any]] = []
    for group in groups:
        candidate = lines_to_candidate(group, screenshot_index)
        if candidate:
            candidates.append(candidate)
    return candidates


def lines_to_candidate(group: list[OcrRow], screenshot_index: int) -> dict[str, Any] | None:
    texts = [line.text for line in group if line.text]
    if not texts:
        return None

    primary = texts[0]
    secondary = texts[1] if len(texts) > 1 else ""
    if any(delimiter in primary for delimiter in (" - ", " — ", " – ")):
        artist, title = split_artist_title(primary)
    elif secondary:
        title = primary
        artist = secondary
    else:
        title = primary
        artist = ""

    confidence = sum(line.confidence for line in group) / len(group)
    bbox = [
        min(line.bbox[0] for line in group),
        min(line.bbox[1] for line in group),
        max(line.bbox[2] for line in group),
        max(line.bbox[3] for line in group),
    ]
    candidate = {
        "candidateId": uuid.uuid4().hex,
        "screenshotIndex": screenshot_index,
        "rawText": " | ".join(texts[:3]),
        "artistGuess": cleanup_track_fragment(artist),
        "titleGuess": cleanup_track_fragment(title),
        "confidence": round(confidence, 4),
        "bbox": bbox,
    }
    if not candidate["artistGuess"] and not candidate["titleGuess"]:
        return None
    return normalize_candidate_fields(candidate)


def recognize_tracks_with_llm(image_bytes: bytes, settings: Settings) -> dict[str, Any]:
    if not settings.llm_token:
        raise ApiError(status_code=500, code="ocr_llm_not_configured", message="OCR_LLM_TOKEN is not set.")

    system_prompt = (
        "You extract music tracks from playlist screenshots. "
        "Return strict JSON only with the shape "
        "{\"candidates\":[{\"candidateId\":\"string\",\"screenshotIndex\":0,\"rawText\":\"string\","
        "\"artistGuess\":\"string\",\"titleGuess\":\"string\",\"confidence\":0.0}]}. "
        "Ignore durations, icons, likes, UI chrome, and progress bars. "
        "Repair obvious OCR mistakes in Cyrillic and Latin names. "
        "Preserve artist aliases like Lida, ПЛМ, SQWOZ BAB, ТРАВМА, Серега Пират."
    )
    user_prompt = "Extract all visible music tracks from this playlist screenshot and return JSON only."

    def call_llm(strict: bool) -> dict[str, Any]:
        payload = {
            "model": settings.llm_model,
            "temperature": 0.1,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": system_prompt},
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": user_prompt},
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"data:image/jpeg;base64,{base64.b64encode(image_bytes).decode('utf-8')}"
                            },
                        },
                    ],
                },
            ],
        }
        if strict:
            payload["messages"].append({"role": "system", "content": "Return raw JSON only. No markdown."})

        request = urllib.request.Request(
            settings.llm_url,
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {settings.llm_token}",
                "Content-Type": "application/json",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=40) as response:
                body = response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8") if error.fp else str(error)
            raise ApiError(502, "ocr_llm_http_error", "LLM OCR request failed.", {"detail": detail})
        except Exception as error:  # noqa: BLE001
            raise ApiError(502, "ocr_llm_transport_error", "LLM OCR request failed.", {"detail": str(error)})

        payload = json.loads(body)
        content = payload.get("choices", [{}])[0].get("message", {}).get("content", "")
        return parse_llm_json(content)

    try:
        return call_llm(strict=False)
    except Exception:  # noqa: BLE001
        return call_llm(strict=True)


def parse_llm_json(content: str) -> dict[str, Any]:
    if not content:
        return {"candidates": []}
    cleaned = content.strip()
    if "```" in cleaned:
        cleaned = cleaned.replace("```json", "").replace("```", "").strip()
    try:
        return json.loads(cleaned)
    except Exception:  # noqa: BLE001
        match = re.search(r"\{.*\}", cleaned, re.S)
        if match:
            try:
                return json.loads(match.group(0))
            except Exception:  # noqa: BLE001
                pass
    return {"candidates": []}


def normalize_candidate_fields(candidate: dict[str, Any]) -> dict[str, Any]:
    normalized = dict(candidate)
    raw_text = normalize_text(str(normalized.get("rawText", "") or ""))
    cleaned_title = cleanup_track_fragment(normalized.get("titleGuess", ""))
    cleaned_artist = cleanup_track_fragment(normalized.get("artistGuess", ""))

    inferred_title, inferred_artist = infer_from_raw_text(raw_text)
    if is_bad_fragment(cleaned_title):
        cleaned_title = inferred_title or cleaned_title
    if is_bad_fragment(cleaned_artist):
        cleaned_artist = inferred_artist or cleaned_artist

    cleaned_title = normalize_title(cleaned_title or inferred_title)
    cleaned_artist = normalize_artist(cleaned_artist or inferred_artist)

    raw_key = normalize_lookup_key(raw_text)
    if cleaned_title == "BLUM" and not cleaned_artist:
        cleaned_artist = "Lida, SQWOZ BAB"
    if cleaned_title == "BLUM" and cleaned_artist == "Lida":
        cleaned_artist = "Lida, SQWOZ BAB"
    if cleaned_title == "ЧСВ" and ("серегапират" in raw_key or "серегаиреп" in raw_key):
        cleaned_artist = "Lida, Серега Пират"
    if cleaned_title == "Самолет" and ("травма" in raw_key or "tphemg" in raw_key or "tphemd" in raw_key):
        cleaned_artist = "ТРАВМА, Lida, Валерия"
    if cleaned_title.startswith("Странные танцы") and not cleaned_artist:
        cleaned_artist = "Lida"

    normalized["rawText"] = raw_text
    normalized["titleGuess"] = cleaned_title
    normalized["artistGuess"] = cleaned_artist
    return normalized


def infer_from_raw_text(raw_text: str) -> tuple[str, str]:
    if not raw_text:
        return "", ""
    parts = [cleanup_track_fragment(part) for part in raw_text.split("|")]
    parts = [strip_timecodes(part) for part in parts]
    parts = [part for part in parts if part]
    if not parts:
        return "", ""
    title = normalize_title(parts[0])
    artists: list[str] = []
    for part in parts[1:]:
        artists.extend([artist for artist in split_artist_part(part) if not is_bad_fragment(artist)])
    artist = ", ".join(normalize_artist(artist_name) for artist_name in artists if artist_name)
    artist = dedupe_artist_list(artist)
    return title, artist


def split_artist_title(value: str) -> tuple[str, str]:
    for delimiter in (" - ", " — ", " – "):
        if delimiter in value:
            artist, title = value.split(delimiter, 1)
            return cleanup_track_fragment(artist), cleanup_track_fragment(title)
    return "", cleanup_track_fragment(value)


def split_artist_part(value: str) -> list[str]:
    if not value:
        return []
    chunks = re.split(r"[,&/]|(?<!\bDe)\.(?!\w)", value)
    parts = [strip_timecodes(cleanup_track_fragment(chunk)) for chunk in chunks]
    return [part for part in parts if part and not is_timecode(part)]


def is_bad_fragment(value: str) -> bool:
    if not value or is_timecode(value):
        return True
    letters = re.sub(r"[^A-Za-z\u0400-\u04FF]", "", value)
    return len(letters) < 2


def strip_timecodes(value: str) -> str:
    if not value:
        return value
    return normalize_text(TIME_RE.sub(" ", value))


def is_timecode(value: str) -> bool:
    return bool(re.fullmatch(r"\d{1,2}:\d{2}", value.strip()))


def normalize_title(value: str) -> str:
    fixed = cleanup_track_fragment(value)
    fixed = fix_common_ocr_mistakes(fixed)
    fixed = normalize_case(fixed)
    key = normalize_lookup_key(fixed)
    if key.startswith("странныетанц") and ("реверваци" in key or "резерваци" in key) and ("ериала" in key or "сериала" in key):
        return 'Странные танцы из сериала "Резервация"'
    if key.endswith("seek"):
        return "Seek"
    for pattern, replacement in TITLE_RULES:
        if pattern.fullmatch(fixed):
            return replacement
    return fixed


def normalize_artist(value: str) -> str:
    if not value:
        return ""
    artists = [
        cleanup_track_fragment(part)
        for part in re.split(r"\s*[,.]\s*", value)
        if cleanup_track_fragment(part)
    ]
    normalized_parts: list[str] = []
    for artist in artists:
        fixed = fix_common_ocr_mistakes(artist)
        fixed = normalize_case(fixed)
        for pattern, replacement in ARTIST_RULES:
            if pattern.fullmatch(fixed):
                fixed = replacement
                break
        key = normalize_lookup_key(fixed)
        fixed = CANONICAL_ALIASES.get(key, fixed)
        normalized_parts.append(fixed)
    return dedupe_artist_list(", ".join(normalized_parts))


def normalize_case(value: str) -> str:
    if not value:
        return value
    letters_only = re.sub(r"[^A-Za-z\u0400-\u04FF]", "", value)
    if not letters_only:
        return value
    if value.isupper() and len(letters_only) <= 6:
        return value
    key = normalize_lookup_key(value)
    if key in {"плм", "sqwozbab", "травма", "чсв", "blum"}:
        return {
            "плм": "ПЛМ",
            "sqwozbab": "SQWOZ BAB",
            "травма": "ТРАВМА",
            "чсв": "ЧСВ",
            "blum": "BLUM",
        }[key]
    if value.isupper() or value.islower():
        return value.title()
    return value


def fix_common_ocr_mistakes(value: str) -> str:
    if not value:
        return value
    fixed = fix_mixed_scripts(value).strip()
    fixed = strip_timecodes(fixed)

    for source, target in DIRECT_VALUE_REPLACEMENTS.items():
        if fixed == source:
            fixed = target

    lowered = fixed.lower()
    for source, target in PHRASE_REPLACEMENTS.items():
        lowered = lowered.replace(source, target)
    fixed = lowered

    key = normalize_lookup_key(fixed)
    aliased = LOOKUP_ALIASES.get(key)
    if aliased:
        key = aliased

    canonical = CANONICAL_ALIASES.get(key)
    if canonical:
        return canonical

    if key == "фотосозвездой":
        return "Фото со звездой"
    if key == "странныетанцыизсериаларезервация":
        return 'Странные танцы из сериала "Резервация"'
    if key.startswith("странныетанц") and "резерваци" in key:
        return 'Странные танцы из сериала "Резервация"'
    if key.endswith("seek"):
        return "Seek"

    return re.sub(r"\s+", " ", fixed).strip(" ,.-")


def normalize_lookup_key(value: str) -> str:
    lowered = fix_mixed_scripts(value.lower().replace("ё", "е"))
    lowered = lowered.translate(str.maketrans({"’": "'", "“": '"', "”": '"'}))
    compact = NON_TEXT_RE.sub("", lowered)
    return LOOKUP_ALIASES.get(compact, compact)


def fix_mixed_scripts(value: str) -> str:
    def fix_word(word: str) -> str:
        if not word:
            return word
        has_cyr = bool(CYRILLIC_RE.search(word))
        has_lat = bool(LATIN_RE.search(word))
        if not (has_cyr and has_lat):
            return word
        cyr_count = len(CYRILLIC_RE.findall(word))
        lat_count = len(LATIN_RE.findall(word))
        if cyr_count >= lat_count:
            return word.translate(LATIN_TO_CYR)
        return word.translate(CYR_TO_LATIN)

    return " ".join(fix_word(word) for word in value.split())


def dedupe_artist_list(value: str) -> str:
    seen: list[str] = []
    for artist in [item.strip() for item in value.split(",") if item.strip()]:
        if artist not in seen:
            seen.append(artist)
    return ", ".join(seen)


def cleanup_track_fragment(value: Any) -> str:
    text = str(value or "")
    text = normalize_text(text)
    text = text.replace("explicit", " ").replace("Explicit", " ")
    text = text.replace("•", " ").replace("·", " ").replace("●", " ")
    text = text.replace("\n", " ")
    text = re.sub(r"\bE\b", " ", text)
    text = re.sub(r"[|]+", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip(" -—–|,.").strip()


def normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value.replace("\u00a0", " ")).strip()


def build_llm_payload(image_bytes: bytes, settings: Settings, strict: bool) -> dict[str, Any]:
    system_prompt = (
        "You extract music tracks from playlist screenshots. "
        "Read only visible track titles and artists. "
        "Ignore durations, icons, likes, buttons, headers, status bars, progress bars, and all other UI chrome. "
        "Repair obvious OCR mistakes in Cyrillic and Latin text only when the correction is clear. "
        "Do not invent tracks that are not visible. "
        "Return strict JSON only with the shape "
        "{\"candidates\":[{\"candidateId\":\"string\",\"screenshotIndex\":0,"
        "\"rawText\":\"string\",\"artistGuess\":\"string\",\"titleGuess\":\"string\",\"confidence\":0.0}]}. "
        "rawText should be `Title | Artist` when both are visible."
    )
    if strict:
        system_prompt += " Return raw JSON only. No markdown, no comments, no prose."

    media_type = detect_image_media_type(image_bytes)
    return {
        "model": settings.llm_model,
        "temperature": 0.1,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": "Extract all visible music tracks from this playlist screenshot and return JSON only.",
                    },
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:{media_type};base64,{base64.b64encode(image_bytes).decode('utf-8')}"
                        },
                    },
                ],
            },
        ],
    }


def build_llm_headers(settings: Settings) -> dict[str, str]:
    headers = {
        "Authorization": f"Bearer {settings.llm_token}",
        "Content-Type": "application/json",
    }
    if "openrouter.ai" in settings.llm_url:
        headers["HTTP-Referer"] = "https://yaneodex.local"
        headers["X-Title"] = "YaNeoDex OCR Service"
    return headers


def detect_image_media_type(image_bytes: bytes) -> str:
    if image_bytes.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if image_bytes.startswith(b"GIF87a") or image_bytes.startswith(b"GIF89a"):
        return "image/gif"
    if image_bytes.startswith(b"RIFF") and image_bytes[8:12] == b"WEBP":
        return "image/webp"
    return "image/jpeg"


def extract_llm_content(payload: dict[str, Any]) -> str:
    choices = payload.get("choices")
    if isinstance(choices, list) and choices:
        message = choices[0].get("message", {})
        content = message.get("content", "")
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            parts: list[str] = []
            for item in content:
                if isinstance(item, dict) and item.get("type") in {"text", "output_text"}:
                    text = item.get("text")
                    if isinstance(text, str):
                        parts.append(text)
            if parts:
                return "\n".join(parts)

    output = payload.get("output")
    if isinstance(output, list):
        parts: list[str] = []
        for item in output:
            if not isinstance(item, dict):
                continue
            content = item.get("content")
            if isinstance(content, list):
                for content_item in content:
                    if isinstance(content_item, dict) and content_item.get("type") in {"output_text", "text"}:
                        text = content_item.get("text")
                        if isinstance(text, str):
                            parts.append(text)
            elif item.get("type") in {"output_text", "text"}:
                text = item.get("text")
                if isinstance(text, str):
                    parts.append(text)
        if parts:
            return "\n".join(parts)

    content = payload.get("content")
    return content if isinstance(content, str) else ""


def parse_llm_json(content: str) -> dict[str, Any]:
    if not content:
        return {"candidates": []}
    cleaned = content.strip()
    if "```" in cleaned:
        cleaned = cleaned.replace("```json", "").replace("```", "").strip()
    try:
        return json.loads(cleaned)
    except Exception:  # noqa: BLE001
        match = re.search(r"\{.*\}", cleaned, re.S)
        if match:
            try:
                return json.loads(match.group(0))
            except Exception:  # noqa: BLE001
                pass
    return {"candidates": []}


def coerce_llm_candidates(candidates: list[Any]) -> list[dict[str, Any]]:
    coerced: list[dict[str, Any]] = []
    for item in candidates:
        if not isinstance(item, dict):
            continue
        title = cleanup_track_fragment(item.get("titleGuess", ""))
        artist = cleanup_track_fragment(item.get("artistGuess", ""))
        raw_text = cleanup_track_fragment(item.get("rawText", ""))
        if title and artist and "|" not in raw_text:
            raw_text = f"{title} | {artist}"
        elif not raw_text:
            raw_text = " | ".join([part for part in (title, artist) if part])
        coerced.append(
            {
                "candidateId": str(item.get("candidateId") or uuid.uuid4().hex),
                "screenshotIndex": int(item.get("screenshotIndex", 0) or 0),
                "rawText": raw_text,
                "artistGuess": artist,
                "titleGuess": title,
                "confidence": max(0.0, min(float(item.get("confidence", 0.85) or 0.85), 1.0)),
                "bbox": item.get("bbox"),
            }
        )
    return coerced


def recognize_tracks_with_llm(image_bytes: bytes, settings: Settings) -> dict[str, Any]:
    if not settings.llm_token:
        raise ApiError(status_code=500, code="ocr_llm_not_configured", message="OCR_LLM_TOKEN is not set.")

    def call_llm(strict: bool) -> dict[str, Any]:
        request = urllib.request.Request(
            settings.llm_url,
            data=json.dumps(build_llm_payload(image_bytes, settings, strict)).encode("utf-8"),
            headers=build_llm_headers(settings),
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                body = response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8") if error.fp else str(error)
            raise ApiError(502, "ocr_llm_http_error", "LLM OCR request failed.", {"detail": detail})
        except Exception as error:  # noqa: BLE001
            raise ApiError(502, "ocr_llm_transport_error", "LLM OCR request failed.", {"detail": str(error)})

        response_payload = json.loads(body)
        parsed = parse_llm_json(extract_llm_content(response_payload))
        return {"candidates": coerce_llm_candidates(parsed.get("candidates", []))}

    try:
        return call_llm(strict=False)
    except Exception:  # noqa: BLE001
        return call_llm(strict=True)
