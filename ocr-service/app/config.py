from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(slots=True)
class Settings:
    data_dir: Path
    db_path: Path
    job_ttl_seconds: int = 3600
    auth_token: str = ""
    rate_limit_requests: int = 60
    rate_limit_window_seconds: int = 60
    llm_url: str = "https://openrouter.ai/api/v1/chat/completions"
    llm_token: str = ""
    llm_model: str = "google/gemma-3-4b-it:free"

    @classmethod
    def from_env(cls) -> "Settings":
        data_dir = Path(os.getenv("OCR_DATA_DIR", "data")).resolve()
        db_path = Path(os.getenv("OCR_DB_PATH", str(data_dir / "ocr.db"))).resolve()
        return cls(
            data_dir=data_dir,
            db_path=db_path,
            job_ttl_seconds=int(os.getenv("OCR_JOB_TTL_SECONDS", "3600")),
            auth_token=os.getenv("OCR_API_TOKEN", "").strip(),
            rate_limit_requests=int(os.getenv("OCR_RATE_LIMIT_REQUESTS", "60")),
            rate_limit_window_seconds=int(os.getenv("OCR_RATE_LIMIT_WINDOW_SECONDS", "60")),
            llm_url=os.getenv("OCR_LLM_URL", "https://openrouter.ai/api/v1/chat/completions").strip(),
            llm_token=os.getenv("OCR_LLM_TOKEN", "").strip(),
            llm_model=os.getenv("OCR_LLM_MODEL", "google/gemma-3-4b-it:free").strip(),
        )

    @property
    def jobs_dir(self) -> Path:
        return self.data_dir / "jobs"

    @property
    def auth_enabled(self) -> bool:
        return bool(self.auth_token)
