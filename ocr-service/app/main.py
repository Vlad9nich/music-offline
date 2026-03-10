from __future__ import annotations

import os
import re
import shutil
import tempfile
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel
from PIL import Image, ImageOps
from paddleocr import PaddleOCR

app = FastAPI(title="YaNeoDex OCR Service", version="1.0.0")
executor = ThreadPoolExecutor(max_workers=2)
jobs_lock = threading.Lock()
jobs: dict[str, dict[str, Any]] = {}
ocr_engine = PaddleOCR(use_angle_cls=True, lang="ru", show_log=False)

JOB_TTL_SECONDS = 3600


class CreateJobResponse(BaseModel):
    jobId: str


class CandidateResponse(BaseModel):
    candidateId: str
    screenshotIndex: int
    rawText: str
    artistGuess: str
    titleGuess: str
    confidence: float
    bbox: list[int] | None = None


class JobResponse(BaseModel):
    jobId: str
    status: str
    processedFiles: int = 0
    totalFiles: int = 0
    error: str | None = None
    candidates: list[CandidateResponse] = []


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


@app.post("/v1/playlist-import/jobs", response_model=CreateJobResponse)
async def create_job(files: list[UploadFile] = File(...)) -> CreateJobResponse:
    cleanup_expired_jobs()
    if not files:
        raise HTTPException(status_code=400, detail="No files uploaded")

    job_id = uuid.uuid4().hex
    job_dir = Path(tempfile.mkdtemp(prefix=f"yaneodex-ocr-{job_id}-"))
    saved_files: list[Path] = []

    try:
        for index, upload in enumerate(files):
            suffix = Path(upload.filename or f"shot-{index}.jpg").suffix or ".jpg"
            target = job_dir / f"{index:03d}{suffix}"
            content = await upload.read()
            target.write_bytes(content)
            saved_files.append(target)
    except Exception:
        shutil.rmtree(job_dir, ignore_errors=True)
        raise

    with jobs_lock:
        jobs[job_id] = {
            "jobId": job_id,
            "status": "queued",
            "processedFiles": 0,
            "totalFiles": len(saved_files),
            "error": None,
            "candidates": [],
            "expiresAt": time.time() + JOB_TTL_SECONDS,
            "jobDir": str(job_dir),
        }

    executor.submit(process_job, job_id, saved_files)
    return CreateJobResponse(jobId=job_id)


@app.get("/v1/playlist-import/jobs/{job_id}", response_model=JobResponse)
def get_job(job_id: str) -> JobResponse:
    cleanup_expired_jobs()
    with jobs_lock:
        payload = jobs.get(job_id)
        if payload is None:
            raise HTTPException(status_code=404, detail="Job not found")
        return JobResponse(**{k: v for k, v in payload.items() if k != "expiresAt" and k != "jobDir"})


def process_job(job_id: str, files: list[Path]) -> None:
    update_job(job_id, status="processing", processedFiles=0, candidates=[])
    all_candidates: list[dict[str, Any]] = []

    try:
        for index, path in enumerate(files):
            lines = extract_lines(path)
            grouped_candidates = group_lines_into_candidates(lines, screenshot_index=index)
            all_candidates.extend(grouped_candidates)
            update_job(
                job_id,
                status="processing",
                processedFiles=index + 1,
                candidates=all_candidates,
            )
        update_job(
            job_id,
            status="completed",
            processedFiles=len(files),
            candidates=all_candidates,
        )
    except Exception as error:
        update_job(
            job_id,
            status="failed",
            error=str(error),
        )
    finally:
        cleanup_job_files(job_id)


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
        result = ocr_engine.ocr(str(prepared_path), cls=True)
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
        if not group:
            continue
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

    if " - " in primary:
        artist, title = split_artist_title(primary)
    elif secondary:
        title = primary
        artist = secondary
    else:
        artist = ""
        title = primary

    raw_text = " | ".join(texts[:3])
    confidence = sum(line.confidence for line in group) / len(group)
    bbox = [
        min(line.bbox[0] for line in group),
        min(line.bbox[1] for line in group),
        max(line.bbox[2] for line in group),
        max(line.bbox[3] for line in group),
    ]

    cleaned_title = cleanup_track_fragment(title)
    cleaned_artist = cleanup_track_fragment(artist)
    if not cleaned_title and not cleaned_artist:
        return None

    return {
        "candidateId": uuid.uuid4().hex,
        "screenshotIndex": screenshot_index,
        "rawText": raw_text,
        "artistGuess": cleaned_artist,
        "titleGuess": cleaned_title,
        "confidence": round(confidence, 4),
        "bbox": bbox,
    }


def merge_lines_into_rows(lines: list[OcrLine]) -> list[OcrRow]:
    if not lines:
        return []

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


def split_artist_title(value: str) -> tuple[str, str]:
    for delimiter in (" - ", " — ", " – "):
        if delimiter in value:
            artist, title = value.split(delimiter, 1)
            return cleanup_track_fragment(artist), cleanup_track_fragment(title)
    return "", cleanup_track_fragment(value)


def cleanup_track_fragment(value: str) -> str:
    return (
        value.replace("•", " ")
        .replace("explicit", " ")
        .replace("E", " ")
        .replace("\n", " ")
        .strip(" -—–|")
        .strip()
    )


def normalize_text(value: str) -> str:
    value = value.replace("\u00a0", " ")
    value = re.sub(r"\s+", " ", value)
    return value.strip()


def update_job(job_id: str, **kwargs: Any) -> None:
    with jobs_lock:
        if job_id not in jobs:
            return
        jobs[job_id].update(kwargs)
        jobs[job_id]["expiresAt"] = time.time() + JOB_TTL_SECONDS


def cleanup_job_files(job_id: str) -> None:
    with jobs_lock:
        payload = jobs.get(job_id)
        if payload is None:
            return
        job_dir = payload.get("jobDir")
    if job_dir:
        shutil.rmtree(job_dir, ignore_errors=True)


def cleanup_expired_jobs() -> None:
    now = time.time()
    expired: list[str] = []
    with jobs_lock:
        for job_id, payload in jobs.items():
            if payload.get("expiresAt", 0) < now:
                expired.append(job_id)
        for job_id in expired:
            payload = jobs.pop(job_id, None)
            job_dir = payload.get("jobDir") if payload else None
            if job_dir:
                shutil.rmtree(job_dir, ignore_errors=True)
