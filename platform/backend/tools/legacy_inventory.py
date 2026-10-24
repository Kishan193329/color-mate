"""Lists everything the old ionicsolutions.in "By Number" server serves, so it can be moved.

    python tools/legacy_inventory.py OUT_DIR

Writes OUT_DIR/inventory.json: {"categories": [...], "feeds": {category: [rows in server order]},
"audios": [...]} and prints counts plus the size of one sample picture. Read-only — downloads lists
only, no images.
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

OLD = "http://ionicsolutions.in/ColoringBook/"


def get_json(url, tries=4):
    for attempt in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:  # flaky host: back off and retry
            if attempt == tries - 1:
                raise
            print("  retry", url, e, flush=True)
            time.sleep(2 * (attempt + 1))


def size_of(url):
    req = urllib.request.Request(url, method="HEAD")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return int(r.headers.get("Content-Length") or 0)
    except Exception:
        return -1


def feed(category):
    rows, nxt, seen = [], "", set()
    while True:
        url = OLD + "index.php/Number/gimg/c/" + urllib.parse.quote(category)
        if nxt:
            url += "?next=" + urllib.parse.quote(str(nxt))
        page = get_json(url)
        data = page.get("data") or []
        fresh = [r for r in data if r.get("fileName") not in seen]
        for r in fresh:
            seen.add(r.get("fileName"))
        rows += fresh
        nxt = page.get("next") or ""
        if not nxt or not fresh:
            return rows


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    cats = get_json(OLD + "index.php/Number/category/")["categories"]
    feeds = {}
    for c in cats:
        feeds[c] = feed(c)
        print(f"{c:10s} {len(feeds[c]):6d} pictures", flush=True)
    audios = get_json(OLD + "index.php/d2number/Number/audios").get("data", [])
    json.dump({"categories": cats, "feeds": feeds, "audios": audios},
              open(os.path.join(out, "inventory.json"), "w"), indent=1)

    names = {(r["category"], r["fileName"]) for rows in feeds.values() for r in rows}
    print("unique pictures:", len(names), " total rows:", sum(map(len, feeds.values())))
    c, f = next(iter(names))
    sizes = [size_of(f"{OLD}thumb/{c}/t/{f}.png"), size_of(f"{OLD}thumb/{c}/{f}.png"),
             size_of(f"{OLD}thumb/{c}/{f}.colored.png")]
    print("sample", c, f, "thumb/line/colored bytes:", sizes)
    print("audios:", len(audios))


if __name__ == "__main__":
    main()
