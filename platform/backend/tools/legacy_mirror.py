"""Copies the old ionicsolutions.in "By Number" catalogue into our storage + DB (see legacy.py).

Run ON THE SERVER, from the backend folder, with the server's storage settings loaded:

    set -a; . ./storage.env; set +a
    .venv/bin/python tools/legacy_mirror.py              # everything (~10.7k pictures, ~6 GB)
    .venv/bin/python tools/legacy_mirror.py --limit=20   # a trial: first 20 per category

Safe to stop and run again: a file already copied is skipped (progress in data/legacy_done.txt),
and the DB lists are rewritten from the old server each run, in its order. A picture whose files
could not all be copied is left out of the lists, so the app never shows a broken tile.
"""
import concurrent.futures as cf
import os
import sys
import threading
import time
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import db  # noqa: E402
import legacy  # noqa: E402
import storage  # noqa: E402

OLD = "http://ionicsolutions.in/ColoringBook/"
WORKERS = 8
DONE_FILE = Path(db.DB_PATH).parent / "legacy_done.txt"


def fetch(url, tries=4):
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "ColourMate-mirror/1.0"})
            with urllib.request.urlopen(req, timeout=90) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            err = e
        except Exception as e:  # noqa: BLE001 — flaky host
            err = e
        time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"{url}: {err}")


def get_json(url):
    import json
    return json.loads(fetch(url).decode("utf-8"))


def feed(category):
    rows, nxt, seen = [], "", set()
    while True:
        url = OLD + "index.php/Number/gimg/c/" + urllib.parse.quote(category)
        if nxt:
            url += "?next=" + urllib.parse.quote(str(nxt))
        page = get_json(url)
        fresh = [r for r in page.get("data") or [] if r.get("fileName") not in seen]
        seen.update(r.get("fileName") for r in fresh)
        rows += fresh
        nxt = page.get("next") or ""
        if not nxt or not fresh:
            return rows


def main():
    limit = next((int(a.split("=", 1)[1]) for a in sys.argv[1:] if a.startswith("--limit=")), 0)
    store = storage.get_store()
    print("storage:", store.kind, flush=True)
    db.init()

    cats = get_json(OLD + "index.php/Number/category/")["categories"]
    feeds = {c: feed(c) for c in cats}
    if limit:
        feeds = {c: rows[:limit] for c, rows in feeds.items()}
    audios = get_json(OLD + "index.php/d2number/Number/audios").get("data", [])
    for c in cats:
        print(f"  {c:10s} {len(feeds[c])}", flush=True)

    done = set(DONE_FILE.read_text().split()) if DONE_FILE.exists() else set()
    lock = threading.Lock()
    log = open(DONE_FILE, "a")

    def copy(key, url):
        if key in done:
            return True
        data = fetch(url)
        if data is None:
            return False
        store.put_bytes(key, data)
        with lock:
            done.add(key)
            log.write(key + "\n")
            log.flush()
        return True

    jobs = {}
    for c, rows in feeds.items():
        cat = c.strip().lower()
        for r in rows:
            f = r["fileName"]
            for sub in (f"t/{f}.png", f"{f}.png", f"{f}.colored.png"):
                key = f"{legacy.PREFIX}thumb/{cat}/{sub}"
                jobs[key] = (OLD + "thumb/" + urllib.parse.quote(c) + "/" + urllib.parse.quote(sub), cat, f)
    for a in audios:
        name = a.rsplit("/", 1)[-1]
        jobs[f"{legacy.PREFIX}audios/{name}"] = (a, None, None)

    todo = [k for k in jobs if k not in done]
    print(f"files: {len(jobs)} total, {len(todo)} to copy", flush=True)
    failed = set()
    started = time.time()
    with cf.ThreadPoolExecutor(WORKERS) as pool:
        futs = {pool.submit(copy, k, jobs[k][0]): k for k in todo}
        for i, fut in enumerate(cf.as_completed(futs), 1):
            k = futs[fut]
            try:
                ok = fut.result()
            except Exception as e:  # noqa: BLE001
                ok = False
                print("  FAIL", k, e, flush=True)
            if not ok:
                failed.add(k)
            if i % 500 == 0:
                rate = i / max(1, time.time() - started)
                print(f"  {i}/{len(todo)}  {rate:.1f} files/s  failed {len(failed)}", flush=True)

    broken = {(jobs[k][1], jobs[k][2]) for k in failed if jobs[k][1]}
    kept = {c: [r for r in rows if (c.strip().lower(), r["fileName"]) not in broken]
            for c, rows in feeds.items()}
    ok_audios = [a for a in audios if f"{legacy.PREFIX}audios/{a.rsplit('/', 1)[-1]}" not in failed]
    n = legacy.save_catalogue(cats, kept, ok_audios)
    print(f"done: {n} rows listed, {len(broken)} pictures left out, {len(failed)} files failed")


if __name__ == "__main__":
    main()
