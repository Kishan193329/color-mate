import zipfile, hashlib, sys
from pathlib import Path
sys.path.insert(0, r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai\backend")
from services import game_package_builder as GPB, publishing

f = Path(r"c:\Users\pc07\Downloads\colorforge-ai\colorforge-ai\storage\jobs\br_14")
for x in ["publish.json", "game_package.json", "game_assets.zip"]:
    (f / x).unlink(missing_ok=True)

publishing.init(f, category="animals", title="Girl on Street", job_id="br_14")
try:
    GPB.build_game_package(f)
    print("BUG: packaged a non-approved image")
except ValueError as e:
    print("refused (not approved):", str(e)[:55])

publishing.set_status(f, "approved", validate=False)
m = GPB.build_game_package(f)
print("complete:", m["complete"], "| missing:", m["missing_assets"], "| review_status:", m["review_status"])
print("image_id:", m["image_id"], "| dims:", m["dimensions"], "| counts:", m["counts"])
print("scores:", m["scores"])
print("category:", m["category"])
print("assets:", len(m["assets"]), "entries")

bad = [v for v in m["assets"].values()
       if v.startswith("/") or "\\" in v or ":" in v or not v.startswith("assets/")]
print("android-readable paths:", not bad, "(bad:", bad, ")")
print("game_package.json:", (f / "game_package.json").exists(), "| game_assets.zip:", (f / "game_assets.zip").exists())

with zipfile.ZipFile(f / "game_assets.zip") as z:
    names = z.namelist()
    print("zip entries:", len(names), "| has manifest:", "game_package.json" in names)
    ok = all(zp in names and hashlib.sha1(z.read(zp)).hexdigest() == sha
             for zp, sha in m["integrity_sha1"].items())
    print("zip integrity matches manifest sha1:", ok)
    print("entries:", names)
