# CLAUDE.md — ColorForge / Colour Mate

> **Read this first.** It is the working memory for this repo. It records what was
> discovered by reading the code, what was decided, and what is still open.
> **Keep it updated** — when a decision changes or new facts are found, edit this file.
>
> Last updated: **2026-10-22** (SVG / Daily / Hit deployed + merged, v16 — §8.5by;🔴 **Play "Disruptive Ads" strike fixed — read §8.5bx before touching ads**; nav changes §8.5bx; LIVE on a VPS §8.5ar; S3 §8.5at; July engine restored §8.5bv) · Branch: `quality-fixes`
> Owner: Kishan (kishankalathiya1111@gmail.com). Owner writes in Gujarati/Hinglish —
> replying in simple English mixed with Gujarati words works well.

---

## 1. What this repo actually is

Two **separate** products that need to be connected:

| # | Thing | Path | What it is |
|---|---|---|---|
| 1 | **ColorForge** | `platform/backend/` | Python/FastAPI. AI engine + Admin CRM. Turns any image into colour-fill or diamond content. |
| 2 | **Colour Mate** | `Colour Mate/Colour Mate/Colour Mate/` | **Native Android app (Java + Gradle). LIVE ON PLAY STORE.** 134 Java files. |

**The goal:** publish content from ColorForge (1) into the live app (2).

### ⚠️ Repo state warnings

- The **root `README.md` is STALE**. It describes a `backend/` + `frontend/` (React/Vite)
  layout that no longer exists. Ignore it. The backend moved to `platform/backend/`.
- **Since 2026-10-14 the whole thing IS in git** — `Colour Mate/` (the Android app),
  `platform/backend`, `colour_mate/` (paused Flutter), CLAUDE.md, DEPLOY.md — pushed to
  **https://github.com/Kishan193329/color-mate.git**. **Branch layout (owner's rule):**
  `main` = **ONLY the Android project, at the repo root** (no CLAUDE.md, no backend) — made with
  `git subtree split --prefix="Colour Mate/Colour Mate/Colour Mate"` and force-pushed; `quality-fixes`
  = the full tree (app + `platform/backend` + Flutter + docs).
  **🔴 Owner's rule since 2026-10-23: do NOT merge or re-split into `main`.** New work goes on a named
  branch and is pushed there. **Name a new branch `YYYY-MM-DD-<feature>`** (owner, 2026-10-23) —
  latest is **`2026-10-24-cpp-core`** (§8.5cc, on top of `2026-10-23-svg-vector-engine` §8.5bz). `main` stays at `b48fb2b` until
  the owner says otherwise. **VPS = git:** on 2026-10-23 all 21 tracked backend files on the VPS
  were made byte-identical to this branch (deployed `1ee361f`/`cbd2507`/`72e03e1` incl. the new
  `referral.py`; its table is created lazily on the first `/index.php/App/ref/*` call; backup in
  `/root/deploy_backup_20261023_202320/`). Tag **`viral`** = the full tree at `92e0c21` (same commit as
  `quality-fixes` and `svg-daily-hit-v16` on that date) — the snapshot to come back to.
  *(Before that date the rule was: commit on `quality-fixes`, re-split, `git push -f origin
  <split>:main`.)* Ignored on purpose: `platform/backend/data/`, `models/*.pth`, `.venv`, `build/`,
  `local.properties`, and `color_mate_old/` (the owner's 3 GB July backup — it was staged once by
  accident and left a 3 GB dangling blob in `.git` until pruned). `google-services.json` is
  committed deliberately. Put `ADMOB_APP_ID` in `~/.gradle/gradle.properties`, never the project.
  *(Historical: before this date `platform/` and `Colour Mate/` were untracked — see §8.5al for
  what that cost.)*
- **🌐 WORKING WITHOUT THE PC (set up 2026-10-24).** Everything below runs on GitHub, so a cloud
  session (claude.ai/code, the Claude mobile app) can carry the project with no PC:
  | Need | How |
  |---|---|
  | Backend live | `.github/workflows/deploy-backend.yml` — push to the branch in repo variable **`DEPLOY_BRANCH`** (Settings → Variables; unset = push deploys nothing), or Actions → *Deploy backend* → Run workflow on any branch. Server side `.github/deploy/vps_deploy.sh`: waits for the generation queue, backs up code to `/root/deploy_backups/`, copies, `pip install` only if `requirements.txt` changed (CPU torch index), `import app`, restart, health check, **auto-rollback**. Writes `/opt/colorforge/DEPLOYED` (sha/branch/time). First run 2026-10-24 on `quality-fixes` @ `7c95531`: success, no-op (that backend == live). |
  | App APK | `.github/workflows/build-apk.yml` — any push touching the Android project (or Run workflow) builds the debug APK and puts it on the rolling pre-release **`apk-<branch>`** (Releases page → download on the phone). Signed with the PC's debug key (secret `DEBUG_KEYSTORE_B64`), so it installs over the test phones' debug build. CI overrides the project's Windows `org.gradle.java.home` via `~/.gradle/gradle.properties`. Release AABs still need the owner's upload keystore (not in CI). |
  | SVG uploads | `.github/workflows/upload-svgs.yml` — Run workflow with ids (or empty = all) and publish on/off. The 53 SVGs are on the VPS in `/root/svg_upload/source/pics`; the admin password stays on the VPS. |
  | Admin | the web admin, as always: https://srv1919471.hstgr.cloud/ |
  Secrets: `VPS_HOST`, `VPS_SSH_KEY` (key `github-actions-deploy@color-mate`, used only by CI; its
  public half is in the VPS `authorized_keys`), `VPS_KNOWN_HOSTS`, `DEBUG_KEYSTORE_B64`. **The repo is
  PUBLIC** — never commit a secret; APK pre-releases are publicly downloadable. A cloud session has
  no SSH to the VPS: change the server only through these workflows.
  ⚠️ Workflow files only show "Run workflow" when they are on the default branch (`quality-fixes`),
  so new workflows go there as well as on the feature branch.
- Root has ~40 `*_REPORT.md` files from earlier work. Mostly historical, low value.
- `flutter_app/` was **deleted on 2026-07-20** at the owner's explicit request. It was an
  old Flutter prototype, not the real product. Do not recreate it. (An empty locked folder
  may remain until VS Code is restarted.)

---

## 2. How to run ColorForge backend

```bash
cd platform/backend
python -m venv .venv
.venv\Scripts\python.exe -m pip install fastapi "uvicorn[standard]" python-multipart \
    numpy opencv-python-headless boto3 PyJWT
.venv\Scripts\python.exe -m pip install torch --index-url https://download.pytorch.org/whl/cpu
.venv\Scripts\python.exe -m uvicorn app:app --port 8000
```

Open **http://localhost:8000** → Colour Studio (Admin CRM).
Seeded boss login (from `db.py:95`): **kishankalathiya1111@gmail.com** / `colorforge123`

### Environment notes (verified 2026-07-20 on the home PC)

- Works on **Python 3.14.2**. `torch 2.13.0+cpu` has 3.14 wheels — installs fine.
  (Old `__pycache__` was 3.12; either version works.)
- `torch` is **optional**. Without it the engine falls back to an OpenCV line-art path.
  Quality difference on `cat.png`: fallback = 101 regions / 13 colours in ~6 s;
  torch ML model = 296 regions / 87 colours in ~50 s. **ML output is clearly better.**
- Verified end-to-end: login → upload → generate → review → publish → `/api/library`,
  producing `data/cdn/images/<id>/` with `border.png`, `border.svg`, `id_map.png`,
  `regions.json`, `swatches/N.png`.

**Gotcha when viewing output PNGs:** `border.png` / `play_border.png` store **black RGB with
the drawing in the ALPHA channel**. Any viewer that ignores alpha shows a solid black square.
That is NOT a bug. Composite over white before judging it.

---

## 3. Colour Mate — how the live app works (reverse-engineered 2026-07-20)

Package `com.colourmate.paintbynumber` · appId `com.pixel.art.coloring.color.number.paint`
· versionCode 13 · minSdk 24 / targetSdk 36 · Firebase + ProGuard + NDK.
Built on the third-party **Sumo "Recolor"** engine (`com/sumoing/recolor/`).

### 3.1 There are ZERO images in the APK

All content is fetched at runtime from **three unrelated backends**:

| Tab | Backend | Owner |
|---|---|---|
| **Number** (main menu) | `http://ionicsolutions.in/ColoringBook/` | **Owner BUYS this monthly. No access.** |
| Colour | `https://libcdn.recolor.com/Books/` | Recolor company — not ours |
| Pixel | `https://s3.amazonaws.com/fungames-forfree/colornumber/public/` | Not ours |

**Only the Number tab matters for our work.**

Base URLs live in native C++ — `app/src/main/cpp/colorbynumber.cpp` — as **plaintext**
`NewStringUTF` literals. `xor_helper.py` in the project root was written to XOR-obfuscate
them but **was never applied**. Changing a URL currently needs an **NDK rebuild**.

### 3.2 Number-tab API contract (what we must mimic)

```
GET  index.php/Number/category/         → {"categories": ["animals", "flowers", ...]}
GET  index.php/Number/gimg/c/{category} → {"data": [ {key, fileName, category,
                                                      timeStamp, isNewToday} ], "next": "..."}
GET  thumb/{category}/t/{fileName}.png          → grid thumbnail
GET  thumb/{category}/{fileName}.png            → line art
GET  thumb/{category}/{fileName}.colored.png    → solution (see 3.3)
```
Pagination is `?next={key}`. Categories are just **plain strings** — no id, no order, no icon.

### 3.3 Image format — region numbers hide in the ALPHA channel

This is the critical/fragile part. `model/game/GameView.java:2137-2156`:

```java
int alpha = Color.alpha(originalColorPixels[i]);
if (alpha > 0 && alpha < 255) {          // partial alpha == "draw a number label here"
    int adjustedTextSize = alpha;        // the alpha VALUE is the font size
```
`resolvePixelColor()` (`:2174-2207`) samples the 4 neighbours to recover which colour that
marker belongs to. Palette numbers are then assigned by **`HashSet` iteration order**
(`:2163-2171`) — so **numbering is not stable between runs**. Pre-existing bug.

**ColorForge output does NOT match this.** ColorForge makes `id_map.png` + `regions.json` +
`swatches/`. A **converter is required** to emit `{name}.png` + `{name}.colored.png`.
ColorForge has all the needed data (per-region `cx`, `cy`, `r`, colour), so it is doable.

### 3.4 ✅ Three owner requirements are ALREADY supported — no app code needed

| Requirement | Status | Evidence |
|---|---|---|
| New image at **top** of menu | ✅ Already | **No client-side sort exists.** Items render in exact server array order. `Manager.java:400-440`; `BaseRestAdapter.reverseOrder` defaults false and is never set. Put the new item at `data[0]`. |
| **Categories** add dynamically | ✅ Already | Category list is server-driven (`NumberViewModel.java:113-114`). New string → new tab. |
| **Date-wise** scheduling | ✅ Already | `Manager.java:416-425` **drops any item whose `timeStamp` is in the future**. Set `timeStamp` to the publish date and the client hides it until then. |

A `DAILY` category and a working daily banner already ship (`NumberViewModel.java:270-363`),
using a date-seeded shuffle so every device shows the same images on the same date.

### 3.5 ✅ Adding content cannot break existing users

Nothing is index-addressed. Every store is **string-keyed by `fileName`**:
`SavedSteps_{fileName}`, `IsFinishedGame_{fileName}` (`util/DataManager.java:104-142, 200-206`),
favourites, unlocks (`library/TokenManager.java`, keyed by name).
No Room, no SQLite — SharedPreferences + loose files only.

**The one rule: never rename or reuse an existing `fileName`.**

### 3.6 🔴 Known bugs / landmines (found, NOT fixed)

| # | Where | Problem |
|---|---|---|
| 1 | `viewmodel/GameViewModel.java:653-660` | Saved artwork path uses only the **last path segment** of `fileName`. `daily/rose` and `flowers/rose` collide and overwrite each other. **→ use globally unique, date-prefixed fileNames (`20260725_rose`).** |
| 2 | `NumberViewModel.java:349` | `Math.min(2, ...)` — **hard cap of 2 daily images**. Owner wants 3. Also the prefs schema is 2-wide (`fn1`/`fn2`, `:288-291`). |
| 3 | `BannerSlideAdapter.java:64` + `NumberFragment.java:281` | Banner page count hardcoded `2` in two places that must stay in sync. |
| 4 | `NumberFragment.java:326-332` | Only `ivPreview1`/`ivPreview2` exist — a 3rd daily image has nowhere to render. |
| 5 | `NumberViewModel.java:41` vs `ColoringListViewModel.java:35` | `"DAILY"` vs `"daily"` — **casing mismatch**. The URL is built literally as `gimg/c/DAILY`, so the backend must use **uppercase DAILY**. |
| 6 | `ColoringsAdapter.java:269` + `BaseRestAdapter.java:147-150` | `setHasStableIds(true)` but `getItemId()` returns **position** → stale thumbnails when the list is prepended. Cosmetic but will look like a bug once we add content at top. |
| 7 | `GameViewModel.java:376-382` | `onError` recurses into itself unconditionally — **unbounded retry loop**, no backoff. |
| 8 | `Utils/ImageInfo.java:75` | `getBoolean("free")` throws when the key is missing and the item is silently dropped. 5 of 10 entries in `assets/images.json` are lost this way. Use `optBoolean`. Applies to any JSON we emit — **a missing optional field silently deletes the image.** |
| 9 | `Manager.java:229` | `mNextKey` is `static` — pagination cursor is global, concurrent lists corrupt each other. |
| 10 | `AndroidManifest.xml:66-67` | AdMob app id is **Google's public TEST id**. Check before release. |

### 3.7 Useful facts

- **Cleartext HTTP is already permitted** (`AndroidManifest.xml:60` + `res/xml/network_security_config.xml`).
  Pointing the app at `http://10.0.2.2:8000` or a LAN IP needs **no manifest change**.
- **Firebase Remote Config is initialized but NEVER READ** (`Manager.java:143-150`) — a free,
  already-wired channel. Ideal place to put a switchable base URL.
- `util/DailyChallengeManager.java` (128 lines: streaks, countdown, `getTodayChallengeIndex`)
  is **fully written and never called**. Free to adopt.
- No Retrofit. Five parallel HTTP stacks: OkHttp (the real REST client, `Utils/Manager.java`),
  Volley, Ion, PRDownloader, HttpURLConnection. Plus Glide **and** Picasso **and** Ion caches.
- `MainActivity.loadCategories()` (`:1102-1110`) hardcodes 7 categories bound to JNI URLs, but
  it only writes counters — it does **not** feed the UI, and it is **called twice** (`:172`, `:187`),
  duplicating 7 network requests per launch.

### 3.8 ✅ Converter BUILT — Phase 1 item 1 (2026-07-21)

