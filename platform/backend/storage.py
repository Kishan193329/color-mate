"""Local disk / S3 storage for published bundles.

Flip with env `STORAGE=local|s3`. Local serves through FastAPI `/cdn/{key}`;
S3 uses the bucket (optional `CDN_BASE` for CloudFront URLs).
"""
from __future__ import annotations

import os
import shutil
from pathlib import Path

BASE = Path(__file__).resolve().parent
CDN_ROOT = BASE / "data" / "cdn"


class LocalStore:
    kind = "local"

    def __init__(self, root: Path = CDN_ROOT) -> None:
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)

    def put_bytes(self, key: str, data: bytes) -> None:
        path = self.root / key
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def url(self, key: str) -> str:
        return f"/cdn/{key.lstrip('/')}"

    def local_path(self, key: str) -> Path | None:
        path = self.root / key.lstrip("/")
        if path.is_file():
            return path
        return self._resolve_ci(key)

    def exists(self, key: str) -> bool:
        return self.local_path(key) is not None

    def _resolve_ci(self, key: str) -> Path | None:
        """Case-insensitive fallback for a key that did not match exactly.

        Category folders are written lower-cased, but a folder created by an older
        publish can carry the category's original casing ("Animals"). Windows hides
        that difference; Linux answers 404. Walk the key one segment at a time and
        match case-insensitively so both spellings resolve.
        """
        current = self.root
        for part in key.lstrip("/").split("/"):
            if part in ("", ".") or part == ".." or "\\" in part:
                if part in ("", "."):
                    continue
                return None
            nxt = current / part
            if nxt.exists():
                current = nxt
                continue
            try:
                match = next(
                    (c for c in current.iterdir() if c.name.lower() == part.lower()), None
                )
            except OSError:
                return None
            if match is None:
                return None
            current = match
        return current if current.is_file() else None

    def delete_prefix(self, prefix: str) -> None:
        folder = self.root / prefix.rstrip("/")
        if folder.is_dir():
            shutil.rmtree(folder, ignore_errors=True)
        # also delete a lone file with that exact key, if any
        file_path = self.root / prefix
        if file_path.is_file():
            file_path.unlink(missing_ok=True)


class S3Store:
    kind = "s3"

    def __init__(self) -> None:
        import boto3

        self.bucket = os.environ["S3_BUCKET"]
        self.region = os.environ.get("AWS_REGION") or os.environ.get("AWS_DEFAULT_REGION") or "ap-south-1"
        self.cdn_base = (os.environ.get("CDN_BASE") or "").rstrip("/")
        self._s3 = boto3.client("s3", region_name=self.region)

    def put_bytes(self, key: str, data: bytes) -> None:
        key = key.lstrip("/")
        ctype = "application/octet-stream"
        lower = key.lower()
        if lower.endswith(".png"):
            ctype = "image/png"
        elif lower.endswith((".jpg", ".jpeg")):
            ctype = "image/jpeg"
        elif lower.endswith(".json"):
            ctype = "application/json"
        elif lower.endswith(".svg"):
            ctype = "image/svg+xml"
        elif lower.endswith(".html"):
            ctype = "text/html; charset=utf-8"
        self._s3.put_object(Bucket=self.bucket, Key=key, Body=data, ContentType=ctype)

    def url(self, key: str) -> str:
        key = key.lstrip("/")
        if self.cdn_base:
            return f"{self.cdn_base}/{key}"
        return f"https://{self.bucket}.s3.{self.region}.amazonaws.com/{key}"

    def local_path(self, key: str) -> Path | None:
        return None

    def exists(self, key: str) -> bool:
        try:
            self._s3.head_object(Bucket=self.bucket, Key=key.lstrip("/"))
            return True
        except Exception:                 # botocore raises ClientError(404) for a missing key
            return False

    def delete_prefix(self, prefix: str) -> None:
        prefix = prefix.lstrip("/")
        paginator = self._s3.get_paginator("list_objects_v2")
        for page in paginator.paginate(Bucket=self.bucket, Prefix=prefix):
            objs = [{"Key": o["Key"]} for o in page.get("Contents") or []]
            if objs:
                self._s3.delete_objects(Bucket=self.bucket, Delete={"Objects": objs})


def get_store():
    mode = (os.environ.get("STORAGE") or "local").strip().lower()
    if mode == "s3":
        return S3Store()
    return LocalStore()
