from __future__ import annotations

from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from .models import ErrorEnvelope, ErrorPayload


class ApiError(Exception):
    def __init__(
        self,
        status_code: int,
        code: str,
        message: str,
        details: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message
        self.details = details


def build_error_response(
    request_id: str,
    code: str,
    message: str,
    details: dict[str, Any] | None = None,
) -> ErrorEnvelope:
    return ErrorEnvelope(
        error=ErrorPayload(
            code=code,
            message=message,
            requestId=request_id,
            details=details,
        )
    )


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def handle_api_error(request: Request, error: ApiError) -> JSONResponse:
        request_id = getattr(request.state, "request_id", "")
        payload = build_error_response(request_id, error.code, error.message, error.details)
        return JSONResponse(status_code=error.status_code, content=payload.model_dump())

    @app.exception_handler(HTTPException)
    async def handle_http_error(request: Request, error: HTTPException) -> JSONResponse:
        request_id = getattr(request.state, "request_id", "")
        detail = error.detail if isinstance(error.detail, str) else "Request failed."
        payload = build_error_response(request_id, "http_error", detail)
        return JSONResponse(status_code=error.status_code, content=payload.model_dump())

    @app.exception_handler(Exception)
    async def handle_unexpected_error(request: Request, _: Exception) -> JSONResponse:
        request_id = getattr(request.state, "request_id", "")
        payload = build_error_response(request_id, "internal_error", "Internal OCR service error.")
        return JSONResponse(status_code=500, content=payload.model_dump())
