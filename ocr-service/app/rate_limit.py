from __future__ import annotations

import threading
import time
from collections import defaultdict, deque
from typing import Deque

from fastapi import Request

from .errors import ApiError


class SlidingWindowRateLimiter:
    def __init__(self, requests_per_window: int, window_seconds: int) -> None:
        self.requests_per_window = max(0, requests_per_window)
        self.window_seconds = max(1, window_seconds)
        self._hits: dict[str, Deque[float]] = defaultdict(deque)
        self._lock = threading.Lock()

    def check(self, key: str) -> tuple[bool, int]:
        if self.requests_per_window <= 0:
            return True, 0

        now = time.time()
        with self._lock:
            hits = self._hits[key]
            while hits and hits[0] <= now - self.window_seconds:
                hits.popleft()
            if len(hits) >= self.requests_per_window:
                retry_after = max(1, int(self.window_seconds - (now - hits[0])))
                return False, retry_after
            hits.append(now)
            return True, 0


def enforce_rate_limit(request: Request) -> None:
    limiter: SlidingWindowRateLimiter = request.app.state.rate_limiter
    forwarded_for = request.headers.get("x-forwarded-for", "")
    client_host = forwarded_for.split(",")[0].strip() or (request.client.host if request.client else "unknown")
    allowed, retry_after = limiter.check(client_host)
    if not allowed:
        raise ApiError(
            status_code=429,
            code="rate_limit_exceeded",
            message="Too many OCR requests. Please retry later.",
            details={"retryAfterSeconds": retry_after},
        )
