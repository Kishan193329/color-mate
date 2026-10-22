"""Upload the Figma-vectorised SVGs into ColorForge admin, category-wise, then approve & publish.

  python3 upload_svgs.py https://srv1919471.hstgr.cloud  <email>  <password>  [--no-publish]
                         [--dir=<folder with the .svg files>]  [--only=35_2,35_232838]

Resumable: a picture whose title already exists in the admin is skipped. Uploads are sequential
(the server generates one job at a time anyway — §8.5aw) and the script waits for each to reach
'review' before approving it.
"""
import json
import sys
import time
import urllib.request
import uuid
from pathlib import Path

from manifest import M

HERE = Path(__file__).parent


def call(base, path, token=None, method="GET", body=None, files=None, fields=None):
    headers = {}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if files is not None:
        bnd = uuid.uuid4().hex
        parts = []
        for k, v in (fields or {}).items():
            parts.append(f'--{bnd}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
        for k, (name, blob, ctype) in files.items():
            parts.append(f'--{bnd}\r\nContent-Disposition: form-data; name="{k}"; filename="{name}"\r\n'
                         f'Content-Type: {ctype}\r\n\r\n'.encode() + blob + b"\r\n")
        parts.append(f"--{bnd}--\r\n".encode())
        data = b"".join(parts)
        headers["Content-Type"] = f"multipart/form-data; boundary={bnd}"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=300) as r:
        return json.loads(r.read() or b"null")


def main():
    base, email, pw = sys.argv[1].rstrip("/"), sys.argv[2], sys.argv[3]
    publish = "--no-publish" not in sys.argv
    opt = dict(a[2:].split("=", 1) for a in sys.argv[4:] if a.startswith("--") and "=" in a)
    src = Path(opt.get("dir", HERE))
    only = set(filter(None, opt.get("only", "").split(",")))
    tok = call(base, "/api/auth/login", body={"email": email, "password": pw}, method="POST")
    tok = tok.get("access") or tok.get("token")
    have = {i["title"]: i for i in call(base, "/api/items", tok)}
    for k, (title, cat) in M.items():
        if only and k not in only:
            continue
        it = have.get(title)
        if not it:
            blob = (src / f"{k}.svg").read_bytes()
            it = call(base, "/api/items", tok, "POST",
                      files={"file": (f"{k}.svg", blob, "image/svg+xml")},
                      fields={"title": title, "category": cat, "type": "colour"})
            print("uploaded", title, "->", cat, flush=True)
        iid = it["id"]
        for _ in range(240):                         # up to ~20 min for one picture
            it = next(i for i in call(base, "/api/items", tok) if i["id"] == iid)
            if it["status"] != "processing":
                break
            time.sleep(5)
        print("  ", it["status"], it.get("regions"), "regions", it.get("colours"), "colours",
              ("WARN: " + it["error"]) if it.get("error") else "", flush=True)
        if it.get("error"):
            print("   left in review for the owner (warning above)", flush=True)
            continue
        if publish and it["status"] in ("review", "submitted", "rejected"):
            call(base, f"/api/items/{iid}/approve", tok, "POST", body={})
            print("   approved & publishing", flush=True)


if __name__ == "__main__":
    main()
