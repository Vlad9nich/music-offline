from __future__ import annotations

import json
import sqlite3
import threading
import time
from contextlib import closing
from pathlib import Path
from typing import Any


class SqliteJobStore:
    def __init__(self, db_path: Path) -> None:
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._lock = threading.Lock()
        self._initialize()

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.db_path, timeout=30, check_same_thread=False)
        connection.row_factory = sqlite3.Row
        return connection

    def _initialize(self) -> None:
        with closing(self._connect()) as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS jobs (
                    job_id TEXT PRIMARY KEY,
                    status TEXT NOT NULL,
                    processed_files INTEGER NOT NULL DEFAULT 0,
                    total_files INTEGER NOT NULL DEFAULT 0,
                    error TEXT,
                    candidates_json TEXT NOT NULL DEFAULT '[]',
                    expires_at REAL NOT NULL,
                    job_dir TEXT NOT NULL,
                    source_files_json TEXT NOT NULL DEFAULT '[]',
                    created_at REAL NOT NULL,
                    updated_at REAL NOT NULL
                )
                """
            )
            connection.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_jobs_status_expires
                ON jobs(status, expires_at)
                """
            )
            connection.commit()

    def create_job(
        self,
        job_id: str,
        total_files: int,
        job_dir: str,
        source_files: list[str],
        expires_at: float | None = None,
    ) -> None:
        now = time.time()
        with self._lock, closing(self._connect()) as connection:
            connection.execute(
                """
                INSERT INTO jobs (
                    job_id, status, processed_files, total_files, error,
                    candidates_json, expires_at, job_dir, source_files_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    job_id,
                    "queued",
                    0,
                    total_files,
                    None,
                    "[]",
                    expires_at or now,
                    job_dir,
                    json.dumps(source_files),
                    now,
                    now,
                ),
            )
            connection.commit()

    def get_job(self, job_id: str) -> dict[str, Any] | None:
        with closing(self._connect()) as connection:
            row = connection.execute("SELECT * FROM jobs WHERE job_id = ?", (job_id,)).fetchone()
        return self._row_to_payload(row) if row else None

    def update_job(self, job_id: str, **kwargs: Any) -> None:
        if not kwargs:
            return
        columns: list[str] = []
        values: list[Any] = []
        column_map = {
            "status": "status",
            "processed_files": "processed_files",
            "processedFiles": "processed_files",
            "total_files": "total_files",
            "totalFiles": "total_files",
            "error": "error",
            "candidates": "candidates_json",
            "expires_at": "expires_at",
            "expiresAt": "expires_at",
            "job_dir": "job_dir",
            "jobDir": "job_dir",
            "source_files": "source_files_json",
            "sourceFiles": "source_files_json",
        }
        for key, value in kwargs.items():
            column = column_map.get(key)
            if not column:
                continue
            if column in {"candidates_json", "source_files_json"}:
                value = json.dumps(value)
            columns.append(f"{column} = ?")
            values.append(value)
        columns.append("updated_at = ?")
        values.append(time.time())
        values.append(job_id)
        with self._lock, closing(self._connect()) as connection:
            connection.execute(f"UPDATE jobs SET {', '.join(columns)} WHERE job_id = ?", values)
            connection.commit()

    def touch_job_ttl(self, job_id: str, ttl_seconds: int) -> None:
        self.update_job(job_id, expires_at=time.time() + ttl_seconds)

    def list_recoverable_jobs(self) -> list[dict[str, Any]]:
        now = time.time()
        with closing(self._connect()) as connection:
            rows = connection.execute(
                """
                SELECT * FROM jobs
                WHERE status IN ('queued', 'processing') AND expires_at >= ?
                ORDER BY created_at ASC
                """,
                (now,),
            ).fetchall()
        return [self._row_to_payload(row) for row in rows]

    def cleanup_expired_jobs(self, now: float | None = None) -> list[str]:
        current_time = now or time.time()
        with self._lock, closing(self._connect()) as connection:
            rows = connection.execute("SELECT job_dir FROM jobs WHERE expires_at < ?", (current_time,)).fetchall()
            connection.execute("DELETE FROM jobs WHERE expires_at < ?", (current_time,))
            connection.commit()
        return [row["job_dir"] for row in rows]

    def _row_to_payload(self, row: sqlite3.Row) -> dict[str, Any]:
        return {
            "jobId": row["job_id"],
            "status": row["status"],
            "processedFiles": row["processed_files"],
            "totalFiles": row["total_files"],
            "error": row["error"],
            "candidates": json.loads(row["candidates_json"] or "[]"),
            "expiresAt": row["expires_at"],
            "jobDir": row["job_dir"],
            "sourceFiles": json.loads(row["source_files_json"] or "[]"),
            "createdAt": row["created_at"],
            "updatedAt": row["updated_at"],
        }
