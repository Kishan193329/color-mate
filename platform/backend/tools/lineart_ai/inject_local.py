"""Publishes a generated picture (painting + our line art) on a LOCAL test backend, for trying it in
the app. Run from that backend's folder with its Python:

    python inject_local.py COLOUR.png LINE.png "Title" [colours]

The line art goes in as ai_lineart.png, so the engine uses it instead of running its own model.
`colours` (default 36) lowers the palette for this picture only; the server's own default (100,
the owner's choice — CLAUDE.md §8.5v) is not changed.
"""
import sys
import time
import uuid

import cv2

sys.path.insert(0, ".")
import app  # noqa: E402
import db  # noqa: E402
from services import reveal_game_builder as R  # noqa: E402


def main():
    colour_path, line_path, title = sys.argv[1], sys.argv[2], sys.argv[3]
    R.COLOR_K = int(sys.argv[4]) if len(sys.argv) > 4 else 36

    colour = cv2.imread(colour_path)
    line = cv2.imread(line_path, cv2.IMREAD_GRAYSCALE)
    H = app.MAX_SIDE
    W = round(colour.shape[1] * H / colour.shape[0])
    colour = cv2.resize(colour, (W, H), interpolation=cv2.INTER_CUBIC)
    line = cv2.resize(line, (W, H), interpolation=cv2.INTER_AREA)

    iid = uuid.uuid4().hex[:12]
    folder = app.ITEMS / iid
    folder.mkdir(parents=True)
    for name in ("original.png", "reveal_master.png"):
        cv2.imwrite(str(folder / name), colour)
    cv2.imwrite(str(folder / "ai_lineart.png"), line)
    db.add_image(iid, title, 1, 1, time.time(), status="review")
    info = R.build_color(folder)
    db.update_image(iid, regions=info.get("regions", 0), colours=info.get("colours", 0))
    app._publish(iid)
    it = db.get_image(iid)
    print(iid, it["status"], it["file_name"], "regions", it["regions"], "colours", it["colours"], it.get("error"))


if __name__ == "__main__":
    main()
