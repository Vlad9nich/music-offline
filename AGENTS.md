# YaNeoDex Project Context

## Project Overview

- `YaNeoDex` is an Android 11+ offline music app built with Jetpack Compose.
- Main app module: `app/`
- OCR backend for playlist screenshot import: `ocr-service/`
- GitHub repo currently tracks both the Android app and the OCR service in one repository.

## What The App Already Does

- imports local audio files into app-private storage
- extracts title, artist, and duration metadata
- creates playlists and adds tracks to them
- plays local tracks and playlist queues
- supports shuffle playback
- imports playlist tracks from screenshots through the external OCR service

## Important Android Areas

- App state and orchestration:
  - `C:\Users\vladg\codex\app\src\main\java\com\codex\signalflow\MainViewModel.kt`
- Playlist UI:
  - `C:\Users\vladg\codex\app\src\main\java\com\codex\signalflow\ui\PlaylistsScreen.kt`
- OCR import client:
  - `C:\Users\vladg\codex\app\src\main\java\com\codex\signalflow\importer\ScreenshotImportRepository.kt`
  - `C:\Users\vladg\codex\app\src\main\java\com\codex\signalflow\importer\ScreenshotImportMatcher.kt`
- Playback queue and shuffle logic:
  - `C:\Users\vladg\codex\app\src\main\java\com\codex\signalflow\player\PlaybackManager.kt`

## Important OCR Service Areas

- FastAPI entry:
  - `C:\Users\vladg\codex\ocr-service\app\main.py`
- OCR and LLM pipeline:
  - `C:\Users\vladg\codex\ocr-service\app\ocr_pipeline.py`
- Runtime settings:
  - `C:\Users\vladg\codex\ocr-service\app\config.py`
- OCR tests:
  - `C:\Users\vladg\codex\ocr-service\tests\test_ocr_pipeline.py`

## Current OCR Architecture

- Android sends screenshots to the OCR service over HTTP.
- Single image:
  - `POST /v1/ocr-image`
- Batch flow:
  - `POST /v1/playlist-import/jobs`
  - `GET /v1/playlist-import/jobs/{jobId}`
- Candidate normalization:
  - `POST /v1/ocr`

The app expects the OCR service to return:

```json
{
  "candidates": [
    {
      "candidateId": "string",
      "screenshotIndex": 0,
      "rawText": "Title | Artist",
      "artistGuess": "Artist",
      "titleGuess": "Title",
      "confidence": 0.0,
      "bbox": [0, 0, 100, 50]
    }
  ]
}
```

## OCR Runtime Notes

- OCR auth is bearer-token based.
- Secrets must stay in env or systemd override, never in repo.
- Current OCR service is designed for OpenRouter `chat/completions` multimodal models.
- The repo default model in `ocr-service/app/config.py` is currently:
  - `google/gemma-3-4b-it:free`
- Do not switch to embedding-only models for OCR. The pipeline expects a model that accepts image input and returns JSON text.

## Known Product Decisions

- Playlist detail long-scroll crash was fixed by deduping playlist track IDs before rendering and by using safer list keys.
- Shuffle was adjusted to avoid placing adjacent tracks by the same artist when alternatives exist.
- OCR matching is strict enough to reduce false positives, but still relies on the OCR service quality.
- `ScreenshotImportRepository` uses longer HTTP read timeouts because free-tier multimodal OCR can take well over one minute.

## Build And Test

### Android

This workspace does not always have a stable root Gradle wrapper path, so verify before assuming commands.

If wrapper is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

If using the OCR service only:

```powershell
cd C:\Users\vladg\codex\ocr-service
uv run --with Pillow --with fastapi --with python-multipart --with httpx python -m unittest tests.test_ocr_pipeline -v
```

## Emulator Notes

- The Android emulator has been used heavily for OCR and playlist testing.
- System file pickers in the emulator can be flaky.
- If OCR UI testing behaves inconsistently, compare:
  - direct OCR service response
  - app-side recognized candidates
  - app-side matched tracks

## Deployment Notes

- OCR service on VPS is managed via `systemd` service:
  - `yaneodex-ocr`
- Runtime env is typically in:
  - `/etc/systemd/system/yaneodex-ocr.service.d/override.conf`
- Useful commands on VPS:

```bash
systemctl is-active yaneodex-ocr
journalctl -u yaneodex-ocr -n 100 --no-pager
```

## Files And Directories That Are Local-Only

These should not be treated as product source:

- `tmp/`
- `output/`
- `runtime/`
- `.codex/`
- `.serena/`
- `ocr-service/data/`
- `__pycache__/`

## Guidance For Future AI Sessions

- Inspect repo state before editing. The worktree may contain local-only files.
- Prefer reading the files listed above before making changes.
- Keep OCR secrets out of code and commits.
- Treat OCR model changes carefully:
  - verify the model supports multimodal image input
  - verify it returns valid JSON through OpenRouter `chat/completions`
- Do not assume the OCR service on VPS matches repo state unless explicitly redeployed.
