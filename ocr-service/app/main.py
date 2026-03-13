from __future__ import annotations

import shutil
import tempfile
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import Depends, FastAPI, File, UploadFile

from .auth import require_api_token
from .config import Settings
from .errors import ApiError, register_exception_handlers
from .job_store import SqliteJobStore
from .logging_utils import audit_log, configure_logger
from .models import CandidateResponse, CreateJobResponse, JobResponse, OcrNormalizeRequest, OcrNormalizeResponse, OcrResponse
from .ocr_pipeline import extract_lines, group_lines_into_candidates, normalize_candidate_fields, recognize_tracks_with_llm
from .rate_limit import SlidingWindowRateLimiter, enforce_rate_limit


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved_settings = settings or Settings.from_env()
    logger = configure_logger()
    store = SqliteJobStore(resolved_settings.db_path)
    rate_limiter = SlidingWindowRateLimiter(
        resolved_settings.rate_limit_requests,
        resolved_settings.rate_limit_window_seconds,
    )

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        resolved_settings.data_dir.mkdir(parents=True, exist_ok=True)
        resolved_settings.jobs_dir.mkdir(parents=True, exist_ok=True)
        app.state.executor = ThreadPoolExecutor(max_workers=2)
        _resume_recoverable_jobs(app)
        yield
        app.state.executor.shutdown(wait=False, cancel_futures=False)

    app = FastAPI(title="YaNeoDex OCR Service", version="2.0.0", lifespan=lifespan)
    app.state.settings = resolved_settings
    app.state.logger = logger
    app.state.job_store = store
    app.state.rate_limiter = rate_limiter
    register_exception_handlers(app)

    @app.middleware("http")
    async def request_audit(request, call_next):
        request_id = uuid.uuid4().hex
        request.state.request_id = request_id
        cleanup_expired_jobs(app)
        started_at = time.time()
        audit_log(
            logger,
            "request_started",
            requestId=request_id,
            method=request.method,
            path=request.url.path,
            client=request.client.host if request.client else "unknown",
        )
        response = await call_next(request)
        duration_ms = round((time.time() - started_at) * 1000, 2)
        response.headers["X-Request-ID"] = request_id
        audit_log(
            logger,
            "request_finished",
            requestId=request_id,
            method=request.method,
            path=request.url.path,
            status=response.status_code,
            durationMs=duration_ms,
        )
        return response

    protected = [Depends(require_api_token), Depends(enforce_rate_limit)]

    @app.get("/healthz")
    def healthz() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/v1/playlist-import/jobs", response_model=CreateJobResponse, dependencies=protected)
    async def create_job(files: list[UploadFile] = File(...)) -> CreateJobResponse:
        if not files:
            raise ApiError(400, "no_files_uploaded", "No files uploaded.")

        job_id = uuid.uuid4().hex
        job_dir = resolved_settings.jobs_dir / job_id
        job_dir.mkdir(parents=True, exist_ok=True)
        saved_files: list[str] = []

        try:
            for index, upload in enumerate(files):
                suffix = Path(upload.filename or f"shot-{index}.jpg").suffix or ".jpg"
                target = job_dir / f"{index:03d}{suffix}"
                target.write_bytes(await upload.read())
                saved_files.append(str(target))
        except Exception as error:  # noqa: BLE001
            shutil.rmtree(job_dir, ignore_errors=True)
            raise ApiError(500, "job_file_write_failed", "Failed to save uploaded screenshots.", {"detail": str(error)})

        store.create_job(
            job_id=job_id,
            total_files=len(saved_files),
            job_dir=str(job_dir),
            source_files=saved_files,
            expires_at=time.time() + resolved_settings.job_ttl_seconds,
        )
        audit_log(logger, "job_created", jobId=job_id, totalFiles=len(saved_files))
        _submit_job_processing(app, job_id)
        return CreateJobResponse(jobId=job_id)

    @app.get("/v1/playlist-import/jobs/{job_id}", response_model=JobResponse, dependencies=protected)
    def get_job(job_id: str) -> JobResponse:
        payload = store.get_job(job_id)
        if payload is None:
            raise ApiError(404, "job_not_found", "Job not found.")
        store.touch_job_ttl(job_id, resolved_settings.job_ttl_seconds)
        return JobResponse(
            jobId=payload["jobId"],
            status=payload["status"],
            processedFiles=payload["processedFiles"],
            totalFiles=payload["totalFiles"],
            error=payload["error"],
            candidates=[CandidateResponse(**item) for item in payload["candidates"]],
        )

    @app.post("/v1/ocr", response_model=OcrNormalizeResponse, dependencies=protected)
    def normalize_ocr(payload: OcrNormalizeRequest) -> OcrNormalizeResponse:
        normalized = [normalize_candidate_fields(item.model_dump()) for item in payload.candidates]
        audit_log(logger, "candidates_normalized", count=len(normalized))
        return OcrNormalizeResponse(candidates=[CandidateResponse(**item) for item in normalized])

    @app.post("/v1/ocr-image", response_model=OcrResponse, dependencies=protected)
    async def ocr_image(file: UploadFile = File(...)) -> OcrResponse:
        image_bytes = await file.read()
        if not image_bytes:
            raise ApiError(400, "empty_file", "OCR image payload is empty.")

        candidates: list[dict[str, Any]]
        try:
            llm_result = recognize_tracks_with_llm(image_bytes, resolved_settings)
            candidates = [normalize_candidate_fields(_coerce_candidate(item)) for item in llm_result.get("candidates", [])]
            audit_log(logger, "single_image_llm_processed", candidates=len(candidates))
        except Exception as error:  # noqa: BLE001
            audit_log(logger, "single_image_llm_failed", detail=str(error))
            candidates = []

        if not candidates:
            with tempfile.NamedTemporaryFile(prefix="ocr-shot-", suffix=".jpg", delete=False) as temp_file:
                temp_file.write(image_bytes)
                temp_path = Path(temp_file.name)
            try:
                lines = extract_lines(temp_path)
                candidates = group_lines_into_candidates(lines, screenshot_index=0)
                audit_log(logger, "single_image_fallback_processed", candidates=len(candidates))
            finally:
                temp_path.unlink(missing_ok=True)

        return OcrResponse(candidates=[CandidateResponse(**item) for item in candidates])

    return app


