#!/usr/bin/env python3
"""Daily safety copy of content.db — the one file that makes every S3 asset addressable.

The assets live in S3 and the job folders on disk, but WHICH file_name maps to which item,
what is published, the users and the audit trail are only in this 56 KB SQLite file. Losing it
empties the app's feeds even though every picture still exists.

sqlite3's own .backup() is used, not a file copy: it is safe while the server is writing.
Keeps 14 local copies and mirrors the newest to S3 under backups/colorforge/ (the bucket is
shared with the Laravel backend, hence the sub-prefix).
"""
import os, sqlite3, sys, time
from pathlib import Path

ROOT = Path('/opt/colorforge')
sys.path.insert(0, str(ROOT))
os.chdir(ROOT)

SRC = ROOT / 'data' / 'content.db'
OUT = ROOT / 'data' / 'backups'
KEEP = 14

def main() -> int:
    if not SRC.is_file():
        print('no content.db'); return 1
    OUT.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime('%Y%m%d-%H%M%S', time.gmtime())
    dst = OUT / f'content-{stamp}.db'
    src = sqlite3.connect(f'file:{SRC}?mode=ro', uri=True)
    try:
        tgt = sqlite3.connect(dst)
        try:
            src.backup(tgt)          # consistent even mid-write
        finally:
            tgt.close()
    finally:
        src.close()
    size = dst.stat().st_size
    print(f'local  {dst.name} {size} bytes')

    old = sorted(OUT.glob('content-*.db'))[:-KEEP]
    for f in old:
        f.unlink(missing_ok=True)
    if old:
        print(f'pruned {len(old)} old copies')

    try:
        import storage
        st = storage.get_store()
        if st.kind == 's3':
            st.put_bytes(f'backups/colorforge/content-{stamp}.db', dst.read_bytes())
            st.put_bytes('backups/colorforge/content-latest.db', dst.read_bytes())
            print('s3     backups/colorforge/ updated')
        else:
            print('s3     skipped (STORAGE is not s3)')
    except Exception as e:
        print(f's3     FAILED: {type(e).__name__}: {e}')
        return 2          # local copy still succeeded — non-zero so the timer logs it
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
