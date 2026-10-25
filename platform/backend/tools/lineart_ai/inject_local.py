import sys, time, uuid
from pathlib import Path
import cv2
sys.path.insert(0, ".")
import app, db
from services import reveal_game_builder as R

colour = cv2.imread("C:/Users/User/cmci/orig/05_colour.png")
line = cv2.imread("C:/Users/User/cmci/orig_clean/05_styled.png", cv2.IMREAD_GRAYSCALE)
H = 1254
W = round(colour.shape[1] * H / colour.shape[0])
colour = cv2.resize(colour, (W, H), interpolation=cv2.INTER_CUBIC)
line = cv2.resize(line, (W, H), interpolation=cv2.INTER_AREA)

iid = uuid.uuid4().hex[:12]
folder = app.ITEMS / iid
folder.mkdir(parents=True)
for n in ("original.png", "reveal_master.png"):
    cv2.imwrite(str(folder / n), colour)
cv2.imwrite(str(folder / "ai_lineart.png"), line)
db.add_image(iid, "Fox Fairy Princess", 1, 1, time.time(), status="review")
info = R.build_color(folder)
print("build", {k: info.get(k) for k in ("regions", "colours", "palette_numbers") if k in info})
db.update_image(iid, regions=info.get("regions", 0), colours=info.get("colours", 0))
app._publish(iid)
it = db.get_image(iid)
print(iid, it["status"], it["file_name"], it.get("error"))