def _coerce_candidate(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "candidateId": item.get("candidateId") or uuid.uuid4().hex,
        "screenshotIndex": int(item.get("screenshotIndex", 0) or 0),
        "rawText": str(item.get("rawText", "")).strip(),
        "artistGuess": str(item.get("artistGuess", "")).strip(),
        "titleGuess": str(item.get("titleGuess", "")).strip(),
        "confidence": float(item.get("confidence", 0.85) or 0.85),
        "bbox": item.get("bbox"),
    }


def _submit_job_processing(app: FastAPI, job_id: str) -> None:
    app.state.executor.submit(_process_job, app, job_id)


def _resume_recoverable_jobs(app: FastAPI) -> None:
    for payload in app.state.job_store.list_recoverable_jobs():
        source_files = [Path(path) for path in payload.get("sourceFiles", [])]
        if source_files and all(path.exists() for path in source_files):
            audit_log(app.state.logger, "job_resumed", jobId=payload["jobId"], status=payload["status"])
            _submit_job_processing(app, payload["jobId"])
        else:
            app.state.job_store.update_job(
                payload["jobId"],
                status="failed",
                error="Source screenshot files are missing after restart.",
            )


def _process_job(app: FastAPI, job_id: str) -> None:
    store: SqliteJobStore = app.state.job_store
    settings: Settings = app.state.settings
    logger = app.state.logger
    payload = store.get_job(job_id)
    if payload is None:
        return

    files = [Path(path) for path in payload.get("sourceFiles", [])]
    store.update_job(job_id, status="processing", processed_files=0, candidates=[], error=None)
    store.touch_job_ttl(job_id, settings.job_ttl_seconds)
    audit_log(logger, "job_processing_started", jobId=job_id, totalFiles=len(files))
    candidates: list[dict[str, Any]] = []

    try:
        for index, path in enumerate(files):
            lines = extract_lines(path)
            grouped = group_lines_into_candidates(lines, screenshot_index=index)
            candidates.extend(grouped)
            store.update_job(job_id, status="processing", processed_files=index + 1, candidates=candidates)
            store.touch_job_ttl(job_id, settings.job_ttl_seconds)
            audit_log(logger, "job_file_processed", jobId=job_id, fileIndex=index, candidates=len(candidates))

        store.update_job(job_id, status="completed", processed_files=len(files), candidates=candidates, error=None)
        audit_log(logger, "job_completed", jobId=job_id, candidates=len(candidates))
    except Exception as error:  # noqa: BLE001
        store.update_job(job_id, status="failed", error=str(error))
        audit_log(logger, "job_failed", jobId=job_id, detail=str(error))
    finally:
        _cleanup_job_files(store, job_id)


def _cleanup_job_files(store: SqliteJobStore, job_id: str) -> None:
    payload = store.get_job(job_id)
    if payload is None:
        return
    job_dir = payload.get("jobDir")
    if job_dir:
        shutil.rmtree(job_dir, ignore_errors=True)


def cleanup_expired_jobs(app: FastAPI) -> None:
    for job_dir in app.state.job_store.cleanup_expired_jobs():
        shutil.rmtree(job_dir, ignore_errors=True)


app = create_app()