`platform/backend/services/number_tab_builder.py` turns a ColorForge colour-fill result into
the two PNGs the live Number tab consumes. **Proven correct against the app's own decoder**
(a faithful port of `processPixelsAndCreateColorAreas` + `resolvePixelColor` runs over the
output and recovers every anchor's colour — 0 mismatches on synthetic *and* a real 1254² job).

- **`{name}.png`** = `blackLineBitmap`: black region-outline on a **transparent** background
  (it is drawn *on top* of the fill layer — an opaque bg would erase the user's colours).
- **`{name}.colored.png`** = `colorBitmap`: flat palette colour per region (alpha 255), black
  walls (`RGB=0`), and **one partial-alpha pixel per region** at its inscribed-circle centre
  (`0<alpha<255`, alpha = text size). Outlines are **binary alpha** — ColorForge's own
  `border.png` stores anti-aliased lines in alpha, which would become thousands of phantom
  "numbers"; the converter derives crisp walls from the id-map instead.
- **`t/{name}.png`** = grid thumbnail (flat colours + lines over white).
- Regions too thin to host a legible number are **absorbed into a neighbour** (iterated to a
  fixed point) so there are **no un-fillable gaps** — every cell is numbered (`numbered==regions`).
- Output capped at `max_dim=1400` (the app allocates `2 * w*h` ints in RAM).
- Entry points: `export_from_bundle(bundle_dir, out, name)` (documented `color_bundle`
  artifact) and `export_from_job(job_dir, out, name)` (straight from a job folder via
  `reveal_game_builder._game_data_color`; **no torch needed** if `ai_lineart.png` already
  exists — no top-level torch import).

**Run / re-test** (works on the office PC's system Python — the repo `.venv` is broken here,
it points at a `C:\Users\pinal savaliya\...` interpreter; use a Python 3.12 with `opencv`):
```bash
cd platform/backend
python -c "from services import number_tab_builder as N; \
  print(N.export_from_job('data/items/0f69457779c5','OUT','20260721_krishna'))"
```
Real-job result (`0f69457779c5`, Krishna): **448 regions / 448 numbered / 100 colours / 1254²**.

**Tuning caveats (content quality, NOT converter bugs) — carry into the "test 2-3 images" step:**
- **100 colours is a lot** for a casual level. That is the engine's `COLOR_K=100` default
  (`reveal_game_builder.py`), not the converter. Lower it for easier levels.
- Palette digits are still **app-assigned by `HashSet` order** (§3.3 / open Q6.4). The
  converter only guarantees *distinct colour → distinct number*; it cannot pin the digits
  without an app change.
- Anchor text size (`alpha`) is clamped to `[8,200]`; the app re-scales it by zoom ×0.8 and
  clamps to `min/max_text_size` at draw time, so exact values can be tuned during image tests.

### 3.9 ✅ Number-tab API server BUILT — Phase 1 items 2, 3, 4 (2026-07-21)

When the boss publishes a colour item in the Admin, it is now **automatically delivered to the
live app** in the exact `ionicsolutions.in`-shaped API. Backend only — **the Android app is not
touched** (owner keeps that side ready). Verified end-to-end with a TestClient over the real
routes (publish → convert → store → serve → correct wire format, incl. the app's date-hiding).

**Publish flow** — `_publish()` in [app.py](platform/backend/app.py) now, after the existing
bundle export, also runs the converter and stores the app's three files:
`number/{category}/{file}.png` · `.colored.png` · `t/{file}.png`, and records `file_name` +
`publish_ts` on the image row (new columns in [db.py](platform/backend/db.py)).

**Endpoints** (public, unauthenticated — the app hits these on its base URL):
| Path | Returns |
|---|---|
| `GET /index.php/Number/category/` | `{"categories":[...]}` |
| `GET /index.php/Number/gimg/c/{category}` | `{"data":[{key,fileName,category,timeStamp,isNewToday}], "next":""}` |
| `GET /thumb/{category}/{file}.png` / `.colored.png` / `t/{file}.png` | the images |

- **fileName** = date-prefixed unique `YYYYMMDD_slug` → fixes the saved-progress collision (§3.6#1).
- **timeStamp** = `publish_ts` formatted `yyyy-MM-dd`. A **future** date → the app hides it until
  then (client-side, Manager.java:417-424). Set it with `publish_date` on approve/publish
  (`'YYYY-MM-DD'` or epoch). *(Admin UI still needs a date input — see below.)*
- **new-at-top**: `number_items` orders by publish date DESC; the app renders server order verbatim.
- **Merge/redirect** ([number_tab.py](platform/backend/number_tab.py)): config `number_merge`
  (default **on**) serves our items first, appends the upstream list, and **302-redirects**
  image requests we don't own to `number_upstream` (default the ionicsolutions base). Upstream is
  **best-effort** — if it's slow/down we fall back to our own items (never a hard failure).
  Toggle/point it via `PUT /api/config {"number_merge":false,"number_upstream":"http://..."}`.

**Run/test:** `python <scratch>/test_pipeline.py` (temp DB + temp store, no network needed).

**🔴 One thing to confirm with the owner before it works against his app:** the app's **base
URL**. Our routes sit at the server ROOT (`/index.php/...`, `/thumb/...`). If his ready app's
base URL still carries the old `/ColoringBook/` path prefix, the routes need that prefix too
(easy: a FastAPI router prefix). → **See Open Q6.5.**

**Still open (small):** the Admin **publish-date input** in `admin.html` (the API accepts it;
the UI field isn't added yet) — Phase 1 item 3's last mile.

---

## 4. THE PLAN (agreed 2026-07-20)

### Core constraint — do not disturb the paid content

The owner **buys the `ionicsolutions.in` content on a monthly subscription and has no access
to that server.** It is human-drawn, proper line art, different setup. **It must keep working
exactly as it does today.** We are **ADDING** a second source, not replacing.

### Architecture — server-side merge (proxy) + client fallback

```
App ──► ColorForge server ──┬──► own ColorForge content (served directly)
        (one BASE_URL)      └──► everything else → 302 redirect → ionicsolutions.in
                                 (redirect = no bandwidth cost to us)
```

**Why proxy and not client-side merge:** the owner wants ColorForge images to appear **inside
the SAME category** as bought content (`animals` = `animals`, NOT `ai_animals`). Merging two
independently-paginated sources inside the app is complex and would need a Play Store update
for every tweak. Server-side merge keeps app changes minimal and logic changeable any time.

**Merge rule per category:** serve **our items first** (they are new and few), then fall
through to the upstream list with its own cursor. This makes "new at top" automatic.

**🔴 Risk:** our server down ⇒ paid content also down.
**Mitigation (required):** in the app, on ColorForge timeout/error, **fall back to
`ionicsolutions.in` directly**. Our new content disappears; the paid content keeps working.

**Base URL:** currently in native C++ ⇒ NDK rebuild. Since an app update is needed anyway
(for Diamond), take that chance to read the base URL from **Firebase Remote Config**
(default = the existing native URL). After that, the URL can change with no app update.

### Phase 1 — ColorForge colour-fill into the Number tab

Backend:
1. ✅ **DONE (2026-07-21)** **Converter**: ColorForge bundle/job → `{name}.png` +
   `{name}.colored.png` (alpha-encoded label anchors). See §3.8. Verified against the app decoder.
2. ✅ **DONE (2026-07-21)** Serve the Number-tab API shape (3.2), merging + redirecting. See §3.9.
3. 🟡 **MOSTLY DONE** Admin: publish-date → `timeStamp`. The API accepts `publish_date`; the
   `admin.html` date input is the only remaining bit. See §3.9.
4. ✅ **DONE (2026-07-21)** Date-prefixed, globally unique `fileName` (bug 3.6#1). See §3.9.

App: **owner says the Android side is already prepared** (2026-07-21) — items 5 & 6 are
**owner-handled**, we do NOT touch the app.
5. ~~Base URL via Remote Config~~ → owner has pointed his ready app at his server (confirm base
   URL path prefix, Open Q6.5).
6. ~~Daily count 2 → 3~~ → owner-handled on the app side.

Not needed: new-at-top and date-hiding already work (3.4).

### Phase 2 — Diamond (a whole new, separate section)

ColorForge **already generates diamond content** (`platform/backend/diamond_builder.py`).
Its payload is app-friendly and much simpler than the colour format:

```json
{"cols":120, "rows":120, "colours":32,
 "palette":["#rrggbb", ...],
 "grid":[[1,2,3,...], ...]}      // 1-based palette number per cell
```

App work required (this is the **largest new piece**): new bottom-nav tab, gallery
fragment + adapter, and a **new diamond game screen** (bead grid, tap/drag to place, zoom/pan).
Simpler logic than colour-fill — no flood fill, no region detection — Canvas is enough,
OpenGL not required.

### Recommended order

1. Phase 1 first (low risk to the live app) → test with 2-3 real images → then Phase 2.

---

## 5. Decisions log

| Date | Decision |
|---|---|
| 2026-07-20 | `flutter_app/` deleted permanently at owner's request (108 MB, 29 bundles, untracked). Owner was warned in detail and confirmed. Not the real product. |
| 2026-07-20 | Real product is **Colour Mate** (native Android), not Flutter. |
| 2026-07-20 | Paid `ionicsolutions.in` content **stays untouched**. We ADD a second source. |
| 2026-07-20 | ColorForge content goes into the **SAME** category names (`animals`, not `ai_animals`). |
| 2026-07-20 | Therefore: **server-side merge (proxy) + client fallback**, replacing the earlier client-side-merge idea. |
| 2026-07-20 | Diamond becomes a **separate new section**, Phase 2. |
| 2026-07-21 | Phase 1 item 1 **converter built + verified** (`services/number_tab_builder.py`, §3.8). Consumes a job folder or an exported `color_bundle`; correctness proven by replaying the app's own `GameView` decoder over the output. |
| 2026-07-21 | Phase 1 items 2 & 4 **built + verified**: Number-tab API server + merge/redirect (`number_tab.py`, new `app.py` routes, `db.py` columns), §3.9. Publish now auto-delivers to the app. Item 3 (publish-date) API done, admin UI field pending. |
| 2026-07-21 | **Owner: the Android app side is already prepared** — app-side Phase 1 (items 5, 6) is owner-handled. We stay backend-only. (Supersedes the §7 note that the app was only read; still true that *we* haven't changed it.) |
| 2026-07-21 | **PIVOT: no server-side merge.** ionicsolutions stays separate/untouched; ColorForge becomes its own **"New 2026"** app tab (+ **Diamond** tab), with ColorForge's own categories. Merge/redirect (§3.9) to be turned OFF but kept in code. See **§8**. |
| 2026-07-21 | **App development AUTHORIZED.** Owner wants: performance fix (**#1 problem**), New 2026 tab, Diamond tab, advanced design, proper ads, small app size, code cleanup (no output impact), colour-fill splash animation, app-open title animation, first-run welcome/intro. Full roadmap + app structure in **§8**. Constraint: don't break the 4 existing tabs. |
| 2026-07-21 | Fixed an **admin bug**: the boss couldn't publish his own `review` images (no button). Added boss "Approve & publish" on review/rejected in `admin.html`. |
| 2026-07-21 | **First real app code changes.** Roadmap #1 (performance): **H1** blur stop-gap, **H2** off-thread level init, then **M5** (Picasso `.fit()`) + **M7** (RecyclerView config), then **M4/M6/M8/M9** — the whole HIGH+MEDIUM list, each built and installed on the owner's device. Owner's call on H1: **keep the frosted-glass look**, only stop the per-frame recalculation. See §8.6. |
| 2026-08-21 | **Deployed to a Hostinger VPS** — `https://srv1919471.hstgr.cloud/`, generation runs on the server (torch installed), storage still local, admin password rotated. Verified from a real phone over the internet. See §8.5ar + `DEPLOY.md`. |
| 2026-07-21 | **Owner restated the goal:** finish the Android app *completely* — full design, all screens, and the ColorForge integration. Performance was the gate; with #1 done the work moves to the **"New 2026" tab** (#2), then design (#4) and the remaining screens. |

---

## 6. Open questions

1. **Daily images:** should ColorForge daily items be free, or reward-ad / premium gated?
   `Level` has **no lock field at all** — gating would need a new API field + app change.
2. Should ColorForge images carry a visible marker (an "AI" or "New" badge) inside the shared
   category, or blend in completely? (`item_coloring.xml` currently has **no badge widget**,
   and `Level.isNewToday` is computed but **never rendered** — `ColoringsAdapter.java:195-199`.)
3. Where will the ColorForge server be hosted for production (VPS / AWS)? Storage is already
   abstracted — `STORAGE=s3` flips it (`platform/backend/storage.py`).
4. Is the palette-numbering instability (3.3) acceptable, or should the converter pin numbers?
5. **App base URL (blocks go-live):** the owner's ready app points at his server — at what path?
   Our Number-tab routes are at the server ROOT. If the base URL keeps the old `/ColoringBook/`
   prefix, we add a FastAPI router prefix to match. Also: which host/port will the app hit
   (needed to actually connect it). (Ref §3.9.)
6. Content is too **dense** (§3.8: 448 regions / ~100 colours on the test image). Tune the engine
   (`COLOR_K`, `COLOR_TARGET`, `COLOR_LINE_RMIN`) for calmer casual levels before going live?

---

## 7. Working agreements

- **App changes are now AUTHORIZED (2026-07-21)** — the owner asked for new tabs (New 2026, Diamond)
  and app-wide polish (§8.3). Still: **never break the 4 existing tabs**, keep the app working as it
  does now, and refactor only where output/behaviour is unaffected. Every app edit is a live
  Play-Store app + needs a rebuild — be careful and test.
- Android app changes so far: `admin.html` (ColorForge, not the app, §8.4), and — since
  **2026-07-21** — the first real **app** edits: the H1/H2 performance fixes in §8.6.
- Android SDK on the home PC: `C:\Users\pinal savaliya\AppData\Local\Android\Sdk`
  (`local.properties`). `adb` is not on PATH. **This path will differ on the office PC —
  update `local.properties` there.**
- **Build + install + run — verified working 2026-07-21 (home PC):**
  ```bash
  # No JAVA_HOME needed — gradle.properties pins org.gradle.java.home (§8.5ak)
  cd "Colour Mate/Colour Mate/Colour Mate" && ./gradlew.bat assembleDebug   # ~3 min cold (NDK/cmake)
  SDK/platform-tools/adb.exe -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
  ```
  - Debug APK ≈ **79 MB**. `debug` has **no `applicationIdSuffix`** — same appId as the Play Store
    build, so it replaces the installed app (fine: the device copy is sideloaded, same debug key).
  - The owner's phone (**CPH2683**) is on **wireless ADB** and shows up **twice** (IP + mDNS entry)
    → always pass `-s <serial>`, or gradle `installDebug` tries both.
  - Driving the app over `adb shell input tap` is unreliable: **interstitial ads** open on tab
    switches and can swallow the tap. Get real coordinates with
    `adb shell uiautomator dump /sdcard/ui.xml` (in Git Bash prefix `MSYS_NO_PATHCONV=1`, else the
    `/sdcard` path gets mangled). Bottom nav bounds: number `[0,1450][180,1580]`, color
    `[180..360]`, pixel `[360..540]`, saved `[540..720]`.
  - The ads that appear are labelled **"Test Ad"** — confirms the TEST AdMob id landmine (§3.6#10)
    is still live in the debug build.

---

## 8. 🔄 PIVOT + APP ROADMAP (2026-07-21) — READ THIS TO CONTINUE

> This section is the **handoff**. On 2026-07-21 the owner changed the architecture and opened up
> **app development**. If you are picking this up on another PC, start here: it says what changed,
> the app's real structure, the full vision, what's done, and what's next.

### 8.1 The pivot — ColorForge is its OWN section, NOT merged into ionicsolutions

The earlier idea (§4) was a **server-side merge**: inject ColorForge into the SAME ionicsolutions
categories. **The owner dropped that on 2026-07-21.** New model — the two are **separate**:

- **ionicsolutions = the "By Number" tab → stays EXACTLY as it is.** Untouched. Its own
  categories/names. We do NOT mix into it.
- **ColorForge colour content → a NEW "New 2026" section** — its own bottom-bar tab (owner wants it
  in the **middle** of the bar), with ColorForge's **own categories** as sub-categories inside it.
- **Diamond → another NEW section/tab** (Phase 2).

→ **Server change:** turn the merge OFF (`PUT /api/config {"number_merge": false}`) so our server
  serves ColorForge **standalone** (our categories + items only, no upstream). The merge/redirect
  code (§3.9) stays in place but disabled — harmless, in case it's ever wanted. The casing-lowercase
  normalisation (§3.9) is still fine to keep.
→ The new **"New 2026"** app tab points at OUR server and reuses the same Number-tab API shape
  (`/index.php/Number/category/`, `/gimg/c/{cat}`, `/thumb/...`) — so **the backend we already built
  (§3.8, §3.9) is still exactly what feeds it.** Only the app side is new.

### 8.2 The app's real structure (read from code 2026-07-21)

Bottom bar = a **custom floating blur LinearLayout** (`res/layout/activity_main.xml`) driven by
`MainActivity.setupBottomNavigation()` — NOT a `BottomNavigationView`/menu XML. It **show/hides**
fragments (never replace). **4 equal-width tabs today:**

| # (NAV_) | Label | item id | Fragment | Content source |
|---|---|---|---|---|
| 0 NUMBER | **By Number** | `nav_item_number` | `NumberFragment` | ionicsolutions (paid) |
| 1 COLOR | **By Images** | `nav_item_color` | `ColorFragment` | recolor.com |
| 2 PIXEL | **By Pixel** | `nav_item_pixel` | `PixelFragment` | fungames |
| 3 SAVED | **Saved** | `nav_item_saved` | `SavedFragment` | local device |

**There is NO "New 2026" and NO "Diamond" tab** — they must be built. To add a tab: new `<LinearLayout>`
item (indicator + `ImageView` icon + `TextView` label) in `activity_main.xml`, new icon drawables,
extend the four arrays (`navItems`/`navIndicators`/`navLabels`/`navIcons`) + a `NAV_*` constant +
a `case` in `selectNavItem()`, and a new `Fragment`.

**"New 2026" tab is cheap-ish:** it is functionally the **By Number tab pointed at OUR server** —
clone `NumberFragment`'s REST flow (category list → gimg → thumb/line/colored) and swap the base URL.
The image format is already the converter's output (§3.3/§3.8). **"Diamond" is the big one** (a whole
new game screen — see diamond payload in §4 Phase 2).

### 8.3 App development roadmap — owner's vision (priority order, 2026-07-21)

**⚠️ HARD CONSTRAINT: the app must keep working exactly as it does now. Do NOT break the existing
tabs. Clean up / shorten code ONLY where it does not change behaviour or output.** The owner is
willing to refactor for quality but not at the cost of the working app.

1. **PERFORMANCE — the app does not run smoothly. THIS IS THE #1 PROBLEM. Fix this first.**
   (Profile jank, oversized bitmaps, the double `loadCategories()` (§3.7), 5 parallel HTTP stacks,
   Glide+Picasso+Ion caches, etc. Measure before changing.)
   **✅ HIGH + MEDIUM ALL DONE 2026-07-21** (H1–H3, M4–M9 — §8.6). Only LOW items and the big
   refactors remain. **→ Roadmap moves to item 2, the "New 2026" tab.**
2. **"New 2026" tab** — ColorForge colour content in its own section (clone By Number → our server).
3. **Diamond tab** — **✅ tab (§8.5c) + playable game screen DONE 2026-07-22 (§8.5d).** Remaining:
   the gallery, which is blocked on the same server URL as New 2026.
4. **Design at an advanced level, maintained consistently** across the app ("design wall").
5. **Ads placed properly** — **🔄 the TEST AdMob app-id landmine is fixed 2026-07-22 (§8.5e);
   the owner still has to supply the real id.** Ad *placement* (where/how often) is untouched.
6. **App size** — keep the APK small; be deliberate about new assets/libs.
   **🔄 2026-07-22:** the test samples were moved to **`app/src/debug/assets/`**, so they exist in
   debug builds only and **cannot reach a release APK**; the sample buttons are wrapped in
   `if (BuildConfig.DEBUG)` and hidden otherwise (this needed `buildFeatures { buildConfig true }`
   — AGP 8 stopped generating `BuildConfig` by default). The dead flat-fill sample
   (`ColorForgeSample.java` + its two PNGs, ~0.7 MB) was deleted once New 2026 moved to the reveal
   engine. Debug APK ~82 MB; the real weight is elsewhere (NDK libs, fonts, MyBook.json) and is
   still to be looked at.
7. **Code cleanup** — short & sweet, but only where output/behaviour is unaffected.
8. **Colour-fill UX delight** — **✅ DONE 2026-07-22, and the finding matters: the splash was
   already built, but shipped OFF.** `GameSaver.isParticlesEnabled()` defaulted to **false**, so
   `GameView.spawnParticles()` (pooled star/butterfly particles, already wired into
   `handleColorAreaTap`) never ran for anyone. Default is now **true** (Settings can still turn it
   off), and the burst went from `PARTICLE_COUNT` 8 → **14** (`MAX_PARTICLES` 50 → 70) because 8
   specks read as a fizzle. Spark and ripple animations were already firing and are untouched.
   *Built + installed; the owner should confirm it feels right when filling a region.*
9. **App-open animation** — **✅ DONE 2026-07-22.** `MainActivity.playIntroAnimation()`: the title
   springs in (alpha + scale 0.72→1 + translationY, overshoot 1.9, 620 ms after a 90 ms delay) and
   the three top-bar icons follow staggered 70 ms apart. Guarded by `savedInstanceState == null`
   so a rotation does not replay it. *Built and installed; not seen running — a static screenshot
   cannot show an animation, and the test phone kept returning black captures. Owner to judge.*
10. **First-time-user experience** — **✅ DONE 2026-07-22.** `activity/WelcomeActivity.java` +
    `res/layout/activity_welcome.xml`: title, one line on what the game is, a Start button, over a
    softened splash frame, with the same overshoot spring as the app-open intro so the two feel
    like one product. Launched from `MainActivity.onCreate` when `WelcomeActivity.shouldShow()`.
    **The "seen" flag is written on arrival, not on the Start button** — however the player leaves
    (back, home, task-switch), it must not block them again.
    *Confirmed it ran by reading `shared_prefs/welcome.xml` (`seen=true`) off the device; the
    screen itself was never captured — the phone kept returning black screenshots. Owner should
    look at it once: clear it with
    `adb shell run-as <pkg> rm shared_prefs/welcome.xml` to see it again.*

### 8.4 Current state — what's DONE (backend) and how it was tested

**Backend (ColorForge) — Phase 1 delivered & verified (see §3.8, §3.9):**
- Converter `services/number_tab_builder.py` — job → `{name}.png` + `{name}.colored.png` + thumb.
  Proven against the app's own decoder.
- `number_tab.py` + `app.py` routes — publish auto-delivers to the Number-tab API. `db.py` has
  `file_name` + `publish_ts` columns.
- **Admin bug fixed** in `admin.html`: the **owner (boss) could not publish his own `review`
  images** — the drawer/card showed a button only for employees (Submit) or for `submitted`
  (Approve). Added a boss "Approve & publish" on `review`/`rejected` items (`cardActions` +
  `drawerActions`). *(admin.html is re-read per request — no server restart to pick up edits.)*

**Verified LIVE on 2026-07-21 (real end-to-end, not just unit tests):**
- Ran the server: `python -m uvicorn app:app --host 0.0.0.0 --port 8000` (system Python 3.12 at
  `C:\Users\pc07\AppData\Local\Programs\Python\Python312` — the repo `.venv` is broken here, it
  points at a `C:\Users\pinal savaliya\...` interpreter).
- Exposed it to the owner's real phone with a **cloudflared quick tunnel** (LAN + Windows Firewall
  were in the way; the firewall rule needs admin, and binary download was blocked, so the owner
  downloaded `cloudflared-windows-amd64.exe` to `C:\Users\pc07\Downloads` and we tunnelled
  `http://localhost:8000`). The tunnel URL is temporary and changes each run.
- Owner published one image via the admin: **`20260721_chatgpt_image_...` (Krishna), category
  Animals** → it served correctly through the tunnel (category/gimg/thumb/line/colored all 200).

**Key realisation (why the app output looked "different" from the admin's playable preview):**
- The admin's **"Open playable preview"** is **ColorForge's own browser game** (`play_color.html`):
  it does **photo-reveal** (fills morph to the ORIGINAL photo) with the clean AI line-art border.
- The **live Colour Mate app is a FLAT paint-by-number engine** (`GameView` fills SOLID palette
  colours; it never receives the photo). So the app can render ColorForge content **only as flat
  colour** — exactly like the existing paid content. Photo-reveal in the app would require rewriting
  the app's game engine (big; a possible far-future item, NOT planned now).
- **Density:** the test image came out **very dense** (the admin itself flagged "587 regions — very
  dense", ~100 colours). That's the **engine defaults** (`reveal_game_builder.py`: `COLOR_K=100`,
  `COLOR_TARGET=400`, `COLOR_LINE_RMIN=9`), NOT the converter. Owner has NOT yet decided to tune it;
  the admin's **Regenerate → Light/Medium/Strong** (simplify/flatten) already reduces it per-image
  with no code change. Revisit for calmer casual levels. (Open Q6.6.)

### 8.5 To continue on another PC

1. `platform/` and `Colour Mate/` are **untracked** — copy the folders physically (§1).
2. Backend: use a **Python 3.12 with `opencv-python-headless`** (+ fastapi/uvicorn); the committed
   `.venv` won't work off this machine. Run from `platform/backend`.
3. Android: open `Colour Mate/Colour Mate/Colour Mate/` in Android Studio; fix `local.properties`
   SDK path for that PC (§7); `gradlew assembleDebug`.
4. Do the roadmap in §8.3 order — **performance first**, and **never break the 4 existing tabs.**

### 8.5b "New 2026" tab — integration plumbing DONE (2026-07-21)

The **network/data half** of roadmap item 2 is built and compiles. The **UI half** (5th bottom-bar
tab + its fragment) is not written yet.

**Where the URL comes from — `Utils/ColorForgeApi.java` (new).** Resolution order: local override
(`setLocalOverride`, for a dev tunnel) → **Firebase Remote Config key `colorforge_base_url`** →
baked-in default (currently `""`). Remote Config is already in the app (`Manager`), so **the owner
can move the server later without shipping an app update**. `isConfigured()` is false while unset,
so the tab must show an empty state rather than call a bogus host. Builders in that class mirror
the backend exactly (`app.py:583-602`): `{base}index.php/Number/category/`,
`{base}index.php/Number/gimg/c/{cat}`, `{base}thumb/{cat}/t/{file}.png`,
`{base}thumb/{cat}/{file}.png`, `{base}thumb/{cat}/{file}.colored.png`.

**Why it could not just reuse `Manager.BASE_URL`:** that value comes from a **native method**
(`GetMainUrl()` in the NDK lib) and is the *paid* ionicsolutions host — untouchable. So:

| Change | File | Effect |
|---|---|---|
| `RestDataSource` gained an optional `baseUrlOverride` (null = old behaviour) | `Utils/Manager.java` | By Number path is byte-identical |
| `executeApiRequestWithCall(baseUrl, …)` overload; old signature delegates | `Utils/Manager.java` | same |
| `getColorForgeImagesByCategory(ctx, cat)` — returns **null** when unconfigured | `Utils/Manager.java` | the tab's data source |
| `Level.colorForge` flag **+ parcelled** | `model/model/Level.java` | survives the Intent into GameActivity |
| `parseAndAddResults` stamps the flag from `baseUrlOverride` | `Utils/Manager.java` | can't be forgotten by a caller |
| `GameViewModel.imagesRoot()` picks host by that flag | `viewmodel/GameViewModel.java` | ColorForge pictures download from our server |

⚠️ **The `Level` parcel + the stamp are the load-bearing bits.** `GameViewModel` used to hardcode
`UrlConstants.IMAGES_URL` (= `Manager.BASE_URL + "thumb/"`); a ColorForge level opened without the
flag would 404 on the ionicsolutions host.

**The UI half is now built too (2026-07-21) — `BUILD SUCCESSFUL`, not yet run on a device.**

| Piece | File |
|---|---|
| 5th bottom-bar item, **inserted after "By Number"** (index 1) | `res/layout/activity_main.xml` |
| `NAV_NEW = 1`; COLOR/PIXEL/SAVED renumbered to 2/3/4; arrays, `selectNavItem()` case, icon case, `setupFragments()` find+hide | `activity/MainActivity.java` |
| Sparkle icon, select (filled) + unselect (outline), matching the existing pair convention | `res/drawable/ic_new_select.xml`, `ic_new_unselect.xml` |
| The screen: category TabLayout + grid + loading/message states | `fragments/New2026Fragment.java`, `res/layout/fragment_new2026.xml` |
| Thumbnails switch host on `level.isColorForge()` | `adapter/ColoringsAdapter.java` |

The bar is a weighted `LinearLayout`, so five items simply become five equal columns — no width
maths needed. `New2026Fragment` is **not** a copy of `NumberFragment` (that one carries the daily
banner, search and ad plumbing of the paid tab); it is the plain categories→grid flow reusing the
already-tuned `ColoringsAdapter` and the shared `RestDataSource`, and it shows "Coming soon"
instead of calling a host that isn't configured yet.

**Playable sample, no server needed (2026-07-22).** Like the Diamond tab, New 2026 now has a
**"Try a sample"** button — `Utils/ColorForgeSample.java` copies a bundled picture into
`filesDir/color_images/` under the names `GameViewModel.requestLevelData()` expects, and since that
method only downloads when the files are **missing**, the sample then runs through the ordinary
game path with **no engine changes at all**. The picture is real ColorForge output, exported by the
backend's own `number_tab_builder.export_from_bundle()` from the already-published bundle
`data/cdn/images/6185e29ae125` (402 regions). It respects the low-res setting (`.small` names).
*(Cost: ~0.7 MB of assets, and the same for the diamond sample — worth revisiting under roadmap
item 6 "app size" before release. Debug APK is now ~86 MB.)*

⚠️ **Gallery still not verified on a device.** Two things are needed:
1. **A server URL the phone can reach** — a cloudflared quick tunnel for a session, or a permanent
   host for real. Set it without rebuilding via Firebase Remote Config `colorforge_base_url`, or
   in code for a dev build via `ColorForgeApi.setLocalOverride(ctx, url)`.
2. Wireless ADB re-paired (it drops when the phone sleeps).

### 8.5c Bottom bar redesign + default tab (2026-07-22)

Owner: *"floating bar nu design change kri devi — akhi app ni design **modern and simple** krva ni
che"*, and *"by default atyre By Number open thay che, ani jagya e **New 2026** thavu joye"*.

**Default tab is now New 2026.** Two places had to agree or the app opens on a blank container:
`setupBottomNavigation()` → `selectNavItem(NAV_NEW)`, **and** `setupFragments()` now adds
`New2026Fragment` as the initially-added fragment (it used to add `NumberFragment`).

**Design outcome — the owner chose the ORIGINAL look, keep it.** A flatter Material-3 variant was
tried first (light pill behind the icon only, teal-tinted icon+label, ripple). The owner replied
with a screenshot of the *existing* bar and *"avi kri de"* — so it was reverted to:
**selected = teal `game_button_default` block behind icon+label, white icon + white label;
unselected = `#171717` outline icon + dark label.** Don't "modernise" this again without asking.
(`@color/nav_selected`/`nav_unselected`/`nav_pill` and `@drawable/bg_nav_indicator` are left in the
tree, unused, in case he changes his mind.)

**The pill hugs its content — it is no longer a fixed square.** First attempt kept the old
`main_nav_indicator_size` (65dp, then 56dp) as a `View` sitting *behind* icon+label; with 6 tabs
each column is only 720px/6 = **60dp** on the test phone (720×1604, density 320), so a fixed square
was both wider than its column and a different shape from the owner's reference. Now
`nav_indicator_*` **is** the `LinearLayout` holding the icon and label, `wrap_content` with
`main_nav_pill_padding_h/v`, and `updateNavVisuals()` sets/clears
`R.drawable.game_button_default` as its background. `main_nav_indicator_size` is dead.

**Sizing (learned by getting it wrong):** with 6 tabs at padding 10dp + label 10sp the labels
truncated to "By", "New", "Diamon". Once **Saved moved to the top bar** (below) the bar was back to
5 tabs = 72dp columns, so padding_h 6dp + label 10sp fits comfortably. Any tab added or removed
must be re-checked on a 720px screen — this is the setting that breaks first.

**Saved moved out of the bottom bar to the top bar (owner, 2026-07-22):** `iv_saved_top` sits in
`top_bar` immediately left of `iv_setting`, using **`ic_saved_select` (filled white)** — the top
bar sits on the colourful background, so the dark `_unselect` outline was nearly invisible there. `nav_item_saved` and its four array entries are gone,
but **`NAV_SAVED = 5` and its `selectNavItem` case stay** — it is now an index with no bar item, so
`updateNavVisuals()` iterates the five real tabs, matches none, and correctly leaves every pill
off while Saved is open. `runPendingAction(OPEN_MY_WORKS)` keeps working unchanged.

Bottom bar is now: `By Number · New 2026 · Diamond · By Images · By Pixel`.
**2026-08-20:** a sixth item — a **Daily** tab (`nav_item_calendar`, opening
`DailyCalendarActivity`) — had appeared between Diamond and By Images. The owner asked for it to be
**hidden**, so it now is: `android:visibility="gone"` in the layout *and* a
`dailyTabEnabled = false` guard around its click/tint wiring in `MainActivity`. Nothing was deleted;
flip both back to restore it. Note it was never part of `navItems[]` — that array always held the
five tabs in NAV_ order, so the NAV_ indices were correct then and are correct now. Do not "fix" the
array to match the bar.
**Checked while doing it:** Settings has **no** Daily entry (General / Premium / MORE only, verified
on the device), and `DailyCalendarActivity` is referenced from just three files — itself,
MainActivity and the manifest. The bottom bar was its only way in.

**Tap animation** (owner asked for it): the pill pops in (`scale 0.75→1`, `alpha 0→1`,
`OvershootInterpolator(2.5)`) **and** the icon bounces (`scale 0.5→1`, overshoot 3.5). Re-tapping
the tab you are already on plays `playTapBounce()` — no fragment switch, but the tap still lands.

**Diamond tab added at the same time (roadmap item 3), placed after New 2026:**
`By Number · New 2026 · Diamond · By Images · By Pixel · Saved`. `NAV_DIAMOND = 2`;
COLOR/PIXEL/SAVED shifted to 3/4/5. `fragments/DiamondFragment.java` +
`res/layout/fragment_diamond.xml` are a **placeholder "Coming soon" screen** — the tab, icon
(`ic_diamond_select`/`_unselect`) and navigation are real, but the bead-grid game screen itself
(§4 Phase 2) is not written.

The nav block in `activity_main.xml` (all 6 items) is generated from one template, so the items are
identical apart from id/label/icon.

**✅ Verified on the device 2026-07-22** (screenshots): white filled Saved icon in the top bar next
to the gear; five bottom tabs with full labels; the pill hugs icon+label (teal, white content);
opening Saved leaves every bottom pill off; Diamond shows its placeholder; no crash. The default
tab was separately proven with `adb shell dumpsys activity top` →
`Added Fragments: #0: New2026Fragment … tag=new2026`.

*(Device tips — this phone fought every check: it sleeps in ~30 s and **ignores `KEYCODE_WAKEUP`
alone**; send `KEYCODE_WAKEUP` **+** `KEYCODE_MENU`, then swipe up. `screencap` sometimes returns a
black or 62-byte image over wireless ADB — just retry. **Don't trust a screenshot taken more than a
few seconds after launch**: two "default tab is wrong" reports turned out to be the owner tapping
the phone during the wait. With his OK the screen timeout was temporarily raised to 600000 and
**restored to its original 30000** afterwards.)*

### 8.5d Diamond game screen — BUILT AND PLAYABLE (2026-07-22)

Roadmap item 3's hard part. **Owner's brief: "same as the web" + "the canvas design and tools from
my old Number Colour screen".** Both were treated as the spec:

| Piece | File |
|---|---|
| Payload model (`{cols,rows,palette,grid}` → flat `byte[]` + progress) | `model/diamond/DiamondLevel.java` |
| The board: paper, sockets, glossy beads, pan/zoom, paint-on-drag | `model/diamond/DiamondView.java` |
| Screen chrome + async load + progress save | `activity/DiamondActivity.java`, `res/layout/activity_diamond.xml` |
| Web-style colour cards | `adapter/DiamondPaletteAdapter.java`, `res/layout/item_diamond_swatch.xml`, `drawable/bg_diamond_swatch*.xml` |
| Bundled test picture | `assets/diamond_sample.json` |

**The BOARD copies the web build** (`diamond_builder._PLAY_HTML`) so a picture looks the same in
the browser and the app: paper `#EFE9DD`, empty cells are a faint wash of their own colour + a
socket ring + the number (all stronger for the colour in hand), beads are a radial shade
(+62 → colour → −42) with two speculars. **Beads are pre-rendered once per colour into a bitmap**
and blitted — building a `RadialGradient` per cell per frame would allocate thousands of objects a
second.

**The CHROME copies the By Number game screen**, which the owner asked for explicitly on
2026-07-22 (he sent a screenshot of `activity_game.xml` and said "this screen, in New 2026 and in
Diamond"): the same `bg_hint_btn` rounded containers, back top-left, a counter chip top-right
(progress % where the game shows hints left), a left tool rail, a round button bottom-right, and
the palette in a rounded strip along the bottom. The palette **reuses the game's own
`color_item.xml`** — circular swatch, colour number, selection ring, badge with beads left — so
the two games share one visual language rather than looking like two apps.
*(An earlier version used web-style rectangular cards; that was replaced. `bg_diamond_swatch*.xml`
and `item_diamond_swatch.xml` are now unused.)*

**New 2026 needed nothing for this** — its pictures already open in `GameActivity`, i.e. the very
screen in that screenshot; only the host the images download from was changed (§8.5b).

**Rules of play:** one finger paints (drag lays a run), two fingers pan/zoom, and a bead only lands
where the picture wants that colour — a wrong tap is ignored, which is what makes the genre
relaxing. Finishing a colour auto-advances to the next unfinished one. Progress is saved to
SharedPreferences on `onPause` and restored on open (`restoreProgress` ignores a save whose cells
disagree with the grid, so a stale save can't corrupt a different picture).

**Testing without a server:** `assets/diamond_sample.json` was generated by running the backend's
own `diamond_builder` over `cat.png` (80×80, 24 colours, 18 KB) — so the Diamond tab offers
**"Try a sample"** and the game is playable today. `DiamondActivity.fromUrl()` already exists for
when the gallery lands.

**✅ Verified on device:** the sample opens, the cat reads through the sockets, beads place on
tap/drag, the palette shows remaining counts, progress %, no crash. `DiamondActivity` is
`exported="false"`, so it cannot be launched with `adb am start` — drive it through the tab.

### 8.5e Ads — the TEST AdMob id landmine, defused (2026-07-22)

**Confirmed on the device:** every ad in this build renders labelled **"Test Ad"**. The cause was
`AndroidManifest.xml`, which hardcoded
`com.google.android.gms.ads.APPLICATION_ID = ca-app-pub-3940256099942544~3347511713` —
**Google's public sample app id**. Ads served under it are always test ads and **earn nothing**.

**What changed:** the manifest now uses `${admobAppId}`, filled by `app/build.gradle`:

- **debug** → the test id (correct for development)
- **release** → the `ADMOB_APP_ID` gradle property, **and a release build now FAILS** if that is
  missing or still the test id. Verified both ways: `assembleDebug` succeeds,
  `processReleaseMainManifest` stops with the explanation.

**Still needed from the owner (cannot be guessed):** the real id from
**AdMob → App settings → App ID**, put in `gradle.properties` (or `-PADMOB_APP_ID=…`) as
`ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`. Keep it out of git.

**⚠️ Open question worth checking:** the **live Play Store build (versionCode 13)** was made from
this source, so it may carry the same test app id — if so the app has been serving test ads and
earning nothing. Check the AdMob dashboard for impressions; if they are zero, that is the answer.

**Ad *unit* ids are NOT affected** — they are read at runtime from preferences
(`AppPreference.getString(context, Keys.KEY_ADX_ID_*, "")`), not compiled in, so they come from
whatever remote config feeds them.

### 8.5f Photo-reveal engine for New 2026 — BUILT (2026-07-22)

**Owner's call, reversing §8.4's "not planned now":** New 2026 must play like the admin's review
preview — *"admin na review ma che ej same system ma joye"*. So the app now has the reveal game:
tap a numbered region, it paints its flat colour and **morphs (~0.3 s) into the original
photograph's pixels**; a finished picture is the real photo. That is what
`reveal_game_builder.py` produces and what `play_color.html` plays in the admin.

**⚠️ This only works for ColorForge content, and that is not a limitation to "fix":** the reveal
source is `original.png`, the real photo. ionicsolutions' paid pictures have no such photo, so
**By Number / By Images / By Pixel keep the flat engine**. `GameView`/`GameActivity` were therefore
left completely untouched and a **parallel** screen was added instead.

| Piece | File |
|---|---|
| Level: id map, photo, display buffer, regions, palette, morph blend | `model/reveal/RevealLevel.java` |
| Board: draw + pan/zoom + tap-to-reveal + 300 ms morph | `model/reveal/RevealView.java` |
| Screen (same chrome as the game/diamond screens) | `activity/RevealActivity.java`, `res/layout/activity_reveal.xml` |
| Palette (reuses `color_item.xml` again) | `adapter/RevealPaletteAdapter.java` |
| Bundled sample | `assets/reveal/` |

**Data it needs** — all of it already in a ColorForge bundle, served by `/cdn/{key}`:
`regions.json` (`{w,h,regions:[{id,number,cx,cy,r,bbox}],palette:[{number,color}]}`),
`id_map.png` (**id = R + G*256**), `original.png`, `border.png`. `RevealActivity.fromUrl()` takes a
bundle URL; `fromAssets()` takes the bundled sample. Loading runs off the UI thread.

**Sample generation gotcha:** the bundle is ~3.5 MB, too heavy for the APK, so it was rescaled to
700 px (~520 KB). **`id_map.png` MUST be resized with `INTER_NEAREST`** — any interpolation invents
region ids that do not exist and silently corrupts the picture. `original` was re-encoded as JPEG
(the reveal source needs no alpha); `RevealLevel` tries `original.jpg` then `original.png`.

**✅ Verified on device:** the sample opens, numbers and palette render, tapping a region of the
selected number reveals it (0% → 2%), and finishing a colour auto-advances the palette to the next
unfinished one. No crash.

**Progress saving added (2026-07-22):** `RevealLevel.encodeProgress()` stores the revealed region
ids as a comma list; `restoreProgress()` re-reveals them painted straight to the photo (no morph)
and **ignores ids the picture does not contain**, so a save from another picture cannot corrupt it.
`RevealActivity` writes on `onPause`, keyed by the asset folder / bundle URL.
*Partly verified: the prefs file is written on pause with the right key, but a round trip with
actually-revealed regions was not confirmed — blind `adb` taps kept landing on regions of other
numbers. Worth one manual check: reveal a few, leave the screen, come back.*

**Photo-crop palette swatches added (2026-07-22).** The web build ships these as
`swatches/<n>.png`; `RevealLevel.swatch(number, size)` instead **cuts them out of `original` in
the app** — a square window on the centre of that number's **biggest** region (biggest = most
representative, least likely to be mostly line art), cached per number, drawn circular via
`RoundedBitmapDrawableFactory`. So no extra files are downloaded or bundled, and it falls back to
the flat colour chip if a crop cannot be made. **Built and installed, but NOT seen on screen** —
the phone returned black captures again. Worth a look next time the reveal screen is open.

**Not done yet:** the gallery, which still needs the server.

### 8.5g DECIDED, NOT YET BUILT — New 2026 must use the By Number *process* + 2 extra files

**Owner, 2026-07-22 (end of session):** *"By Number vali image ni jem process follow karvi — e loko
API mathi je vastu magave che e j apde magavi, and ek vadhrani apdi."* Asked which extra file, he
chose **both: the photo and the round palette swatches.**

**What that means concretely.** Today the two paths are different shapes:

| | Fetches | Where from |
|---|---|---|
| By Number (and the New 2026 gallery) | `{name}.png` + `{name}.colored.png` | `thumb/{cat}/…` — §3.9 |
| Reveal engine as built (§8.5f) | `regions.json` + `id_map.png` + `original.png` + `border.png` | a bundle directory |

**The target:** New 2026 keeps the **Number-tab request shape** and adds two files of ours:
`{name}.png`, `{name}.colored.png`, **+ the original photo**, **+ the palette swatches**.

**Work this implies (none of it done):**
1. **Backend** — serve the two extra files under the same `thumb/{cat}/…` naming the app already
   uses (e.g. `{name}.original.png` and `{name}.swatch{n}.png`), from `number_tab.py` /
   `image_response()`, and export them in `number_tab_builder`.
2. **App** — `RevealLevel` must stop requiring `regions.json`/`id_map.png` and instead derive
   regions the way the existing engine already does from `.colored.png` (per-pixel palette colour
   → connected areas), using the photo purely as the reveal source. That is a real change to
   §8.5f, not a tweak.
3. **Swatches** — use the server's images instead of `RevealLevel.swatch()`'s in-app crops (keep
   the crop as the fallback when a swatch is missing).

**Why it is worth it:** one request shape for both tabs, and the app stops needing a second,
bundle-shaped API. **Cost:** every picture carries two more files.

### 8.5h DECIDED — New 2026 should run the admin's OWN page in a WebView, not a port of it

**Owner, 2026-07-22:** *"jevu review ma chale che admin ma, avu phone ma **exact** joye che."*

**Why the native engine (§8.5f) cannot satisfy that.** `RevealView` is a *re-implementation* of
`diamond_builder`/`reveal_game_builder`'s HTML. However close it gets, the shading, the morph
curve, the socket ring, the number placement will always differ a little — and the moment the
ColorForge engine is improved, the app is behind again. "Exact" and "ported" are incompatible.

**The decision: load the same page.** The backend already writes a **self-contained
`play_color.html` per item** (`ITEMS/{iid}/play_color.html`, the file the admin's "Open playable
preview" opens; `play_diamond.html` for diamond — `app.py:478-481`). Point a WebView at that URL
and the phone runs **the same code, the same colours, the same animation** — not a copy. Future
engine changes reach the app for free.

**Trade-offs, honestly:** heavier than a Canvas view (a WebView per game screen); needs the server
(or the page + assets cached locally) instead of working from bundled files; and the app must still
own the chrome — back, ads, progress — around it.

**Plan when this is picked up:**
1. A `WebGameActivity`: full-screen `WebView` (JS on, no zoom controls, `setBackgroundColor`),
   loading `{base}/…/play_color.html` for the tapped item.
2. Keep the app's own back button and ad hooks around it; let the page own the gameplay.
3. Progress: the page keeps its state in-page today, so either (a) accept per-session progress at
   first, or (b) add a tiny JS bridge so the page can hand its state to the app to persist.
4. `RevealActivity`/`RevealView`/`RevealLevel` (§8.5f) become the **offline fallback**, or are
   deleted if the WebView route proves good enough. Do not delete them until it has.

**Not started** — raised at the end of a long session and deliberately left unbuilt rather than
half-built.

**🐞 Two reveal bugs found and fixed first (2026-07-22), both worth remembering:**
1. **"Reveal doesn't happen."** `regions.json`'s palette is **sparse** — numbers run 1..100 with
   gaps — but `RevealPaletteAdapter` listed *every* number 1..max. Chips for numbers with **no
   regions** looked tappable and could never reveal anything. Now it lists only numbers where
   `totalOf(n) > 0`. (Same bug in `RevealActivity`, which treated a number as a list position:
   `notifyItemChanged(number - 1)` / `scrollToPosition(next - 1)` → use `positionOf(number)`.)
2. **"Numbers look terrible / spoil the image."** `drawNumbers()` printed all ~511 of them at
   once, burying the artwork. Now it draws only the regions of the **selected** number, with a soft
   colour dot behind and a smaller cap on the text size. **Verified on device: the picture reads
   cleanly and the palette shows the photo-crop swatches.**

### 8.5i STOP POINT — everything now waits on the server (2026-07-22, end of session)

**Owner: "kale server live karine proper setup karshu."** Right call, and the reason matters.

**Every workaround built today existed only because the phone cannot reach ColorForge:** the
bundled colour sample, the down-scaled reveal bundle, and — briefly — a **6.4 MB self-contained
`play_color.html` stuffed into assets**. The owner stopped that last one with *"stop jugad"*, and
he was right: it made one hardcoded picture play on the phone while moving the product nowhere.
**It was removed the same session.** `activity/WebGameActivity.java` was kept — it takes a **URL**
as well as an asset, so it is the real §8.5h path, just not wired to anything yet.

**The trap to avoid tomorrow:** without a reachable server the only choices are *build things that
cannot be verified* or *fake the data to verify them*. Both produce mess. Get the server up first.

**When the server is live, in this order:**
1. Point `colorforge_base_url` at it (Firebase Remote Config, or `ColorForgeApi.setLocalOverride`)
   → the New 2026 gallery should list real categories and pictures with no code change (§8.5b).
2. Wire `WebGameActivity.fromUrl(...)` to the item's `play_color.html` and compare it side by side
   with the admin preview. **That is the "exact same as admin" the owner has asked for three
   times** — decide then whether the native `RevealView` survives as an offline fallback or goes.
3. Only then §8.5g (serving the photo + swatches through the Number-tab request shape).

**Also still open, unrelated to the server:** the real `ADMOB_APP_ID` (release builds fail without
it, by design — §8.5e), and roadmap item 4 (design pass), which needs the owner to name a screen
rather than be guessed at.

### 8.5j Hosting decided (2026-07-23) — see **DEPLOY.md**

Owner's plan: **images → AWS S3**, **AI engine → RunPod (GPU)**, **everything else → Hostinger**.
Full step-by-step in **`DEPLOY.md`** at the repo root. The parts worth knowing here:

- **Hostinger must be a VPS.** Shared/web hosting is PHP-only; FastAPI will not run on it.
- **`STORAGE=s3` is already implemented** (`storage.py`) — S3 is one env var, not a code change.
- **RunPod: the worker exists (`runpod-worker/`, Dockerfile ready) but the backend never calls
  it** — verified by grep. Deploying it alone does nothing; `services/ai_lineart_local.py` would
  have to be taught to use it. So it is a *code* task, not a deploy task.
- **Only "serving" blocks the app.** Generating (torch + the 224 MB `models/`) can stay on the PC —
  the pictures are already made and published. Do not conflate the two.
- **Two things to fix before it is public:** the seeded boss password `colorforge123`
  (`db.py:98`), and letting the server generate its own `data/.jwt_secret` instead of copying the
  laptop's.

### 8.5k Test before you buy — cloudflared tunnel, and it WORKS (2026-07-23)

Owner: *"atyare testing karvu che, chale to levay — haji chaltu to nathi"* — right instinct, so no
hosting was bought. A **free cloudflared quick tunnel** from the PC proved the server side instead:

```bash
# cloudflared-windows-amd64.exe, then:
cloudflared.exe tunnel --url http://127.0.0.1:8000 --no-autoupdate
```

**✅ Confirmed serving real content over the public URL** (not samples):
`/index.php/Number/category/` → 9 categories (animals, daily, love, flowers, mandalas, nature,
other, people, kids); `/index.php/Number/gimg/c/animals` → the real published items.
**So `number_tab.py` + the app's expected API shape are proven end-to-end.** What was never
verified is only the *app* half — the phone's wireless ADB dropped before the URL could be set.

**2026-07-25 follow-up — the tunnel is too flaky even for testing.** Trying to point the app at it:
- App side is fine. New logging shows it builds the right URL and fires the request; the failure
  was `java.net.UnknownHostException` on the **first** call after launch (DNS race), so
  `New2026Fragment` now has **3 auto-retries with back-off, 30 s timeouts, and a tap-to-retry
  message** — a genuine robustness fix, keep it.
- But the real problem was the **quick tunnel itself dying**: cloudflared streamed `ERR / failed to`
  and `curl` to the public URL returned `000` while `localhost:8000` stayed `200`. Free
  trycloudflare tunnels drop constantly and the URL changes each run.
- **LAN shortcut doesn't work here either:** phone → PC `ping 192.168.1.108` times out (router
  client isolation / firewall), and adding the firewall rule needs admin (Access denied).
- **Conclusion: stop fighting the tunnel. The server side is already proven** (first tunnel run
  did serve the real 9 categories + published items). The blocker is purely "a stable host the
  phone can reach" = the VPS in DEPLOY.md. Do that; don't sink more time into tunnels.

**Practical notes (still true):**
- Download cloudflared with **PowerShell `Invoke-WebRequest`** — `curl` in Git Bash timed out at
  5 minutes and left a corrupt 37 MB file that failed with `Exec format error`.
- Start the backend with `--host 0.0.0.0` (not just 127.0.0.1).
- The URL changes on every run and dies with the PC — testing only.
- **The phone and PC are on the same WiFi** (PC `192.168.1.106`, phone `192.168.1.100`), so
  `http://192.168.1.106:8000` may work with no tunnel at all — untested, and CLAUDE.md §8.4 notes
  Windows Firewall blocked exactly this on the office PC.

### 8.5l ✅ END-TO-END WORKING — admin → server → app (2026-07-25)

**The whole chain runs on a real device.** No hosting bought — used `adb reverse tcp:8000 tcp:8000`
so the phone's `localhost:8000` maps to the PC backend (stable, unlike the flaky tunnel; no
internet involved). Set `base_url_override = http://127.0.0.1:8000/`, and:

- **New 2026 grid shows the real published pictures** — the Krishna image published from the admin,
  plus the kitchen-shelf / dragon / fox / eagle ColorForge pieces. Confirmed by screenshot + server
  logs (`GET /index.php/Number/gimg/c/animals → 200`, `GET /thumb/animals/… → 200/206`).
- **Opening one downloads its line art + solution and plays** (`GameActivity`; `.png` + colored
  `.colored.png`, HTTP 206). So the New 2026 → server → game path is proven.

**Two real bugs fixed to get here (both keepers):**
1. **`New2026Fragment` first request lost the DNS race** → 3 auto-retries + back-off, 30 s
   timeouts, tap-to-retry message.
2. **Grid stayed empty despite 200s.** `BaseRestAdapter.setSource()` snapshots `getItems()` at
   construction — when the source is still empty — and `getItems()` returns a **copy**, not a live
   list. So after the fetch filled the source, the adapter still held the old empty list. Fix:
   in the data-source listener, `adapter.setSource(dataSource); notifyDataSetChanged();` once data
   has arrived.

**✅ §8.5h DONE (2026-07-25) — New 2026 now plays the admin's OWN page in a WebView.**
Owner, unambiguous: *"reveal game akhi joie, colour vali nahi — admin ma je game che, exact ej."*
So the tap no longer opens the native flat `GameActivity`; it opens the server's self-contained
`play_color.html` — the exact page the admin's "Open playable preview" shows — in `WebGameActivity`.
Not a port, the real thing; future engine changes reach the app for free.

Wiring:
- **Backend** `app.py` `number_play()` → new route `GET /index.php/Number/play/{category}/{fileName}`
  (in `number_router`, so it is on the app's base URL). Maps `fileName → iid` via the new
  `db.iid_for_file_name()` and returns the same HTML as `/review/{iid}`.
- **App** `New2026Fragment.onColoringClicked()` → `WebGameActivity.fromUrl(ColorForgeApi.playUrl(...))`
  instead of `GameActivity`. `ColorForgeApi.playUrl()` builds `{base}index.php/Number/play/{cat}/{file}`.
- **Verified on device:** tapping the kitchen-shelf picture opened "ColorForge · Colour Fill",
  `0/511 coloured`, photo-crop palette; tapping regions revealed the real photo colours
  (`14/511`). Server log: `GET …/play/animals/… → 200`.

**`play_color.html` is self-contained** (~0.2–6 MB, data inlined; no external `src=`), so the
WebView needs only that one URL. The native `RevealView`/`RevealActivity`/`RevealLevel` are now
redundant for New 2026 — keep as a possible offline fallback, or delete later; do not delete while
the WebView path is this new.

**🐞 MERGE WAS ON — New 2026 was showing ionicsolutions' paid content mixed in (fixed 2026-07-25).**
The first end-to-end test showed **9 categories and 1616 "animals" items** — that was
`number_tab.py`'s upstream merge (`number_merge`, **default True**) blending our few published
pieces with the paid ionicsolutions catalogue. **Owner: New 2026 must be ColorForge ONLY, nobody
else** — which is exactly the §3.6 "PIVOT: no server-side merge" that was decided but never
switched off. Fix: `db.set_config('number_merge', False)`. Now `/category/` → `["animals"]` and
`/gimg/c/animals` → the **2** real published items. **The merge default in `_cfg()` is still
`True` in code — a fresh DB / new deploy will merge again unless this config row is set. Set
`number_merge=False` as part of any deployment (add it to DEPLOY.md).**

### 8.5m 🔴 MAJOR PIVOT — owner wants the whole app REBUILT IN FLUTTER (2026-07-25)

**Supersedes the WebView approach (§8.5h) and much of the native app work.** The owner played the
WebView `play_color.html` and rejected it: *"a no chale, bov bngar output, app ni theem sathe
match nathi thatu, gametya tap karo to reveal thay"* — it looks like a webpage, not the app, and
tap-anywhere-reveals is wrong. **Direct HTML is out.** He wants **the whole app rebuilt in Flutter**
from scratch, modern and consistent.

**Note the reversal:** `flutter_app/` was deleted on 2026-07-20 "do not recreate" (§1). That is now
overridden — the owner explicitly wants Flutter this time. Update/replace §1's warning once the new
project exists.

**Requirements he gave for the Flutter app:**
- **Reveal game = colour-by-number mechanics, not tap-anywhere.** Select a number in the bottom
  palette → the regions of that number **highlight** → tapping fills/reveals **only that number's**
  regions (exactly how the existing By Number flat game selects/fills, but the fill is a
  photo-reveal). We already have the data for this natively: `regions.json` (number, id, cx/cy,
  bbox) + `id_map.png` + `original` + `border.png` — same inputs `RevealLevel` parses.
- **Grid thumbnails = the BORDER / line-art image, not the coloured preview.**
- **Screen UI/UX same as By Number**; **design + settings common across the whole app.**
- **Daily**: show date-wise uploads with the date on top; then category-wise below. Same shape for
  **Diamond**.
- Follow the app's **settings + ad rules**; proper **loading bar, coin setup, ad setup.**
- **Small APK; modern, simple, animated.** Owner is fine with a full redesign ("tari rite modern to
  pan chalse").
- Backend/admin (Python) **stays as-is** — the Flutter app talks to the same ColorForge server.

**⚠️ Reality (state honestly, do not sugar-coat): this is a from-scratch rebuild of a LIVE
Play-Store app** (134 Java files, Sumo Recolor native engine, Firebase, billing, 3 paid third-party
tabs). It is weeks-to-months, not a change. **Do it in parallel — leave the Java app untouched and
shipping** until the Flutter app is real.

**THE open scope question that changes everything (ask before building):** does the Flutter app
need the **3 paid tabs** (By Number=ionicsolutions, By Images=recolor, By Pixel=fungames), each a
separate third-party integration to re-implement? Or is it **ColorForge only** (New 2026 + Diamond +
Daily, from our own admin) — far smaller, and what his vision actually sounds like?

**Everything from §8.5b–8.5l (native New2026Fragment, RevealView, WebGameActivity, adb reverse
testing, merge-off) was on the Java app.** The end-to-end proof (admin → server → app shows real
content, plays) still stands and de-risks the Flutter build: the **backend + data contract are
proven**; only the client is being redone.

### 8.5ar 🚀 LIVE ON A VPS — the whole chain runs over the internet (2026-08-21)

**`https://srv1919471.hstgr.cloud/`** — Hostinger VPS `187.77.141.63`, Ubuntu 24.04, 2 CPU / 8 GB /
100 GB, bought by the owner. **Full operational detail is in `DEPLOY.md`'s "IT IS DEPLOYED" box at
the top** (paths, service, nginx, TLS, how to redeploy a file); it is not repeated here. The parts
that change how you work on this repo:

- **Generation runs ON the server.** torch 2.13.0+cpu is installed there, so uploads produce real ML
  line art (§8.5ao) without the PC being involved. `models/` was deliberately not uploaded —
  `ensure_weights()` re-downloaded the 17 MB lineart file by itself.
- **Storage: was `local`, flipped to `s3` the same day — see §8.5at.** `data/cdn` stays on the VPS
  disk as a backup. ⚠️ Under S3 the case-insensitive fallback below never runs (`image_response`
  takes the redirect branch), so keys must be written lower-cased at export time, which they are.
- **`number_merge = false`** was already set in the uploaded DB, so New 2026 is ColorForge only.
- **Secrets:** `.jwt_secret` was generated fresh on the server (the laptop's was not copied), and the
  seeded `colorforge123` password (§8.5j) is gone — rotated to a random 20-char value which is
  **only** in `/root/colorforge-admin-password.txt` (chmod 600). It was never printed in chat; the
  owner reads it through Hostinger's browser console. Verified: the new password works, the old one
  is rejected.

**🐞 The bug that only exists on Linux — the single most useful thing learned here.**
Category folders are written **lower-cased** (`number/animals/…`), but a folder created by an older
publish carries the category's *original* casing (`Animals`). **Windows is case-insensitive, so
every local test passed for a year**; on the VPS the app's three `/thumb/{cat}/…` files returned a
hard **404**. Fixed in the layer that owns filesystem semantics: `LocalStore.local_path()` now falls
back to `_resolve_ci()`, a segment-by-segment case-insensitive walk (refuses `..`, and a directory
never counts as a file). The legacy `Animals/` folder was also renamed to lowercase so the exact
match is the fast path and the fallback stays a safety net. **This fixes Number and Diamond at once**
— both call `store.local_path()`. *Generalise the lesson: anything that round-trips a name through
the filesystem is untested until it has run on Linux.*

**✅ VERIFIED END TO END FROM A REAL PHONE, over HTTPS, with `adb reverse` explicitly removed** so
nothing local could answer. `base_url_override` was set to the live URL and the server's own nginx
access log was used as the proof — not the app's logs:
- `okhttp/5.3.2` (the app's REST client) fetched `Number/category/`, `gimg/c/animals`, and the grid
  thumbnail; then `Dalvik/2.1.0` (the game's `HttpURLConnection`) pulled all four reveal assets —
  `regions.json`, `id_map.png`, `border.png`, `original.jpg`.
- **Screenshot: the snow-globe reveal game playing from production**, with the new ML line art, the
  photo-crop palette swatches and a filling banner ad.
- **Diamond the same:** `Diamond/category/` → `gimg/c/animals` → `dthumb/…` → `Diamond/data/….json`,
  board rendered with its 32-colour palette.
- Also confirmed in the UI dump: **no `nav_item_calendar`** — the Daily tab is correctly hidden
  (§8.5c).

**🔴 The one step only the owner can do:** Firebase Console → Remote Config →
**`colorforge_base_url` = `https://srv1919471.hstgr.cloud/`** → Publish. Until then a *shipped* app
has no server; debug builds fall back to `127.0.0.1:8000` (§8.5ap).

**Confirmed on screen, and it is a content problem, not a code one:** the diamond board
(112×199 = **22,288 beads**) reads as a pale wash — you cannot tell what the picture is. Same
"too dense to be fun" class of issue as the colour side in §8.5p. The dial is `diamond_builder`'s
cols / palette. **The owner has still not been asked** — worth raising before he publishes a batch.

### 8.5at ☁️ VPS storage flipped to S3 — bucket `color-mate` (2026-08-21)

Owner: uploads must land in AWS. The VPS ran `STORAGE=local` (§8.5ar); S3 was implemented but
never enabled. Now live:

- **`STORAGE=s3`, bucket `color-mate` (ap-south-1, public-read)** — creds in
  `/opt/colorforge/storage.env` (chmod 600), loaded by the systemd unit's `EnvironmentFile=`.
  The bucket is **shared with the Learn-to-Draw Laravel backend** (its `drawings/`, `categories/`,
  `backups/` prefixes vs our `images/`, `number/`, `diamond/` — no collision, deliberate).
- **All 633 `data/cdn` files migrated** (via `S3Store.put_bytes` itself, so content-types match),
  0 failures; the local `data/cdn` copy stays on disk as a backup. All 3 published items were
  re-published through the API so their `base_url` is the S3 form and live writes were proven
  (S3 `Last-Modified` = the republish minute).
- **🐞 Real bug fixed on the way:** the native reveal route (`app.py` `number_reveal_asset`)
  derived its key by splitting `base_url` on `"/cdn/"` — an S3 `base_url` has no `/cdn/`, so
  every item published under S3 would have 404'd the whole New 2026 game. The key is now built
  directly as `images/{iid}/{asset}` (base_url only proves the item is published). Verified both
  base_url forms serve 200 through the route.
- Verified over the public URL: Number thumb → 302 → S3 200 · reveal `regions.json`/`id_map.png`
  200 · Diamond feed/grid/thumb 200. Admin thumbnails use `/api/items/{iid}/thumb` (local ITEMS
  folder) and are unaffected.

### 8.5as ✏️ "Lines look too hard" — the control existed, its default was wrong (2026-08-21)

Owner, with a reference coloring page (clean cartoon outlines, sparse numbers): *"mare svg jevi
clearly border vali image show thavi joyi atyare bov hard lage che."*

**Measured first.** The published snow globe's `border.png` was **24% ink** — a clean coloring page
runs 5-8%. Composited over white it is plainly a *pencil sketch*: roof shingles, pine needles, wall
grain, hatching everywhere. The complaint was exact.

**Four cleanup routes were tried on the finished line art and all four are dead ends** — recorded so
nobody spends the day re-deriving them:
| Attempt | Why it failed |
|---|---|
| Hard threshold + despeckle | Strokes break into dashes; the picture reads as dotted |
| Texture-density suppression | Kills the subject along with the texture |
| Region-boundary borders | Blobby contour-map look, nothing like a drawing |
| **Hysteresis** (Canny-style, keep whole strokes touching a strong one) | **The faint hatching is *connected* to the main lines**, so one component swallows the page and nothing is dropped |

**The fix is upstream: flatten the PHOTO before the model draws it.** Texture the model cannot see is
texture it cannot ink. And that already exists — `app.py:_generate` runs the admin's Simplify level
(`simplify.py`) over the line-art source, leaving `reveal_master.png` the true photo.
**The bug was only that `#upSimp` defaults to "Off"**, so every upload asked for maximum detail.

**⚠️ The trap that cost the most time, and the reason to always test on two kinds of picture:** a
global flatten inside `ai_lineart_local.generate()` (bilateral + 640 input) looked like a clean win
on the photograph — and **destroyed a flat cartoon**, wiping the turtle's shell and the hedgehog's
spikes, because a bilateral pass eats exactly the thin dark outlines that art is made of. That
change was **reverted**; `SIMPLIFY_ROUNDS` stays 0 with a comment saying why. **One setting cannot
serve both a photograph and an illustration** — auto-detection was attempted and abandoned
(flat-fraction 0.45 vs 0.34, bilateral-delta 5.6 vs 6.6 — far too close to branch on with 2 samples).

**What shipped:**
- `admin.html` — the Detail control now defaults to **Cartoon / illustration (light)**, is labelled
  by *what the picture is* rather than by strength, and carries a one-line hint. Options in order:
  Cartoon (default) · Detailed drawing · Photograph · Off.
- `reveal_game_builder.LINE_MIN_STROKE` (4e-5 of the canvas) — drops ink blobs too small to be a
  stroke. They read as dirt and can cut a region into unfillable slivers. Helps both kinds.

**Measured on the same snow globe, regenerated as Photograph:** ink **24% → 14%**, regions
**124 → 70**, colours **57 → 18**. Published as `20260821_clean_lineart` **alongside** the old
`20260820_lineart_check`, both live in the animals feed, so the owner can judge them side by side in
the app instead of from a screenshot.

**Reference-vs-reality, stated plainly:** the owner's reference is *hand-drawn* — uniform confident
strokes, deliberate sparseness. Ours is traced from a photograph. Simplify closes most of the gap;
it does not make a photo into a cartoon. Feeding the engine flat illustrations rather than
photographs is the bigger lever, and costs no code.

### 8.5au 🎛️ Admin design pass — and how to actually SEE the admin while working on it (2026-08-21)

Owner: *"akhi admin panel ni proper design kar mast."* The page was **not** a bare form — it already
had tokens, light/dark themes, a sidebar, panels and cards. So this was a pass over specific
defects, not a rewrite.

**The technique is the reusable part.** `admin.html` is behind a login, so it cannot simply be
screenshotted. A **harness** solves it: copy the page, inject one `<script>` ahead of its own that
seeds a token and stubs `window.fetch` with mock JSON for the ten `/api/...` paths, then let
`location.hash` drive `go(view)`. Chrome headless (`--headless=new --virtual-time-budget=5000
--screenshot`) then renders **the page's real CSS and real render functions** with fake data, at any
width and either theme.
⚠️ **Rebuild that harness from `admin.html` + the saved mock block — never from the previous
harness.** Doing the latter re-injects the whole earlier file, so the page ends up with several
`<head>`s and the *older* stylesheet lands last and wins. That cost a long detour chasing a
"broken" media query which was correct all along; a probe writing `getComputedStyle` into
`document.title` and read back with `--dump-dom` is what settled it. **Check `h.count('<title>')==1`.**

**Defects found and fixed:**
| | |
|---|---|
| The sticky bar printed the page title **directly above** the page's own `<h2>` | bar is now a slim breadcrumb (`Content / Library`); the page owns its heading |
| Category and status filters shared **one undifferentiated chip row** — two dimensions looking like one list, and not combinable | two labelled groups (`STATUS` / `CATEGORY`), each chip carrying its own count; independent `FILTER` + `CATF`; a Clear button |
| **A member with no activity rendered a solid RED bar** (0% good + 100% bad) — a new employee looked like every upload was rejected | empty track + "No activity yet" |
| Missing/loading thumbnails were flat dark rectangles that read as broken | a diagonal pattern as a **second background layer** under the photo. An `::after{z-index:-1}` does NOT work — it hides behind the element's own background |
| `Nothing here.` dead end | empty state with a reason and a way out |
| Dashboard left ~40% of a 1440×900 screen empty | a **Categories** panel (uses already-loaded `CATS` — no new request) |
| Cards went ragged when one carried an error box | `min-height` on `.cbody` |
| `70r/18c` was unexplained | `title="… regions · … colours"` |
| Regenerate said Off/Light/Medium/Strong while upload now says Cartoon/Photograph | both name the **picture**, not the strength |
| Sidebar collapse existed only below **820px**, so 900-1080px squeezed the grid | raised to 1080px, and the duplicate responsive block I had added was removed — it sat *above* `.content{padding:26px}` and lost on source order |

**Verified by screenshot** at 1440 and 980, in dark **and** light, plus the drawer: sidebar collapses
to icons, three cards per row at 980, chips and counts legible in both themes. Deployed; the live
page serves with one `<style>`, balanced braces, and the login renders.

### 8.5av 🧰 Admin: the seven gaps, all closed (2026-08-21)

Follow-on to §8.5au — the owner asked what was left worth improving, then said do all of it.

1. **Publish date — the long-open §3.9 item.** The API has taken `publish_date` since July and the
   app already hides future-dated items (`Manager.java:417-424`), so scheduling worked end to end
   *except* nothing could set it. `confirmModal` gained an optional date picker (`o.date`; `onOk`
   now receives `(text, date)`, so existing callers are untouched), Approve carries it, published
   items get a **clock button** to change it, and a future date shows a **"Goes live 24 Aug"** badge
   on the card — a "Live" pill alone was a lie for content the app is still hiding.
2. **`number_merge` had no UI at all** and its code default is `True`, so a fresh DB silently mixes
   the bought ionicsolutions catalogue into New 2026 — which is exactly what happened in §8.5l.
   Settings now has the switch, spells out the consequence, and **asks for confirmation when
   turning it ON** (the dangerous direction), with the upstream URL beside it.
3. **Lazy thumbnails.** Every card fetched its picture the moment the grid rendered; with S3 that is
   also a request each. Now `data-thumb` + IntersectionObserver. ⚠️ **The observer's first callback
   is not immediate** (and never fired at all under headless), so visible cards are promoted by a
   synchronous `getBoundingClientRect` pass first and only the rest are observed.
4. **Pagination** — 48 cards, "Show 48 more". `shown` resets per view.
5. **Bulk publish / delete** with a select-all that only ever reaches the *filtered* ids
   (`visibleIds()`), and a **sequential** batch runner — publishing is a heavy server job, so firing
   50 at once would bury the box. Reports `ok/failed`.
6. **Categories are editable** (rename, order, delete, add) in Settings. `categories()` already
   sorted by `(ord, name)` but nothing could write `ord`, so category order in the app was
   unreachable — `db.update_category()` + `PATCH /api/categories/{cid}` accepting `name/icon/ord`.
7. **Every icon-only button now has `title` + `aria-label`** (was 6 titles, 0 aria-labels in the
   whole file); the theme toggle rewrites its own label when it flips.

**Also fixed: the 4-second poll rebuilt the entire screen.** `schedulePoll` called `go(VIEW)`, which
reset scroll, re-applied filters, dropped the "show more" page and re-requested every thumbnail.
`pollTick()` now refetches, compares a signature of `(id, status, regions, error)`, and **swaps only
the cards whose state moved** — falling back to a full redraw only when list membership changes. It
also skips entirely while the tab is hidden.

**Verified functionally, not just by eye** — a 60-item mock driven in headless Chrome reported
`cards=48 loaded=12 more="Show 12 more"`, `SELECTED=8 bar="8 selected Publish 4 Delete 8"` (note
Publish counts only the publishable states), `scheduledBadge="Goes live Aug 24, 2026"`, and
`ERRORS(0)` on both Library and Settings. Live: `PATCH {"ord":5}` took effect and was restored.

### 8.5aw 🛡️ Production hardening — backups, one-at-a-time generation, login throttle, swap (2026-08-22)

Four things the VPS needed once it stopped being a test box.

**🐞 The real bug: generation was never serialised, and the code said it was.**
`reveal_game_builder._flattened()` patches module globals for the Simplify level and carries the
comment *"Not thread-safe — generation already runs one job at a time on a background task."*
**That was an assumption, not a fact.** `_process`/`_process_diamond`/`_regenerate` are plain `def`,
so Starlette runs each BackgroundTask in its **threadpool**, and the upload input is `multiple` —
so the ordinary act of dragging ten photos in launched ten torch passes at once on a 2-CPU box,
each stamping on the others' `COLOR_K`. Two images at different Detail levels could silently come
out with each other's settings. Fixed with a **single worker thread draining a `queue.Queue`**
(`_enqueue`); every generation and publish now goes through it, order preserved, and the item sits
in `processing` while it waits — which is what the admin already displays. A queue, not a lock:
blocking N threadpool threads on a mutex would starve request handling when a big batch lands.
*Verified: three uploads fired back-to-back kept load under 1.0 on 2 CPUs and the admin answered
200 throughout; before, three concurrent torch runs would have pushed it past 3.*

**Backups — there were none.** `content.db` is 56 KB and holds the only mapping from `file_name` to
item, plus publish dates, users and the audit trail. The assets are safe in S3, but **without this
file they are not addressable and the app's feeds go empty.** `backup_db.py` + a systemd timer
(`colorforge-backup.timer`, 02:30 UTC daily, `Persistent=true`) takes a **`sqlite3 .backup()`**
snapshot — safe while the server is writing, unlike `cp` — keeps 14 locally in `data/backups/`, and
mirrors to `backups/colorforge/` in S3 (sub-prefix because the bucket is shared with the Laravel
backend). *Verified by restoring, not by trusting: the local copy opens, `pragma integrity_check`
returns `ok`, and the S3 object is byte-identical and opens with the right row counts.*

**Login throttle.** The admin is on the public internet behind one password and nothing counted
failures. 8 per IP per 10 min, then 429 for 15 min; a correct password clears the slate, and a
correct password *during* a lockout is still refused. In-memory is deliberate — single uvicorn
process, and a restart clearing counters is not worth a table. *Tested from the server itself
(127.0.0.1) so the owner's own IP could not be locked out; his IP verified still 200 afterwards.*
⚠️ Checked first that `_client_ip()` sees the real address: uvicorn's `proxy_headers` default trusts
127.0.0.1 and nginx sends `X-Real-IP`, so the audit table already stores real IPs. Had it returned
`127.0.0.1` for everyone, this throttle would have locked out the whole world at once.

**Swap.** 8 GB RAM, torch, and zero swap — a big image could have been OOM-killed outright. 4 GB
swapfile, `vm.swappiness=10` (RAM first; swap is the net, not the plan), in `/etc/fstab`.

**Noticed while testing, NOT changed:** the engine is **not deterministic** — `cv2.kmeans(...,
KMEANS_PP_CENTERS)` with an unseeded OpenCV RNG, so the same photo at the same Detail level gave
91 / 77 / 95 regions on three runs. Pre-existing, unrelated to the queue. It makes "Try again"
usefully varied but means no result is reproducible; `cv2.setRNGSeed()` keyed off the job id would
fix it if that is ever wanted.

### 8.5ax 🎛️ Both generators measured, then tuned (2026-08-22)

Owner asked what was worth improving in the colour and diamond generators. Everything below was
**measured on real published output first**, and two of my own hypotheses were killed by the data.

**Colour — the numbering was the real defect.** A 25-colour picture came out numbered
`5, 8, 13, 15, 18, 27, 30, 50, 85 …`, because `numbers[rid] = ci + 1` used the **k-means cluster
index** and the quantiser asks for `COLOR_K = 100` clusters while only a fraction survive. To a
player that reads as "numbers are missing", and the order said nothing about the colours (12
lightness inversions out of 24). New `_compact_palette()` renumbers **1..N, darkest first** — the
same convention `diamond_builder` already used, so the two games now agree.
⚠️ **It had to be applied in TWO places** and the first one was the wrong one: `_color_regions()`
is only the no-torch fallback; the path that actually produces `regions.json` is
`_game_data_color()`. Patching only the first looked like a no-op and the sparse numbers survived.
*Verified on the live server: served `regions.json` → numbers 1..54 contiguous, 0 inversions,
every number used by a region.*

**Otherwise the colour engine measured healthy** and was left alone: smallest inscribed radius 9 px
(so no region is too small to hold its number), and **no palette pair closer than Lab dE 16** — the
`COLOR_MERGE_DE` guard is doing its job.

**Diamond — the board was 48,400 beads.** `cols` auto-detects from bead pitch and **hit the old 220
cap**. The finished render reads fine, but the board the player *starts* on is a near-white wash of
sockets whose numbers cannot be read without deep zoom — that, not the render, was the "can't tell
what it is" complaint in §8.5ar. `MAX_COLS` 220 → **130** (≈16,900 beads).
Also added: a **de-speckle** pass (a bead matching none of its four neighbours takes the majority
colour, iterated) and light **bilateral smoothing of the small cell grid** before quantising.
*Live result: 130×130 = 16,900 beads, lone beads **3.5% → 0.01%**.*

**Two things I assumed and the measurements refuted — don't re-derive them:**
1. **Flattening the photo first does almost nothing for diamond** (23.2% → 23.1% neighbour-change).
   The §8.5as line-art lesson does *not* transfer: `_cell_colours` already takes a median over each
   cell's centre, which is its own low-pass filter.
2. **Fewer beads makes local noise WORSE, not better** (110 wide → 34%, 78 wide → 39.7%), because
   each bead then covers more of the picture. Bead count is a **session-length and legibility**
   dial; de-speckling is the noise dial. They are different problems.

**Still open on both, deliberately not done:** neither engine is deterministic — `cv2.kmeans(...,
KMEANS_PP_CENTERS)` with an unseeded RNG gave 91/77/95 regions for the same photo at the same
setting. And `border.svg` still traces **colour-region boundaries** at stroke-width 1, not the
inked line, so the owner's "SVG jevi clearly border" request has no true vector export yet.

**Follow-up the same day — the two items §8.5ax left open are now done:**

- **Both engines are deterministic.** `cv2.setRNGSeed(RNG_SEED)` before every `cv2.kmeans` — four
  call sites (`reveal_game_builder`, `lineart_region_builder`, `color_paint_builder`,
  `diamond_builder`). *Live proof: the same photo uploaded twice through the admin produced
  `92r/35c` both times; locally three runs gave a byte-identical `regions.json` and diamond grid.*
  ⚠️ **This is only safe because §8.5aw serialised generation** — `setRNGSeed` sets a process-wide
  global, so under the old concurrent BackgroundTasks two jobs would have reseeded each other
  mid-run. Do not re-introduce parallel generation without revisiting this.
- **`border.svg` is now a TRUE vector of the drawn line.** It used to trace each colour region and
  stroke it at width 1 — it described where the *colours* change, not where the artist's line is,
  and flattened every stroke to one weight. `ink_svg()` traces the border's **alpha** as *filled*
  shapes (`fill-rule="evenodd"` keeps holes), so the varying ink weight is reproduced exactly and
  it scales without pixelating. The old file is kept, honestly renamed **`regions.svg`**, because it
  is still the machine-readable map of playable cells. *Published bundle: `border.svg` 68 KB / 1
  path vs `border.png` 78 KB — the vector is smaller than the raster it replaces.*
  Verified by rendering the SVG against its own PNG thresholded at the same cut: identical drawing.

### 8.5ay 🎨 Admin redesign — a real design pass, not a tune-up (2026-08-22)

Owner after §8.5au/av: *"hju admin panel ni design ma kai maja nathi avti — UI and UX followed kari
ne all page ni design banay."* Fair. The earlier passes fixed defects; the page still read as a
generic admin template — every surface the same weight, no focal point, and pages half empty.

**Foundation first, because it re-skins everything at once.**
- **Elevation ramp** `--e1/--e2/--e3` plus `--edge` (a hairline top highlight) replaced the single
  flat `--shadow-sm`. A card now sits above the page and a control above the card, so the eye has
  somewhere to start.
- **One type scale** (`--fs-display/title/h/body/label/micro`) instead of a dozen ad-hoc px values;
  page titles went 22px → 30px at −.032em tracking so a page has a clear head.
- ⚠️ **Two mistakes made and caught immediately, worth remembering:** a Devanagari digit was typed
  into a hex colour (`#D93F४7`), and the dark tokens were written as
  `:root[data-theme="dark"], @media(...)` — **a media query cannot sit in a selector list**, which
  silently killed the whole dark theme. The block is now the correct three-part form: light on
  `:root`, system dark under `@media … :root:not([data-theme="light"])`, and both explicit choices
  as their own rules. Check `<style>` brace balance after any token edit.

**Per page:**
| Page | Was | Now |
|---|---|---|
| Dashboard | four unrelated stat boxes | a **pipeline** (Generating → Ready → Awaiting you → Live → Failed), each stage clickable through to that filter, with a share bar; a **14-day uploads chart** built from `created` timestamps already loaded; "At a glance" |
| Upload | a lonely form on a 1440px canvas | one focused 760px column, the **drop zone as the hero**, settings beneath it, and the four steps as a strip |
| Audit | a five-column table | a **timeline** with coloured event dots and relative time (`ago()`), which is how a log is actually read |
| Library | 4:3 cards, ragged heights | square image-forward cards, **sticky filter bar**, tier moved onto the picture, meta on one line, and equal row heights (dropping `align-items:start` — an error box used to make the row ragged) |

**Verified**: every view rendered in headless Chrome against the real CSS with a 60-item mock,
`ERR(0)` on all of them, and **both themes checked** — the light theme is not an afterthought here,
it is the one the owner's browser may well use. Deployed; the live page serves one `<style>`,
308/308 braces, and the login still renders.

### 8.5az 📣 Ads: config moved to the admin, and the splash stopped walking past the ad (2026-08-22)

**🐞 The bug the owner reported — the splash opened the next screen behind a live app-open ad.**
`tryNavigate()` is the gate that waits for the launch ad (`launchAdFinished` +
`AdsManager.isFullScreenAdPending()`), and the 8 s safety timeout already checked it. But the
**animated splash finishing did not go through that gate at all**: `onSplashDone()` and the
`SCENE_MAX_MS` fallback each set `introFinished = true` and called **`navigateAfterSplash()`
directly**. That method has no ad check, so whenever the splash animation ended while the ad was up
— which is the normal case — the app navigated straight out from under it. Both call sites now use
`tryNavigate()`, and `navigateAfterSplash()` gained a bounded self-deferral
(`MAX_NAV_DEFERRALS`, 15 s) so a future call site cannot reintroduce it.
*Verified on the device: logcat shows `AdActivity` on screen, then
`Safety timeout reached but the launch ad is on screen — waiting`, and MainActivity only after the
ad was dismissed.*

**Ad settings now come from the admin, not Firebase.** The architecture made this small, and the
reason is worth remembering: the app read Firebase Realtime DB node `V1` into `AdsManager.AdModel`,
then `saveAdSettings()` wrote all 34 values into SharedPreferences — and **the other ~9,000 lines of
ad code only ever read SharedPreferences**. So only the source had to change.
- **`ad_config.py`** — the 34 fields with types and defaults, stored as one row in the existing
  config table. `save()` **refuses to let an empty string overwrite a stored value**: one blank box
  in the admin would otherwise wipe a live ad unit id and stop the app earning. `warnings()` flags
  Google's TEST unit ids and anything not shaped like `ca-app-pub-…/…`.
- **`GET /index.php/App/ads`** (public, on the app's own base URL) returns the object keyed with
  **exactly the `AdModel` field names**, so the app decodes straight into it — no second model.
- **Admin → Ads** page: unit ids, frequency, placement toggles, store/legal, with the warnings box
  at the top and consequences spelled out next to each switch.
- **App** `AdsManager.loadAdConfig()` tries our server first and **falls back to Firebase** on any
  failure — and treats a config with no unit ids at all as a miss, since applying it would blank
  good ids. Uses `hasExplicitServer()`, not `baseUrl()`, so a debug build cannot point the live ad
  config at `127.0.0.1`.
*Verified live: the server log shows `okhttp/5.3.2` fetching `/index.php/App/ads`; saving through
the API round-trips types correctly (`dAdsCount` int, `dIsSplashOpen` bool) and an empty save left
the stored id intact.*

⚠️ **This needs an app release to take effect for real users** — the fetch code ships inside the
APK. Until a new version is on Play, installed apps keep reading Firebase.

**Still open, and worth doing in that same release:** there is **no UMP/CMP consent form anywhere**
(0 references), so EEA/UK users get no ads at all; **New 2026 and Diamond show a banner only** —
`RevealActivity`/`DiamondActivity` have 0 interstitials and 0 rewarded, while `GameActivity` has 2
and 5; and the real `ADMOB_APP_ID` is still missing.

### 8.5ba 💰 Ad revenue: consent gate + the two silent tabs (2026-08-22)

Owner asked for higher CPM and more impressions. Measured what was actually missing first — two
findings dominated, and two more were checked and found to be **fine already**, which is worth
recording so nobody "optimises" them:
- **Banners are already adaptive** (`getCurrentOrientationAnchoredAdaptiveBannerAdSize`) — the
  common "switch off 320×50" win does not apply here.
- Frequency, preload counts and placement are all **already remote-controlled**, and since §8.5az
  they are editable from the admin. Tuning them needs no code.

**1. There was no consent form at all — that is an impressions AND a CPM hole.** AdMob serves
EEA/UK users nothing until a consent message has been shown, and those are the highest-CPM users
the app can get. New `ads/AdConsent.java` wraps Google's UMP SDK (added to
`libs.versions.toml` + `app/build.gradle`) and runs in `SplashActivity` **before** the launch ad is
requested — asking for an ad before the answer is in just wastes the request.
⚠️ Three things it must do, and does: the callback fires **exactly once in every outcome**
(granted / refused / not required / lookup failed) because ad init waits on it; a **failed lookup
proceeds** rather than blocking, since outside the EEA that would cost every user their ads; and
when the user refuses, `launchAdFinished` is set immediately so navigation does not sit waiting for
an ad that will never come. Outside the EEA `requestConsentInfoUpdate` returns at once, so nobody
else is delayed. *Device log: `Consent finished — canRequestAds=true`, ads still loading.*

**2. New 2026 and Diamond were earning from a banner only** — `RevealActivity` and
`DiamondActivity` had **0 interstitials and 0 rewarded**, while `GameActivity` has 2 and 5. The two
tabs the owner is investing in were the two worst earners. Both now show an interstitial on exit
(`leaveWithAd()`), which is the same break point By Number uses; `MediationManager` still applies
the usual frequency rule, so it is not an ad on every exit, and the callback closes the screen
whether the ad showed, failed or was skipped. The **system back gesture and the on-screen button
share the one exit path**, with a re-entry guard.
⚠️ Deliberately **not** placed on the load-failure `finish()` — an ad after "could not open this
picture" is bad for the user and frowned on by Google.
*Verified on device: back from RevealActivity opened `AdActivity`.*

**Still on the table, in rough order of value:**
- **Native ads in the picture grids.** `ColoringsAdapter` / `BaseRestAdapter` / `LibraryItemAdapter`
  all carry **zero** ads, and the grids are where users spend the most time. In-feed native pays
  well. Bigger change — those adapters also serve the three paid tabs, so it needs care.
- **App-open ad is written but switched off** (`dIsSplashOpen` default false). Highest-CPM format,
  code already there, now a toggle in the admin — it needs a unit id and the owner's decision, as it
  is also the most intrusive.
- The real **`ADMOB_APP_ID`** (release builds still fail by design without it).

### 8.5bb 🖼️ Grid tiles: the "shadow" found and removed (2026-08-22)

Owner: *"list ni design proper kar and image ma shadow ave che e remove kari ne fari thi re-design kar."*

**There were two greys, from two different places, and only measuring told them apart.** Scanning
one row of a device screenshot inward from a tile edge gave: page lavender → a **~20px grey ramp**
(220→193) → a **1px hard line** (135) → white artwork.
- The 20px ramp was **`cardElevation="4dp"` + `cardUseCompatPadding="true"`** in
  `item_daily_number_color.xml`. Elevation is now 0 with the platform shadow colours cleared.
- The 1px line is **baked into the thumbnails themselves**: every ionicsolutions PNG carries a
  frame in its **alpha** channel — ~196 along the top/left, ~55 along the bottom/right, clear from
  the second pixel in (verified by fetching one straight from their server and reading its alpha).
  Their files cannot be changed, so the ImageView is scaled `1.008` and clipped, cropping ~2px.

⚠️ **I edited the wrong layout first.** The By Number grid does not all come from one adapter: the
category grid is `ColoringsAdapter` → `item_coloring.xml`, but the **`daily` tab** — which is what
the owner's screenshot showed — is `LevelsAdapter` → `item_daily_number_color.xml`. Changing only
the first had no visible effect. Both are now identical, so the two grids cannot drift apart again.

**Two regressions the removal caused, which is why it needed re-checking on the device:**
1. `cardUseCompatPadding` had been doing the **spacing**. With it gone the tiles touched, because
   `grid_item_margins` was only **2dp** — raised to 6dp.
2. The favourite button (`cm_glass_circle`, 85% white) was invisible on white line art — new
   `cm_fav_circle` adds a hairline edge, tint darkened.
Also added page padding + `clipToPadding=false` on the grid RecyclerView, so the outer column no
longer touches the screen edge.

**Both BlurViews are now deleted, not just hidden** (`ColoringsAdapter` and `LevelsAdapter` lost
their `ensureBlurSetup`, fields and imports). They had been `GONE` since the performance work, so
this only removes dead code — and the RenderScript import with it.

### 8.5bc 🔎 AdMob audit — three real defects, and a lot that was already right (2026-08-22)

Owner asked for a policy-compliant pass to raise impressions, fill rate and eCPM **without** simply
showing more ads or spending CPU. Audited first; **most of the system turned out to be sound**, and
three of my own assumptions were wrong. Recording both halves, because "already fine" is worth as
much here as "fixed" — it stops the next pass rebuilding what works.

**Already correct — do not "optimise" these:**
| | |
|---|---|
| Frequency | show-every-N (`KEY_SHOWICON`) **plus** a per-session cap (`adShowed >= dAllowedPlay`, reset per process) **plus** a 20 s quiet window after any full-screen ad (`FULL_SCREEN_AD_COOLDOWN_MS`) |
| Duplicate requests | `InterstitialPool.loading` already guards one-request-at-a-time |
| Backoff | already exponential (10 s / 30 s / 120 s) |
| Rewarded | user-initiated, with a confirmation dialog, and the reward is granted **only** if `onUserEarnedReward` fired — checked in the dismissal callback |
| Banners | already adaptive |
| Leaks | no static `Activity` fields anywhere in the ads package |
| Analytics | `AdsAnalytics` already logs request / loaded / failed / closed with load times |

**Three defects found and fixed:**
1. **The retry loop never ended.** The *delay* was clamped at 120 s but the retry itself was
   unbounded, so a session that could not fill kept requesting every two minutes for as long as the
   process lived — **including in the background, where an impression is impossible.** That is
   "excessive ad requests" in AdMob's words, and requests that cannot become impressions are
   exactly what drags **match rate** down. Now `MAX_RETRIES = 4`, and a retry is skipped outright
   while the process is not `STARTED`. Giving up is safe because
   `InterstitialPool.onAppForegrounded()` (called from the existing app-open `ON_START` observer)
   clears the failure count and warms the pool again — so a user who was offline earlier still gets
   an ad later, without anything having polled in the background to achieve it.
2. **Banners were never destroyed.** `new AdView(activity)` was added to a container and forgotten.
   An AdView auto-refreshes for as long as it exists, so a screen the user had left carried on
   requesting — wasted requests, a retained `Activity`, and battery. `bindBannerToLifecycle()` now
   pauses on `onPause`, resumes on `onResume`, and removes + `destroy()`s on `onDestroy`.
3. **New 2026 and Diamond had no interstitial at all** — fixed in §8.5ba, listed here for
   completeness.

**Deliberately NOT done, and why:** no new ad *slots* were added beyond the two missing exit
interstitials, no frequency value was made more aggressive, and nothing polls to manufacture
requests. Raising impressions by asking more often would cost match rate and retention, which is
the opposite of what was asked for.

**Still open:** native ads in the picture grids (the adapters carry none, and the grids are where
users spend their time — but those adapters also serve the three paid tabs, so it needs care);
App Open is written but switched off; and the real `ADMOB_APP_ID` is still missing.

### 8.5bd 🧱 One tile design across every grid (2026-08-22)

Owner approved the Number-tab tile from §8.5bb and asked for it everywhere. There are **five item
layouts behind the app's grids**, and they had drifted apart — different radii, different
elevations, three of them still carrying a BlurView:

| Layout | Used by | Was | Now |
|---|---|---|---|
| `item_coloring.xml` | By Number categories, New 2026, Diamond (`ColoringsAdapter`) | done in §8.5bb | reference design |
| `item_daily_number_color.xml` | the `daily` tab (`LevelsAdapter`) | 4dp elevation + blur | matched |
| `frag_bookitem.xml` | By Images (`LibraryItemAdapter`) **and** the colour gallery (`ArtworksAdapter`) | compat padding + blur + 4dp inset | matched |
| `row_item_image.xml` | By Pixel (`RVAdapterImage`) | **4dp elevation** + blur | matched |
| `item_favorite.xml` | Favourites | 2dp elevation, 12dp radius | matched |

All five: `cardElevation=0`, platform shadow colours cleared, `cm_surface` plate,
`cm_radius_md`, spacing from `grid_item_margins`, picture filling the tile.

⚠️ **The 2px crop was applied only where the defect was proven.** The 1px alpha frame is an
**ionicsolutions** artefact, verified by reading their PNG; By Images (recolor) and By Pixel
(fungames) come from different servers and were left uncropped rather than cropped on a guess.
**By Pixel also keeps `fitCenter`** — pixel art centre-cropped loses columns.

**Dead blur code removed from four places** (`ColoringsAdapter`, `LevelsAdapter`,
`LibraryItemAdapter`, `PixelFragment`) along with the `RenderScriptBlur` imports. It had been
`GONE` since the performance work, so nothing changed visually — but `item_blur_view` no longer
exists in any layout, so leaving the lookups would have been a compile error waiting to happen.
The blur views on the **tab strips and banners** (`tab_blur_view`, `offer_blur_view`,
`daily_blur_view`) are a different thing and were left alone.

*Verified on device: By Number, By Images and By Pixel all render the same clean tile.*

### 8.5be 🔍 Reveal/Diamond zoom — three defects (2026-08-22)

Owner: opening a New 2026 picture, *"image zoom proper work nathi karti."* Three separate causes,
all present in **both** game views because they were written the same way.

1. **The scale was clamped after the offsets were derived from it.** `onScale` computed the new
   offsets from the raw pinch value and only then let `clampTransform()` pull the scale back to a
   limit — so at either end of the zoom range the two disagreed and the picture **lurched under the
   fingers on every frame** instead of simply stopping. Now the scale is clamped first and the
   offsets move by the ratio actually applied; if the clamp leaves the scale unchanged the frame is
   skipped entirely. `RevealView.onScale` and `DiamondView.handleTransform`.
2. **A resize threw the zoom away.** Instrumenting `fitToScreen()` on the device showed the real
   sequence: `SKIPPED level=false view=1080x1548` → `view=1080x1530 img=1254x1254` → then
   **`view=1080x1386`** — the banner ad below the board loads *seconds after* the picture is up and
   shrinks the view, and `onSizeChanged` called `fitToScreen()`, resetting whatever the player had
   zoomed into. Now the zoom is kept as a **multiple of fit**: recompute what fit means, then
   restore `scale = fitScale * ratio`. Only a genuinely unzoomed view re-fits.
3. **The tail of a pinch registered as a tap.** Fingers rarely lift together, so `GestureDetector`
   reported the last one as `onSingleTapUp` and a region was revealed that the player never aimed
   at. A `multiTouchGesture` flag, set on `ACTION_POINTER_DOWN` and cleared on the next fresh
   `ACTION_DOWN`, now suppresses that.

⚠️ **What I could NOT verify, and did not pretend to:** a two-finger pinch cannot be sent reliably
through `adb`. Verified on the device instead: the picture opens fitted (`fitScale=0.861` for a
1254² image in a 1080×1530 view), the fit button works, single-tap reveal still works, and nothing
snaps back after interaction. **The pinch itself needs the owner's fingers.**

*Aside worth remembering for device testing: an early "taps do nothing" reading was wrong twice
over — the picture was still loading in the first screenshot, and in the second the taps landed on
regions of a number that was not selected, which is correct behaviour. Also, the app-open test ad
repeatedly swallowed taps and opened the Play Store; writing `key_is_premium=true` plus
`app_prefs{age_consent_v1=true, launch_count=1}` gives a clean run with no ads and no paywall.*

### 8.5bf ✒️ The border is now a vector in the app — crisp at any zoom (2026-08-22)

Owner, on a zoomed-in reveal screen: *"border proper show karay."* The outlines break into visible
squares once you zoom, and that is not a filtering setting — `bitmapPaint` already carries
`FILTER_BITMAP_FLAG`. **Measured cause:** the app receives a **1254×1254** raster, so at ~5× zoom
one source pixel covers about **4.3 screen pixels**. There is no more detail in the file to show.

The backend already produces the answer (§8.5ax follow-up: `ink_svg()` traces the ink as filled
shapes) — it just was not reachable and was not used.

- **Backend:** `border.svg` added to `_REVEAL_ASSETS`. One line; it was already in every bundle.
- **App:** new `model/reveal/BorderVector.java` — a **~120-line parser, no new dependency**. Our
  exporter builds the path from `approxPolyDP` output, so the file only ever contains **M, L, Z**;
  anything else is treated as unparseable and the caller falls back to the raster. That keeps the
  APK exactly the size it was (roadmap item 6) instead of pulling in AndroidSVG.
- **`RevealView`** draws the path, filled and anti-aliased, once the player is past
  `VECTOR_BORDER_FROM = 1.3×` fit. Below that the bitmap is used: at fit the two are
  indistinguishable and a blit beats a 5,000-point path every frame.

**🐞 The trap, found by rendering it: `border.svg` does not mean the same thing on every item.**
Pictures published before `ink_svg()` carry the OLD export under that name — **741 `<path>`
elements** with `fill="none" stroke="#000"`, one per colour region. Filling those paints the
picture **inside out**: the device showed a black mandala with white lines. `BorderVector.parse()`
now requires the real thing's signature (`fill-rule="evenodd"` **and** `fill="#000"`) and returns
null otherwise, so old items keep using the raster. Republishing an item regenerates it in the new
form — verified: 741 paths → **1 path, evenodd, filled**, and the device then rendered the mandala
correctly the right way round.

⚠️ **Verification note, twice over:** a "239 mean brightness = correct" reading was measuring the
**paywall**, not the artwork — the screenshot had to be looked at to catch it. And a two-finger
pinch still cannot be sent through `adb`, so the vector was proved by temporarily setting the
threshold to 0 (vector at fit) and comparing against the raster; **the zoomed-in sharpness itself
is for the owner to confirm.**

### 8.5bg 🌀 Rounded outlines — the vector stopped looking traced (2026-08-22)

Owner, once the vector was in: *"border rounded jevi banay atle sari lage."* Fair — the lines were
crisp but **polygonal**: `approxPolyDP` emits straight segments, so a traced circle rendered as a
decagon and every corner was a hard angle.

**`ink_svg()` now emits curves.** Each contour is first smoothed by averaging every point with its
two neighbours (wrapping, since the contour is closed) to take the pixel staircase out, then a
**quadratic through the MIDPOINTS with each original vertex as its control point** turns the
polygon into a smooth closed curve. The app's `BorderVector` parser gained `Q` (two coordinate
pairs) — still no SVG library.

**Settings chosen by rendering four candidates side by side at 4x, not by taste:**
`eps 1.6 + smoothing` lost real detail (the wreath's cross vanished); `eps 0.8 + smoothing` rounds
the corners while keeping it. So `SVG_EPS` stays **0.8** and the smoothing pass is what does the
rounding.

**Size is the honest cost.** Curves need a control point as well as an end point: the mandala's
`border.svg` went **227 KB → 528 KB**. Mitigated by emitting on a **x2 pixel grid** (`SVG_GRID`)
so every midpoint is a whole number — same geometry, about a third fewer characters than writing
decimals. The `viewBox` is therefore twice the image size and the `width`/`height` attributes stay
at image size; the app already divides by the viewBox, so nothing else had to change.

*Verified on device: the vector parses, renders the right way round, and the central rosette is a
circle again rather than a polygon. The zoom threshold is back at 1.3x fit.*

⚠️ **Only newly published pictures get this.** Anything published earlier keeps its old file, and
the app falls back to the raster for those (§8.5bf's signature check). Use the admin's bulk publish
to regenerate a batch.

### 8.5bh 🩹 Broken strokes joined — but only where they are actually broken (2026-08-22)

Owner: *"hju border ma impove kar."* Measured the finished border before changing anything, and the
numbers named the defect: **227 separate pieces of ink, 196 loose ends, and the largest piece held
only 27% of it** — 41% of the pieces were under 6 px. The drawing was a heap of fragments, which is
why it reads as sketchy rather than drawn.

`_ai_border_bgra()` now runs a **gap-closing pass** (`LINE_CLOSE_PX = 2`) before the bold pass, and
`LINE_MIN_STROKE` went 4e-5 → 5e-5. On that picture: **227 pieces → 96, 196 ends → 0**, and the
vector got *smaller* (87k → 77k chars) because the contours are simpler. Applied in the renderer,
not in `ink_svg()`, so **the raster and the vector stay identical** — otherwise the drawing would
visibly change the moment the player zoomed past the switch-over.

**⚠️ Two wrong turns, both caught by measuring, both worth not repeating:**
1. **Closing the wrong mask.** Closing everything above a faint threshold and then inking the
   result solid turned every soft halo into a stroke: **13% ink → 21%**, the drawing fused into a
   blob. Only the SOLID ink (`>= LINE_SOLID`, the same cut the vector traces) may be closed.
2. **Closing unconditionally.** On a dense mandala it went **25% → 31% ink** while buying almost
   nothing, because that picture was **already 81% in one piece** — there was nothing to join, so
   it just fattened every line on the busiest artwork. Closing is now gated on
   `LINE_FRAGMENTED = 0.6`: bridge gaps only while the biggest piece holds less than 60% of the ink.

**Checked against all 10 real pictures on the server, not just the one being tuned:** nine are
already coherent (biggest 63–94%) and are left untouched; one (53%) gets closed. The mandala was
regenerated and republished and is back at **exactly 25.0%**.

**Honest limit:** the measurements say these pictures do not share one defect. The sparse one was
fragmented; the dense ones are simply dense (25–39% ink, hundreds of small pieces but one dominant
mass). Tuning further without knowing which of thickness / density / smoothness the owner objects
to would be guesswork — that question is worth asking before the next pass.

### 8.5bi 🎨 The fill seam under the vector ink (2026-08-22)

Owner, on a deeply zoomed screen: *"border and color fill ty e bane impove kar."* The screenshot
showed the defect precisely — the ink was crisp (the vector is working) but the **flat fill beside
it had a hard pixel staircase**, so the two did not look like they belonged to the same drawing.

**Cause, and it was self-inflicted.** The region map is built from the RAW line art, but §8.5bg's
vector is a *smoothed* trace of that same ink. Smoothing moves the outline by a fraction of a
pixel, so the fill — which still follows the raw region boundary — peeked out along the ink. At fit
that is invisible; at 5× it is a staircase.

`borderPaint` is now `FILL_AND_STROKE` with the stroke width set **per frame** as
`BORDER_SEAM_PX / k`, where `k` converts path units to screen pixels. Growing the ink by half a
stroke width covers the seam, and dividing by `k` keeps that cover a constant thickness on screen
at any zoom rather than ballooning with it.

**Checked first, so this was not fixed by raising resolution:** every reveal asset — `original`,
`id_map`, `ai_lineart` — is **1254²**, because `app.py`'s `MAX_SIDE = 1254` caps uploads. Raising
it is the other lever, but `RevealLevel` holds three int arrays of w×h, so 1254² already costs
~19 MB and 2000² would be ~48 MB on a phone that already asks for `largeHeap`. Not worth it for
this artefact when a hairline of ink covers it.

⚠️ **Not visually confirmed at zoom.** `adb` cannot send a pinch, and a temporary `DEBUG_ZOOM`
build was twice hijacked by the app-open test ad opening the Play Store, so both captures were of
the wrong screen. The debug hook was removed and the build re-verified (RevealActivity opens, zero
fatal exceptions), **but the zoomed appearance itself is for the owner to judge.**

*Device-testing note that finally worked reliably: force-stop the app AND `com.android.vending`
first, then write `key_is_premium=true` plus `app_prefs{age_consent_v1=true, launch_count=1}`, then
launch. Writing prefs before the force-stop lets the running app overwrite them.*

### 8.5bj 🧩 Fill shapes: the colour map was built at 720px (2026-08-22)

Owner, with a zoomed screenshot of colour spilling in ragged blocks: *"color fill hju proper kar …
clear shape ma j color fill thavo joyi."*

**Root cause, and it was never a rendering problem.** `_fine_color()` ran mean-shift + k-means on a
**720px** copy and then upscaled the *label map* with **`INTER_NEAREST`** to full resolution — so
every colour boundary became a staircase of ~2px blocks. `intersect_color()` then splits each
line-art cell along those boundaries, so **the regions inherited the blocks**, and the game showed
chunky fill edges no amount of smoothing in the app could fix.

**Fix:** keep only the *centres* from the small pass and **classify the full-resolution image**
against them. The boundary then follows the picture instead of the downscale grid. Chunked at
200k pixels because the full distance matrix would be ~600 MB. Costs about **1 s** (the expensive
mean-shift still runs small); a full rebuild went 3.1 s. Verified by rendering both label maps
zoomed 6x side by side — the upscaled one is visibly blocky, the full-res one follows the artwork.

**The structural finding underneath, which the owner should know:** measured on a finished picture,
**only 39% of region boundaries lie under ink**. The other 61% are colour-meets-colour with no
drawn line between them — because `intersect_color` deliberately splits a line-art cell when it
contains two colours (so a blue arm never shares a number with a different-coloured background).
That is why fills appear to "spill" past the drawing: there is genuinely no line there. Options if
this is to change: draw a hairline along every region boundary (which is what the old contour
fallback did, and the owner rejected that look), or stop splitting cells by colour and accept one
number per line-art cell. **That is a product decision, not a bug** — worth asking before acting.

*Regenerated and republished the mandala: 199 regions / 24 colours, and the fit view reads clean.
The zoomed-in appearance still needs the owner's eyes — `adb` cannot pinch.*

### 8.5bk 🎯 The blotchy fill: one colour was arriving as three numbers (2026-08-22)

Owner uploaded a flat Tom & Jerry cartoon — the ideal case — and the grey coat still filled in
ragged patches. Chasing it turned up **two separate faults**, and the second one is the real answer.

**Measured first, which killed two of my own theories.** The source really is flat art (**95% of the
image sits in its top 12 colours**, local noise 0.79 — a photo is 2+). The region map is not noisy
either: 0.05% lone pixels, one region under 400 px. So neither the picture nor the segmentation was
at fault.

**Fault 1 — `COLOR_MERGE_DE` does not exist in the code.** CLAUDE.md has referred to it since July
and §8.5ax even concluded "the merge guard is doing its job" — **that was wrong**. The engine
restored from git after the disk corruption (§8.5an) has *no colour-merging step at all*, and that
picture simply happened to have well-separated colours. The palette for the cartoon held
**#19 rgb(254,254,254) AND #18 rgb(252,252,252)** as different numbers, plus **three greys within
two units of each other** — so one grey coat needed numbers 7, 8 and 9, each filling part of it.
That is the blotchy fill, seen from the inside. New `_merge_close_colours()` fuses palette entries
under **Lab ΔE 10** (Lab, not RGB — a step means the same visible change anywhere in the space) and
keeps the colour of whichever member covers the most regions.
*Result: Tom & Jerry went **19 colours → 11**, the two whites became one 69.7% region and the three
greys one 16.8% region. The mandala went 27 → 26, i.e. barely touched, because its colours were
genuinely separate — which is the check that the threshold is not over-merging.*

**Fault 2 — nothing rejoined the regions afterwards.** Even with one number, a flat area stayed
split into separately-tappable pieces: 79 of 244 touching pairs shared a number.
`_merge_same_number_neighbours()` unions them. Runs **before** `_subdivide_big` so the deliberate
one-tap area cap is not undone.
*Cartoon: 101 regions → 71.*

⚠️ **Worth knowing: `_subdivide_big` nearly doubles the region count on flat art.** It splits any
region over 2% of the canvas, and a cartoon's background was **54%** — so one white area becomes
~27 same-coloured pieces by design. That cap makes sense for a photo, where a huge region is
unusual, and much less for flat artwork where a big uniform shape is the whole point. Not changed
yet; it is a game-feel decision (how much should one tap fill?) for the owner.

*Both flat pictures were regenerated and republished. Verified on the server, not the device — the
app-open test ad kept opening the Play Store mid-test, and the owner has the phone anyway.*

### 8.5bl 🖐️ One tap, one shape — the area cap is off (2026-08-22)

Owner, on the much-improved cartoon: *"a vi color hoy to e k click to color fill ty javo joyi"* —
if it is all one colour, one tap should fill it. The picture still had a grey block in the corner
because `_subdivide_big` chops any region over 2% of the canvas into same-numbered pieces.

**Measured on four real pictures before touching it, and the result decided it:**

| picture | cap off | cap 2% (was) | cap 8% | cap 20% |
|---|---|---|---|---|
| Tom & Jerry (flat) | **20** | 71 | 29 | 22 |
| tiger | 58 | 85 | 65 | 58 |
| mandala | 231 | 237 | 231 | 231 |
| photo | 246 | 256 | 246 | 246 |

**The cap only ever fires on flat artwork** — which is precisely where it is unwanted, because a
cartoon's background *is* one shape. Photographs and dense line art never produce regions that big
(largest 5% and 12%), so turning it off changes them by a handful of regions.

`COLOR_AREA_CAP = 0.0` disables it, with the table above kept in the source so the next person does
not have to re-measure. Set it to e.g. `0.20` if a picture is ever so plain that one tap finishing
it feels wrong.

*Cartoon republished: **101 → 71 → 20 regions**, 19 → 11 colours, across the three fixes in
§8.5bk-bl.*

⚠️ **This is a game-feel change, not just a bug fix.** A flat cartoon is now a ~20-tap picture
rather than ~70. That is what was asked for and it matches how the reference app plays, but if the
owner later wants longer sessions from flat art, the dial is this constant — not the colour merge,
which should stay.

### 8.5bm 💡 Finished colours leave the strip; the wand became a real hint (2026-08-22)

Owner, on the now-clean cartoon: finished colours should drop out of the palette, and the hint
should highlight the shape **and zoom to it**.

**Palette.** `RevealPaletteAdapter` built its list once, in the constructor, so a completed colour
stayed in the strip for ever — untappable, and pushing the ones still to do off screen. `rebuild()`
now keeps only numbers with `revealedOf < totalOf`, and `refreshCounts()` (called from
`onRegionRevealed`) rebuilds and reports whether the list changed, so the caller only does the
cheap `notifyItemChanged` when it did not. The strip now empties as the picture fills — a progress
bar for free.

**Hint.** The wand used to be `jumpToNextNumber()`, which silently changed the selection and moved
nothing. It now: picks a region, selects its colour if different, **glides the camera onto it**
(420 ms, decelerating) and pulses a violet ring for 2.6 s.

⚠️ **Two things had to be right or the hint does nothing, and the first version got both wrong:**
1. **It must zoom IN.** `clampTransform()` re-centres the whole picture whenever `scale == fitScale`,
   so at fit the camera physically cannot put a region in the middle — the first build drew its ring
   half off the right edge. `HINT_MIN_ZOOM = 2.4x` fit guarantees there is room to pan.
2. **It must point at the SMALLEST unfilled region**, not the first. A player asking for a hint has
   already found the obvious shapes; aiming at the 69%-of-the-canvas background helps nobody.

*Verified on the device: the picture zooms in and the ring lands centred on a small region marked
"1". Two earlier readings were wrong and worth remembering — 1,720 changed pixels meant my tap
missed the button (the wand is at y=522, not 470; `uiautomator dump` gave the real bounds), and a
later 2.3 M meant a test **interstitial** had covered the screen, not that the zoom worked. Always
confirm the top activity is still RevealActivity before trusting a pixel diff.*

### 8.5bn 🔢 Numbers that survive a busy drawing (2026-08-22)

Owner, on a zoomed tiger: *"number proper show karay."* Two separate reasons the digits were hard
to read, both visible in his screenshot.

**1. No contrast against the line art.** `SmartLabelRenderer` painted plain black text, and these
pictures are mostly black line work — over a dense patch a digit simply merges into the strokes
around it. Each label is now drawn twice: a **stroked halo in the paper colour**
(`haloWidthFactor = 0.30` of the font size, round joins) and then the black glyph on top. The
number keeps its own weight and colour and gains its own clear space.
*On the device the "1" sitting inside a solid black stripe is now perfectly legible — it was
invisible before.*

**2. Labels were placed under the palette.** The frame the renderer clips to was the picture's
rectangle only, so a number could land where the floating palette strip covers the board —
half-visible, which reads as broken rather than as hidden. `draw()` takes a `bottomInset`, and
`RevealActivity` **measures the real overlap** (`getLocationOnScreen` on both, in a `post`) rather
than hard-coding a strip height that would be wrong on another screen. The frame is also now
clamped to the view on all four sides.

### 8.5bo 🧹 Shredded regions go to the line (2026-08-23)

Owner, on a zoomed tiger: *"a ma sav jina region che e line ma j ly lee."* Two orange smears inside a
black stripe, plus some navy flecks beside them.

**They were all ONE region, and that is the whole story.** Sampling the id-map at four of the specks
returned id 21 every time: 971 px in the map, of which the ink left **352 px in four fragments, the
largest 268 px and 4.1 px thick**. Regions are the line-art's cells split by colour, so a region can
be whole in the id-map and still reach the player as crumbs — the ink crosses it *afterwards*.
Nothing about that is findable or tappable; unfilled it reads as dirt on the drawing, filled it
reads as dirt in the photograph.

`_specks_into_ink()` (`reveal_game_builder.py`) now measures, per region, the largest piece the ink
leaves visible and hands the whole region to the border when it is only crumbs — alpha 255, dropped
from `numbers`, its pixels left in the id-map so nothing has to be relabelled. The app needs no
change: `regions.json` simply has fewer regions, `RevealActivity` re-downloads every time (no local
cache), and `restoreProgress` already ignores ids a picture does not contain, so old saves survive.

**Two tests, and each rules out a different false positive** — this is why one number was not enough:

| | keeps what area-alone would kill | keeps what radius-alone would kill |
|---|---|---|
| `INK_SPECK_R = 5.0` px | a compact 265 px blob 7.4 px thick — small, but plainly there | |
| `INK_SPECK_FRAC = 4.5e-4` | | a ribbon 4 px thick holding **2,790 px** of real artwork |

**⚠️ Per REGION, never per piece — the first version was per piece and it was badly wrong.** A
hatched drawing is thousands of small gaps between strokes: one measured picture had **3,788 visible
pieces across 45 regions**, worth 5.9% of the canvas. Those gaps are the shading. Inking them turns
hatching into a solid black mass, and they belong to big reachable regions that must stay fillable.
A budget (`INK_SPECK_BUDGET = 0.02` of the canvas) caps the pass either way; past that the
shattering is a content problem (§8.5p), not something the border should paper over.

**Measured on five real pictures** — cost is 1 second and a tenth of a percent of ink:

| picture | regions | colours | ink |
|---|---|---|---|
| tiger | 58 → **55** | 27 → 26 | +0.10% |
| kitchen shelf | 73 → 66 | 44 → 40 | +0.15% |
| dense portrait | 155 → 145 | 91 → 90 | +0.30% |
| snow globe | 45 → 45 | 42 → 42 | — (nothing shredded) |

**Verified live, not just locally:** deployed, all three colour items republished, and the served
`regions.json` re-measured — region 21 is gone with **0 visible pixels**, the palette is contiguous
1..26, no chip without a region and no region without a chip. Rendering that crop old-vs-new shows
the stripe going from orange smears to solid black.

⚠️ **A separate change reached these pictures in the same republish**: the tiger dropped 85 → 58
regions *before* the speck pass ran, because it had been generated while `COLOR_AREA_CAP` was still
2% and had never been republished since §8.5bl turned it off. Chasing that as a bug in the new code
cost real time — **when a republish changes more than you expect, check what else has been deployed
since the item was last generated.**

*Also learned: `/root/colorforge-admin-password.txt` on the VPS is a four-line note (url / email /
pass), not a bare password — parse the `pass:` line. And the login route returns `access`, not
`token`.*

### 8.5bp ✒️ The border, rebuilt: one drawing at every zoom (2026-08-26)

Owner: *"border ma su improve karva jevu che?"* — measured the three live pictures, then he asked
for all of it. The headline finding was not on the list I expected.

**🔴 The rounded border only existed when you zoomed in.** `RevealView` switches to the vector at
`VECTOR_BORDER_FROM = 1.3x` fit, so the view a player *opens on* was still the model's raw ink:
lumpy, dashed, speckled. §8.5bg's rounding went into `border.svg` alone, and `border.svg` is the
half nobody saw. Worse, the picture visibly **changed** as you zoomed past the switch-over.

`border.png` is now filled from the same `_ink_contours()` the SVG is written from
(`_raster_from_contours` / `_svg_from_contours`, one `fillPoly` with the EVEN-ODD rule so holes
inside a stroke stay open, supersampled x4 for real anti-aliasing). **Verified by replaying the
app's own `BorderVector` parser over the published file: IoU 0.985-0.990 against the raster, and
identical ink percentages** — the two are the same drawing.

**🐞 Found on the way, and it was in the shipped vector too: smoothing filled sharp junctions into
blobs.** The 3-tap neighbour average was unclamped, so wherever thin strokes met at an acute angle
it pulled the tip of the notch inward until the wedge closed — plainly visible on the mandala, and
worth **3.3 points of ink** (29.2% -> 25.9%). A pixel staircase is half a pixel and a real corner
moves much further, so the displacement is now capped: `SMOOTH_MAX_PX = 0.5`. Rendering 0.4 / 0.7 /
1.2 / unclamped side by side is what settled it.

**Region boundaries the model never drew** (`_hairline_missing_edges`). 21-23% of them on a dense
photograph had no ink under them at all — the fill just stops in mid-air, which is the "colour went
outside the picture" complaint. Drawing *every* one is the contour-map look rejected in §8.5bj and
took a snow-globe photo from 15% to 27% ink. Two gates fix that: a minimum missing run
(`HAIRLINE_MIN_RUN = 24`) and, the one that matters, **`HAIRLINE_MIN_DE = 30` in Lab** — below that
the two fills read as one wash and a line there is only more ink. Result on that photo: 20% ink for
boundary coverage 50% -> 76%. ⚠️ **A flat cartoon measured 93% covered and is left untouched** —
ink 7.9% -> 7.9%, byte-identical SVG. That is the check that this cannot hurt good artwork.

**Dirt** — `INK_MIN_SHAPE = 4.5e-5` of the canvas, applied to the contours, so raster and vector
drop the same specks. It replaces the old `SVG_MIN_AREA = 6`, which was small enough that 100-150
isolated crumbs survived per picture.

**The SVG is about half the size**: commands are now RELATIVE (`q`), because the deltas are one or
two digits where absolute coordinates ran to four. Mandala 513 -> 255 KB, tiger 290 -> 182 KB,
cartoon 99 -> 54 KB — and `RevealActivity` re-downloads every asset on every open, so that is real
data saved. `BorderVector` gained relative parsing (upper/lower case, pen tracking, `z` returning
the pen to the subpath start). **An older APK that cannot read it falls back to `border.png`, which
is now the identical drawing** — so shipping this was safe without a release.

**Measured, live** (old -> new):

| picture | ink | ink pieces | anti-aliased | boundary covered | border.svg |
|---|---|---|---|---|---|
| Tom & Jerry (flat) | 8.5% -> **7.9%** | 30 -> **6** | 3.3% -> 27.6% | 96% -> 93% | 99 -> **54 KB** |
| tiger | 27.4% -> **25.6%** | 323 -> **199** | 5.8% -> 37.9% | 77% -> 88% | 290 -> **182 KB** |
| mandala | 25.1% -> 27.3% | 225 -> **166** | 2.4% -> 31.6% | 79% -> 92% | 513 -> **255 KB** |

*(the mandala's ink rises because the hairline closes its undrawn edges; its pieces still fall)*
Palette integrity re-checked on all three: numbers contiguous 1..N, no chip without a region, no
region without a chip.

**What the source picture does, and it dwarfs the renderer.** Tom & Jerry is 8% ink and 6 pieces of
line; the tiger and the mandala are 26-27% and hundreds. That is not a rendering fault — the model
inks whatever texture it can see, and the admin's Detail control already fixes it per picture.
Nothing was telling the uploader which to pick, so `source_kind()` now measures the upload (edge
density, coarse-histogram flatness, median gradient), stores it on the item, and the drawer's
Regenerate label says *"Lots of texture in this picture — if the lines look busy, rebuild it as
Photograph"* or *"Flat artwork — Cartoon is the right setting"*.
⚠️ **It suggests, it never applies.** §8.5as auto-flattened and wiped a cartoon's outlines, and the
numbers do not separate "detailed illustration" from "photograph" cleanly enough to act on: across
18 masters only the true cartoon stands apart (top-8 colours 97%, edge density 2.8%, vs 8.6-58%
and 4.5-25% for everything else).

**Checked and found FINE — do not "fix" these:** ink never fuses into masses (0.1-0.7% of it is
thicker than 12 px), stroke width is 4-6 px on a 1254 px page, there are no partial-alpha ghost
blobs at all, and the largest ink piece holds 63-81% so §8.5bh's gap-closing correctly stays off.

*Deployed, all three colour items republished, live files re-measured. App rebuilds clean.*
⚠️ **A heredoc mangled the Java escapes again** (`'\n'` written as a real newline). Writing Java
through a shell heredoc keeps costing a build — construct backslashes with `chr(92)` and write
BYTES, since the file is CRLF and Python's text mode rewrites the line endings underneath you.

### 8.5bq 📦 The audit acted on: caching, a JPEG photo, and the first tests (2026-08-26)

Owner asked what else was worth improving, then said do all of it. Everything below was measured
before it was changed.

**🔴 Every open re-downloaded the whole picture — 2.1 MB.** Nothing was cached anywhere: the
loader used a bare `HttpURLConnection` and Android installs no HTTP cache by default. A player
opens the same picture again and again to finish it, so five sittings cost 10 MB and five slow
starts. Two independent fixes:

1. **The photo goes out as JPEG.** `original` is the reveal SOURCE — never composited, so it has
   no use for an alpha channel — and it is by far the heaviest asset. At quality 95 the tiger went
   **1,617 KB → 368 KB** for a mean error of 1.6/255 that cannot be seen. The route already
   accepted `original.jpg` and the app already asks for it first, so this was an export change;
   `storage.exists()` (new, on both stores) lets one route serve whichever of `.jpg`/`.png` an item
   actually has, so nothing published earlier had to be touched. *Live: tiger 2,147 → **897 KB**,
   cartoon → 505 KB, mandala 2,168 → 1,852 KB (a high-frequency mandala photo simply does not
   compress as well).*
2. **`RevealAssetCache`** (new) keeps a picture under `cacheDir/reveal/<key>`, downloads with three
   attempts and a growing pause, and prunes oldest-first past 120 MB. Within 24 h the copy is
   trusted; after that only `regions.json` (5-17 KB) is re-fetched and compared byte-for-byte —
   unchanged means the whole set is unchanged, changed means the item was republished and the
   folder is dropped. **If that check cannot reach the server the cached copy is used anyway**, so
   a downloaded picture plays with no network at all.
   ⚠️ A 404 is deliberately NOT retried: the loader probes for files that may not exist (an item
   published before the JPEG has no `original.jpg`), and retrying that would just be slow.

**✅ Verified on the device, with the server's own log as the witness.** First open wrote all five
files (1,867 KB). Then the cache was left in place, the picture closed and re-opened:
**RevealActivity was up in about a second and nginx logged ZERO requests.**
*(Visual confirmation was not possible — the owner picked the phone up mid-test and the screenshot
caught a Unity app. The §8.5c rule again: a capture taken more than a few seconds after the action
is not evidence of anything.)*

**The first automated tests — `platform/backend/tests/test_engine.py`.** There were none, on an
engine with ~30 constants that each have a measurement behind them. Ten checks, all synthetic and
about two seconds, no torch and no fixture image, each one written because that exact bug shipped:
id-map channel encoding (§8.5an), bbox `[x,y,w,h]` (§8.5q), contiguous palette (§8.5ax), near-
identical colours merging (§8.5bk), same-number neighbours joining (§8.5bk), raster and vector
being one drawing (§8.5bp), a shredded region retiring cleanly (§8.5bo), the ink budget refusing to
black out a quarter of a picture, the hairline ignoring invisible edges, and `number_merge` being
off. It carries **a miniature port of the app's own `BorderVector` parser**, so an exporter change
the phone could not read fails here instead of on a player's screen.
⚠️ The first version of the shredded-region test failed for a good reason worth keeping: on a
100×100 canvas the 2% ink budget is 200 px, and the test's sliver was 360. Synthetic fixtures have
to stay in PROPORTION to the thresholds they exercise.

**`number_merge` now defaults to OFF.** It defaulted on, so a fresh database or a rebuilt server
silently blended the bought ionicsolutions catalogue back into New 2026 — which happened once
(§8.5l) and does not look like a failure when it does.

**Memory: `RevealLevel.idMap` is a `char[]`.** An id is `R + G*256` and so cannot exceed 65535 by
construction, which halves the array: at 1254² that is **3.1 MB instead of 6.3 MB**, on a screen
that already holds two more arrays the same size plus the border bitmap.

**Diamond: three attempts instead of one.** The board is fetched once and any failure ended as
"Could not open this picture". The reveal list got retries back in §8.5l; the game screens never
did.

**Deliberately NOT built, and why:**
- **Native ads in the picture grids** — the biggest earner left, but `ad_config.py`'s 34 fields
  have **no native unit id** and neither does `AppPreference`. There is nothing to serve, so the
  code would be dead until the owner creates the unit in AdMob. Blocked on him, not on us.
- **App Open ad** — written, switched off (`dIsSplashOpen`), and now a toggle in the admin. Needs a
  unit id and his decision, since it is also the most intrusive format.
- **Collapsing the 5 HTTP stacks and 3 image loaders** (§8.6) — weeks of work across a live app's
  four shipping tabs. Not something to half-do between two other tasks.

### 8.5br 🏁 The finish is a moment now, and the pipeline got a daily dial (2026-08-26)

Owner picked four items from the improvement list ("1 thi 4 kari dee"). All verified on the device
end to end.

**1. Completion.** Finishing a picture used to show a two-second Toast — the most emotional moment
of a 20-minute session, thrown away. `onFinished()` now: zooms the finished picture to fit, fires
`RevealView.celebrate()` (the existing fill particles, across the whole canvas), **saves the
artwork into My Works** — the same `MyColorByNumber/{fileName}.png` folder and naming the By
Number game uses, so the Saved tab and FavoritesActivity list it with no changes — and slides up a
sheet (900 ms later, so the player sees their picture first) with **Next picture / Done**.

**"Next" chains without going back through the grid.** `New2026Fragment` passes the whole gallery
as parallel url/name arrays; the sheet picks the first not-yet-finished picture after the current
one (wrapping), routes through `MediationManager.showInterstitial` — the same natural break By
Number monetises, frequency rules intact — and opens it with the queue carried along, so the chain
continues picture after picture. *Device: finished the cartoon → sheet → Next → interstitial →
the mandala opened at 0%.*

**2. Grid progress.** `RevealActivity.onPause` now writes `key + "#pct"` alongside the progress
string, and `ColoringsAdapter` shows a violet chip on ColorForge tiles — "42%" playing, **✓**
finished — reading that one int, no region decoding. `New2026Fragment.onResume` rebinds so the
chip updates the moment the player comes back. Paid tabs show nothing (their progress is a
different system).

**3. Admin: one picture per day, in one action.** Select 2+ items → **"Daily schedule N"** in the
bulk bar → pick a start date → item one goes live that day, the next a day later, and so on, in
grid order. The app's date-wise hiding (§3.4) does the rest. Published items go through a new
cheap `POST /api/items/{iid}/schedule` that sets ONLY `publish_ts` — routing them through
`publish` would burn a 40-second full re-export per picture for a one-column change. Unpublished
ones are published WITH their date. *Verified live: schedule 200, a future date put
`timeStamp: 2027-01-01` in the feed, restored after.*

**🐞 Found on the way: `publish()` bypassed the §8.5aw queue.** `approve()` went through
`_enqueue`; `publish()` called `background.add_task(_publish, iid)` directly — so a bulk republish
ran N exports concurrently on the 2-CPU box, exactly what the single worker exists to prevent
(and what `setRNGSeed` depends on). One line.

**4. Favourites.** The ❤️ already worked on New 2026 tiles (`FavoritesManager` keys by fileName),
but FavoritesActivity **built every thumbnail from `Manager.BASE_URL`** — the ionicsolutions host,
a guaranteed 404 for our pictures — and opened everything in the flat `GameActivity`, same 404.
ColorForge names are recognisable (`^\d{8}_`, the §3.9 date prefix, and favourites store only the
name); those now load `border.png` over a white plate via the reveal route — **which ignores its
category segment, resolving by fileName alone**, the thing that makes this possible at all — and
open `RevealActivity`. *Build-verified; the favourites screen itself was not driven on-device.*

**Device-testing recipe that made verification cheap:** seed `reveal_progress.xml` with every
region id but one (from the served `regions.json`), open the picture → it restores to 95% with one
region left → one tap finishes it. No 19-region blind-tap sequence needed.

⚠️ Cosmetic, owner to judge: in the completion sheet the ghost "Done" button renders a touch
washed-out over the scrim; and `celebrate()` bursts at most `MAX_PARTICLES` (70), which reads
fine but is not confetti-dense.

### 8.5bs 🎯 The fill reaches the line now (2026-08-26)

Owner, zoomed deep into the cartoon: *"border na region ma clear color fill thavo joyi"* — his
screenshot showed a jagged grey fringe between the orange fill and the black line.

**Cause: §8.5bp made the ink one drawing, but the REGIONS were still cut from the other one.**
The id-map is built from the model's RAW line (threshold < 165 — a wide, soft band) and
`fill_labels` splits that band between neighbours; the ink the player sees is that band
processed — gamma-pushed, smoothed, filled from the vector contours. Measured on the published
cartoon, **22% of region boundaries sat OUTSIDE the solid ink, median 1.4 px away, worst 3.7** —
so a filled region stopped short of the line and the neighbour's unfilled grey showed through.
The app's seam stroke (§8.5bi) covers 1.2 *screen* px; this mismatch is in *source* px, which at
6x zoom is a dozen screen pixels. No seam width fixes that honestly.

**Fix, in the right place: `_snap_regions_to_ink()`** (reveal_game_builder, after `_finish_ink`).
Every visible pixel within `INK_SNAP_PX = 4` of the ink — and every ink pixel — is reassigned to
the region of its nearest CORE pixel (one farther than that from any ink), so the boundary between
two fills falls on the ink's centreline and both fills reach cleanly under the line. One
`distanceTransformWithLabels` pass, ~50 ms.

Two deliberate refusals inside it:
- **A boundary with no ink near it is not moved** — two colours meeting where nothing is drawn is
  §8.5bj's product question, not this bug. That is why the tiger still *measures* 28% "uncovered":
  its fur is full of colour joins running parallel to nearby strokes that do not separate them.
  Rendered before/after, the actual fringe and the orange speckle-dust on the navy are gone.
- **A region so thin it has no core is protected outright** rather than swallowed by its
  neighbours (the 4 px-wide 2,790 px ribbon from §8.5bo would otherwise vanish here).

**Live: cartoon 22.2% -> 5.68% uncovered** (the residue is legit no-ink joins), median distance
1.4 -> 1.0 px (sub-anti-aliasing). All three items republished; palette integrity re-checked.
Regression test added (`test_region_boundaries_snap_under_the_ink`) — near-ink boundary must sit
ON the ink, far-from-ink boundary must not move. 12/12.

**The lesson, same as §8.5bp:** anything derived from the line must be derived from the line the
player actually sees. The raw model output is an intermediate, not a source of truth.

*(Also hit twice this session: an expired admin token makes the republish loop print nothing and
change nothing — check the response body, not just the loop finishing.)*

### 8.5bt 🖌️ Game feel: sweep-fill, sound, share, a hint economy (2026-08-27)

Owner: *"game ma improve karva jevu su che?"* — assessed against the reference apps (§8.5u),
then he said do all of it. **Built and installed; NOT verified on the device — the phone
PIN-locked overnight and adb cannot get past that.** The owner should play one picture and check
each item below.

**1. Drag-to-fill (the big one).** One finger used to only tap-fill or pan. Now the mode is
decided by where the finger LANDS: on an unrevealed region of the selected colour (exact pixel —
the intent must be unambiguous) the drag **paints**, filling every selected-colour region it
passes over (snap `SWEEP_TOLERANCE_DP = 10`, much tighter than a tap's); anywhere else it pans as
before. A pinch cancels a stroke. Fifteen small spots become one sweep instead of fifteen taps.
⚠️ **Known trade-off:** zoomed deep INSIDE a big unfilled region of the selected colour,
a one-finger drag now paints it rather than panning — pan with two fingers there, which is what
the reference apps require too. If the owner hates it, the dial is `startsOnSelected()`.

**2. Sound + haptics.** The reveal game was completely silent — By Number plays
`SoundUtils.TAP` on every fill and the reveal played nothing. Every fill now: the same TAP
(SoundUtils itself honours the Settings toggle) + `CONTEXT_CLICK` haptic (the system honours its
own setting). `fillFeedback()` in RevealView.

**3. Colour-complete burst.** Finishing a colour used to just silently drop the chip and advance
the selection. Now 14 particles burst over the region that completed it.

**4. Share on the completion sheet.** The finish already saves the PNG; the sheet now has a Share
button that opens the app's existing `ArtworkShareActivity` with it (the screen Favourites
already uses). The save runs on a background thread, so a very fast tap retries once at 600 ms.

**5. Hint economy.** The wand was free and unlimited — worthless as a mechanic. Now
`FREE_HINTS = 3` per picture, then a rewarded ad buys the next (the By Number trade). Premium
(ads off) keeps unlimited hints — that is part of what was paid for. **If no video is available
the hint is GRANTED, not refused** — an ad-network fill error must never lock a player out of
help (§8.5ai's lesson). Uses the existing `MediationManager.showRewardedAd`;
`onRewardNull` also grants.

**6. Diamond completion sheet.** `onLevelFinished()` was still a Toast; it now shows the same
sheet (Done only — diamond has no photo artwork to share and no Next queue yet).

*Build verified clean-compile (javac wiped and rebuilt); installed on CPH2333. The §8.5bq/br
device recipe could not run — lock screen. First manual check: sweep a finger across several
same-colour spots, listen for the tap sound, finish a colour, finish the picture, hit Share,
press the wand four times.*

### 8.5bu 🌐 "Works on this phone, not on that one" — the baked-in server URL (2026-08-27)

Owner: the app is complete on the test phone but another phone says *could not reach the server*.

**The test phone was never proof of anything.** It worked because `base_url_override =
https://srv1919471.hstgr.cloud/` had been written into its SharedPreferences **by hand over adb**
during testing. Every other phone resolves: no override → Firebase Remote Config
`colorforge_base_url` — **the one owner-only step from §8.5ar that was never done** → the baked
default, which was `127.0.0.1:8000` on debug and **empty on release**. So a fresh install had no
server at all.

**Fix: `ColorForgeApi.defaultBaseUrl()` now returns the production URL** for both build types.
The server is a permanently owned VPS with TLS, so baking it is safe — and the resolution order
is unchanged (override → Remote Config → default), so the server can still be moved later without
an app update. `hasExplicitServer()` now means "the resolved host is not a developer loopback"
(it still refuses 127.0.0.1 / localhost / 10.0.2.2, protecting the §8.5az ad-config rule and the
default-tab choice from a host that only answers while `adb reverse` runs).

**Verified by simulating the other phone on this one:** cleared the hand-written override,
launched, and watched the server's own nginx log — `Number/category/` → `gimg/c/Dark Girls`,
all 200, from the baked default. The grid rendered the live catalogue.

⚠️ **The other phone needs this new APK installed** — the URL is compiled in; the build it has
still carries the localhost default. And the Firebase Remote Config step is now optional rather
than blocking, but still worth doing as the moving-the-server lever.

*(Noticed in the log while verifying: the owner has started filling content — a new "Dark Girls"
category with six pictures published 2026-08-27. The §8.5br daily scheduler has material now.)*

### 8.5bv 🔄 OWNER'S CALL — the July-25 original engine is back (2026-08-29)

**The §8.5al backup finally surfaced.** The owner produced
`color_mate_old/colorforge-ai/` — a full copy of the project from **July 25**, i.e. the true
pre-corruption engine with the Jul-25 tuning that git never had (`reveal_game_builder.py`
43,690 bytes — the exact size the zero-filled file had). His instruction: *"use the same
image-processing logic from that folder in both the app and the admin panel. Do not make any
other changes."*

**What was swapped (image-processing ONLY):** `services/reveal_game_builder.py`,
`lineart_region_builder.py`, `ai_lineart_local.py`, `color_paint_builder.py`,
`number_tab_builder.py`, `simplify.py`, `diamond_builder.py` — byte-identical copies from the
backup, locally and on the VPS. **Everything else stayed current**: `app.py`, `db.py`,
`storage.py`, `number_tab.py`, `diamond_tab.py`, `admin.html`, ads, backups, throttle, queue.
The old and current engines share every call signature the server uses (`build_color(folder,
flatten)`, `export_color_bundle`, `simplify(img, level)` + `LEVELS`, `DIA.build/render_*/
to_json/play_page`), so no glue was needed; `_source_kind()` already swallows the missing
`source_kind()` via its try/except and the Detail hint just goes quiet.

**Verified before and after deploy:** locally, the old engine's bundle has the wire format the
app depends on — bbox `[x,y,w,h]` with cx/cy inside (127/127), every region id present in the
id-map (127/127), `original.png` (the route's `.jpg` fallback covers it), old-style
`border.svg` (the app's `BorderVector` signature check rejects it and falls back to the raster,
by design — §8.5bf). On the VPS: a scratch upload through the real API came back
`review, 371 regions / 98 colours` — the July-era output — and was deleted.

**What this deliberately gives up** (the owner chose the old output over it): the Aug engine work
— palette compaction (numbers are sparse again), close-colour merging, region snap-to-ink,
shredded-region retirement, the vector-derived raster border, the JPEG photo (downloads are
~2.1 MB PNGs again), hairlines, determinism (`setRNGSeed`), `source_kind`. **Published items are
untouched** — only new uploads/regenerates use the old logic.

**Follow-up (same day): "a code hju latest che" — he was seeing old PUBLISHED pictures.**
Everything in the app had been generated by the Aug engine before the swap and never
regenerated, so the grid looked unchanged and the swap looked like it had not happened. Two
finishing moves: (1) the admin upload's Simplify select is back to the July form — **default
"Off"**, the §8.5as "Cartoon default" removed — so a default upload processes exactly as July
did; (2) **all 14 published colour items were republished through the queue**, so the served
regions/id-map/border are old-engine output. The phone's `RevealAssetCache` trusts a copy for
24 h — cleared on the test phone; another phone needs its app cache cleared (or a day) to see
the regenerated pictures.

⚠️ **`tests/test_engine.py` now tests the REPLACED engine** — most checks reference functions the
old module does not have, so running it fails by construction. It documents the Aug engine, which
lives in git (`colorforge-gh` commit `729c269`) and in the session scratchpad
(`engine_new_backup/`). That, plus this section, is the rollback path.

### 8.5bw 🚀 "data ava ma time lage che" — delivery made fast around the July engine (2026-08-29)

Owner: loading takes too long. Measured first: **one picture open was ~2.1 MB and ~30 s** on his
connection, and one grid screen ~3.3 MB. Three causes, none of them the VPS (KL, ping 92 ms):

1. The July engine ships the photo as **PNG only** — 1.1-2.2 MB where the Aug bundle's JPEG was
   ~250 KB. New uploads therefore lost the small photo.
2. **Grid tiles loaded the FULL `border.png`** — measured 554 KB *per tile* (§8.5x had flagged
   this "before scaling up"; the catalogue scaled up).
3. The app fetched the five reveal files **sequentially**, so wall time was the SUM of five
   transfers.

**Fixes — delivery only, the July image-processing output is untouched (§8.5bv):**
- `_publish` (server glue, not the engine) now ALSO stores `original.jpg` (q95) and a 420px
  `border_thumb.png` beside the bundle. The reveal route serves the thumb with a server-side
  fallback to the full border, so pre-thumb items can never 404 a tile. All published items
  backfilled.
- `ColorForgeApi.borderThumbUrl()` — the grid loads the 95 KB thumb instead of the 554 KB border.
- `RevealAssetCache.prefetchAll()` — the five files download **concurrently**; a 404 writes a
  `.miss` marker so the known-absent probe (`original.jpg` on old items) never costs a round trip
  again.

**Verified on the device with nginx as the witness:** the grid fetched exactly
`6 × border_thumb.png`, and a fresh picture open fetched all five assets **within the same
second** (previously spread over ~30 s), `original.jpg` (226 KB) not the PNG (1.3 MB).

⚠️ **The backfill trap that cost 20 minutes:** running a script with `.venv/bin/python` on the VPS
does NOT load `storage.env` — `get_store()` silently came up LOCAL and wrote every "backfilled"
file to the VPS disk while S3 (what the route checks) had nothing. `set -a; . ./storage.env`
first, and print `store.kind` before trusting any storage script.

*(Also: the measuring network itself was ~100 KB/s that day — sizes, not routes, were the real
lever. And the owner replaced most of the catalogue mid-session; scripts against "all published
items" must re-list, not reuse an id list from an hour before.)*

### 8.5bx 🔴 Play policy strike "Disruptive Ads" — fixed in code, v14 (2026-10-14)

Google Play Support email (received 2026-10-14 04:14): *"Issue found: Disruptive Ads — we have
determined your app contains code to facilitate Disruptive Ads … Version code 13 … fix by
**October 20, 2026** or your app may face further action."* Status "Further action required".

**Audited the whole ads path before changing anything** (manifest, merged manifest incl. library
receivers/permissions, all ~9,000 lines in `ads/`, every `show*` call site). The manifest is
clean — no overlay permission, no boot/screen/unlock receivers, no ad SDK that draws outside the
app. The problems were all **remote-config-driven template code**, present identically in the
V1 (`AdsManager`) and M1 (`AdsManagerMediation` + `ads/Utils`) paths:

| # | What the code did | Policy clause | Fix |
|---|---|---|---|
| 1 | `showWarningMessage()` — on every launch, if config key `bMsg`/`KEY_MSG` held `[{message,pkg}]`, a **non-cancelable AlertDialog titled "<app> - (Ad)"** whose only button opened **another package's Play Store page and then `finishAffinity()`** | "cannot force a user to click an ad … before they can fully use an app" — the first example in the policy | **Deleted** in both paths; `openHomeScreen()` now continues unconditionally |
| 2 | Double-back exit opened `dialog_exit.xml` with a **native ad** inside (`KEY_IS_EXIT_DIALOG` default **true**) | "ads displayed when the user exits" | `showExitDialog()` in both paths = plain `finishAffinity()`; `MainActivity.handleBackPress` ignores the remote switch |
| 3 | `showBackInterstitial()` — interstitial on **Back** from 9 screens when `ALLOW_BACK` | unexpected interstitial | Both paths: always just `listener.onAdClosed()`; remote switch ignored so it cannot return from config |
| 4 | Splash fallback showed an **InterstitialAd on app load** (`showSplashInterstitial` / `executeSplashInterstitial`) when no splash-open unit was set | AdMob placement policy: no interstitial on app launch | Removed; the launch ad is the **App Open format only** (`startLaunchAd` / `showSplashAppOpen`) |

**Deliberately kept, and why:** the App Open ad on launch and on foreground (`AppOpenManager`,
with the §8.5bc cooldown) — a sanctioned format; the in-game interstitials at natural breaks
(opening a picture, leaving a finished game via `leaveWithAd`), frequency-capped and dismissible;
rewarded ads (user-initiated, with a confirm dialog); banners. The `isOldAppVersion` forced-update
dialog is not an ad and was left alone.

**Also done:** `versionCode 13 → 14` (`app/build.gradle`) — the strike names 13, so the fixed
build must be a new version. `admin.html` lost the now-meaningless "Ad on back press" / "Exit
dialog" toggles and the "Splash interstitial" unit field (deployed to the VPS; `ad_config.py`
keeps the keys for wire compatibility, the app ignores them). Debug build installed and tested:
Home → double back → launcher, no dialog, no crash.

**Release build pre-flight (same day) — two things that would have bitten at upload time:**
- **`assembleRelease` FAILED on `lintVitalRelease`** — 8 fatal `InvalidSetHasFixedSize` errors
  (RecyclerView `wrap_content` in the scroll direction + `setHasFixedSize(true)`). Nobody had built
  a release since those screens were written. Fixed without behaviour change: the three grids
  inside a `SwipeRefreshLayout` (`rv_new`, `rv_diamond`, `fragment_view_pager/recyclerView`) are
  now `match_parent` (SRL measured them EXACTLY anyway — §8.6 M7); the two horizontal palette
  strips (`RevealActivity`, `DiamondActivity`) and the hidden calendar grid simply dropped
  `setHasFixedSize`. *(Stale note: `app/lint-baseline.xml` lists 4 entries no longer in the
  project — harmless.)*
- **There is NO release `signingConfig` in `app/build.gradle`.** The owner signs in Android Studio
  (Build → Generate Signed Bundle) with his upload keystore, or passes
  `-Pandroid.injected.signing.*`. For the pre-flight here the release APK was built with a
  **placeholder** `-PADMOB_APP_ID=ca-app-pub-0000000000000000~0000000000` and the debug keystore,
  installed on CPH2333, and driven: splash → Home → tile tap → interstitial → **GameActivity
  renders** (numbers, palette, banner), **0 FATAL / ClassNotFound / UnsatisfiedLink** — so R8 +
  ProGuard rules + the Sumo native engine are fine in a minified build. Release APK 56.7 MB vs
  78.7 MB debug.
- ⚠️ Noticed: the release build still served ads labelled **"Test Ad"**. In release
  `debugAdUnitId()` is NOT in play, so the unit ids coming from the ad config are either Google's
  test units or this phone is registered as a test device. **Check Admin → Ads → the warnings box**
  before publishing; test unit ids earn nothing.

**Retention + revenue batch, same day ("all kari nakh") — all built, device-verified, in v14:**
- **Coins economy v1** — `util/CoinManager` (prefs `coins`): start 100, **daily login +50**
  (claimed on Home's `onResume`, once per local day, with a chip bounce + toast), **rewarded video
  +30** from the coin sheet, **hint = 20 coins** in both games (`RevealActivity.requestHint` after
  the 3 free ones; `GameActivity`'s hint-ad observer) with the rewarded video only as the
  empty-purse fallback. The header chip (`cm_coins_chip`, `tv_coins`) now shows the balance and
  opens `dialog_coins.xml` (balance · Watch video · Go Premium; premium users see balance only).
  ⚠️ `onRewardNull` grants NOTHING for coins — unlike hints (§8.5bt), a free grant here would be
  farmable. ⚠️ The coin icon inside the chip (`iv_removeAds`) had its own Premium click; both now
  open the sheet.
- **Daily reminder** — `util/DailyReminder`: WorkManager periodic (24h, first at 19:00 local,
  unique `daily_reminder`, KEEP). Silent if the app was opened in the last 6h or POST_NOTIFICATIONS
  is denied; one id (4242) so missed days never stack. Scheduled from `MainActivity.onCreate`.
- **Daily tab is BACK** in the bottom bar (`dailyTabEnabled = true`, visibility line removed) —
  bar is `Number · Daily · Images · Pixel · Saved`. Opens `DailyCalendarActivity` (streak card,
  calendar, date-wise pictures). ⚠️ Seen there: two of the date tiles render blank white — the
  pictures come from `assets/images.json` via `ImageManager` (§3.6#8's `free`-key drop), not from
  ColorForge. Pre-existing, not touched; worth a look before it is promoted.
- **Foreground App-Open cooldown 20s → 60s** (`FULL_SCREEN_AD_COOLDOWN_MS`).
- **Facebook SDKs removed** (`audience-network-sdk`, `facebook-android-sdk`): Firebase V1 has no
  FB app id / unit ids and `network_order` is empty, so they were never used. `FacebookAdsUtils`
  is a no-op stub keeping every signature (fails like a no-fill), the FB native layouts
  (`ads_unified_facebook*.xml`) and manifest meta-data are gone, `AdController`/
  `AdsManagerMediation` lost their `com.facebook.ads` instanceof checks. Verified
  `debugRuntimeClasspath` has no facebook artifact. **Release APK 56.7 → 53.6 MB**; debug size
  barely moves (no shrinking there). Also drops the FB ContentProvider from process start.

**Screen-by-screen design pass (same day, owner: "all screen ni design re-design karvi che").**
Audited every reachable screen by screenshot first. Home, Settings, Premium, By Number grid,
Pixel grid, Saved, Reveal, Diamond were already on the design system — left alone. Redesigned:
- **Daily Challenge** (`activity_daily_calendar.xml` rewrite): edge-to-edge; hero = streak +
  two quiet stat pills (Resets in / Total done) + ONE white CTA "Play today's picture";
  calendar is monochrome now — finished = violet wash + tick (`cm_cal_cell_done`), today = ring,
  selected = heavy ring, future = faded — the per-picture pastel tints are gone; the grid under
  it has a heading naming the selected date + category instead of a "14" badge on every tile,
  and is **capped at `DAILY_PICK_COUNT = 12`** (it sits in a ScrollView, so every tile inflates
  at once — "flowers" was inflating 1,124). 🐞 **`markTodayCompleted()` had no caller — the
  streak could never grow.** `GameActivity.EXTRA_DAILY_DATE` (set by `openGame`) now marks the
  day on finish; `DailyChallengeManager` gained a per-day history (`completed_days`) so the
  calendar can show ticks. `onResume` refreshes streak/ticks.
- **Favourites**, **How to play** (`activity_tools_info.xml`, 40 attribute swaps via script),
  **Welcome** (brand mark + `PaintParticleView` on paper, Quicksand, `cm_btn_primary`),
  **Consent** (paper + one card), **Pixel game** chrome (glass back/done, tool rail on a palette
  strip, matching Reveal/Diamond). New `util/EdgeToEdge.apply(activity, top, bottom)` is the one
  way to go edge-to-edge now — the old `CompatUtils.setupStatusBar(..., WHITE)` calls are gone
  from these screens.
  ⚠️ Two traps: `cm_glass_panel` is **invisible on a white canvas** — the pixel game uses the
  new opaque `cm_glass_panel_strong`; and **`PixelArtActivity` had no manifest theme** (black
  status band) — it now uses `Theme.Game.FullScreen` like Reveal.
- **Daily became a TAB, then got its final look** (owner: "fragment ma j ly lee … gradient
  kadhi ne simple colour … calendar ni design mast"): `fragments/DailyFragment` +
  `fragment_daily.xml` replace the activity screen — `NAV_DAILY = 6` is the 7th entry in the
  nav arrays (the calendar item in the bar), `selectNavItem`/icon switch have its cases, and the
  old loose click-listener block is gone. `DailyCalendarActivity` is still in the tree and the
  manifest but **nothing opens it** — delete when convenient. Hero is a white `cm_card` (flame in
  a violet-wash box, ink streak, lavender stat pills, violet CTA — no gradient). The calendar is
  its own card: numbers centred, **today = solid violet / white number**, finished = wash + ✓,
  past = white, future = ghost (`cm_cal_cell_future`), selected-other-day = 2dp ring, legend
  underneath. Picture list capped at 12 and the ScrollView is a `NestedScrollView` — a plain
  ScrollView measured the nested grid at ONE row (only 2 of 12 tiles showed).
- **Not yet looked at**: the three share screens (`activity_share.xml`,
  `activity_pixels_share.xml` — only reachable after finishing a picture), the By Images editor
  (`activity_editor.xml`, recolor engine) and `ScanProcessActivity`. Next design items if the
  owner wants more.

**🔴 What only the owner can do, before 20 Oct:**
1. Put the real `ADMOB_APP_ID` in `gradle.properties` (release builds refuse to build without it,
   §8.5e) and build the **release** AAB (`./gradlew.bat bundleRelease`).
2. Upload v14 to Play Console (production), then on the policy notice choose **"I've fixed the
   issue"** / submit for re-review — mention that the cross-promo dialog, exit-dialog ad,
   back-press interstitials and launch interstitial were removed.
3. Do NOT turn `dAllowBack` / `dIsExitDialog` back on in any config — the app ignores them now,
   but the intent must stay clear.

**Earlier the same day (UI, owner's requests):**
- **New 2026 + Diamond tabs HIDDEN** (not deleted) — `android:visibility="gone"` on
  `nav_item_new` / `nav_item_diamond` + `colorForgeTabsEnabled = false` in `MainActivity`
  (keeps the default tab on By Number and the two click listeners off). Flip both to restore.
  Fragments, reveal game, backend all untouched.
- **Saved moved from the top bar back to the bottom bar** — `nav_item_saved` is the 6th array
  entry (index `NAV_SAVED = 5`, which already existed); the top-bar `iv_saved_top` FrameLayout is
  `gone` but kept for the binding. Bar is now `Number · Images · Pixel · Saved`.
- **Bottom nav redesigned to the owner's reference** (a Dribbble shot: floating fully-rounded
  white bar, selected tab = soft grey pill hugging icon + label, monochrome): `bg_nav_bar`
  radius 44dp, `bg_nav_pill_selected` = solid `nav_pill #F1EEF8` radius 40dp, pill padding
  16/8dp, labels 10sp; selected = filled icon + `cm_ink` + semibold, unselected = outline +
  `cm_ink_2`. `navColorFor()` (per-tab paint colours) is kept but unused. An intermediate
  "violet gradient pill behind the icon only" variant was built and replaced the same day.

### 8.5by 🖼️ Figma-SVG pipeline, mix into By Number, Daily + Hit image (2026-10-22, branch `claude/svg-daily-hit`)

Owner's new content flow: ChatGPT image (black-outline prompt) → **Figma "Vectorize"** → SVG upload
in the admin → app. Owner's rules: the new pictures **mix into the existing By Number categories,
newest on top**; **Daily** shows them date-wise; one **Hit image** sits on top with an unlock
countdown. Game-style UI redesign: later. iOS: owner wants a **native Swift** app (not Flutter) —
not started; needs a Mac + Xcode.

**Backend**
- `services/svg_builder.py` — SVG → game data with **no AI pass**: paths with luminance < 0.10 are
  the line art (pre-drawn), every other path is a region; slivers < 80 px absorbed; near-identical
  shades merged (Lab ΔE 8, threshold rises until ≤ 150 colours — Figma emits up to ~2,100 shades);
  touching regions of the same group joined. Writes `reveal_master.png`, `original.png`,
  `svg_game.npz/json`.
- `reveal_game_builder._game_data_color()` returns `svg_builder.game_data()` when `svg_game.npz`
  exists — so build_color / export_color_bundle / Number-tab export run **unchanged** for SVG items.
  Photo items never have that file, so the July engine path is untouched (§8.5bv).
- `app.py`: `.svg` uploads (colour dept) → `_process_svg`; regenerate re-runs it. A picture with
  < 15 ink shapes gets a warning in `error` while staying `review` (drawer shows it).
- `GET /index.php/Number/daily/` — all our published colour pictures, newest first.
- `GET /index.php/App/hit` — `{fileName, category, title, unlockAt, now}` or `{}`; `PUT /api/hit`
  (boss) stores `{iid, unlock_ts}` in config. Admin drawer of a published picture → "Make Hit image".
- **Categories must use By Number's names** (`animals, flowers, mandalas, nature, people, other,
  love, kids`) — the mix prepends by category name, so a new name would never appear in By Number.

**App** (`claude/svg-daily-hit`)
- `Manager.RestDataSource.setMixRoot()` — `getImagesByCategory()` fetches our server's list for the
  same category **in parallel** and puts it above the paid list. The paid list never waits; if our
  server is down nothing changes. `mixInList` makes a refresh replace (not duplicate) the mix.
- Tapping a mixed (colorForge) picture → `RevealActivity` (NumberListFragment / ColoringListViewModel).
- `DailyFragment` — the selected date's own pictures first (by `timeStamp`), then the paid picks;
  opens RevealActivity with `EXTRA_DAILY_DATE`, which now marks the day done on finish.
- `util/HitCard` + `include_hit_card.xml` on By Number — dimmed colour preview + padlock + countdown
  on the **server clock**, Play after unlock.

**Outline look (2026-10-22):** every SVG picture comes in two looks the boss picks per picture in
the admin drawer — **black** or **soft** (grey #A3A3A3) outline — both over `start.jpg`, a light grey
sketch of the picture's own colours (`255-(255-L)*0.5`) that the app uses as its blank canvas instead
of white (`RevealLevel`; optional, so photo items still start white). Choice lives in `outline.txt`
in the item folder + the `outline` column; `PUT /api/items/{iid}/outline` re-publishes a published
item. `_publish` stamps `"look"` into `regions.json` because the app cache revalidates on that file
only — without it phones would keep the old border.png. Upload helper: `tools/upload_svgs.py`.

**No Figma needed (2026-10-22, Figma credits ran out):** colour uploads have a *Picture type* select —
"Flat art with black outlines — vectorize here" (default) saves `source.png`, runs
`svg_builder.vectorize()` (**vtracer**, `hierarchical=cutout`, in `requirements.txt`) to `source.svg`, then the
normal SVG import. vtracer puts `translate()` on every `<path>`, so `parse()` now reads per-path translate too
(without it the shapes land at 0,0 and the picture is garbage). Tested end-to-end on the canyon picture:
2194 regions / 142 colours / 10% ink, outline choice + publish OK. "Photo / painting" keeps the July AI path.

**✅ DEPLOYED 2026-10-22, and merged into `quality-fixes` (fast-forward) with versionCode 15 → 16.**
Before deploying, the VPS copies were diffed against `quality-fixes` — identical apart from CRLF, so
nothing live was lost. Uploaded those 6 files **plus `requirements.txt`**, and ran
`.venv/bin/pip install vtracer` first: it is imported lazily, so a server without it still starts but
every "vectorize here" upload fails. Upload the files from `git show` (LF), not a Windows checkout.
Backup of the replaced files + `content.db` in `/root/deploy_backup_20261022_190123/` (rollback = copy
back + restart; the new `outline` column is harmless to the old code). Verified live: all feeds 200,
the existing "Dark Girls" reveal assets 200, `outline` column added, `number_merge` off, admin login OK.
Before that, the branch was smoke-tested locally on a fresh DB: SVG upload → review → both outline
previews → publish → category/gimg/reveal/daily → outline change re-publishes → Hit set and served.
⚠️ Setting a Hit image right after changing an outline returns "publish the picture first" — the item
is briefly `publishing` again. Wait and retry.
**Phone-tested 2026-10-22 (v16 debug, CPH2333)** against a throwaway local server: mix on top of
`animals`, reveal fill + progress, Daily date-first, Hit lock → countdown → unlock → Play, and our
server down → paid content fine, no crash. ⚠️ `hasExplicitServer()` refuses any URL containing
`127.0.0.1`/`localhost`, so a local test needs `http://localtest.me:8011/` (public DNS → 127.0.0.1)
plus `adb reverse`. ⚠️ **Open UX bug:** on the grey `start.jpg` canvas the selected number's grey
highlight is invisible at fit zoom — a player cannot tell where to tap.

**The SVG set arrived as `Colour Mate/Colour by Number own/`** (untracked, 119 MB — keep it out of
git): `pics/*.svg` (53 of the manifest's 56; `26_2`, `30_2`, `26_2322` missing), `thumbs/`, and an
`index.html` web player. Those SVGs put each colour in **`data-c` with no `fill`** — `parse()` used to
default to black, so every shape became ink and the import failed. Fixed (falls back to `data-c`);
all 53 import, 5-47 s each, the same 6 under 3% ink as listed in the owner's PDF. `upload_svgs.py`
gained `--dir=` and `--only=`. Deployed; 3 uploaded to the live admin with `--no-publish` (Moonlit
Cat, Painted Peonies, Sunset Canyon) — in `review`, not yet in the app. Run uploads ON the VPS
(`/root/svg_upload/`, credentials read from the password file there) so the password never leaves it.

### 8.5bz 🪙 First-launch speed + coin economy + Hit coins + coin packs + referral (2026-10-23)

Owner: first open is slow and lags after the age screen; code must be lean; and a coin economy for
players who will not subscribe. Decided numbers (owner picked the recommended set — permanent):
**premium picture 150 · Hit instant 300 · reward ad +30 · finished picture +20 (first finish only) ·
packs 500/$0.99 1,200/$1.99 3,500/$4.99 8,000/$9.99 · referral: referrer +200, friend +100 welcome,
referrer gets 10% of coins the friend earns finishing pictures.**

**Speed (no device to profile — fixed what the code showed):**
- `MainActivity.loadCategories()` downloaded **all 7 paid category lists** (animals alone 1,600+ items)
  and parsed them **on the main thread** on every launch, to write counters **nothing reads**. Deleted,
  with `model/CategoryModel` and `util/ColourColourArchPreference`.
- First run was 5 screens (Splash → Age → Premium → Main → Welcome) plus a notification dialog.
  Agreeing on the age screen now marks Welcome seen; the notification prompt waits for launch 2.
- 🐞 The consent run never counted as a launch, so the paywall showed on launch 1 **and** 2 — fixed.
- By Number's daily list is built on a background thread (it sorts/parses dates for hundreds of items).

**Coins (app, all in `util/`):** `CoinManager` (+ `rewardCompletion`, `isUnlocked/markUnlocked`),
`PictureUnlock` (gate + tile badge "🪙 150" / "▶ Ad"; premium subscribers skip it), `CoinSheet`
(the one purse sheet for Home, locked pictures and the Hit card — replaced ~60 lines in MainActivity),
`CoinStore` (its own BillingClient for consumables: consume first, then credit, token remembered so a
purchase can never pay twice; unconsumed packs recovered on connect), `Referral` (Android-ID keyed
POSTs). Tier comes from the admin's existing **Tier** control (free / reward / premium) — now in the
feed rows (`number_tab._row`, which both feeds share).
**Hit card:** before unlock = countdown; from unlock to +24 h = "Play now · 300 🪙" or wait;
after 24 h (or premium, or paid once) = free.

**Server:** `referral.py` (table `referrals`, created on first use) + `POST /index.php/App/ref/{register|redeem|earned|claim}`.
"New device" = Android ID first seen within 3 days and never redeemed; own code refused; one report
caps at 50 coins. **Owner must create the 4 in-app products in Play Console** (ids `coins_500`,
`coins_1200`, `coins_3500`, `coins_8000`) or the pack buttons show "…".
Deploy adds `referral.py` + `number_tab.py` (and `app.py`, `services/svg_builder.py` from the
thumbnail change).

### 8.5ca 🔒 Premium is never revoked by a billing error; end-of-plan notice; support email (2026-10-23)

Owner: old premium users must keep premium through the update (bad reviews otherwise), monthly/yearly
subscribers should see a notice 3-4 days before the plan ends, support email = admin@gyandatabit.com.
- `IAPManager.queryPurchasesForType`: a non-OK billing result sets `queryFailed` and the stored
  `KEY_IS_PREMIUM` is **left as is** — only two OK answers from Play may change it. Before, a failed
  query counted as "no premium" and switched ads back on for paying users.
- Any purchased **subscription** = premium, and any purchased one-time product that is **not a coin
  pack** = premium — so a product id renamed since an old purchase still keeps premium.
- `util/PremiumExpiry`: remembers product / purchaseTime / isAutoRenewing from the SUBS query,
  computes the current period end (monthly or yearly steps from purchaseTime), and on Home's
  `onResume` shows once a day, inside the last 4 days: "renews in N days" or "ends in N days —
  Keep Premium" (opens the Play subscription page). `periodEnd()` is the dial; `WARN_DAYS = 4`.
- `SettingsActivity.sendFeedback` → `admin@gyandatabit.com` (was a broken `…gmail.com` without `@`).
Not changed: the SKUs `colour_mate_monthly` / `colour_mate_yearly` — same ids as the live build.

### 8.5cb 🖼️ Admin: preview = the app's look, outline on upload + in bulk, N-per-day schedule (2026-10-23)

Owner: *"grey valu add kari devi, preview exact nathi aavtu, bulk ma daroj 50 image jay e rite."*
- **Review page was wrong for SVG pictures**: `play_color.html` always started on a WHITE page with
  whatever border existed at import, while the app opens on the grey `start.jpg` sketch with the
  chosen outline. `_PLAY_COLOR_HTML` now takes `start` (a JPEG data-URI, empty for photo items) and
  paints the canvas from it (`blank()`, also used by Reset). `choose_outline` rebuilds the page in
  the queue (`_rebuild_preview`) and `_publish` rebuilds it for SVG items — so Review always shows
  the look the app gets. Verified headless: grey sketch + soft outline rendered.
  *(Still different on purpose: the web page prints every number; the app shows numbers only when
  zoomed in, §8.5w.)*
- **Outline at upload** (`#upOut` → form field `outline` → `set_outline` before processing) and
  **in bulk** (Library bulk bar: "Black outline N" / "Soft outline N" on selected SVG items,
  sequential PUTs; published ones re-publish).
- **Bulk "Daily schedule" takes pictures-per-day** (modal input, default 50): the first N go live on
  the start date, the next N a day later, grid order. `confirmModal` gained `inputValue`.
Deploy: `app.py`, `admin.html`, `services/reveal_game_builder.py` (same list as §8.5by/bz).

### 8.5bz ✒️ SVG pictures get their own VECTOR engine (2026-10-23)

Owner, on a zoomed SVG picture: soft white-glow borders, pixels, grey-on-grey — *"svg nu apde alag
color fill engine banavu pade … png and svg both separate engine."* Cause: `svg_builder` rasterised
everything to 1254 px (id_map + border.png + start.jpg), so the SVG's crisp line was thrown away on
the server. The owner's own web player (`Colour by Number own/index.html`) was read as the spec: each
`<path>` is a part (`data-g` group, `data-c` colour, `data-t=1` = pre-filled ink), drawn as vector.

**Backend** — `svg_builder.write_vector()` → `vector.json.gz`:
`{"v":1,"w","h","s":[[regionId,"rrggbb",[x0,y0,dx,dy…]…]…]}`, coords in tenths of a px on the 1254
canvas, `approxPolyDP` eps 0.25, regionId 0 = ink, **same ids as regions.json / id_map** (each shape
→ the region holding most of its visible pixels; hidden shapes dropped). Written by `prepare()`, and
by `_publish` for SVG items that predate it. Published to `images/{iid}/vector.json.gz`, served by the
reveal route; `regions.json` gets `"vec":1` so phones' caches refetch. Measured: biggest picture
(Mushroom Forest, 9,640 shapes) 361 KB gz; region agreement vs id_map 95.7–99.6%, and 93–95% of the
disagreeing pixels sit within 2 px of a region edge (interior mismatch 0.06–0.31%).
`_ink_flags()` is now shared by `prepare()` and `write_vector()` so both classify ink identically.

**App** — `model/reveal/VectorArt` (parses the gz; checks the magic bytes, since a server may or may
not undo the gzip in transit). `RevealLevel.vector` + `.look`; `RevealView` draws vector when present,
the old raster path otherwise (photo pictures unchanged):
- shapes drawn **in the SVG's own order** — the first build drew all colours then all ink and put
  black discs over the moon and rose centres (dark shapes sit UNDER light ones in these files);
- unfilled = grey tone of the shape's own colour, filled = its true colour, ink = black/soft per look;
- picture rendered into a screen-sized bitmap on a worker thread when the camera settles (80 ms),
  moved with the fingers in between; a fill re-draws only its region's box, in order, clipped to the
  spreading circle; fills that land mid-render are re-applied on arrival;
- selected number blinks **violet (#7A5CFF)** — a tint of the colour itself was measured invisible
  (0 coloured pixels for a dark-green number on the grey sketch); the blink redraws every frame, so
  the settle timer only restarts when the camera actually moved, or it would never fire;
- finished artwork saved from the vector (`VectorArt.renderFinished`).

✅ **Verified on the device against a local test server** (2 real SVGs): correct z-order, crisp at
hint zoom (~2.4x+), tap fills with true colour, violet blink visible, progress saved and restored.
**Not yet on the VPS** — needs deploy + republishing every published SVG item to produce the vector.

### 8.5cc 🧱 Shared C++ game core for Android + iOS (2026-10-24, branch `2026-10-24-cpp-core`)

Owner wants the app on iOS too. Decided: native Java Android stays, a native **Swift** iOS app
later, and the **game rules written once in C++** both link. Step 1 (this branch) = the reveal game.

- **`shared/cmcore/`** (repo root, outside the Android project): `json.*` (tiny reader, no deps),
  `reveal_game.*` (`cm::RevealGame`: regions.json, tap target = exact pixel else nearest unfilled
  of the selected number within tolerance, reveal, per-number counts, percent with Java's rounding,
  hint = smallest unfilled, `encodeProgress`/`restoreProgress` — same "12,40,7" format as before,
  so old saves restore), `vector_art.*` (streaming parser for `vector.json.gz` → flat arrays; gzip
  via zlib, decided by magic bytes). C++17 + zlib only. **Drawing stays per platform.**
- The id map is NOT copied into the core — calls get a pinned view of Java's `char[]`
  (`GetPrimitiveArrayCritical`), so no extra 3 MB.
- **Android**: compiled into the existing `libcolorbynumber.so` (`app/src/main/cpp/CMakeLists.txt`
  does `add_subdirectory(shared/cmcore)` — listing the sources directly broke the Windows build on
  path length). Bridge `cpp/reveal_core_jni.cpp` ↔ `model/reveal/RevealCore.java`. `RevealLevel`
  now builds its Regions from the core and delegates counts/progress/save/restore/tap/hint;
  `RevealView.findTarget`/`nearestRegion` and `RevealActivity.firstUnfilledRegion` are gone;
  `VectorArt.parse` uses the core and only builds `Path`s. `Region.revealed` is kept as a mirror
  for the renderer. ⚠️ **The Android build now needs the full repo tree** (the `main` branch,
  which is the Android project alone, would not have `shared/`).
- **Tests**: `shared/cmcore/tests/core_test.cpp` — synthetic checks + optional real
  `regions.json`/`vector.json.gz`. CI `.github/workflows/cmcore-tests.yml` (Linux + macOS,
  ASan/UBSan). Locally there is no host compiler: build it with the NDK clang
  (`--target=aarch64-linux-android24 ... -lz -static-libstdc++`), `adb push`, run in `adb shell`.
  Passed on CPH2333 incl. a real SVG picture (549 regions, 1111 shapes).
- ✅ **App verified on the emulator** against a throwaway local server (`C:/cmtest`, port 8011,
  `localtest.me` + `adb reverse`): Moonlit Cat vector picture draws, hint zooms to the smallest
  region, an off-target tap still fills it, count 4→3, progress saved as the region id and restored.
- 🐞 **Found on the way, fixed: Premium users got a BLANK reveal/diamond game.** With ads off the
  ad strip is `GONE`; the palette was `layout_above` that strip, so RelativeLayout put the palette
  at the top and squeezed the board to 0 px. `layout_alignWithParentIfMissing="true"` added to the
  six ad-anchored layouts (reveal, diamond, game, editor, favorites, pixels_by_number) — no change
  when ads show.
- Next: Diamond logic into the core; then the iOS SwiftUI app (needs a Mac + Xcode).

### 8.5cd 👥 Multiplayer: Challenge, Race, Co-op (2026-10-24, branch `2026-10-24-multiplayer`)

Owner asked for all three modes. Everything is reached ONLY through a 6-letter code (alphabet without
0/O/1/I) — no stranger matching, no chat (moderation + the 18+ pictures). Own server, not Firebase.

- **Backend `multiplayer.py`** + routes in `app.py` (on `number_router`):
  `POST /index.php/App/mp/challenge` {fileName,category,device,name,ms} → {code};
  `POST …/mp/challenge/{code}/result` → {rank, beat, board} (keeps each device's BEST time);
  `POST …/mp/room` {mode race|coop,…} → {code} (async route — rooms only touched on the event loop);
  `GET …/mp/{code}` → {kind, fileName, category, …}; `WS …/mp/{code}/ws?device=&name=`;
  `GET /j/{code}` = invite page whose button opens `colourmate://j/CODE` (Play Store fallback).
  Challenges live in DB tables `challenges` / `challenge_results` (created lazily). **Rooms are in
  memory** (single uvicorn process; a restart drops live rooms — acceptable). Max 4 players, idle
  rooms swept after 15 min. Race start = server clock + 3 s countdown, and the race time is measured
  by the SERVER, not the phone. Challenge times ARE client-reported (casual; clamped 5 s..24 h).
  Close codes: 4404 no room, 4409 full, 4400 bad device, 4000 same phone reconnected.
  Tests: `tests/test_multiplayer.py` (TestClient; needs `httpx2` — run it in a COPY of the backend,
  since `db.DB_PATH` is next to the code). 5/5 pass.
- **App**: `util/Multiplayer` (HTTP + OkHttp WebSocket `Room` with auto-reconnect),
  `activity/RevealMultiplayer` (lobby card, status pill, timer, results text),
  `activity/JoinActivity` (translucent; handles `colourmate://j/CODE` and the Home 👥 "Join a friend"
  button; asks the player's name once), `RevealView.setFillEnabled / revealSilently / revealByFriend`
  + `Listener.onRegionFilled`, `RevealActivity.forMultiplayer` (fresh board, never touches solo
  progress; solo play time now saved as `key#ms` so a challenge carries the real time). Tool rail
  👥 → Race / Colour together; finish sheet → "Challenge a friend ⏱".
- 🔴 **Deploy needs nginx WebSocket headers** on the VPS (`proxy_http_version 1.1;
  proxy_set_header Upgrade $http_upgrade; proxy_set_header Connection "upgrade";`) or Race/Co-op
  fail to connect over HTTPS. Challenge works without it.
- ⚠️ **NOT yet tested on a device**: the app builds, but the emulator could not start (PC had <1 GB
  free RAM) and the phone was in use by another session. Test plan: `C:/Users/User/cmci/bot.py`
  plays the friend over WebSocket (`python bot.py coop CODE fill 20`).

### 8.5ce 🏠 The old ionicsolutions catalogue moves to our server (2026-10-24, branch `2026-10-24-own-domain`)

**Owner, correcting §3.1:** *"e maru j che, e domain mare re-new nathi karvu"* — ionicsolutions.in
is HIS domain; he is retiring it and wants everything on one domain (our VPS + S3).

- **Inventory** (`tools/legacy_inventory.py`): 9 categories, **10,720 pictures** (daily 1,847 ·
  other 1,793 · animals 1,614 · people 1,532 · flowers 1,124 · mandalas 1,090 · nature 739 · love 736
  · kids 310), 3 files each (`thumb/{cat}/t/{f}.png`, `{f}.png`, `{f}.colored.png`), ~1.5 MB per
  picture → **~16-18 GB**, plus 5 mp3s (~45 MB, Settings music). Newest `timeStamp` is 2024-04-30.
  Some files 404 on the old server itself (the app already shows those tiles broken); the mirror
  leaves such pictures out of the lists.
- **`legacy.py`** serves the SAME paths, mounted at **`/ColoringBook/`** (registered before the
  `number_router` copy at that prefix, so category/gimg/thumb resolve to the old catalogue while
  App/ads, mp, … still work there). Rows are stored as the raw JSON the old server sent (table
  `legacy_items`, `hidden` flag), whole list per category, `next: ""`. Images/audio from storage key
  `legacy/thumb/…`, `legacy/audios/…` (S3 → 302). Category lookup is case-insensitive (`DAILY`).
- **`tools/legacy_mirror.py`** — run ON THE VPS with `storage.env` loaded; resumable
  (`data/legacy_done.txt`), 8 threads, rewrites the DB lists from the old server's order each run.
  `--limit=N` for a trial. Trial locally: 27 pictures + 5 mp3s, all endpoints 200.
- **Admin → "By Number (old)"**: category chips with counts, search, Hide / Show in app
  (`/api/legacy*`, boss only). Nothing is deleted.
- **App**: every ionicsolutions URL in `cpp/colorbynumber.cpp` now points at
  `https://srv1919471.hstgr.cloud/` (BASE_URL = `…/ColoringBook/`, music likewise). Builds; **not
  device-tested** (no device free that day).
- 🔴 **Order matters:** deploy backend → run the full mirror → only then ship the app. Until the
  mirror runs, `/ColoringBook/` on production serves an empty catalogue.
- 🔴 **Installed apps (v16 and older) still call ionicsolutions.in** — they break the day the domain
  lapses. Keep it alive until most users have updated, and/or point its DNS at the VPS before it
  expires (nginx `server_name` + certbot), and use the forced-update dialog.
- 💸 Serving ~17 GB of images from S3 means **S3 egress is billed per GB downloaded** — the old host
  carried that before. Watch the AWS bill; CloudFront or serving from the VPS disk are the levers.

### 8.5cf 🧩 Own Pixel + Colour Book sections (2026-10-24, branch `2026-10-24-pixel-book`)

Owner wanted the Pixel (fungames) and Colour Book (Recolor) content copied to our server too. **Not
done, deliberately:** those are other companies' catalogues with no licence (unlike ionicsolutions,
which is his own domain — §8.5ce). Instead, separate admin pages for OUR OWN content in those formats:

- **Pixel** — `services/pixel_builder.py` turns any picture into the format the app's Pixel engine
  plays (`OpenGL/GroupTexture`: one PNG pixel = one cell, each distinct ARGB = one number,
  value 0 = empty cell). Colours are chosen on a ~512 px work copy, then each cell takes the
  majority colour of its block (shrinking straight to the grid invented in-between shades: a
  2-colour drawing came back with 6 numbers; now 2). Tiny/close clusters fold into bigger ones;
  a plain backdrop becomes empty cells. `pixel_tab.py` stores `pixel/images/{id}.png` +
  `pixel/preview/{id}.png` (ids `cm_YYYYMMDD_slug`, never colliding with old ids) and serves
  **`/pixel/images2.json`** + **`/pixel/images/{id}.png`** — the exact shape `ContentProvider`
  reads, so switching the app = pointing `GetContentBaseUrl()` at `{host}/pixel/`. **Not switched**:
  that would replace the whole current Pixel catalogue; do it once there is enough own content.
- **Colour Book** — the Recolor engine needs its own binary index files we cannot produce, so this
  is our own shelf: `book_tab.py` stores line-art pages (`book/art|thumb/{id}.png`) and serves
  `/book/feed.json`. **App mode built 2026-10-24 (not device-tested):** By Images tab shows a
  "✨ New: Colour Book" card only when the feed has pages (the pager is re-anchored under it in code —
  anchoring a RelativeLayout view to a GONE sibling pulls it to the top, the §8.5cc bug again) →
  `BookGalleryActivity` (category chips + grid, progress chip) → `BookActivity` + `model/book/BookView`:
  the page (max 1600 px) is split once into shapes (light pixels between dark line art, luma < 160 =
  wall), a tap colours a whole shape with the chosen palette colour (taps on the line snap to the
  nearest shape), line art drawn over with MULTIPLY, undo (40), progress `book_progress` prefs as
  `shape:colour`, Done saves to My Works. Page PNGs cached in `cacheDir/book/`.
- Admin: "Pixel" (upload + size/colours/background/free/date, preview grid, hide/delete) and
  "Colour Book" (multi-upload into a category, hide/delete). Upload = any logged-in user;
  hide/delete = boss. Verified on a local server: upload → feed JSON → PNG 48×48 with exactly the
  chosen colours; hide drops it from `images2.json`; book feed/art/thumb 200; no token → 401.

### 8.5cg 🚀 Deployed: multiplayer + own-domain catalogue + Pixel/Book (2026-10-24)

- Backend `2026-10-24-pixel-book` @ `0071e1a` deployed with the **Deploy backend** workflow
  (`gh workflow run deploy-backend.yml --ref <branch>`); `/opt/colorforge/DEPLOYED` says so. All
  existing feeds still 200 (Number, daily, ads, hit, Diamond, admin).
- **nginx**: `location /index.php/App/mp/` with `Upgrade`/`Connection "upgrade"` added to
  `/etc/nginx/sites-enabled/colorforge` (backup `/root/nginx_colorforge_*.bak`). Verified a live
  co-op room over `wss://` — two clients, a fill relayed.
- **Mirror** of the ionicsolutions catalogue started on the VPS (`/root/legacy_mirror.log`,
  32,086 files, ~6 files/s ≈ 1.5 h, resumable — just run it again if it stops). Trial first: S3
  `legacy/...` objects served through `/ColoringBook/...` 302s, audio URLs come back `https://`.
- SSH from the PC: `ssh -i ~/.ssh/colorforge_vps root@187.77.141.63` (plain `ssh root@…` is refused).

### 8.5ch 🤖 Own line-art AI: SDXL + ControlNet + trained LoRA on RunPod (2026-10-25, branch `2026-10-25-lineart-ai`)

Owner wants his own model that turns any picture into a clean border (colouring-page) image.
- **No GPU on the VPS** (2 vCPU EPYC, 8 GB). PC has a GTX 1650 4 GB. GPU work runs on **RunPod**;
  the API key lives ONLY in `~/.runpod_key` on the PC (never in the repo — the repo is public).
  The key was once pasted in chat: the owner should rotate it in the RunPod console.
  Helper `C:/Users/User/cmci/pod.py` (create/wait/stop/status); SSH key `~/.ssh/runpod_ed25519`.
  **Always terminate pods** (`python pod.py stop <id>`), they bill by the hour.
- `tools/lineart_ai/proto_sdxl.py` — SDXL base + `xinsir/controlnet-canny-sdxl-1.0` + sdxl-vae-fp16-fix,
  ~5 s/image on a 4090, plus `hollow()` post-process (solid black areas → 3 px outline, specks
  dropped) which was the single biggest improvement. Pins: diffusers 0.31.0 + transformers 4.46.3
  (newer transformers breaks diffusers 0.31 imports).
- `prep_data.py` → 1,437 of the owner's own By Number line-art pages (160/category, from
  `/ColoringBook/`), `train_lora.sh` → LoRA rank 16, 1200 steps, 1024 px (A40, ~1 h 45 m, ~$1).
  ⚠️ Do NOT pass `--variant fp16` with a separate VAE (the fp16-fix VAE has no fp16 variant file).
  Weights: `cmlineart_v1.safetensors` (93 MB) on the VPS in `/opt/colorforge/models/lineart_lora/`
  and on the PC in `C:/Users/User/colourmate_models/` — not in git. Trigger word `cmlineart`.
- **Result (eval_lora.py, 8 test images): a MODEST gain, not a leap.** Mandala and lion bolder and
  cleaner, snow globe simpler; Krishna about the same; busy photos still carry texture noise because
  ControlNet canny copies the photo's texture. Next levers, cheapest first: flatten/posterise the
  photo before canny; higher canny thresholds; a ControlNet trained on the owner's colour→line pairs.
- **Control-map test (2026-10-25, `control.py` + `eval_controls.py`, sheet = original | A | C | D):**
  `A_raw` = bilateral + canny 70/170; `C_flatter` = mean-shift + 9-colour k-means + canny 50/150,
  pieces < 60 px dropped; `D_sketch` = the server's own `lineart_sk_model` output as the control.
  **`C_flatter` wins on most pictures** (kitchen, snow globe, Jagannath, woman, lion clean and
  closed); `A_raw` keeps more mandala detail; **`D_sketch` is unreliable** (kitchen fell apart into
  dashes, Krishna lost half the face). **Not solved by any variant:** busy photos (Krishna, the
  bead-texture cat) — that needs flat source art or a trained colour→line ControlNet.
- **"Line first, colour second" test (2026-10-25, `redraw_test.py`, A100 80GB):** the picture is
  REDRAWN by **Qwen-Image-Edit** (Apache-2.0, commercial OK; FLUX Kontext dev is non-commercial —
  avoid) with a colouring-page prompt. **The line art is the breakthrough** — clean, closed, bold
  outer / thin inner, solid-black pupils, the look of the owner's reference apps, on all 8 pictures
  incl. Krishna and the bead-cat that every canny variant failed. Caveat: it REDRAWS, so it drops
  backgrounds and restyles (Krishna got cuter, Jagannath simplified) — not aligned to the photo.
  **The colour step failed**: SDXL canny-ControlNet img2img from the photo gave washed, blurry
  colour that ignores the lines (woman, Jagannath worst). Next: colour the LINE ART with
  Qwen-Image-Edit too (edit models keep structure), then put the lines back on top.
  Needs pins `torch 2.6 (cu124)`, `diffusers>=0.35.1`, `transformers>=4.51.3,<5`; ~75 GB disk.
- **`line_cleanup.py` (CPU, no cost)** makes the redrawn line art a colour-by-number board: 2048 px
  with smoothed edges; a solid mass is opened to a 6 px outline only when its THICK CORE (ink
  > 28 px wide) covers > 0.4% of the canvas — Krishna's hair opens, pupils / a lion's nose stay
  solid (judging by the whole ink piece was wrong: the pupil is joined to the eye outline, so the
  eyes got hollowed too); crumbs thinner than 3.5 px become ink, specks < 60 px dropped.
  ⚠️ Inking every cell under r 7 turned beaded jewellery into black blotches — those small cells
  (Krishna 258, Jagannath 177) must instead be MERGED into a neighbouring region by the game builder.
  Result: Krishna 696 → 404 cells, hair colourable, eyes intact.
- **`line_style.py` (CPU) — the reference look.** Measured on the owner's reference apps at 1272 px:
  strokes 2 / 4 / 9–26 px (three weights), anti-aliased, thousands of open detail strokes; ours was
  one weight (6–10 px). Every skeleton pixel is classed by what it separates (nearest-region map,
  min/max across the stroke): background edge → silhouette r 9, two regions > 0.4% → major r 6,
  other boundary → minor r 3.2, separates nothing → detail capped r 1.6 keeping its own taper; then
  redrawn at 2x with each pixel taking its nearest skeleton pixel's half-width. Two fixes that were
  needed: widths eased along the stroke (σ 6) — a hard class change drew a knot; skeleton spurs
  < 8 px pruned (prune then regrow from surviving ends) — they drew thorns on bumpy edges.
  Pipeline so far: Qwen redraw (GPU) → `line_cleanup` → `line_style` → (next) vectorize + game.
- Spend so far ~$5.70 of $15 (balance $9.31). The A100 run cost **$4.06**, double the estimate:
  the ~58 GB model download and a hung ssh session kept the pod up longer. Download weights once to
  a RunPod network volume before the next GPU run.

### 8.5cj 🎨 Original pictures from our own GPU — `gen_original.py` (2026-10-25/27)

Owner asked to copy another app's picture ("Soft Aesthetic Color") and remove its logo — **refused**
(their artwork, and removing their branding risks a Play copyright strike); offered and built
original pictures on the same THEME instead. Pipeline: SDXL + `cmlineart` LoRA draws the line art
from text → SDXL + canny ControlNet colours that line art → `line_cleanup` + `line_style` →
`inject_local.py` publishes it on a local test backend (`ai_lineart.png` = our line, so the engine
skips its own model; `COLOR_K` lowered for that picture only — the server default stays 100, §8.5v).
- Run 1 (ControlNet 1.0 whole run): colour follows the lines but is washed/blurry; line art carries
  grey shading → black blotches after a 128 cut. Run 2 (ControlNet 0.8, `control_guidance_end` 0.7,
  "vibrant/crisp" prompt): vivid colour but it **drifts off the lines** (hair). Neither alone is right.
- What works today: run-1 picture (aligned) + CLAHE/saturation/unsharp in post (vivid) + line cut at
  **`INK_CUT=95`** (new env knob in `line_cleanup.py`; drops the grey shading, keeps strokes).
  Published locally as `20261027_fox_fairy_bright`: 36 colours, 942 regions (416 under r 8 — the
  line art has too many small cells; next lever).
- **`outline_only.py`** — for art that already has a drawn outline (coloured-pencil cottages, 2026-10-27):
  grey < 60 → cleanup → style, no AI, ~1 min/picture on the PC, clean coloring pages. `flat_to_lines`
  on the same pictures was unusable (a line along every pencil shade); an HSV saturation gate drops
  the whole outline because it is dark brown, not black. Lighter inner lines (stone joints, shingles)
  are lost at 60 — raise to ~75 if those matter more than the texture noise they bring.
- `cells_to_curves` on a SHADED painting gives the contour-map look (lines across the face) — only
  use it on flat art.
- Cost: run 1 $0.22, run 2 $0.05 (LoRA staged in S3 via the VPS, presigned URL, deleted after).
  Balance $9.03. Not yet played in the app: emulator crashes with <1 GB free RAM, phone was locked.

### 8.5ck 🤖 Own colour→line model (`train_c2l.py`) + GPU work from the phone (2026-10-27)

Owner, with 3 reference pairs (pencil-coloured cottage → its clean line art): *"model ne improve
karvu pade"* — threshold tricks cannot reach that (hysteresis brought the tile lines back WITH
pencil dashes). So: **train our own colour→line network**, with training pairs we make ourselves.
- `make_pairs.py` — our own line-art pages (prep_data.py, 60/category = 539) coloured by SDXL +
  canny ControlNet in 5 styles (pencil / painting / flat / watercolour / fantasy), the page's own
  line drawn back on top at random strength → (colour, exact line) pairs. 450 pairs ≈ 50 min on a
  4090. Checked: aligned and varied.
- `train_c2l.py` — 5-level U-Net (32→384 ch), 512 crops with scale/flip/colour jitter, BCE
  (pos_weight 3) + dice, 6000 steps ≈ 1 h. `infer_c2l.py` runs it on CPU at 1254 px (fully conv).
- 🐞 First run burned ~$1.1 for nothing: the pod could not reach the VPS at that moment (prep_data
  got a connection error), the run script had no `set -e`, so training ran on 0 pairs and the pod
  idled. Now: pages are prepared ON the VPS, tarred, staged through a presigned S3 URL (same trick as
  the LoRA), and every job script is `set -euo pipefail`.
- **GPU jobs from the phone — `.github/workflows/gpu-job.yml`** ("GPU job" → Run workflow →
  `train_c2l` / `infer_c2l` / `gen_original`, optional `args` like `PAIRS=450 STEPS=6000`). It rents
  the pod (`tools/lineart_ai/runpod.py`, key from the secret), stages inputs from the VPS through S3,
  copies `tools/lineart_ai/*.py` + `jobs/<job>.sh` + `tools/lineart_ai/test/*` to the pod, streams
  the log, pulls `/workspace/out` as the run artifact, and **terminates the pod in an `always()`
  step** (also deletes the tmp S3 objects). Secrets added: `RUNPOD_API_KEY`, `RUNPOD_SSH_KEY`,
  `RUNPOD_SSH_PUBKEY`. `infer_c2l` expects the model at `/opt/colorforge/models/c2l/c2l.pt` on the
  VPS — copy a trained `c2l.pt` there once. Workflow files must also be on `quality-fixes` to show
  "Run workflow".
- **✅ RESULT (c2l_v1, 450 pairs / 6000 steps):** on the owner's 3 pencil-coloured cottages the
  output is **close to his hand-made reference** — stone joints, shingles, leaf veins, window bars
  all present, clean closed strokes, ink 18% vs reference 18–19%. First real win on this problem.
  **It is NOT a photo model**: on photographs/paintings (Krishna, lion, mandala) it falls apart —
  the training pairs are all outlined art. For photos keep the Qwen-redraw route (§8.5ch).
  Weights: `C:/Users/User/colourmate_models/c2l_v1.pt` and VPS `/opt/colorforge/models/c2l/c2l.pt`
  (25 MB, not in git). Inference is CPU-fine (fully conv U-Net at 1254 px).
- 🔴 **Cost lesson, the expensive kind:** the PC "watcher" loop finished at 17:06 UTC but its
  notification only reached the session on the next user turn, ~6 h later — the pod idled the whole
  time: **~$5.30 wasted**, balance $7.91 → **$1.54**. **Never again start a pod from the PC.** Use
  the `GPU job` workflow (terminates in `always()`), or at minimum give a PC-started pod a server-
  side deadline. RunPod needs a top-up before the next GPU run.
- **Wired into the admin (2026-10-28):** upload → Picture type → **"Coloured outline art (pencil /
  cartoon) — our own line model"** (form `vectorize=c2l`) → `app._process_c2l`: cap to 1254 px,
  `services/c2l_lineart.generate()` writes `ai_lineart.png` (CPU, ~5 s), then the normal
  `R.build_color` (which uses an existing `ai_lineart.png`), `source_kind="c2l"`. Returns 503 if the
  model file is missing. Verified locally on a cottage: 4.6 s model + 40 s engine, 908 regions /
  99 colours, border = the model's line. The weight must exist on the server at
  `<backend>/models/c2l/c2l.pt` (VPS: `/opt/colorforge/models/c2l/c2l.pt`, already copied).
  Deploy = Actions → Deploy backend on branch `2026-10-25-reveal-replay` (the auto-mode guard blocks
  a deploy from the PC session; from the phone it is one tap).

### 8.5cl 🧹 c2l dirt filter — dots measured, then removed (2026-10-29)

Owner: the model's border has many small extra dots. **Measured first** on the three pencil
cottages (`c2l_lineart.predict` at 1254 px): 1,486 / 2,417 / 1,005 ink pieces, of which only
**34 / 17 / 20 are 50 px or wider** and those hold 95-97% of the ink. The rest are pencil-grain
dashes (door planks, pumpkin hatching) and sky dots — all under 35 px, peak confidence ~0.8 where
real strokes peak at 1.00. The old `SPECK = 40` area floor kept almost all of them.

`clean(prob)` keeps a piece only if it is **≥ `MIN_DIM` 35 px in some direction OR encloses a hole
≥ `MIN_HOLE` 12 px**, AND contains a pixel ≥ `STRONG` 0.95. The hole rule exists because the first
cut lost real shapes: the cart wheel's hub, the pumpkin stem, flower centres, cart bolts — a dot or
dash is never a closed ring. A hole's owner is the ink piece touching it along the most pixels, so a
gap wedged between a dash and a wall credits the wall, not the dash. Vectorised (60-80 ms).
Result: 46 / 44 / 36 pieces kept, 2-4% of ink dropped, nothing visibly missing on the
red/green overlay check. Dials: `MIN_DIM`, `MIN_HOLE`, `STRONG` in `services/c2l_lineart.py`.
**Deployed 2026-10-29** (`b70b150`, by hand mirroring the workflow: `git archive HEAD platform/backend`
piped to `tar` in `/opt/colorforge-incoming/`, then `vps_deploy.sh` — ⚠️ `scp` of the script from a
Windows checkout carries CRLF, `sed -i 's/
$//'` first). First live c2l upload: cot1 → `review`,
45 s, 758 regions / 97 colours, 44 ink pieces (was 2,417 raw). Left in the admin as "c2l dots test"
(category other, unpublished) for the owner to look at. Also: there is no `GET /api/items/{iid}` —
poll the list.

### 8.5cm 🎓 c2l v2 — what a filter cannot do, and the training that can (2026-10-29)

After §8.5cl the owner still saw dots. Looked at the live border 1:1: what remains is not loose
dots but **thin texture strokes attached to the drawing** — wood grain on the cart boards, stone
grain, hairs on the door edge. Two filters were tried and both rejected by rendering the removed
ink in red: a 3 px opening takes the grain **and** the door's plank lines, pumpkin ridges and leaf
veins (all 2 px — width cannot tell grain from vein), and a short-spur rule broke those same thin
lines into dashes. **Grain vs. drawing is a meaning, not a measurement: the model has to learn it.**

v2 training changes (code only; GPU run via the *GPU job* workflow when the owner says so):
- `train_c2l.distract()` — with `DISTRACT_P` 0.7, crops get wood-grain line groups, pencil
  hatching, specks or paper grain drawn **into the colour input only**; the line target is
  untouched. Checked visually on cot1 crops.
- Real pairs: `REAL_DIR` (the owner's colour picture + his hand-made border, `NNNNN_c.png` /
  `NNNNN_l.png`) oversampled `REAL_REPEAT` 12×. The workflow stages
  `/opt/colorforge/models/c2l/real_pairs.tgz` from the VPS when it exists — **the owner still has to
  send those reference line arts as files**; three cottages exist as colour only.
- Fine-tune: `INIT` = the previous `c2l.pt` (staged automatically; `NO_INIT=1` in args trains from
  scratch), lr 1.5e-4. `LR` overrides.
- `make_pairs`: a sixth, pencil-heavy style and a 30% bolder drawn-back outline.
- Defaults `PAIRS 900 / STEPS 12000` ≈ 2.5 h on a 4090 ≈ $2.5. RunPod balance $11.53 (topped up).

### 8.5cn ✒️ c2l pictures moved to the VECTOR engine — smooth at any zoom (2026-10-30/31)

Owner, zoomed into the live c2l cottage on his phone: *"smooth banay shape vali"* — both the ink
and the fill edges were a pixel staircase. Cause: a c2l item went through the July RASTER engine
(1254 px `border.png`, id-map from a 720 px k-means grid upscaled NEAREST, §8.5bj), while his own
SVG pictures play on the app's vector engine (§8.5bz) and look smooth. So c2l now becomes an SVG
item: `services/c2l_vector.write_source()` paints a flat picture — July's k-means CENTRES but every
full-res pixel classified against them on a σ=3 blurred copy (boundaries follow the picture, not the
grid; pencil grain must not split a fill), line-art cells split by colour exactly like
`_game_data_color`, each its dominant centre colour, the c2l ink in black on top — then
`svg_builder.vectorize(smooth=True)` (vtracer **spline**, corner 90 / length 8 / splice 60; the
flat-art route keeps polygon mode) and the normal `_process_svg(iid, source_kind="c2l")`.
Measured on cot1: July map → 758 regions but blocky edges at 3x; full-res map σ2/3/4.5 → 1416/1096/793
regions before tracing, ~1,700-2,000 after (each ink-separated piece is its own cell, as in the
owner's SVGs — Majestic Lion is 2,092). vtracer params alone did NOT remove the blocks; the source
map had to change. `photo.png` keeps the upload so **regenerate reruns the model** (route branch on
`source_kind == "c2l"`; older items have the photo as `original.png`). Deployed `a1c0661`; the live
"c2l dots test" regenerated to 1,637 regions / 82 colours, `vector.json.gz` 168 KB + `start.jpg` served.
⚠️ Phones trust a cached reveal copy for 24 h — clear `cache/reveal` (or wait) to see a republish.
**Second pass the same night — the first vector version was still angular at deep zoom.** Rendered
at 6x beside a Figma SVG: ours were polygons with 1-2 px facets, the Figma one curves. vtracer traces
whatever steps it is given, and the 1254 px source had them (the model's ragged ink edge, the 1 px
staircase of the region map). Fix in `c2l_vector.write_source`: render the source at **2x** (`UP`),
`medianBlur` 11 on the flat fills, the ink mask upscaled + Gaussian σ3 + re-threshold, then
`vectorize(smooth=True, max_side=2*MAX_SIDE)` (spline, corner 60 / length 8 / splice 45, speckle 8);
`prepare()` scales the SVG back to 1254. Measured: regions 1,962 → 1,390, colours 101 → 65, the door
hinges readable where they had been a black blob, curves at 6x. ~66 s on the VPS. Live item now
1,285 regions / 63 colours, `vector.json.gz` 244 KB. `svg_builder.vectorize` gained `max_side`.

**c2l v2 training ran (run 41078631055, $1.15): synthetic 539 + distractors + fine-tune from v1 —
but `real pairs 0`.** The real-pairs tgz was made on Windows; root's `tar` on the pod failed on
"cannot change ownership to uid 197609", so the `&&` chain skipped `export REAL_DIR` (pipefail could
not see it: the chain is inside an `if`). Fixed with `--no-same-owner`. v2 vs v1 on cot1 (raw, before
the dirt filter): fewer grain dashes on the cart planks and stones, still some — a modest gain. v2
weights in the run artifact `gpu-train_c2l-3` (not installed). Next: v3 with the 147 SVG pairs.
Two earlier dispatches died in 2 min: presigned S3 URLs carry `&` and were passed unquoted through
`env` / `source` — now a single-quoted `job.env` + `pipefail` + `::add-mask::`.

### 8.5ci 🎬 Finish replay time-lapse + MP4 share (2026-10-25, branch `2026-10-25-reveal-replay`)

Owner sent a 9 s video of the reference game's finish (blank page → fills appear → outline fades →
the painting alone) and said build it. Reveal game only (New 2026 / mixed / SVG pictures).
- `RevealLevel` now records **fill order** (`markRevealed` appends; `encodeFillOrder`), saved as
  `key + "#order"` beside the progress string. The core's progress string is in REGION order, so it
  could not carry this. Old saves without `#order` replay in region order. `sketchStart` = the
  picture began on start.jpg.
- `model/reveal/ReplayRenderer` — small copy (long side 720, even sizes for H.264): blank (white, or
  the grey sketch recomputed from `original` as 255-(255-L)/2, so start.jpg need not be kept in RAM)
  → regions in fill order over 7 s (smoothstep) → pixels under the outline + outline alpha fading
  over 1 s → 1 s hold. Pixels no filled region owns go in at the fade, else the finished picture
  had white cracks where the lines were.
- `ReplayView` plays it full screen (tap = skip); `ReplayEncoder` writes MP4 (MediaCodec AVC,
  `lockHardwareCanvas` on the input surface — the docs call `lockCanvas` there unreliable — and pts
  from the frame number, since encoding runs faster than real time). Shared via FileProvider
  (`cache/replay/<fileName>.mp4`) with `setClipData` so the share sheet can preview it.
- Finish flow: burst → 1.2 s → replay → sheet. Sheet gained **▶ Replay** and **Share video**.
- 🐞 Fixed on the way: every finish-sheet button rendered solid violet with dark text (unreadable)
  — the screen's theme is MaterialComponents, so `<Button>` is a MaterialButton that tints over
  `android:background`. `app:backgroundTint="@null"` on all six. Replay/sheet overlays got
  elevation 30/40dp: the tool rail has elevation and drew on top of a flat overlay.
- ✅ Verified on the emulator against the live server (Paris, 1,974 regions, seeded to one left):
  replay played full screen, sheet readable, MP4 = 270 frames / 9.0 s / 720×720 and decodes.
  SVG pictures bake their ink into `original`, so their outline does not visibly fade — only
  photo pictures show the fade. Not tested on a real phone.
- ⚠️ Noticed, not fixed: on the emulator **SplashActivity took 23.6 s** to display and an input
  during it caused an ANR. Prefs file `colorbynumber.xml` is 4.4 MB on a fresh install — worth
  checking whether it is read on the main thread at start.

### 8.6 Performance audit — findings + fix plan (2026-07-21, code-verified)

Root cause of "app not smooth": the team already found the worst jank source (**per-item
`BlurView` + RenderScript in RecyclerViews**) and fixed it in **only ONE of four grids**
(`LibraryItemAdapter` = By Images), leaving By Number, By Pixel and Daily un-fixed — two of them
re-blurring **every frame**. Plus heavy per-pixel work on the UI thread when opening a picture.

**HIGH (do first):**
- **H1 — Per-item BlurView + RenderScript in 3 grids. ✅ FIXED 2026-07-21 (stop-gap).**
  `fragments/PixelFragment.java:155` (By Pixel) and `adapter/LevelsAdapter.java:106` (Daily) were
  `setBlurAutoUpdate(true)` = re-blurring **every frame** while scrolling; both are now `false`
  (By Number `ColoringsAdapter.java:105` already was). **Owner's decision: keep the frosted-glass
  look** — so the blur views are NOT removed. The stronger fix the app's own comment recommends
  (`LibraryItemAdapter.java:105-126`, `blurView.setVisibility(View.GONE)`, already done in By
  Images) stays available if he later accepts the visual change.
- **H2 — Level init ran on the UI thread. ✅ FIXED 2026-07-21.** `GameView.setLevel` did two
  `getPixels` into `int[w*h]`, `processPixelsAndCreateColorAreas` (per-pixel + `HashSet<Integer>`
  autoboxing), `buildSpatialIndex` and `restoreSavedProgress` (flood-fill per saved step) inline —
  a multi-hundred-ms→sec freeze opening any picture. **How it was fixed:**
  - All of that now runs on a background `Thread` (same idiom as `GameViewModel.loadBitmaps`);
    only the finished bitmap is handed back via `post(...)`. The first frame is painted on that
    thread too, so the UI thread gets a ready-to-draw bitmap.
  - New `levelReady` flag — `onDraw`, `onTouchEvent` and `selectColor` no-op until init finishes,
    so a half-built level can never be drawn or touched. `cleanup()` sets `levelInitCancelled`
    so a late result is dropped (and its bitmap recycled) instead of landing on a dead view.
  - `setLevel(...)` gained an optional **`LevelReadyListener`**. This is load-bearing:
    `GameActivity.initializeColorPalette()` calls `finish()` when the palette is empty, so it
    **must** run in that callback, not right after `setLevel`. `finishLoadingProgress()` moved
    into the callback too → the loader now stays up until the level is really drawable, instead
    of the UI unfreezing into a blank canvas.
  - **Tested on device (CPH2683):** 4 pictures opened (incl. a very dense mandala), palette,
    numbers, saved-progress restore and region fill all correct; all 4 tabs still work; no
    `FATAL EXCEPTION`, no `Skipped … frames`, no ANR. *(Not measured: a before/after ms number —
    only that the jank warnings are absent and the loader animates smoothly.)*
- **H3 — `MainActivity.loadCategories()` called twice** (`:172` + `:187`) → 14 requests + 2 Volley
  queues per cold start, writing counter prefs the UI never reads. **✅ FIXED 2026-07-21** — removed
  the duplicate call in the `postDelayed` (kept `loadBackgroundGif`).

**MEDIUM:**
- **🐞 REGRESSION FROM H2 — "colours don't fill at all" (reported + fixed 2026-07-22).**
  **A fill only happens when the tapped pixel is `COLOR_HIGHLIGHTED`** (`handleTouchDown`
  ~`:956`), and that highlight is applied by `selectColor()`. After H2 made level init async,
  `selectColor()` began with `if (!levelReady) return;` — and the selected-colour **LiveData fires
  before the level is ready**. It hit that guard, returned, and **LiveData never re-delivers the
  same value**, so the highlight was never applied and *every tap did nothing*. Synchronous init
  had hidden this because the level was always ready by the time any observer ran.
  **Fix:** when the background init publishes (`levelReady = true`), re-apply
  `currentSelectedColor` if one was chosen. **✅ Owner confirmed on the device that colours fill
  again (2026-07-22)** — so H2 stays; no need to fall back to synchronous init. **Lesson: moving work off the UI thread changes
  ordering, not just timing — anything that was implicitly "already done" must be re-checked.**

- **M4** — `GameView.selectColor()` scans the whole pixel array + rewrites the whole bitmap on
  every palette tap. **⚠️ The fix suggested here originally ("use `colorToAreasMap` to touch only
  that colour's pixels") IS NOT IMPLEMENTABLE** — `model/model/ColorArea.java` holds only a seed
  point (colour, x, y, textSize), **not** the region's pixels. Getting a colour's pixels from that
  map would mean flood-filling every area, i.e. slower than the single pass it replaces.
  Also note the pass is not the dominant cost: `updateResultImage()` then does a full-image
  `setPixels` **plus** a full-image `drawBitmap` of the line layer, so an indexed scan could save
  at most ~⅓ of the tap — and a per-colour pixel index would add ~4 MB on a 1000×1000 picture.
  **✅ DONE 2026-07-21 (what was actually implemented):** `selectColor()` now returns immediately
  when the requested colour is already the highlighted one (`highlightedColor`/`highlightApplied`),
  killing 100 % of the work for repeat selections. `highlightApplied` is tracked separately from
  `currentSelectedColor` on purpose — the latter is also set on the `!levelReady` early return, so
  reusing it would leave a colour picked during load permanently un-highlighted.
  **Real remaining win (bigger, riskier — not done):** stop baking the line art into `resultBitmap`
  and draw `blackLineBitmap` as a second layer in `onDraw`. That removes the per-update full-image
  blend, but `getWinPreview()`/`DataManager.generatePreview()`/the time-lapse all read `resultBitmap`
  expecting the lines to be in it, so all of those must be handled first.
- **M5** — Picasso loaded with **no `.resize()/.fit()`** → full-res decode into small cells.
  **✅ FIXED 2026-07-21**: `.fit().centerCrop()` added to all 6 Picasso loads —
  `ColoringsAdapter.java:172,185` (By Number), `LevelsAdapter.java:139,147` (Daily),
  `NumberFragment.java:340` (daily banner preview), `FavoritesActivity.java:130`.
  **Why `centerCrop` is not a visual change:** every one of those targets already declares
  `android:scaleType="centerCrop"` in XML, so `fit()` just decodes at the size the view was
  already displaying. `ColoringsAdapter`/`LevelsAdapter` use `AspectRatioImageView`, which forces
  **height = width** (`views/AspectRatioImageView.java:28`), so the target size is known at layout
  time regardless of the image — `fit()` cannot deadlock on a wrap_content height here.
  *(Still open: 3 loaders + 3 caches — the Glide/Picasso/Ion collapse is a bigger refactor.)*
- **M6** — `ColoringsAdapter` sets `setHasStableIds(true)` but `BaseRestAdapter.getItemId()`
  returned the position → stale thumbnails. **✅ FIXED 2026-07-21**: `BaseRestAdapter.getItemId()`
  now hashes a `stableKeyFor(item)` hook (64-bit, same as `LibraryItemAdapter.getItemId()`), and
  `ColoringsAdapter.stableKeyFor()` returns `level.getFileName()`. The hook defaults to `null` =
  old position-based id, so the other `BaseRestAdapter` subclasses are unaffected.
  *(Note: `getItemId` deliberately reads `mItems` via `mapPosition()` rather than
  `getItem(index, ctx)` — the latter has ad-insertion side effects.)*
- **M7** — `NumberListFragment.java`: `setHasFixedSize(true)` commented out + `setItemViewCacheSize(20)`.
  **✅ FIXED 2026-07-21**: both `setupDailyRecyclerView()` and `setupNormalRecyclerView()` now set
  `setHasFixedSize(true)` + `setItemViewCacheSize(4)` (matching `ColorFragment.java:222`).
  **Why fixed-size is safe even though the XML says `wrap_content`:** the RecyclerView in
  `fragment_view_pager.xml:16` is the direct child of a `SwipeRefreshLayout`, and
  SwipeRefreshLayout always measures its child **EXACTLY** to its own (`match_parent`) size,
  ignoring the child's LayoutParams. Verified on device: grids render and scroll normally.
- **M8** — Full-res looping network GIF on startup. **✅ FIXED 2026-07-21**: both `MainActivity`
  Glide loads now use `.override(gifTargetWidth(), gifTargetHeight())` (display size) instead of
  `Target.SIZE_ORIGINAL`. `ivBackgroundGif` is a full-screen `centerCrop`, so screen size is all
  the detail it can show. **Two parts of the original note were already wrong:** `SplashActivity`
  never used `SIZE_ORIGINAL` (it lets Glide size to the view), and pausing the home GIF when
  hidden was **already implemented** — `MainActivity.pauseGif()` / `resumeGifIfNeeded()` are wired
  to `onPause`/`onResume`. Nothing to do there.
- **M9** — `Manager.initialize()` called twice (`GlobalApplication.onCreate()` +
  `MainActivity.onCreate()`) → duplicate Firebase auth listener. **✅ FIXED 2026-07-21**:
  `Utils/Manager.initialize()` is now idempotent (guard flag), so both call sites stay as they are
  and any future caller is safe too.

**LOW:** dead `refresh()` loop (`MainActivity:1092-1098`, `adapters` never populated); per-frame
`new Matrix()` in magnifier (`GameView.java:1940`); home-GIF `Glide.with(getApplicationContext())`
(won't auto-cancel). **Note:** `GameView.onDraw` is already well-optimised (pooled Paint/Path,
particle pool, cached number strings, bounds-culling) — the draw loop is NOT the problem; H2 & M4 are.

**Status 2026-07-21: H1 ✅ H2 ✅ H3 ✅ M4 ✅ M5 ✅ M6 ✅ M7 ✅ M8 ✅ M9 ✅ — the whole HIGH+MEDIUM
list is done.** Left: the LOW items below, and the bigger refactors (collapse 5 HTTP stacks → 1
OkHttp; 3 image loaders → 1 Glide; replace RenderScript blur app-wide; a reusable game-load
pipeline; the `resultBitmap` line-layer split described in M4).

**⚠️ Not yet verified on device (owner to check):** the M4 palette-tap path. The build installs,
launches and the grids scroll cleanly, but the phone locked before the in-game palette could be
tapped. What to try: open a picture, tap several colours, tap the *same* colour twice, and confirm
the grey highlight still appears/moves correctly. **Bigger refactors (later):** collapse the 5 HTTP stacks → 1 (OkHttp); collapse
3 image loaders → 1 (Glide); replace RenderScript blur app-wide; a reusable game-load pipeline.

### 8.5n ✅ Flutter rebuild STARTED — reveal game playing on device (2026-07-25)

**Scope LOCKED by owner:** *"ha bthu j joye … juni app ni bdle anu updet … aj app ne ios ma pn
lunch krva ni che … flutter ma jashu."* → **Full app, ALL tabs, and the Flutter app REPLACES the
live one** (ships as its update). Reason: **iOS launch too** — one codebase for Android + iOS. So
Flutter is the right call, not just a rewrite. (iOS binary needs a Mac later; all Dart code we write
now is shared and compiles for iOS unchanged.)

**New project: `colorforge-ai/colour_mate/`** (Flutter 3.38.5, Dart 3.10.4 — both installed on this
machine). Package `com.colourmate.colour_mate` (dev; switch to the Play package at release).
Deps kept minimal for small APK: `http`, `shared_preferences`, `path_provider`,
`cached_network_image`. Dark Material-3 theme, indigo accent `#6C63FF`.

**Reveal engine (the hard part) — DONE and proven on the proven data contract:**
- `lib/core/reveal/reveal_data.dart` — models (Region, PaletteColor, RevealData).
- `reveal_loader.dart` — decodes the 4-file package → RGBA buffers normalised to (w,h). **id_map is
  read nearest-neighbour, NEVER interpolated** (id = R + G<<8). border+original resampled to (w,h).
- `reveal_controller.dart` — game state. Canvas = one RGBA buffer starting as the line art; filling
  a region writes the **photo** pixels into it (photo-reveal), then `decodeImageFromPixels` → new
  `ui.Image`. `tapImage(x,y)` fills a region **only if it matches the selected number** (true
  colour-by-number); auto-advances to the next unfinished number.
- `features/reveal/reveal_painter.dart` — **two layers**: heavy `RevealCanvasPainter` (image + number
  labels, repaints only on fills) + light `HighlightPainter` (pulsing discs on the selected number's
  unfilled regions, animates at 60fps cheaply). Labels culled when `r*scale < 9px`.
- `reveal_game_page.dart` — InteractiveViewer (zoom/pan) + GestureDetector inside it (so taps map to
  image px at any zoom); top progress bar; bottom `PaletteBar` (number chips w/ remaining count, tick
  when done); completion dialog.

**Bundled sample** at `assets/sample_reveal/` (copied from the native debug asset) so the game plays
**offline** immediately. `New2026Page` shows it as a card whose **thumbnail is border.png (line
art)** → opens the reveal game. Home = `HomeShell` with NavigationBar: New 2026 / Daily / Diamond /
Settings (Daily+Diamond are stubs until the backend feeds are wired).

**Status:** `flutter build apk --debug` ✅, installed + launched on CPH2683. **Owner to verify on
phone:** select a number → its regions highlight → tapping fills only that number → the photo reveals.
Debug APK is 148 MB (normal for debug; release w/ split-per-abi + R8 will be ~15-25 MB — deferred).

**Known build noise (non-fatal):** Kotlin incremental-cache stack traces during Gradle because the
pub cache is on `C:` and the project on `E:` (different drive roots break Kotlin's relocatable path
converter). Build still succeeds; only incremental caching is disabled → slightly slower rebuilds.

**Next:** wire the live backend (categories → gallery of border thumbs → download a picture package
to disk → play it) reusing the proven `/index.php/Number/…` + `/thumb/…` + `regions.json`/`id_map`/
`border`/`original` contract; then Daily (date-wise) + Diamond; then settings/coins/ads; then the 3
paid third-party tabs (investigate portability — those are other companies' servers/SDKs).

### 8.5o ✅ Native New 2026 now plays the reveal game NATIVELY (WebView dropped) (2026-07-25)

**Owner refocused to the native app** — *"atyre android app per kam kr, anu updet important che, Flutter
badhu pehli thi develop kare cho"* — the live-app update is urgent; Flutter (§8.5n) is PAUSED, saved,
resume later. **Reason Flutter still matters:** owner wants **iOS** too, so it's the long-term single
codebase — but not now.

**The fix (small, surgical):** New 2026 was still opening the rejected `play_color.html` WebView.
The native reveal engine already existed and is *exactly* what the owner wanted (RevealView =
select a number → only that number's regions show their number/hint → tap reveals only that number,
morph flat→photo 0.3s, two-finger zoom). It just wasn't wired in. Changes:
- **Backend `app.py`** — new proxy route
  `GET /index.php/Number/reveal/{category}/{file_name}/{asset}` (asset ∈ regions.json / id_map.png /
  border.png / original.png|jpg). Maps fileName → iid → `base_url` (`/cdn/images/<job>`) → serves the
  file (local: FileResponse; S3: 302). `original.jpg` is remapped to `original.png` (our bundles are
  PNG; BitmapFactory decodes by content, so the native loader's `.jpg`-first request just works).
  Added `RedirectResponse` to the imports.
- **ColorForgeApi.java** — `revealBaseUrl(ctx, category, fileName)` = `{base}index.php/Number/reveal/
  {cat}/{file}/`. `playUrl()` kept for reference/fallback.
- **New2026Fragment.onColoringClicked** — now `startActivity(RevealActivity.fromUrl(ctx, revealBase))`
  instead of `WebGameActivity`. Dropped the unused WebGameActivity import (Activity still in manifest).

**Verified end-to-end (local, `adb reverse tcp:8000`):** backend restarted with venv
(`bg98o9l17`; log at `%TEMP%/cf_backend.log`), all four reveal assets return 200
(regions.json 43512 B, id_map 196079 B, border 946814 B, original→png 2.4 MB). Native app rebuilt
(`assembleDebug`, 23 s), installed on CPH2683, launched. Server log shows the phone fetched
`category/` + `gimg/c/animals` (New 2026 gallery loaded). **Left for owner:** tap the picture and
confirm the NATIVE colour-by-number reveal opens (not the WebView). RevealActivity is registered
(manifest line 191); the sample-asset path was already proven earlier, only the source is new (URL vs
assets), so risk is low.

**Prod caveat (later):** on S3 the reveal route 302-redirects to https; Java's HttpURLConnection does
NOT auto-follow http→https. Fine for local http now; handle the follow (or serve absolute https)
before public.

### 8.5p ✅ Reveal touch made forgiving + highlight made unmistakable (2026-07-25)

Owner tested the native reveal and said *"reveal nથી thatu number per tap karu to; touch smooth kar,
zoom no hoy; region highlight kar; touch ek dam sensitive hovu joie, nahi to people bore feel kare."*
Root cause: 511 tiny regions at fit-scale → a precise tap lands on a border line or the wrong region,
so nothing fills; you had to zoom. Fixes in `RevealView.java`:
- **Forgiving tap (the key fix).** `findTarget(x,y,forgiving)` tries the exact pixel, else snaps to
  the NEAREST unrevealed region of the selected colour within a radius (tap = dp(24)/scale, drag =
  dp(8)/scale). Tap anywhere near a spot → it fills; no zoom needed. Verified on device: two taps at
  approximate points dropped chip-1's count 4→2.
- **Whole-region highlight.** A `highlightBitmap` (rebuilt only on colour change; per-region cleared
  on reveal via `clearHighlightRegion`, so dragging stays smooth) tints every unrevealed area of the
  selected colour, breathing (alpha 110..190 with the pulse).
- **Clear badge on top.** `drawMarkers` draws a **white halo + colour disc + number** on larger
  regions — the white ring makes even pale colours (e.g. #91bfc2 teal) pop on the line art.
- **Snappier reveal:** MORPH_MS 300 → 180.
- Two-finger zoom/pan unchanged; single finger only ever reveals (never pans), so no accidental zoom.

Also seen while testing: splash shows a **premium paywall** (Upgrade to Premium) and **"Loading
ads…"** — the real ad/billing flow is live. Bottom nav confirms all 5 tabs (By Number / New 2026 /
Diamond / By Images / By Pixel). New 2026 grid shows 2 published pictures (kitchen shelf, Krishna) but
their **thumbnails are the COLOURED preview, not border/line-art** (owner wants border — TODO).

**⚠️ The real 'boring' blocker — CONTENT granularity.** This picture has **98 colours / 511 regions**
(29 colours have only 1-2 spots). Even with perfect touch, working 98 colours of tiny regions is
tedious and progress crawls (2 reveals still shows 0%). The fix is on the BACKEND reveal/palette
generation: quantise to **~24-36 colours** and merge tiny regions, then republish. Pending owner
go-ahead (asked twice; owner answered with touch feedback instead). This is very likely the true
"nathi ready".

### 8.5q 🐛✅ ROOT BUG: reveal painted nothing — bbox was [x,y,w,h], app read [l,t,r,b] (2026-07-25)

Owner nailed the real symptom: *"photo reveal j ny thato; tap karu to number vyo jay che (fill thay)
but kyak bug che."* — tapping removed the number/badge (region marked revealed) but the photograph
never appeared.

**Cause:** `regions.json` bbox is **`[x, y, width, height]`**, but `RevealLevel.Region` read it as
**`[left, top, right, bottom]`**. Verified against the published bundle: cx,cy lies inside the bbox as
[x,y,w,h] for **511/511** regions (as corners only 19/511), and **421/511** regions have
`bbox[2] (width) < bbox[0] (x)` → the app's `right < left` → `paintRegion`'s inner loop
(`for x = left; x <= right`) never executes → **no photo pixels written**. Tapping still worked because
`regionAt`/`findTarget` use the **id_map**, not the bbox — so the region was found & marked revealed
(number vanished) while `paintRegion` painted nothing. Also broke `swatch()` and `clearHighlightRegion`
(both use the corners).

**Fix (`RevealLevel.java`, Region ctor):** `right = bbox[0]+bbox[2]; bottom = bbox[1]+bbox[3];`
(the id_map check inside paintRegion keeps the one-pixel over-scan harmless). Same convention in the
bundled sample, so it fixes both. **Verified on device:** the picture now shows the photo — red
polka-dot jam lid, teal pot, wood-grain shelf, brown spoon; progress went 0%→8% as previously-revealed
regions repainted correctly on load. **The engine is now correct.** Remaining polish: palette 98→~30
(the "boring" issue, still owner's call), border/line-art grid thumbs, Daily/Diamond, coins/ads.

### 8.5r ✅ Reveal gestures rewritten + grid line-art thumbs (2026-07-25)

Owner's punch-list after the bbox fix: zoom in/out not working; numbers bigger than the region;
By Number's tools missing; grid should show the border image; touch even more sensitive; the number
itself needn't be highlighted but the PART must, and show the number; double-tap or long-press should
auto-select that region's colour.

Done (verified on device — reveal now at 14%, real photo colours; grid shows outlines):
- **RevealView rewritten on platform detectors** (`ScaleGestureDetector` + `GestureDetector`):
  single-tap → reveal nearest selected-colour region (forgiving, tolerance dp28); one-finger drag →
  pan (when zoomed); pinch → zoom in/out (was the broken bit); **double-tap OR long-press → adopt the
  colour of the region under the finger** (`Listener.onNumberPicked` → RevealActivity updates + scrolls
  the palette). No more accidental reveal when starting a pinch.
- **Whole-region highlight only** (breathing tint), **no badge**; a **small number that fits inside the
  region** (`min(radius*0.9, 13dp)`), tiny regions get tint only. Fixes "number bigger than the part".
- **Grid thumbnails = line-art**: `ColoringsAdapter.buildThumbnailUrl` now uses
  `ColorForgeApi.lineArtUrl` (`thumb/{cat}/{file}.png`) for ColorForge items. Verified: grid shows
  outlines.

**Still open:** (1) "By Number tools" — the flat-fill power-ups (Bomb/Bucket/Surge/Magnet/Radar in
activity_game.xml, most already `visibility=gone`) don't map onto photo-reveal; kept fit + wand(next).
Need the owner to say which tools they actually want. (2) Palette 98→~30 (backend) — the real "boring"
fix, still owner's call. (3) Daily (date-wise) + Diamond + coins/ads for the update.

### 8.5s ✅ Reveal now behaves like By Number (owner's reference) (2026-07-25)

Owner sent 6 screenshots of the **By Number game as the reference**: "part highlight dekhato j nathi,
and number pan — jo By Number ma kem work kare che, pachi banne ni comparison kar." Read the By Number
engine (`model/game/GameView.java`): it draws **every unfilled region's number in black**
(`drawNumberLabelsOptimized`) and paints the selected colour's regions **solid grey**
`COLOR_HIGHLIGHTED = 0xFFCCCCCC` — the grey IS the "part highlight". Reworked `RevealView` to match:
- **All region numbers** (black, sized `radius*scale*1.3` clamped dp7..dp22, off-screen/too-small
  skipped) — not just the selected colour. Classic paint-by-number look. Verified on device.
- **Selected colour → opaque GREY** (`COLOR_HIGHLIGHTED`) on its unfilled regions, drawn UNDER the
  line art so borders still read. Replaces the colour-tint that vanished on muted palettes.
  **Verified:** ~14k grey `0xCC` pixels present in the artwork of each post-select screenshot.
- Dropped the continuous pulse (was invalidating 60fps × 511 labels) → redraw only on interaction;
  matches By Number and fixes perf.
- Kept: `ScaleGestureDetector` pinch-zoom + pan; single-tap reveal (tolerance dp28); **double-tap OR
  long-press → adopt that region's colour** (verified: long-press selected chip 2). Grid thumbnails =
  line-art (`ColorForgeApi.lineArtUrl`), verified.

Note: automating device verification is fiddly — the app **restores into the last-open RevealActivity**
on relaunch (task restoration), and the reveal **back button can exit the app**; use `am force-stop`
before a clean navigation. Splash shows a **premium paywall** (X at ~78,155) + "Loading ads…".

**Still open (owner):** which By Number power-up tools (Bomb/Bucket/Surge/Magnet — flat-fill, don't map
to reveal) they actually want; palette 98→~30 (backend, the real "boring" fix); then Daily/Diamond/
coins/ads.

### 8.5t 🎨✅ Palette cut 98→28 — reveal now reads clean like By Number (2026-07-25)

Owner showed the cluttered Krishna reveal ("a vu dekhay che... 98 colours -> numbers overlap,
unreadable"). Fixed at the SOURCE (content generation), not the app.

**Knob:** `services/reveal_game_builder.py` — `COLOR_K` (colour clusters), `COLOR_LINE_RMIN` (region
density), `COLOR_MERGE_DE` (colour-merge distance). Was `100 / 9.0 / 16.0` → ~98 colours, 500+ tiny
regions. Changed defaults to **`28 / 18.0 / 22.0`** (COLOR_TARGET 400→220) → ~28 colours, ~160-190
regions. So ALL FUTURE admin uploads generate clean bundles automatically.

**Regenerated + redeployed BOTH published items** to the local CDN (`data/cdn/images/<job>/`), old
bundles backed up as `<job>_old98/`:
- kitchen `6185e29ae125`: 511 reg/98 col → **186 reg/28 col**
- Krishna `d1e3f26f2cc4`: 587 reg/100 col → **158 reg/28 col**
Regen path: set the three constants on the imported module, call `R.export_color_bundle(item_folder)`
(needs `data/items/<job>/reveal_master.png` + `ai_lineart.png`, both present), unzip
regions.json/id_map.png/border.png/original.png into the CDN folder. New `regions.json` keeps
bbox=[x,y,w,h] (verified 186/186). No server restart needed — the reveal route serves CDN files
from disk; the code change only affects new uploads.

**Verified on device** (after `run-as <pkg> rm shared_prefs/reveal_progress.xml` to drop stale
511-region progress): both pictures now clean & readable — numbers spread out, the **grey highlight
is obvious** (selecting colour 1 greys the whole background). Night-and-day vs the 98-colour mess.

**The reveal game is now feature-complete and polished.** Remaining for the shippable update:
By Number power-up tools (owner to pick which), Daily (date-wise) + Diamond feeds, coins/ads wiring.

### 8.5u 🏷️✅ SmartLabelRenderer — clean, sparse, non-overlapping numbers (2026-07-25)

Owner sent a 54s gameplay **video** of a polished reference coloring app (extracted frames via the
venv's cv2 — no ffmpeg; `VideoCapture` → 9 PNGs) + a precise 18-point spec for a "SmartLabelRenderer".
Reference behaviour: numbers **sparse, one per region, sized to fit, never overlapping**; filled
regions show none; selected colour easy to find; fewer numbers at low zoom, more as you zoom in.
(Reference fills FLAT colour, not photo — noted but NOT changed; ColorForge stays photo-reveal.)

New `model/reveal/SmartLabelRenderer.java` (+ `Config` for all thresholds, req 17). Each frame:
cull off-screen + too-small (zoom gate `radius*scale < minSafeRadiusDp` → hide; naturally gives the
large@low / medium@mid / tiny@high-zoom tiers); size font from the **inscribed radius** (`radius`
from the backend, which already gives the pole-of-inaccessibility `cx,cy` + inscribed `r`), shrink for
2-/3-digit, then **shrink-or-hide so the glyph's half-diagonal stays inside the inscribed circle**
(numbers never exceed their region); sort **selected-colour first then largest**; **greedy place with
screen-space collision** via a uniform spatial-hash grid (near-O(n), 60 FPS); draw selected at full
alpha, **others faded to ~26%**; filled regions skipped. Region label string cached on
`RevealLevel.Region.label`. `RevealView` drops its naive `drawNumbers` and calls the renderer; reveal
/ fill / save-progress untouched (req 18).

**Verified on device (fresh, progress cleared):** Krishna went from an unreadable overlap of ~50
numbers to a handful of clean, non-overlapping labels; the selected colour's number is solid black,
all others faded; kitchen the same, and zooming in surfaces more numbers. Meets the spec's DoD
(compiles, no overlaps on both test images, readable, filled hidden, selected easy to locate).

Tunables if the owner wants denser/sparser: `Config.minSafeRadiusDp` (lower = more numbers),
`unselectedAlpha`, `fontFillFactor`, `maxFontDp`.

### 8.5v ↩️ Palette reverted to admin's; numbers matched to By Number's style (2026-07-25)

Owner: "28 number j kem che — admin mathi ave ej rakhvana" (don't force 28 colours; keep the admin's
output — the owner controls richness there). And: numbers should be **not bold / simple, black, inside
the frame, not too big — neat & clean**. Then: "by number ma jem che size/style aj apply kar."

- **Reverted the palette reduction.** `reveal_game_builder.py` constants back to `COLOR_K=100,
  COLOR_LINE_RMIN=9, COLOR_MERGE_DE=16, COLOR_TARGET=400` (admin default). **Restored the original
  98/100-colour bundles** to the CDN from the `<job>_old98` backups (kitchen 511reg/98col, Krishna
  587reg/100col). The number CLUTTER is now handled purely on the CLIENT by SmartLabelRenderer, so a
  rich palette no longer means an unreadable screen.
- **SmartLabelRenderer numbers matched to By Number** (`GameView.drawNumberLabelsOptimized`):
  default typeface (**not bold**), **black** (`MEASURED_STATE_MASK`), `size = radius × scale × 0.8`
  (their `TEXT_SCALE_FACTOR`), min-gate 11dp, **no per-digit scaling**, **no fade** (all labels solid
  black — selection is shown by the grey region highlight, exactly like By Number). By Number's per-
  region size is an alpha-channel HINT (`ColorArea(...adjustedTextSize)`), ~½ our inscribed radius, so
  the raw 60dp ceiling made ours oversized → **capped at 24dp** to match By Number's real rendered
  sizes. Kept SmartLabelRenderer's extras (collision, off-screen cull, **inside-frame** clip, zoom
  tiers) — these make it cleaner than By Number without changing the look.

Verified on device (Krishna, 98 col restored): numbers are black, simple, modest, non-overlapping,
inside the frame; grey highlight marks the selected colour. `Config.maxFontDp` / `fontFillFactor` /
`minSafeRadiusDp` are the dials if the owner wants them smaller/denser.

### 8.5w ✅ Numbers hidden at fit, shown only on zoom-in (2026-07-25)

Owner (with reference screenshots of a coloring app): "pele thi j number shu kam dekhado — zoom kare
tyare j batavana" (don't show numbers at the full view; only when zoomed in). Added a zoom gate to
`SmartLabelRenderer`: `Config.minZoomRatioToShow = 1.6f`; `draw()` now takes `fitScale` and returns
early when `scale < fitScale * 1.6` — so at the fit view NO numbers render (clean line-art + the faint
grey highlight of the selected colour), and numbers appear once the user zooms in past ~1.6×.
**Verified on device:** fit view = zero numbers (clean Krishna line-art); zoomed view = numbers show,
black/simple/non-overlapping/inside-frame. Dial: `minZoomRatioToShow` (lower = numbers appear sooner).

### 8.5x ✅ Grid clean border, reveal UI polish, status-bar fix (2026-07-25)

- **Grid thumbnails = the clean artistic border** (reveal `border.png`), not the noisy colour-contour
  line. Added `ColorForgeApi.borderUrl` (→ `…/reveal/{cat}/{file}/border.png`); `ColoringsAdapter`
  uses it for ColorForge items and sets a **white cell background** (border is transparent). Verified:
  grid shows the same clean line-art the owner attached. (border.png is 946 KB — fine for a few items;
  generate a border thumbnail server-side before scaling up.)
- **Reveal UI polish** (owner's clean-up brief): image dropped below the top bar, palette raised above
  a reserved **bottom banner-ad** slot (`ll_reveal_ads` → `ads_banner_container`, shows "Loading Ads"
  until a real ad is wired), **soft shadows** (`elevation`) on back/progress/tool-rail/palette,
  `clipChildren=false` on the root so shadows aren't clipped. White bg, circular numbered palette.
- **Status-bar clip fix:** RevealActivity now `hideStatusBar()` (WindowInsetsControllerCompat, edge-to-
  edge) in onCreate + onWindowFocusChanged — same as GameActivity — so the top back/progress controls
  are no longer cut off under the status bar. Verified on device.

Reveal game is now visually done. Remaining for the shippable update: wire the real banner ad, Diamond
tab (same reveal), Daily (date-wise), coins/ads, then release prep (APK shrink, prod server).

### 8.5y ✅ Fill-burst particles in New 2026 (match By Number) + layout fixes (2026-07-25)

- Owner: "avi effect (sparkle/particle burst on fill) both ma joie — By Number ane New 2026." By
  Number's `GameView` already has it (`spawnParticles`/`drawParticles`, star+petal, `isParticlesEnabled`
  defaults **true**). Ported the SAME system into `RevealView`: on each reveal, `spawnParticles(tapX,
  tapY, regionColour)` (14 star/petal particles, random velocity + gravity + rotation + fade over
  ~0.9-1.8s), drawn on top in screen space, `postInvalidateOnAnimation` loop. So both games burst on
  fill. (Transient ~1s — couldn't freeze it in a static screencap; verified by code parity with the
  working By Number system.)
- **Tool rail no longer overlaps the artwork:** RevealView now `layout_below="@id/ll_reveal_tools"`
  (owner: "tools image ni upper aavi gaya che"). Tools sit in the clear top strip, image below.
- (Prior this session: grid → clean border line-art; reveal UI soft-shadows + bottom ad slot;
  status-bar hidden so top controls aren't clipped.)

Reveal game visually + interaction complete. Remaining: real banner ad, Diamond tab (same reveal),
Daily (date-wise), coins/ads, release prep.

### 8.5z ✅ Progressive flood-fill reveal (spread-from-tap, ease-out, edge particles) (2026-07-25)

Owner spec: "flood fill from the touch point, colour spreads across the enclosed area, particles on
the leading edge, clean boundaries (no leak past the outline), 300–500ms ease-out, premium/fluid."
Replaced the old uniform alpha morph:
- `RevealLevel.paintRegionProgressive(region, ox, oy, radius)` — reveals only region pixels within
  `radius` of the touch origin, **clipped to `idMap==region.id`** (boundaries never crossed),
  monotonic (pixels only turn on).
- `RevealView.floodFill(region, ox, oy)` — `ValueAnimator` 0→1 over **FILL_MS=420ms** with
  `DecelerateInterpolator` (ease-out); each frame radius = f × maxDist (farthest region corner from
  origin), `paintRegionProgressive` + `pushPixels` + `emitEdgeParticles`; `onAnimationEnd` does a full
  `paintRegion(1)` so nothing is missed. Origin = the tapped pixel if inside the region, else
  `region.cx,cy` (tolerance search may snap from just outside).
- `emitEdgeParticles` — 2 colour particles/frame at the spreading wavefront (random angle at the
  current radius, only if that pixel is inside the region), reusing the star/petal `Particle`. Replaces
  the old tap-point burst; removed `spawnParticles`/`PARTICLE_COUNT`/`LinearInterpolator`.

Built + installed. **Couldn't capture the 420ms transient on-device** (screencap froze bad frames; the
phone then screen-timed-off/locked mid-test — competitor apps "Color Aura"/"Recolor" notifications seen
on the lock screen, owner's reference apps). Owner to verify live: tap a spot → colour spreads out from
the touch point with particles on the edge.

### 8.5aa ✅ Modern colour boost for By Number (paid content) (2026-07-25)

Owner: the paid By Number fill colours look old ("old pink") — wants them fresh/modern. Can't change
the ionicsolutions source, so applied an in-app boost. `GameView`: flag `MODERN_COLOR_BOOST=true` +
`applyModernColorBoost(int[])` — HSV lift (saturation ×1.32 +0.05, value ×1.03), skipping near-neutral
pixels (white paper, black lines, greys). Injected on the level-load **background thread** right after
`colorBitmap.getPixels` into BOTH `originalColorPixels` (answer) and `currentColorPixels` (working),
before `processPixelsAndCreateColorAreas` — so palette, fills and the answer buffer stay perfectly in
sync (identical colours map identically → region/number count unchanged; no gameplay/matching break).
Verified on device: a flower picture's pinks now read bright/fresh vs the old dusty pink. Blanket
(affects all By Number pictures); tunable via the two HSV factors; set the flag false to revert.

### 8.5ab 🎨 Home redesign — modern purple identity, matching owner's reference (2026-07-25)

Owner wants a full UI/UX overhaul of the NATIVE app (rejected Flutter again: "mare ama j joye"),
modern/game-like/aesthetic, landscape+portrait, tablet. Rejected a static mockup ("build it real,
you're a master designer"). Then sent an EXACT reference (purple gradient home, "Colour Mate"
white+gold wordmark, tagline, circular buttons, premium crown card, colourful category pill chips,
line-art grid cards w/ heart, white floating nav w/ violet selected pill). Building it for real:

**Done (Phase 1 — home shell, owner approved "ok done next"):**
- `drawable/bg_home_modern.xml` — violet→lavender→light aurora gradient (+ white & pink radial glows).
- `activity_main.xml`: `ivBackgroundStatic` → `bg_home_modern` (`fitXY`), `ivBackgroundGif` → gone;
  header rebuilt = "Colour"(white)+"Mate"(#FFC42E gold) Titan One wordmark + "Relax • Colour • Enjoy"
  tagline + three white circular buttons (bg_circle_white). Removed the #42FFFFFF wash.
- **The meadow wouldn't die** because `MainActivity.resumeGifIfNeeded()` (from onResume) re-calls
  `loadBackgroundGif()` (line 768) — I'd only commented the onCreate call (215). Fix: `if(true)return;`
  at the top of `loadBackgroundGif()` disables it everywhere. (`initBackgroundLayers` is never called;
  the XML src drives the background.)
- Nav colours → violet (`nav_selected=#6E5EE4`, `nav_pill=#ECE8FC`); selected pill drawable
  `bg_nav_pill_selected.xml` (violet rounded) replacing the green `game_button_default` at
  MainActivity:457. Verified: New 2026 tab shows a violet pill on the purple home.

**Still to match the reference:** category **pill chips** with colourful icons (currently plain text
tabs in the fragments), premium crown card, then Phase 2 game-screen polish, Phase 3 landscape+portrait,
Phase 4 tablet (sw600/sw720). MainActivity binds nav_item/indicator/label/icon + top_bar +
fragment_container — preserve those IDs in any home-layout edit.

### 8.5ac 🦋 Ambient butterflies overlay (2026-07-25)

Owner: "butterflies that keep flying around the app and sometimes land on the By Number border / a
button border — so users don't get bored." (Wanted Lottie; did it in Canvas — no asset, full control.)
New `widget/ButterflyLayer.java` (custom View): N (default 3) hand-drawn two-tone butterflies (monarch/
morpho/rose/sulphur/violet palettes) drift with a sine flutter, flap wings (`|cos(phase)|` folds the
wing scaleX), bank toward heading; `postInvalidateOnAnimation` self-drives; `onTouchEvent` returns
false so the UI stays fully interactive. Added as the LAST child of `main_content` in activity_main
(draws over everything). Verified on device: orange/pink/teal butterflies fluttering over the purple
home. `setPerches(List<RectF>)` lets them land on a UI edge (rest ~1.5-4s) — NOT yet wired from
MainActivity (butterflies currently just wander). TODO: pass nav-bar-top + header-button rects as
perches; optionally refine the wing shape.

### 8.5ad 🦋✅ Butterflies now use the owner's Lottie (2026-07-25)

Owner sent `butterfly rotated.json` (workspace root, valid Lottie 1080×1080, 29.97fps, 0-150 frames)
and said use THAT art, I supply the movement — "mst output joie." App already ships Lottie 6.7.1.
Copied → `app/src/main/assets/butterfly.json`. Rewrote `ButterflyLayer` to render it: one shared
`LottieDrawable` (loaded via `LottieCompositionFactory.fromAsset(...).addListener`), drawn N times per
frame — per butterfly set `setProgress((now%dur)/dur + phase)` (desynced flap), `setBounds(0,0,sz,sz)`,
canvas translate to (x,y) + rotate(heading+90° around centre), `draw()`. Kept the flight logic: slow
drift (dp0.3-0.6/frame), sine flutter, smooth banking, ~half the moves end in a rest, 35% aim for a
perch (`setPerches` from `MainActivity.setupButterflyPerches()` = nav-bar top + `ll_top_actions`).
Dropped `setCallback(this)` (a View isn't a Drawable.Callback; the drawable is driven manually).
Verified: 3 beautiful red-monarch Lottie butterflies fluttering over the purple home.

### 8.5ae 🎨 Nav floating+colourful; owner's full plan for the overhaul (2026-07-25)

Bottom nav now matches the reference dot-to-dot: **floating rounded white bar** (`bg_nav_bar.xml`,
28dp), **lifted** (wrapper marginHorizontal 12 / marginBottom 16, elevation 10), and **per-tab
colourful icons** — `MainActivity.navColorFor(i)` tints: By Number violet (white when selected on the
pill), New blue #4C8DFF, Diamond gold #F5A623, By Images pink #FF5CA8, By Pixel teal #22B3A8
(via `setColorFilter(SRC_IN)` in the nav-update loop). Verified on device.

**Owner's remaining plan (do it all, test it myself, then report what to check):**
1. Complete the home "dot to dot": category **pill chips** (colourful, w/ icons — in the fragments,
   currently plain TabLayout text) + the **premium crown card** ("Unlock All Artworks / Claim Now").
2. **Splash screen** with game-like **motion animation**.
3. **Intro / onboarding** screen.
4. **18+ / mature-content consent gate** (first launch): the app will carry some horror / mature
   (18+) content, so a content-warning + "I'm 18+ & Agree" screen, persisted so it shows once.
Owner wants autonomous completion + self-testing, then a consolidated "here's what to look at".

### 8.5af 🎨 DESIGN SYSTEM implemented in the native app — foundations + Home (2026-07-30)

Owner approved the full UI/UX design (10 screens + system, published artifact) and said **"start
design"**. Implementation began native-side, foundations first so every later screen is a
re-skin rather than a rewrite.

**New files — the single source of truth. Read these before styling anything new:**
- **`res/values/cm_tokens.xml`** — 5 paints (violet `#7A5CFF`, sky, blossom, turquoise, gold),
  violet-biased neutrals (`cm_paper #F6F3FC`, `cm_ink #201636`, `cm_ink_2`, `cm_line`), the dark
  premium surface, semantic success/caution **kept outside the paint set**, radius (12/20/24/28),
  4pt spacing scale, component sizes, 8-step type scale, motion durations.
- **`res/values/cm_styles.xml`** — `CmText.*` (Display/Title/Section/Card/Body/Label/Caption/Data),
  `CmButton.*` (primary/Ghost/Soft/Gold/Small), `CmChip.*`, `CmCard`.
  **Display face is Quicksand**, which already ships in `res/font` — no new font, no APK cost.
  Titan One stays reserved for the wordmark alone.
- **`res/drawable/cm_*.xml`** — buttons (ripple + gradient), cards, chips, `cm_bg_paper` (the app
  ground: lilac gradient + two faint paint blooms), `cm_progress`, `cm_icon_btn`, `cm_hero_gradient`,
  8 soft-3D category balls (`cm_ball_*`) + 8 glyphs (`cm_glyph_*`), `cm_ic_coin`, `cm_avatar_bg`.
- **`Utils/CategoryStyle.java`** — maps a **server category string** onto its ball + glyph + title
  case. Categories arrive with no id/icon/order (§3.2), so the look is derived from the name;
  unknown names hash to a stable pick, so a category the backend invents tomorrow still arrives
  dressed and always looks the same.

**Applied so far:**
- **Home shell** (`activity_main.xml`): ground swapped from the purple aurora `bg_home_modern` to
  `cm_bg_paper`; the header is now **avatar + time-of-day greeting + name** instead of a wordmark
  (`MainActivity.applyGreeting()`), with a **gold coins chip** and two icon buttons.
  ⚠️ **Every bound id was preserved** — `tv_title` (the intro animation springs it), `ll_brand`,
  `ll_top_actions`, `iv_saved_top`, `iv_setting`, `iv_removeAds` (now the coin icon inside the
  chip). New ids: `cm_coins_chip` (whole chip is the tap target → Premium), `tv_greeting`,
  `tv_coins`, `tv_avatar_initial`. **`MainActivity.initBackgroundLayers()` also had to change** —
  it sets the background resource in code and would have overridden the XML.
- **Nav** retuned to the token palette (`nav_selected #7A5CFF`, `nav_pill #F0EBFF`,
  `navColorFor()` now returns the five paints).
- **New 2026 tab** (default screen): the plain text `TabLayout` is now a row of **category pills**
  (soft-3D icon ball + name) via `setCustomView` — so **all existing tab-selection logic is
  untouched**, only the look changed. Selected = solid white + violet edge + lift; unselected =
  translucent white at 96% scale. Empty state uses `CmText.Section` + `CmButton`.

**Checked, not broken:** no layout that renders inside the home container uses white text, so the
light ground is safe for the 4 paid tabs (white text exists only in activities/dialogs that carry
their own background). `setAppearanceLightStatusBars(true)` was already set, which is *correct* for
a light ground — it was arguably wrong on the old purple one.

**⚠️ NOT verified on a device.** `assembleDebug` passes, but the test phone (CPH2333) locked with a
PIN and then disconnected mid-session, so nothing here has been seen running. **First job next
session: install and look at Home + New 2026.**

**Deliberately NOT touched yet** (shared components — a restyle changes the paid tabs too, needs a
decision): `ColoringsAdapter`'s grid tile, `color_item.xml`, the game/reveal/diamond screen chrome,
Premium, Profile, Settings. Roadmap order for the next pass: artwork tiles → reveal + game screens
→ premium → profile.

### 8.5ag ✅ Design system pass 2 — rolled through the app, VERIFIED ON DEVICE (2026-07-30)

**The highest-leverage edit of the whole pass:** `hint_text_color` (#688C8D teal-grey) was the app's
legacy accent — **~103 references**, used as *both* text colour and button/badge fill. Re-pointing
that one name (plus `hint_bg_color`, `hint_inner_highlight`, `hint_text_color_`) to the design
system's violet **re-skinned the entire app in a single edit**, with no layout churn. Contrast
improved as a side effect: #688C8D on white was 3.6:1, #7A5CFF is 4.6:1.
→ **Do not "clean up" these four names into new ones.** Their reach is the point.

Also applied: artwork tile (`item_coloring.xml` → radius 20dp, `cm_elev_card`, glass favourite
circle; favourite tint red→blossom in `ColoringsAdapter`), reveal + diamond game chrome
(`bg_hint_btn`→`cm_glass_panel`, `selector_tool_bg`→`cm_tool_bg`, Titan One→Quicksand for the
progress readout, violet spinners), palette swatch (`bg_color_selected_ring` magenta→violet,
badge text→violet, number face→Quicksand), tab indicators (#171717→violet), and **Premium turned
into the dark tier** by redefining `premium_peach_bg` #FFF9F0→#150F2C plus its plan-row drawables —
`PremiumActivity` swaps `bg_premium_plan_row_peach`/`_outline` in code, so **redefining those two
drawables changed selection styling with zero Java edits**.

**⚠️ Premium system bars:** the top of that screen is the LIGHT hero artwork and the bottom is the
dark tier, so it needs `setAppearanceLightStatusBars(true)` + `setAppearanceLightNavigationBars(false)`.
Setting both to dark-mode made the clock unreadable — fixed, don't "simplify" it back.

**✅ Verified on device (CPH2333), screenshots:** Home (paper ground, avatar + time-of-day greeting,
gold coins chip, violet nav pill, per-tab paint icons) · **By Number renders correctly on the light
ground — no regression to the paid content** · Premium reads as a genuine tier · Diamond game chrome
in glass + violet. **The accent sweep is visible in the By Number banner**: "PREMIUM OFFER", the
trial line, "Claim Now" and the page dot all went teal→violet together.

**🎉 The ad fixes from §8.5ah are also confirmed live** — the Diamond screen (which had NO ads at
all before) now shows a filling banner.

**Still to do:** Profile/Settings screens, the Home content itself (Continue card, Daily Challenge,
Trending rows — currently New 2026 shows "Coming soon" because no ColorForge server is configured),
and the AI generator / rewards / daily-challenge screens from the design, none of which exist in the
app yet.

### 8.5ah 🎨 Splash rebuilt — network GIF removed, drawn by the app instead (2026-07-30)

Owner: *"splash screen ma gif remove kari de ane simple or creative splash banavi ne de animation sathe."*

**What the GIF cost.** `SplashActivity` downloaded `promptgptpro.ai/splash_screen_animation.gif`
on cold start (Glide + a `GifCacheManager` file cache + a static first-frame fallback), then held
the screen for **`GIF_DISPLAY_DURATION_MS = 9500`** — nine and a half seconds — whether or not
initialisation still needed the time. All of that is gone: no network, no cache, no Glide on this
screen, and the safety timeout drops 12s → 8s.

**What replaces it** — everything drawn by the app:
- `widget/PaintParticleView.java` (new): soft blobs of the five paints drift upward with a sine
  sway, plus expanding rings — *a bead of paint landing and spreading*, the app's own gesture as
  ambient motion. Self-driving via `postInvalidateOnAnimation`, stops when detached, and honours
  the system "remove animations" setting by drawing one static frame.
- `drawable/cm_splash_gradient.xml` — violet→purple→blossom, with a turquoise glow low-left so it
  does not read as a stock purple-pink gradient.
- `drawable/ic_cm_mark.xml` — the brand mark as a vector: one brush stroke closing into a C with a
  paint-drop counter-form. **Titan One is used here for the wordmark, which is exactly what it is
  reserved for** (§8.5af).
- `SplashActivity.playIntro()` — mark springs in (overshoot 2.1, 640ms), wordmark follows at 300ms,
  tagline at 520ms, loading row at 680ms; each overlaps the last so it reads as one gesture.
  `startTrackSweep()` runs a highlight along the loading track until navigation.

**The gate field was renamed `gifFinished` → `introFinished`** and now opens after
`INTRO_HOLD_MS = 1750`. Navigation still requires **both** the intro hold *and* `initCompleted`, so
a slow ads/IAP init keeps the paint drifting rather than flashing past — the original two-condition
logic is intact, only the first condition changed meaning.

**Copy:** tagline "Colour your calm"; the status line says **"Mixing your colours…"** instead of
"Loading..." — it says what is happening in the app's own voice.

**⚠️ Built + installed, NOT seen running.** The owner was using the phone (Settings was foreground)
and every screencap caught another app. No crash in logcat after launching SplashActivity directly.
**Owner should just open the app once and look.**

**Left behind on purpose:** `Utils/GifCacheManager.java`, `drawable/splash_first_frame` and the
`BACKGROUND_GIF_URL` home GIF path still exist — `MainActivity` references them and its GIF is
already disabled at the top of `loadBackgroundGif()` (§8.5ab). Cleaning those out is an APK-size
task (roadmap item 6), not a splash task.

### 8.5ai 🐞 Ads — three real defects found and fixed (2026-07-30)

Owner: *"ads not proper show"*, then *"by image list to show inter ads not reword ads"*, then
*"inter ads ave e pela a screen ave che e ma statusbar sarkhu kar"*.

1. **The reveal (New 2026) game screen never asked for an ad.** `activity_reveal.xml` reserved
   `container_banner` — which ships with a "Loading Ads" spinner inside it — but `RevealActivity`
   had **no ad code at all**, so that placeholder sat there for good. Now requests the banner like
   the By Number game and hides the strip when ads are off. **DiamondActivity had no ad slot at
   all**; added the same one. *(Verified on device: the Diamond screen now shows a filling banner.)*
2. **Debug builds could never fill.** The manifest carries Google's TEST app id in debug, while the
   ad unit ids pushed from Firebase are the real publisher `ca-app-pub-2992947096890498`. **AdMob
   will not fill a unit belonging to a different publisher than the app id**, so every debug request
   was doomed. `AppPreference.getString()` now returns Google's official **test unit ids in debug
   builds only** (`debugAdUnitId()`), making debug self-consistent. Release is untouched.
3. **Stuck placeholder.** `AdController.hideBannerSlotIfEmpty()` collapses a slot that has no real
   ad view after 12s, instead of showing "Loading Ads" forever.

**By Images: rewarded → interstitial.** `MainActivity.startEditing()` gated non-free items behind
`showRewardedAd`. Now `showInterstitial(this, () -> startDownload(item))`.
**⚠️ This is a monetisation change, not just an ad-type swap:** rewarded meant *watch a video to
unlock*, and the picture did **not** open if no video was available. `onAdClosed()` fires whether the
ad showed, failed or was skipped — so **all By Images content is now free to open**. The owner asked
for interstitials explicitly and was told this. The middle option, if he changes his mind: interstitial
for free pictures, rewarded only for locked ones. Frequency is remote config, not code
(`key_id_showicon = 2` → every 2nd open; set 1 in Firebase for every open, no rebuild).

**The "Loading Ads…" sheet was cut off at the status bar.** Cause: **a `Dialog` gets its own Window,
which does NOT inherit the activity's edge-to-edge layout** — so the sheet stopped below the status
bar and the home header showed through above it. Fixed centrally in
`AdController.makeFullScreen(Dialog)` (MATCH_PARENT, transparent bars, `setDecorFitsSystemWindows
(false)`, dark bar icons for the light sheet), wired into **both** ad paths — `AdsManager` (V1, the
active one) and `AdsManagerMediation` (M1). The sheet itself is now the app's paper ground with a
violet spinner and a second line, "Your picture opens next".

**Still needed from the owner:** the real `ADMOB_APP_ID` (release builds fail without it by design,
§8.5e) — and worth checking the AdMob dashboard, because the **live v13 build was made from source
that had the test app id**, so it may have been earning nothing. Also: **there is no UMP/CMP consent
form anywhere in the app** (grepped, nothing) — AdMob requires one for EEA/UK users, who therefore
get no ads at all. Irrelevant for India traffic, but it caps revenue and is a policy risk.

### 8.5aj ⚡ Cold-start performance — measured, then fixed (2026-07-30)

Owner: *"app bov load le che, smoothly banay."* Measured first (§8.3's own rule), with
`am start -W` + `logcat Displayed/Skipped frames`. **Tap → usable Home was 15.5 seconds.**

| Stage | Before | After |
|---|---|---|
| Process start → splash's first frame | **3.28 s** (of solid black) | **2.10–2.22 s**, and branded from ~0.2 s |
| Splash → Premium | **8.1 s** | **4.0 s** |
| Process start → Premium | **11.3 s** | **~4.1 s** |

**What was actually wrong — four separate things:**

1. **`GlobalApplication.onCreate()` ran everything inline on the main thread**: the ThreeTen
   timezone DB, a JSON parse + network call (`ImageManager.init`), a Firebase handshake, a
   **SoundPool decode of every sound effect**, and three notification channels. None of it is
   needed to draw the first frame. Now one `cm-startup` background thread does the lot;
   `SoundUtils.initSounds` was dropped entirely because `playSound()` already builds the pool on
   first use. **`ImageManager.getInstance()` now self-initialises instead of throwing** — deferring
   the init created a race and a thrown exception is a far worse outcome than building it on
   demand.
2. **`Theme.Splash` had `windowBackground = @android:color/black`.** That is what the system paints
   before any app code runs, so every cold start was ~2.4 s of black. Now it is
   `cm_splash_gradient`, so the brand is on screen almost immediately — the biggest *perceived*
   win, and one the "Displayed" metric does not show.
3. **The splash waited for the whole ads/IAP/Firebase chain** before it would navigate — 8 s.
   Added `INIT_DEADLINE_MS = 2500`: after 2.5 s it proceeds and lets init finish in the background.
   Nothing on Home needs the ad SDK to be ready.
4. **`MobileAds.initialize()` was called on the main thread** (~2.7 s of dropped frames). Now on a
   background thread in both `AdsManager` and `AdsManagerMediation`, with the ad **preloads posted
   back to the main thread** — loading an ad is a main-thread API.

**🔴 The bitmap landmine, worth remembering:** `premium_hero_art.png` (1024×1536) sat in
**density-less `drawable/`**, which Android treats as **mdpi** — so on this 480 dpi phone it was
upscaled 3× and decoded to **3072×4608 = 54 MB of RAM**, on the main thread, during inflate. Moved
to **`drawable-nodpi/`** → 6 MB, and it now loads via Glide off the main thread. `splash_first_frame.png`
(used by WelcomeActivity) had the same problem: 32 MB → 4 MB. **Check any large PNG's folder before
blaming the code** — and this is very likely why `android:largeHeap="true"` was needed at all.

**⚠️ Still on the table, and it is the owner's call, not a bug:** after the splash the app shows
**PremiumActivity plus two AdActivities before Home** — that chain is most of the remaining launch
time. A ~123-frame stall also persists just before Premium (Firebase/Crashlytics/FA/Facebook SDKs
all initialise via ContentProviders at process start, largely outside our control).

**Paywall cadence (owner's decision, 2026-07-30):** `SplashActivity.shouldShowPaywallNow()` counts
cold launches in `app_prefs/launch_count` and opens Premium on **launch 1, 4, 7…**
(`PAYWALL_EVERY_N_LAUNCHES = 3`); the other launches go straight to Home. Premium is still reachable
from the coins chip and Settings, so this costs exposure, not access. **Verified on device across
four launches: Premium → Home → Home → Premium.**

**Final measured result: tap → Home was 15.5s, now ~7s** (and ~6.7s on a paywall launch), with the
brand on screen from ~0.2s instead of 2.4s of black.

*(Device-testing note: deleting `shared_prefs/app_prefs.xml` to reset the launch counter also clears
`age_consent_v1`, so every launch then hits the ConsentActivity gate and the paywall path never
runs. Restore it by piping a file in: `adb shell "cat /sdcard/app_prefs.xml | run-as <pkg> sh -c
'cat > shared_prefs/app_prefs.xml'"` — `run-as` cannot read /sdcard directly, and `cp` fails.)*


### 8.5ak 🔧 Gradle JVM pinned to JDK 21 (2026-07-31)

**Symptom:** Android Studio refused to sync — *"Gradle 8.13 is incompatible with the Gradle JVM
version 25 ... supports Java versions between 1.8 and 23."*

**Cause:** Android Studio's bundled JBR **auto-updated to Java 25**. Every earlier build note in
this file said to use `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` — that advice is
now wrong, because that path is a JDK 25.

**Fix:** `gradle.properties` now pins the daemon JVM:
```properties
org.gradle.java.home=C:\Users\User\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2
```
That JDK (Temurin 21.0.7) was **already on the machine** — Gradle had auto-provisioned it, so
nothing had to be downloaded. Pinning it in `gradle.properties` fixes **Studio and the command line
together**: `.idea/gradle.xml` has `gradleJvm = #GRADLE_LOCAL_JAVA_HOME`, which is precisely the
macro meaning "use `org.gradle.java.home`", so Studio resolves to 21 as well.

Verified: `./gradlew.bat --version` -> `Daemon JVM: ...eclipse_adoptium-21... (from org.gradle.java.home)`
and a full `assembleDebug` succeeds **with no JAVA_HOME set at all**.

**Note the split:** the *launcher* JVM stays whatever started Gradle (25); only the **daemon** JVM
compiles, and that is what the version rule applies to.

**⚠️ The path is machine-specific**, like `local.properties`. On another PC point it at that
machine's JDK 17/21, or delete the line and pick the JDK in
*Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK*.

**🐞 Follow-up the same day — `jlink executable ... does not exist`.** After the pin above, builds
from an IDE still failed with:
```
Cause: jlink executable C:\Users\User\.antigravity-ide\extensions\redhat.java-1.55.0-win32-x64\
       jre\21.0.11-win32-x86_64\bin\jlink.exe does not exist.
```
That path is the Red Hat Java extension's bundled **JRE** — it has `java.exe` but no `jlink`, no
`jmods`, no `javac`. **The cause was NOT an IDE setting** (three passes of `.vscode/settings.json`
and user settings changed nothing): **Gradle scans the machine for JVMs itself**, found that JRE,
and since it is **21.0.11 — newer than the pinned 21.0.7** — it won any "a Java 21 installation"
lookup, then broke because it is not a JDK.

**Fix, in `gradle.properties`:**
```properties
org.gradle.java.installations.auto-detect=false
org.gradle.java.installations.auto-download=false
```
Now `./gradlew.bat javaToolchains` reports `Auto-detection: Disabled` and exactly one installation
(the pinned Temurin 21, "Detected by: Current JVM"). Safe because nothing in this project requests a
Java toolchain — `app/build.gradle` only sets source/targetCompatibility 11.

**Lesson:** when a JVM path appears that nobody configured, suspect Gradle's own auto-detection
before the IDE. A *newer patch version* of the right major version will out-rank the one you pinned.

*(Also set, and harmless to keep: `java.jdt.ls.java.home` / `java.import.gradle.java.home` /
`java.configuration.runtimes` in the IDE's user settings and in `.vscode/settings.json` at the
workspace root — which is `C:\Users\User\Downloads\colour mate\colour mate`, two levels ABOVE the
Android project. VS Code-family editors only read `.vscode/settings.json` at the workspace root.)*

### 8.5al 🔴 DISK CORRUPTION — the colour-fill engine was zero-filled (found 2026-08-09)

**Symptom:** every colour upload in the admin lands on a red **"Generation failed — try Regenerate
below"** card, "Auto quality check: Not generated yet". Diamond uploads succeed normally.

**Cause — not a bug, a dead disk write.** Five files under `platform/backend/services/` have the
right size and mtime but their contents are **entirely `\x00`**:

| File | Bytes | In git? |
|---|---|---|
| `reveal_game_builder.py` | 43,690 | only an ancestor, 40,911 B, as `backend/services/…` |
| `number_tab_builder.py` | 12,262 | **NO — built 2026-07-21, after the `platform/` move** |
| `lineart_region_builder.py` | 12,823 | ancestor, 7,558 B |
| `color_paint_builder.py` | 10,811 | **same size in git — likely identical** |
| `ai_lineart_local.py` | 7,672 | ancestor, 7,477 B |

**Their `__pycache__/*.pyc` copies are zeroed too**, so there is no bytecode fallback — do not waste
time trying to import or decompile them. 40 files were hit in total; the rest are the root
`README.md` + 4 `*_REPORT.md`, `runpod-worker/app.py`, `models/lineart_sk_model.pth` (17 MB),
`content.db.wiped`, and disposable build caches. **The 9 git-tracked ones were restored 2026-08-09
with `git checkout --`.** `models/anime2sketch_netG.pth` (217 MB) is intact.

**Why the failure is silent:** `_process()` (`app.py:290`) swallows the exception with a bare
`except Exception` and only writes `status="error"` — **no message, no traceback, nothing in the
audit table**. That is why the admin can only say "failed". *Worth adding a `logging.exception` and
an error column if this is ever touched again.*

**A prior session papered over it with stubs** — `services/reveal_game_builder_recover.py` (2.9 KB)
and `number_tab_builder_recover.py` (4.3 KB), imported at `app.py:36` and `number_tab.py:74`. The
stub's `build_color()` **raises on purpose** ("reveal_game_builder was wiped"). So the engine is not
merely broken, it is wired to refuse. **Delete the stubs and restore the two imports** once the real
files are back, then **restart the server** — `admin.html` is re-read per request but Python modules
are not.

**Recovery search, so it is not repeated:** all four drives (C/D/E/F) hold no other copy; restore
points / shadow copies need an elevated shell (`Access denied` from a normal one); the Recycle Bin
has nothing relevant; no `colorforge` archive left in `Downloads`. **Owner has a backup elsewhere
(office PC / Drive) and is fetching it (2026-08-09)** — that is the right source, because it carries
the Jul-25 tuning *and* `number_tab_builder.py`, neither of which git can supply.

**Lesson worth keeping:** `platform/` being untracked (§1) is what turned a routine disk fault into
an unrecoverable one. The four files git *could* offer are only there by accident, from the old
`backend/` tree deleted during the move. **Commit `platform/` — or at least `platform/backend/`
minus `data/` and `models/`.**

**State of the DB when this was found:** the catalogue is empty (0 categories, 0 published);
`content.db` was recreated 2026-08-09 12:28 and the older one sits beside it as `content.db.wiped`
(itself now zero-filled). Two image rows: `0605ead6f466` colour/`error` (the snow globe) and
`9010165de9f0` diamond/`submitted` (22288 beads, 32 colours) — the diamond one generated fine,
which is the clean proof the fault is confined to the colour engine.

### 8.5am 🎨 Admin failure state — real reasons, and a CSS bug that inflated every loose icon
(2026-08-09)

Triggered by §8.5al: the drawer for a failed item showed a **200 px lock outline**, a yellow bar
saying *"Generation failed — try Regenerate below"* (advice that could not work), and a footer whose
only content was the dead word "Failed" — no delete, no retry.

**The icon bug is worth remembering because it is repo-wide.** `ic()` (`admin.html:194`) returns a
bare `<svg viewBox="0 0 24 24">` with **no width/height**. Every call site is rescued by a scoped
rule — `.nav a svg`, `.btn svg`, `.flag .fb svg` … — so an icon dropped anywhere *without* such a
rule stretches to fill its flex line. That is exactly what the "Admin-only preview" lock did. Fixed
once, globally: a base `svg{width:16px;height:16px;flex:none;stroke-width:1.9}`. Element specificity
(0-0-1) loses to every existing class rule, so nothing else moved. **Any new `ic()` call site is now
safe by default.**

**The failure now states its cause.** `_process`/`_process_diamond`/`_regenerate` all ended in a bare
`except Exception: db.update_image(iid, status="error")` — the exception was **discarded**, logged
nowhere, so the admin *could not* say more than "failed". Added:
- `db.py` — an `error TEXT NOT NULL DEFAULT ''` column via the existing `_IMG_COLS` migration
  (applies itself on the next `db.init()`, i.e. on boot) and added to `update_image`'s allow-list.
  `db.images()`/`get_image()` are `SELECT i.*`, so it reaches the client with **no API change**.
- `app.py` — `_fail(iid, exc, stage)` does `log.exception(...)` **and** stores `str(exc)[:400]`.
  Success paths now write `error=""`, so a stale message cannot outlive the failure.
- `admin.html` — an `.errbox` card (alert glyph, headline, the engine's own words in a `<code>`
  block, and "your upload is safe, no need to re-upload"). On a failure the **Auto quality check and
  Tier blocks hide** (nothing generated, nothing to price), the thumbnail desaturates, the lock line
  goes, and the footer becomes **Delete · Try again**. `Try again` = regenerate at defaults
  (`{simplify:''}` / `{mode:'auto'}`), so it reuses the proven path. Library cards show the reason
  in the existing `.rej` chip (now 2-line clamped) and gained the same Try-again button.

**Verified end-to-end, not assumed:** restarted under the venv → migration added the column → logged
in over the API → retried the failed item → the row came back
`status='error'`, `error='reveal_game_builder was wiped — cannot generate new colour-fill images…'`,
and the traceback (with file + line) appeared in the server log for the first time. The card itself
was rendered in headless Chrome against the page's real CSS.

**Aside:** `app.py:664` defines `_STUDIO_HTML`, a whole second admin page that **nothing references**
— `/` serves `admin.html` (`app.py:658-661`). Dead weight; do not edit it by mistake as I nearly did.

### 8.5an ✅ COLOUR ENGINE RESTORED from git — generation works again (2026-08-09)

The owner said he would fetch a backup (§8.5al) but kept hitting the failure, so the engine was
rebuilt from what git actually had. **Colour-fill generation, publishing and the app-facing API all
work again**, verified end-to-end on a real upload.

**What was installed, and how faithful each piece is — this matters if the backup ever arrives:**

| File | Source | Fidelity |
|---|---|---|
| `color_paint_builder.py` | git `backend/services/` | **identical** (same byte size as the corrupted copy) |
| `ai_lineart_local.py` | git | near-current (7,477 vs 7,672 B) |
| `reveal_game_builder.py` | git | **ancestor** — 40,911 vs 43,690 B; the Jul-25 tuning is NOT in it |
| `lineart_region_builder.py` | git | **noticeably older** — 7,558 vs 12,823 B |
| `number_tab_builder.py` | **rewritten** | never in git; reimplemented against the §3.8 spec |

**→ If the owner's backup turns up, prefer it over all five.** These are a floor, not a restoration.

**Two adaptations were needed** because `app.py` had moved on from the git API:
- `build_color(folder, flatten)` — git's takes one argument. Added `_flattened(flatten)`, a context
  manager scaling `COLOR_K` / `COLOR_TARGET` / `COLOR_LINE_RMIN` for the Simplify level. **This is a
  reconstruction of intent, not the original code** — the original's exact curve is unknown.
  ⚠️ It patches module globals, so it is **not thread-safe**; fine only because generation runs one
  job at a time on a background task.
- `_color_regions(master, target=COLOR_TARGET, k=COLOR_K)` had those knobs as **argument defaults**,
  which Python binds at import — so patching the globals would not have reached the fallback path.
  Changed to `None` sentinels resolved at call time. **Watch for this pattern in the other modules.**

**🐞 A real bug in the rewritten converter, caught by replaying the app's own decoder — the check
that matters, and the reason to keep doing it.** `id_map_bgra()` writes `id = R + G*256` with the
low byte in **channel 2** (cv2 is BGRA). The converter read **channel 0** (blue, always 0), so every
id came out a multiple of 256, almost no region matched, and the fill was transparent with labels
scattered on the top row. **0 of 175 anchors decoded.** After the fix: all of them.
**Counts alone would not have caught this** — the file sizes and region counts looked plausible.

**Second fix in the same file:** label anchors were written *before* the wall pass, so any anchor
whose centre sat on a line was overwritten — **46 of 221 regions silently lost their number**, and
an unnumbered region can never be filled in the app. Anchors now go on last, and a centre that lands
on a wall is relocated to the most interior free pixel of that region (distance transform).
**175 → 220 numbered of 221.** The last one is a region that is entirely line; one anchor still has
no non-wall neighbour, so the app cannot resolve its colour — 1 in 221, left as is.

**Verified, in this order, nothing assumed:**
1. `build_color` on the failed job → 205 regions / 42 colours in 3 s.
2. `export_color_bundle` → `regions.json` + `id_map.png` + `border.png` + `original.png` + 42
   swatches; **bbox confirmed `[x,y,w,h]`**, the convention the Android fix in §8.5q depends on.
3. Real multipart upload through `/api/items` → `review`, 205/42, `error` cleared.
4. Approve → `published`, `file_name = 20260809_engine_restore_test`.
5. Every app-facing URL 200: `/index.php/Number/category/`, `/gimg/c/animals`, all three `/thumb/…`
   files, and all four `/index.php/Number/reveal/…` assets.
6. The owner's own failed picture (`FNJmF8hGYD-pre-finish`) retried → `review`, 208 regions / 45
   colours.
7. Composited original / line-art / flat solution side by side — the picture is genuinely correct.

**Running on the pure-colour fallback, and that is a quality ceiling.** `torch` is not installed, so
`_load_lineart()` returns None and `_color_regions()` (colour segmentation) runs instead of the ML
line-art path. Output is good but the borders follow colour boundaries, not drawn lines.
**`models/lineart_sk_model.pth` was zero-filled AND `ensure_weights()` returns early whenever the
file merely exists — so the corrupt file was permanently blocking its own re-download.** It has been
**deleted**, so `pip install torch` is now the only step needed to get the ML path back (the weights
re-download themselves from HuggingFace). `anime2sketch_netG.pth` (217 MB) was never corrupted.

**Left in the library on purpose:** the published test item *"engine restore test"* — delete it once
the owner has looked at it.

### 8.5ao 🖊️ Line art fixed — torch restored + real ink weight on the outline (2026-08-09)

Owner, with a reference `contourv.svg` and two clean coloring-book renders: *"a svg file che — border ni
thickness, shape and color e svg image ma che avu j create thavu joye original image ma thi."* The
admin was producing **colour-contour** borders, not drawn lines.

**Cause, and it was not the border code.** `_game_data_color()` calls `_load_lineart()`, which needs
`ai_lineart.png`; without **torch** that returns None and the whole path silently degrades to
`_color_regions()` + `border_bgra()` — region boundaries, uniform 2 px. Every job created on this PC
had no `ai_lineart.png`; the older ones (from the machine where torch was installed) all do.
**The "wrong border" was the torch fallback, not a rendering bug.**

**Fixed:** `pip install --index-url https://download.pytorch.org/whl/cpu torch` → 2.13.0+cpu, and
`ai_lineart_local.ensure_weights('lineart')` **re-downloaded `lineart_sk_model.pth` by itself** (10 s,
17,173,511 bytes — byte-identical in size to the corrupted original). This only worked because §8.5an
deleted the zero-filled file; `ensure_weights()` returns early whenever the file merely *exists*.
Result on the owner's snow globe: **208 regions / 45 colours (contours) → 124 / 57 (real line art)**,
~6-10 s per image on CPU.

**Then the weight.** The model returns a soft pencil sketch — mid-grey strokes on off-white paper —
and `_ai_border_bgra()` copied that straight into alpha (`255 - line`), so lines came out pale and
all one width. It now (a) clears paper grain below `LINE_PAPER`, (b) gamma-pushes the survivors
toward solid black (`LINE_GAMMA`), and (c) **dilates only strokes at/above `LINE_BOLD_INK` by
`LINE_BOLD_PX`**, so main contours are drawn heavier than the fine detail — the varying line weight
of a printed page. Four settings were rendered side by side and the owner-facing default is the
lightest that still reads as ink: `PAPER 26 / GAMMA 0.62 / BOLD_INK 140 / BOLD_PX 1`. **`BOLD_PX 3`
was tried and is too heavy — the small shapes fill in solid.** These four constants are the dial if
he wants it bolder or finer; nothing else needs to change.

**Verified through the real admin**, not just the library: uploaded via `POST /api/items` after a
restart → `review`, 124 regions / 57 colours, and the server wrote its own `ai_lineart.png`.

**Still different from the reference, and worth saying plainly:** the reference is a *vector* file, so
its strokes are smooth curves at any zoom; ours is a raster outline (`border.png`) whose weight now
matches but whose edges are pixels. The bundle's `border.svg` still traces the **colour regions** at
`stroke-width 1` — it is not the artistic line. Tracing the inked line-art into paths (bold/thin as
separate stroke widths) is the next step if he wants a true SVG. Also, the model keeps some hatching
and shading the reference does not have; the admin's **Regenerate → Light/Medium/Strong** already
thins that per image.

**⚠️ Deployment note:** torch is a real dependency of good output. It **IS** already listed in
`requirements.txt` — the venv simply was never built from that file (§2 installs a hand-written list
that omits it), which is why the fallback was running. On Linux a plain `pip install torch` pulls the
**CUDA** build (multi-GB); always use `--index-url https://download.pytorch.org/whl/cpu`. Without
torch the server silently produces the old contour borders again, with no error anywhere.

### 8.5ap ✅ New 2026: "Coming soon" replaced by a real load (2026-08-20)

Owner: *"coming soon che e ne badle list call kar and proper implementation kar."*

**The placeholder was hiding a genuine bug, not just missing content.** `New2026Fragment.loadCategories()`
opened with `if (!ColorForgeApi.isConfigured(...)) { showMessage("Coming soon"); return; }` — and
`Manager`'s constructor fetches Remote Config **asynchronously** (`Utils/Manager.java:270`). On a cold
launch the tab therefore checked before the URL activated, concluded "no server", and **never asked
again** — the only retry was a tap on the message. So the owner could set `colorforge_base_url` in
Firebase and still see an empty tab forever. That is why it never looked configured.

**What changed**
- `ColorForgeApi.refresh(ctx, cb)` — `fetchAndActivate()`, then reports the resolved base URL on the
  main thread; it *always* calls back (falls through to the current value if Firebase is missing) so
  a caller can never be left hanging.
- `New2026Fragment` no longer dead-ends: unconfigured → `showLoading()` + `refresh()` → if a URL
  arrives it loads the list, otherwise it shows **"No server set up yet — tap to retry"**. The tap
  now clears `configRefreshed` too, so a manual retry re-asks Remote Config, not just the HTTP call.
- `DEFAULT_BASE_URL` (const `""`) → `defaultBaseUrl()`: **debug** returns `http://127.0.0.1:8000/`
  (the `adb reverse tcp:8000 tcp:8000` setup from §8.5l), **release** stays `""`. A shipped APK still
  carries no hard-coded host.
- Layout text `"Coming soon"` → `@string/new2026_loading`.

**Default tab, and the trap in it.** The comment at `MainActivity:365` said the default was moved
`NAV_NEW → NAV_NUMBER` *only* because New 2026 rendered "Coming soon". That reason is gone, so it
opens on New 2026 again — but the test is **`hasExplicitServer()`**, not `isConfigured()`. In a debug
build `isConfigured()` is now *always* true (localhost fallback), and localhost only answers while
`adb reverse` runs — keying off it would open the app on an error screen. `hasExplicitServer()`
ignores the debug fallback and asks only about a real override / Remote Config value.
⚠️ `setupFragments()` and `setupBottomNavigation()` **must** agree on the tab or the app opens on an
empty container, so the choice is made once into `defaultNavIndex` and both read that field.

**✅ VERIFIED ON DEVICE** (CPH2333, `adb reverse tcp:8000 tcp:8000`): the New tab shows the
**Animals** chip and the published picture — no "Coming soon" anywhere. Logcat confirms it now
actually calls the list:
`New2026Fragment: Categories URL (attempt 1): http://127.0.0.1:8000/index.php/Number/category/`.
The grid thumbnail is the **new ML line art** (§8.5ao), so both fixes are visible in one screen.

**Still a placeholder, deliberately untouched:** the **Diamond** tab (`fragment_diamond.xml` —
title/steps/"Try a sample"/`diamond_more_soon`). It has no gallery at all, and unlike New 2026 there
is **no diamond feed on the backend** to call — `number_tab.py` serves colour items only. Building it
means adding a server-side feed first; it is not a fragment-level fix.

### 8.5aq 💎 Diamond tab: real feed + real gallery (2026-08-20)

Roadmap item 3's last piece. The Diamond tab was a placeholder screen (title / 3 steps / "Try a
sample"); the **game** has been playable since §8.5d, only the gallery was missing — and it was
missing because **the backend had no diamond feed at all**, not because of anything in the fragment.

**🐞 Found while wiring it: `_publish()` was wrong for diamond items.** It ran
`R.export_color_bundle(folder)` unconditionally — spending the entire line-art + region pass to
produce colour-fill data a bead grid can never use — and **never stored `grid.json`**, the one file
the game actually plays. It also skipped `file_name`/`publish_ts` for diamond, so such an item could
never be addressed by the app at all. Publish now branches on `type`:
colour → the bundle; diamond → `grid.json` + `beads.png` + `canvas.png` + `original.png`; both get
`thumb.png` and a fileName.

**Backend — `diamond_tab.py`** (new), routes mirroring the Number tab so the app reuses its list code:

| Route | Serves |
|---|---|
| `/index.php/Diamond/category/` | `{"categories": [...]}` |
| `/index.php/Diamond/gimg/c/{cat}` | `{"data": [...], "next": ""}` |
| `/dthumb/{cat}/t/{file}.png` | gallery thumbnail |
| `/index.php/Diamond/data/{cat}/{file}.json` | the bead grid |

Storage sits under `diamond/…`, apart from the Number tab's `number/…`, **so a colour and a diamond
picture may share a category name without colliding**. No upstream merge exists here — diamond
content is ours alone. `db.number_categories()` / `number_items()` grew a `kind="colour"` default, so
every existing caller is untouched.

**App**
- `ColorForgeApi`: `diamondRestRoot / diamondCategoriesUrl / diamondGridUrl / diamondThumbUrl`.
- `Manager.getDiamondImagesByCategory()` — the same `RestDataSource`, different root.
- **`Level.diamond`**, parcelled, stamped in `parseAndAddResults` from
  `baseUrlOverride.contains("/Diamond/")`. ⚠️ Needed because that method sets `colorForge = true`
  for *any* override, so without this flag a diamond row would ask for a colour `border.png` and the
  whole grid would show broken thumbnails.
- `ColoringsAdapter.buildThumbnailUrl()` → diamond rows use the finished-piece thumbnail (a bead
  grid has no line art to show).
- `DiamondFragment` is **derived from `New2026Fragment`** rather than written fresh, so both
  ColorForge tabs behave identically and it inherits that screen's fixes (the Remote Config retry of
  §8.5ap and the `setSource()` snapshot bug). Tap → `DiamondActivity.fromUrl(gridUrl, fileName)`;
  fileName doubles as the progress key, which is safe because it is globally unique and never reused.

**Verified on the real server** (publish → feed → assets), not just compiled:
`20260820_finished` published → `categories ["animals"]` → gallery row with
`key/fileName/category/timeStamp/isNewToday` (all five `Level.fromJson` needs — §3.6#8's lesson) →
thumbnail 200 (185 KB) → grid 200 (59 KB, `cols 112 · rows 199 · palette 32`). A colour item was
published alongside and **both feeds list independently**, so the publish branch did not break colour.

**✅ VERIFIED ON DEVICE, whole chain.** Diamond tab → **Animals** chip → one picture whose thumbnail
is the finished piece (not line art, i.e. `Level.diamond` is being stamped and read correctly) → tap
opens `DiamondActivity` with the grid pulled live. Server log, in order:
`Diamond/category/` → `Diamond/gimg/c/animals` → `dthumb/animals/t/20260820_finished.png` →
`Diamond/data/animals/20260820_finished.json`, all 200. The board renders with its 32-colour palette
at 0%, so `DiamondLevel` parsed the payload.

**⚠️ Content note, not a code fault:** that picture is **112×199 = 22,288 beads**, and at that density
the board reads as noise rather than a picture — the same "too dense to be fun" problem the colour
side had in §8.5p. The fix is on the generating side (`diamond_builder` cols / palette), not in the
app; the owner has not been asked yet.

**Left over, harmless:** `diamond_title` / `diamond_subtitle` / `diamond_step_*` / `diamond_more_soon`
in `strings.xml` are now unused (the old placeholder layout is gone).
