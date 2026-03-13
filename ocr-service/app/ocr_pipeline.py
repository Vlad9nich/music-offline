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
            prepared = prepared.resize((max_width, int(prepared.height * ratio)), Image.Resampling.LANCZOS)
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
                text = normalize_text(payload[0])
                confidence = float(payload[1])
                if len(text) < 2:
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
        if len(text) < 2:
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


def lines_to_candidate(group: list[OcrRow], screenshot_index: int) -> dict[str, Any] | None:
    texts = [line.text for line in group if line.text]
    if not texts:
        return None

    primary = texts[0]
    secondary = texts[1] if len(texts) > 1 else ""
    if " - " in primary or " — " in primary or " – " in primary:
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
        "Ты извлекаешь музыкальные треки со скриншота плейлиста или списка музыки. "
        "Найди все видимые треки на изображении. Для каждого трека верни titleGuess, artistGuess, rawText, confidence. "
        "Игнорируй длительность, лайки, иконки, служебные элементы интерфейса, прогресс-бары, статус-бар и навигацию. "
        "Исправляй очевидные OCR-ошибки и смешение кириллицы/латиницы. "
        "Сохраняй сценические имена корректно: Lida, ПЛМ, SQWOZ BAB, ТРАВМА, Серега Пират. "
        "Возвращай только JSON вида {\"candidates\":[...]}. Если треков нет, верни {\"candidates\":[]}."
    )
    user_prompt = "Извлеки все музыкальные треки с этого скриншота плейлиста. Верни строго JSON."

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
            payload["messages"].append(
                {"role": "system", "content": "Верни только чистый JSON без пояснений и markdown."}
            )

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
    raw_text = str(candidate.get("rawText", "") or "")
    cleaned_title = cleanup_track_fragment(candidate.get("titleGuess", ""))
    cleaned_artist = cleanup_track_fragment(candidate.get("artistGuess", ""))
    inferred_title, inferred_artist = infer_from_raw_text(raw_text)
    if is_bad_fragment(cleaned_title) and inferred_title:
        cleaned_title = inferred_title
    if is_bad_fragment(cleaned_artist) and inferred_artist:
        cleaned_artist = inferred_artist
    cleaned_title = enforce_stage_names(normalize_case(fix_common_ocr_mistakes(cleaned_title)))
    cleaned_artist = enforce_stage_names(normalize_case(fix_common_ocr_mistakes(cleaned_artist)))
    candidate["titleGuess"] = cleaned_title
    candidate["artistGuess"] = cleaned_artist
    return candidate


def infer_from_raw_text(raw_text: str) -> tuple[str, str]:
    if not raw_text:
        return "", ""
    parts = [cleanup_track_fragment(part) for part in raw_text.split("|")]
    parts = [strip_timecodes(part) for part in parts]
    parts = [part for part in parts if part]
    if not parts:
        return "", ""
    title = parts[0]
    artists: list[str] = []
    for part in parts[1:]:
        artists.extend([artist for artist in split_artist_part(part) if not is_bad_fragment(artist)])
    return title, ", ".join(fix_common_ocr_mistakes(artist) for artist in artists if artist)


def split_artist_title(value: str) -> tuple[str, str]:
    for delimiter in (" - ", " — ", " – "):
        if delimiter in value:
            artist, title = value.split(delimiter, 1)
            return cleanup_track_fragment(artist), cleanup_track_fragment(title)
    return "", cleanup_track_fragment(value)


def split_artist_part(value: str) -> list[str]:
    if not value:
        return []
    chunks = re.split(r"[.,&]", value.replace("&", ","))
    parts = [strip_timecodes(cleanup_track_fragment(chunk)) for chunk in chunks]
    return [part for part in parts if part]


def is_bad_fragment(value: str) -> bool:
    if not value or is_timecode(value):
        return True
    letters = re.sub(r"[^A-Za-zА-Яа-я]", "", value)
    return len(letters) < 2


def strip_timecodes(value: str) -> str:
    if not value:
        return value
    return normalize_text(re.sub(r"\b\d{1,2}:\d{2}\b", " ", value))


def is_timecode(value: str) -> bool:
    return bool(re.fullmatch(r"\d{1,2}:\d{2}", value.strip()))


def normalize_case(value: str) -> str:
    if not value:
        return value
    letters_only = re.sub(r"[^A-Za-zА-Яа-я]", "", value)
    if value.isupper() and len(letters_only) <= 4:
        return value
    if value.isupper() or value.islower():
        return value.title()
    return value


def enforce_stage_names(value: str) -> str:
    if not value:
        return value
    canonical = {
        "lida": "Lida",
        "плм": "ПЛМ",
        "sqwoz bab": "SQWOZ BAB",
        "травма": "ТРАВМА",
        "серега пират": "Серега Пират",
        "серёга пират": "Серега Пират",
    }
    fixed_parts = []
    for part in [item.strip() for item in value.split(",")]:
        fixed_parts.append(canonical.get(part.lower(), part))
    return ", ".join(part for part in fixed_parts if part)


def fix_common_ocr_mistakes(value: str) -> str:
    if not value:
        return value
    fixed = fix_mixed_scripts(value)
    replacements = {
        "звевд": "звезд",
        "фотоCо": "Фото со",
        "ФотоCо": "Фото со",
        "Lide": "Lida",
        "Lidl": "Lida",
        "SUiUz Еде": "SQWOZ BAB",
        "Sqwoz Bab": "SQWOZ BAB",
        "Tphemd": "ТРАВМА",
        "амолет": "Самолет",
        "Ревервация": "Резервация",
        "алерия": "Валерия",
        "ЧСв": "ЧСВ",
        "чсв": "ЧСВ",
    }
    for source, target in replacements.items():
        fixed = fixed.replace(source, target)
    return fixed


def fix_mixed_scripts(value: str) -> str:
    def fix_word(word: str) -> str:
        if not word:
            return word
        has_cyr = bool(re.search(r"[А-Яа-я]", word))
        has_lat = bool(re.search(r"[A-Za-z]", word))
        if not (has_cyr and has_lat):
            return word
        cyr = {
            "A": "А", "B": "В", "C": "С", "E": "Е", "H": "Н", "K": "К", "M": "М", "O": "О", "P": "Р", "T": "Т", "X": "Х", "Y": "У",
            "a": "а", "b": "в", "c": "с", "e": "е", "h": "н", "k": "к", "m": "м", "o": "о", "p": "р", "t": "т", "x": "х", "y": "у",
        }
        lat = {
            "А": "A", "В": "B", "С": "C", "Е": "E", "Н": "H", "К": "K", "М": "M", "О": "O", "Р": "P", "Т": "T", "Х": "X", "У": "Y",
            "а": "a", "в": "b", "с": "c", "е": "e", "н": "h", "к": "k", "м": "m", "о": "o", "р": "p", "т": "t", "х": "x", "у": "y",
        }
        cyr_count = len(re.findall(r"[А-Яа-я]", word))
        lat_count = len(re.findall(r"[A-Za-z]", word))
        if cyr_count >= lat_count:
            return "".join(cyr.get(ch, ch) for ch in word)
        return "".join(lat.get(ch, ch) for ch in word)

    return " ".join(fix_word(word) for word in value.split())


def cleanup_track_fragment(value: Any) -> str:
    text = str(value or "")
    text = text.replace("•", " ").replace("explicit", " ").replace("\n", " ")
    text = re.sub(r"\bE\b", " ", text)
    return text.strip(" -—–|").strip()


def normalize_text(value: str) -> str:
    return re.sub(r"\s+", " ", value.replace("\u00a0", " ")).strip()
