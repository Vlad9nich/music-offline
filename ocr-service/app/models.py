from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


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
    candidates: list[CandidateResponse] = Field(default_factory=list)


class OcrNormalizeRequest(BaseModel):
    candidates: list[CandidateResponse]


class OcrNormalizeResponse(BaseModel):
    candidates: list[CandidateResponse]


class OcrResponse(BaseModel):
    candidates: list[CandidateResponse]


class ErrorPayload(BaseModel):
    code: str
    message: str
    requestId: str
    details: dict[str, Any] | None = None


class ErrorEnvelope(BaseModel):
    error: ErrorPayload
