#!/usr/bin/env bash
# Runs ON the VPS, called by .github/workflows/deploy-backend.yml after it has rsynced the
# backend into $INCOMING. Copies it over the live server, restarts, and rolls back on failure.
#
#   vps_deploy.sh <git sha> <branch> <requirements-changed: 0|1>
#
# Never touched: data/ (DB, items, backups), .venv/, models/, storage.env — the server's own state.
set -euo pipefail

SHA="${1:?sha}"; BRANCH="${2:?branch}"; REQ_CHANGED="${3:-0}"
LIVE=/opt/colorforge
INCOMING=/opt/colorforge-incoming
BACKUPS=/root/deploy_backups
HEALTH=http://127.0.0.1:8000/index.php/Number/category/
KEEP=10
EXCLUDES=(--exclude data/ --exclude .venv/ --exclude models/ --exclude storage.env
          --exclude __pycache__/ --exclude '*.pyc')

log() { echo "[deploy $(date -u +%H:%M:%S)] $*"; }

# 1. Generation and publishing run in one in-process queue (§8.5aw); a restart drops a job
#    half-way. Wait for it to drain, at most 15 minutes.
for i in $(seq 1 90); do
  busy=$("$LIVE/.venv/bin/python" - <<'EOF'
import sqlite3
c = sqlite3.connect("/opt/colorforge/data/content.db")
print(c.execute("select count(*) from images where status in ('processing','publishing')").fetchone()[0])
EOF
)
  [ "$busy" = "0" ] && break
  log "queue busy ($busy item(s)) — waiting"; sleep 10
done
[ "$busy" = "0" ] || { log "queue still busy after 15 min — not deploying"; exit 1; }

# 2. Back up the code that is live now (code only — data stays where it is).
mkdir -p "$BACKUPS"
TS=$(date -u +%Y%m%d_%H%M%S)
BK="$BACKUPS/code_$TS.tgz"
tar -C "$LIVE" --exclude=./data --exclude=./.venv --exclude=./models --exclude=./storage.env \
    --exclude='*/__pycache__' -czf "$BK" .
log "backup $BK"
ls -1t "$BACKUPS"/code_*.tgz | tail -n +$((KEEP + 1)) | xargs -r rm -f

rollback() {
  log "ROLLING BACK to $BK"
  tar -C "$LIVE" -xzf "$BK"
  systemctl restart colorforge
  sleep 5
  curl -fsS -o /dev/null "$HEALTH" && log "rolled back — old code is serving" \
    || log "!! rollback restarted but health check still fails — look at journalctl -u colorforge"
  exit 1
}

# 3. Copy the new code over the live tree (no --delete: the server keeps files git does not have).
rsync -a "${EXCLUDES[@]}" "$INCOMING"/ "$LIVE"/
log "files copied"

# 4. Dependencies only when requirements.txt changed. The CPU index matters: a plain
#    `pip install torch` on Linux pulls the multi-GB CUDA build.
if [ "$REQ_CHANGED" = "1" ]; then
  log "requirements.txt changed — pip install"
  "$LIVE/.venv/bin/pip" install -q -r "$LIVE/requirements.txt" \
      --extra-index-url https://download.pytorch.org/whl/cpu || rollback
fi

# 5. The code must import with the server's own settings before it is allowed to serve.
( set -a; . "$LIVE/storage.env"; set +a; cd "$LIVE" && .venv/bin/python -c "import app" ) \
    || { log "import app failed"; rollback; }

# 6. Restart and prove it answers; otherwise put the old code back.
systemctl restart colorforge
for i in $(seq 1 20); do
  sleep 2
  if curl -fsS -o /dev/null "$HEALTH"; then
    printf 'sha=%s\nbranch=%s\nat=%s\n' "$SHA" "$BRANCH" "$(date -u +%FT%TZ)" > "$LIVE/DEPLOYED"
    log "LIVE: $BRANCH @ ${SHA:0:7}"
    exit 0
  fi
done
log "health check failed after restart"
rollback
