"""Pictures that already carry a DRAWN outline (coloured-pencil / cartoon art): the outline alone,
no colour edges -> line_cleanup -> line_style. flat_to_lines also adds colour-boundary lines, which
on textured pencil art draws a line along every shade and buries the picture.

    python outline_only.py IMAGE OUT.png [dark]      dark = grey level below which a pixel is ink
Measured on the autumn-cottage set: the outline is dark BROWN (not black), ~60 keeps it alone,
80+ starts picking up pencil texture, and an HSV saturation gate throws the whole outline away.
"""
import sys, cv2, numpy as np
sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))
from line_cleanup import SIDE, cleanup
from line_style import style
src, out, dark_v = sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 60
im = cv2.imread(src); h, w = im.shape[:2]; s = SIDE / max(h, w)
im = cv2.resize(im, (round(w * s), round(h * s)), interpolation=cv2.INTER_CUBIC)
ink = cv2.cvtColor(im, cv2.COLOR_BGR2GRAY) < dark_v   # the drawn outline is dark brown, not black
raw = np.where(ink, 0, 255).astype(np.uint8)
clean = cleanup(raw); styled, _ = style(clean)
cv2.imwrite(out, styled)
