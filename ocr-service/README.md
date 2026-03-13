# YaNeoDex OCR Service

Self-hosted OCR service for playlist screenshot import.

## Stack

- FastAPI
- PaddleOCR fallback
- Vision LLM fast path for single-image OCR
- SQLite-backed persistent jobs
- CPU-only deployment

## API

Protected endpoints require `Authorization: Bearer <OCR_API_TOKEN>` when `OCR_API_TOKEN` is configured.

- `GET /healthz`
  - simple health check
- `POST /v1/playlist-import/jobs`
  - multipart field `files`
  - returns `{ "jobId": "..." }`
- `GET /v1/playlist-import/jobs/{jobId}`
  - returns job status and OCR candidates
- `POST /v1/ocr`
  - normalizes `candidates[]`
- `POST /v1/ocr-image`
  - multipart field `file`
  - single-image fast path via vision LLM with OCR fallback

All errors return a structured envelope:

```json
{
  "error": {
    "code": "unauthorized",
    "message": "Missing or invalid bearer token.",
    "requestId": "..."
  }
}
```

## Runtime behavior

- jobs persist in SQLite at `OCR_DB_PATH`
- uploaded screenshots are stored under `OCR_DATA_DIR/jobs/<jobId>` until the job finishes
- queued/processing jobs are resumed on service restart if their source files still exist
- expired jobs are cleaned up automatically on incoming requests
- structured JSON audit logs are emitted to stdout/systemd journal
- in-process rate limiting is applied per client IP

## Configuration

Environment variables:

- `OCR_API_TOKEN`
- `OCR_DATA_DIR`
- `OCR_DB_PATH`
- `OCR_JOB_TTL_SECONDS`
- `OCR_RATE_LIMIT_REQUESTS`
- `OCR_RATE_LIMIT_WINDOW_SECONDS`
- `OCR_LLM_URL`
- `OCR_LLM_TOKEN`
- `OCR_LLM_MODEL`

## Local run

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Tests

```bash
uv run --with fastapi --with "uvicorn[standard]" --with python-multipart --with Pillow --with httpx -m unittest discover -s tests -v
```

## Docker run

```bash
docker build -t yaneodex-ocr .
docker run --rm -p 8000:8000 yaneodex-ocr
```

## VPS notes

- Recommended MVP resources: `2 vCPU`, `4 GB RAM`, `20 GB SSD`
- Place behind `nginx` or `caddy` with TLS if possible
- Raw screenshots are deleted after OCR completes
