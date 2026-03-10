# YaNeoDex OCR Service

Self-hosted OCR service for playlist screenshot import.

## Stack

- FastAPI
- PaddleOCR
- CPU-only deployment

## API

- `POST /v1/playlist-import/jobs`
  - multipart field `files`
  - returns `{ "jobId": "..." }`
- `GET /v1/playlist-import/jobs/{jobId}`
  - returns job status and OCR candidates

## Local run

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
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

