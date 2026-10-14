# Deploying ColorForge — Hostinger VPS + AWS S3

> ## ✅ IT IS DEPLOYED — 2026-08-21
>
> **Live: `https://srv1919471.hstgr.cloud/`** (Hostinger VPS `187.77.141.63`, Ubuntu 24.04,
> 2 CPU / 8 GB / 100 GB). Admin, both feeds, and the whole app chain are verified working from a
> real phone over the internet. **Everything below is the original July plan and is now history** —
> read the "What is actually running" box first; where the two disagree, the box is right.
>
> ### What is actually running
> | | |
> |---|---|
> | Code + data | `/opt/colorforge` (`.venv` 1.2 GB incl. **torch 2.13.0+cpu** — generation runs ON the server) |
> | Service | systemd `colorforge.service`, uvicorn bound to **127.0.0.1:8000** |
> | Public | nginx reverse proxy, `client_max_body_size 50M`, `proxy_read_timeout 300s` |
> | TLS | certbot / Let's Encrypt, `--redirect` (http 301 → https), auto-renew |
> | Firewall | ufw: 22 / 80 / 443 only |
> | Storage | **`STORAGE=s3` since 2026-08-21** — bucket `color-mate` (ap-south-1, public-read), creds in `/opt/colorforge/storage.env` (chmod 600, loaded via the unit's `EnvironmentFile=`). All 633 pre-existing `data/cdn` files were migrated (the local copy remains on disk as a backup); published items were re-published so `base_url` is the S3 form. App routes 302-redirect to the bucket. ⚠️ The reveal route needed a fix for S3 (`app.py` — key is now built as `images/{iid}/{asset}` instead of parsed out of a `/cdn/` base_url); do not revert it. The same bucket also holds the Learn-to-Draw Laravel data (`drawings/`, `categories/`, `backups/` prefixes — no collision with `images/`, `number/`, `diamond/`). |
> | Auth | `data/.jwt_secret` generated **on the server**; boss password rotated to a random 20-char value in `/root/colorforge-admin-password.txt` (chmod 600) |
> | Merge | `number_merge = false` — New 2026 is ColorForge only |
> | Models | `models/` was NOT uploaded; the server re-downloaded the 17 MB lineart weights itself |
>
> **SSH:** `ssh -i ~/.ssh/colorforge_vps root@187.77.141.63`
> **Restart after a code change:** `systemctl restart colorforge` · **logs:** `journalctl -u colorforge -f`
> **Deploy a changed file:** `scp -i ~/.ssh/colorforge_vps <file> root@187.77.141.63:/opt/colorforge/<file>` then restart.
>
> ### The one thing still to do
> **Firebase Console → Remote Config → `colorforge_base_url` = `https://srv1919471.hstgr.cloud/` → Publish.**
> Until then a shipped app has no server (debug builds fall back to `127.0.0.1:8000`).
>
> ### 🐞 A bug that only exists on Linux — do not undo the fix
> Category folders are written **lower-cased** (`number/animals/…`) but a folder created by an older
> publish carries the category's original casing (`Animals`). Windows is case-insensitive so this
> never showed up locally; **on the VPS it was three hard 404s** on the app's `/thumb/…` files.
> `LocalStore.local_path()` now falls back to a **case-insensitive walk** (`storage.py`), and the
> legacy folder was renamed to lowercase. Keep both.


Decided 2026-07-23 with the owner: **images on AWS S3**, **AI engine on RunPod (GPU)**,
**everything else on Hostinger**.

> ### ⚠️ Buy a Hostinger **VPS**, not shared/web hosting
> Shared hosting runs PHP only — FastAPI will not start on it at all. The plan must say **VPS**
> (KVM). Anything else wastes the day.

---

## What actually has to move

Two different jobs live in this backend, and only one of them is urgent:

| Job | Needs | Where |
|---|---|---|
| **Serving** — the app asks for content | FastAPI + files | Hostinger VPS |
| **Generating** — a new picture → AI line art | torch + `models/` (224 MB), heavy CPU/GPU | Stays on the PC for now; RunPod later |

**The app is blocked on serving only.** Pictures are already generated and published, so do not try
to move the engine tomorrow — it is a separate, optional improvement.

### Files to upload (~160 MB)

```
platform/backend/*.py
platform/backend/services/
platform/backend/admin.html
platform/backend/requirements.txt
platform/backend/data/content.db      # which items exist / are published
platform/backend/data/cdn/            # the images the app requests (14 MB)
platform/backend/data/items/          # play_color.html + per-item working files (144 MB)
```

**Do NOT upload:**
- `platform/backend/models/` — 224 MB, only used when generating (`services/ai_lineart_local.py`)
- `platform/backend/data/.jwt_secret` — let the server generate its own (see security below)

---

## 🔒 Do these BEFORE it is reachable from the internet

1. **Change the boss password.** `db.py:98` seeds `kishankalathiya1111@gmail.com` /
   **`colorforge123`**. That is fine on a laptop and dangerous on a public URL — anyone who finds
   the address could log in and publish or delete content. Change it right after the first login.
2. **Let the server make its own JWT secret.** `auth.py` persists one in `data/.jwt_secret`. If the
   local file is copied up, tokens signed on your PC stay valid on the server. Delete it on the
   server and restart; a fresh one is generated.
3. **HTTPS.** Android blocks plain `http://` by default. The app currently allows it
   (`usesCleartextTraffic="true"` + a network-security config), but a public admin login over
   `http` sends the password in the clear. Use Let's Encrypt (step 5 below).

---

## Steps on the VPS (Ubuntu)

```bash
# 1. Python + tools
sudo apt update && sudo apt install -y python3-venv python3-pip nginx

# 2. Code + data (from your PC, or git + scp for the data)
#    upload to /opt/colorforge

# 3. Dependencies — WITHOUT torch (that is only for generating)
cd /opt/colorforge
python3 -m venv .venv
.venv/bin/pip install fastapi "uvicorn[standard]" python-multipart numpy \
    opencv-python-headless boto3 PyJWT

# 4. Run it (test first, in the foreground)
.venv/bin/uvicorn app:app --host 127.0.0.1 --port 8000
```

Once that works, make it survive reboots — `/etc/systemd/system/colorforge.service`:

```ini
[Unit]
Description=ColorForge backend
After=network.target

[Service]
WorkingDirectory=/opt/colorforge
ExecStart=/opt/colorforge/.venv/bin/uvicorn app:app --host 127.0.0.1 --port 8000
Restart=always
Environment=STORAGE=local

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now colorforge
```

**5. Nginx + HTTPS** — point a domain at the VPS IP, then:

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d yourdomain.com
```

Nginx server block proxies to the app:

```nginx
location / {
    proxy_pass http://127.0.0.1:8000;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    client_max_body_size 50M;      # admin uploads images
}
```

---

## ⚠️ Turn OFF upstream merge (New 2026 = ColorForge only)

`number_tab.py` defaults `number_merge=True`, which blends the paid ionicsolutions catalogue into
the New 2026 feed. The owner wants **ColorForge content only**. On the server, once:

```bash
.venv/bin/python -c "import db; db.set_config('number_merge', False)"
```

Without this, `/category/` and `/gimg/c/…` return thousands of other people's pictures.

## Then point the app at it

No app rebuild needed — the URL is read at runtime (§8.5b):

- **Firebase Console → Remote Config → `colorforge_base_url`** = `https://yourdomain.com/`
- or, for a dev build, `ColorForgeApi.setLocalOverride(context, url)`

The New 2026 gallery should then list real categories and pictures.

---

## AWS S3 (after the VPS works)

The code already supports it — **flip one env var**, no code change
(`storage.py`: `STORAGE=local | s3`):

```
STORAGE=s3
S3_BUCKET=your-bucket
AWS_REGION=ap-south-1          # Mumbai
CDN_BASE=https://xxxx.cloudfront.net   # optional but recommended
```

plus AWS credentials (env vars, `~/.aws`, or an IAM role). Existing files in `data/cdn/` have to be
copied into the bucket once (`aws s3 sync`).

**Why bother:** images stop eating VPS disk and bandwidth, and CloudFront serves them faster to
phones. Not urgent — the VPS alone works.

---

## RunPod (last, optional)

`runpod-worker/` already has the service and a Dockerfile (`/health`, `/enhance-image`,
`/generate-lineart`, `/detect-semantics`).

**⚠️ Verified 2026-07-23: the main backend does not call it anywhere** — a `grep` for
`runpod|RUNPOD|WORKER_URL|8100` across `platform/backend/` returns nothing. So deploying it changes
nothing on its own; `services/ai_lineart_local.py` would have to be taught to call it. That is a
code change, not just a deployment.

---

## Order that wastes the least time

1. Hostinger **VPS** → FastAPI running → domain + HTTPS
2. `colorforge_base_url` → **the app shows real content** ← everything is blocked on this
3. S3 (`STORAGE=s3`) — an improvement
4. RunPod — needs code first
