from __future__ import annotations

from fastapi import Header, Request

from .errors import ApiError


def require_api_token(
    request: Request,
    authorization: str | None = Header(default=None),
) -> None:
    settings = request.app.state.settings
    if not settings.auth_enabled:
        return
    if authorization != f"Bearer {settings.auth_token}":
        raise ApiError(status_code=401, code="unauthorized", message="Missing or invalid bearer token.")
