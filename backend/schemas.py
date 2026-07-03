"""Pydantic models describing API request/response shapes."""
from __future__ import annotations

from enum import Enum
from typing import Optional

from pydantic import BaseModel


class JobStatus(str, Enum):
    created = "created"
    processing = "processing"
    done = "done"
    error = "error"


class UploadResponse(BaseModel):
    job_id: str
    status: JobStatus
    original_url: str


class ProcessRequest(BaseModel):
    job_id: str


class StepProgress(BaseModel):
    name: str
    status: str  # pending | active | done
    index: int


class JobResponse(BaseModel):
    job_id: str
    status: JobStatus
    progress: int  # 0-100
    current_step: Optional[str] = None
    steps: list[StepProgress] = []
    error: Optional[str] = None
    created_at: str
    updated_at: str
    artifacts: dict[str, str] = {}  # logical name -> download url


class JobSummary(BaseModel):
    job_id: str
    status: JobStatus
    progress: int
    created_at: str
    preview_url: Optional[str] = None


class JobsListResponse(BaseModel):
    jobs: list[JobSummary]


class SaveProgressRequest(BaseModel):
    filled: list[int] = []


class ProgressResponse(BaseModel):
    job_id: str
    filled: list[int]
    total: int
    percent: int
    updated_at: Optional[str] = None
