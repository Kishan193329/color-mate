package com.colourmate.paintbynumber.model.reveal;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;

/**
 * A picture's reveal assets, kept on disk.
 *
 * <p><b>Why:</b> every open used to re-download the whole set. Measured on the live server that
 * was <b>2.1 MB per open</b> — and a player opens the same picture again and again to finish it,
 * so five sittings cost 10 MB and five slow starts. Nothing was cached: the loader used a bare
 * {@link HttpURLConnection} and Android installs no HTTP cache by default.
 *
 * <p>Now the first open writes the files under {@code cacheDir/reveal/<key>} and every later one
 * reads them, so it costs nothing and starts instantly — and a picture already downloaded
 * <b>plays with no network at all</b>.
 *
 * <p><b>Staying current without paying for it.</b> Republishing an item changes its files, so a
 * cache that never looked again would show yesterday's drawing for ever. Within
 * {@link #FRESH_MS} the copy is simply trusted. After that, only {@code regions.json} is
 * re-fetched — 5-17 KB — and compared with the cached one: unchanged means the whole set is
 * unchanged and the folder is just re-dated, changed means the item was republished and the
 * folder is dropped. One small request a day per picture instead of 2 MB an open.
 *
 * <p>If that check cannot reach the server the cached copy is used anyway. Being offline must
 * not cost the player a picture they have already downloaded.
 */
public final class RevealAssetCache {

    private static final String TAG = "RevealCache";

    /** How long a cached picture is trusted without asking the server anything. */
    private static final long FRESH_MS = 24L * 60 * 60 * 1000;

    /** Total disk the cache may hold. Old pictures are dropped oldest-first past this. */
    private static final long BUDGET_BYTES = 120L * 1024 * 1024;

    /** The file whose bytes stand in for "has this item been republished?". */
    private static final String STAMP = "regions.json";

    private static final int ATTEMPTS = 3;
    private static final int CONNECT_MS = 15_000;
    private static final int READ_MS = 30_000;

    private RevealAssetCache() {
    }

    /**
     * A {@link RevealLevel.Source} that reads from disk, downloading only what is missing.
     * Safe to call on a background thread only — it does network I/O.
     */
    public static RevealLevel.Source source(Context context, String baseUrl) {
        final File dir = folderFor(context, baseUrl);
        revalidate(dir, baseUrl);
        prune(dir.getParentFile(), dir);
        prefetchAll(dir, baseUrl);
        return name -> open(dir, baseUrl, name);
    }

    /** Every file a picture needs, fetched at once instead of one after another. */
    private static final String[] ASSETS = {
            "regions.json", "original.jpg", "id_map.png", "border.svg", "border.png", "start.jpg"};

    /**
     * The loader used to fetch the five files sequentially, so the wall time was the SUM of
     * five round trips — measured ~30 s for a fresh picture on a slow connection. Fetching
     * them concurrently makes it the time of the largest file instead. Downloads land in the
     * cache; the loader then reads them from disk. A miss (404) is remembered with a marker
     * so the loader does not pay the same round trip again — an item published before the
     * JPEG photo has no {@code original.jpg}, and that is expected, not an error.
     */
    private static void prefetchAll(File dir, String baseUrl) {
        Thread[] workers = new Thread[ASSETS.length];
        for (int i = 0; i < ASSETS.length; i++) {
            final String name = ASSETS[i];
            File f = new File(dir, safe(name));
            if ((f.isFile() && f.length() > 0) || new File(dir, safe(name) + ".miss").isFile()) {
                continue;                                       // cached, or a known miss
            }
            workers[i] = new Thread(() -> {
                try {
                    download(baseUrl, name, f);
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().startsWith("404")) {
                        try {
                            //noinspection ResultOfMethodCallIgnored
                            new File(dir, safe(name) + ".miss").createNewFile();
                        } catch (IOException ignored) {
                        }
                    }
                    // any other failure: the loader retries this file itself
                }
            }, "reveal-prefetch");
            workers[i].start();
        }
        for (Thread w : workers) {
            if (w == null) continue;
            try {
                w.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ─── one asset ───

    private static InputStream open(File dir, String baseUrl, String name) throws Exception {
        File f = new File(dir, safe(name));
        if (f.isFile() && f.length() > 0) {
            return new FileInputStream(f);
        }
        if (new File(dir, safe(name) + ".miss").isFile()) {
            throw new IOException("404 " + name + " (cached miss)");
        }
        download(baseUrl, name, f);
        return new FileInputStream(f);
    }

    /**
     * Downloads to a temporary file and renames on success, so a half-written asset can never be
     * mistaken for a cached one. Retries with a growing pause; a 404 is NOT retried, because the
     * loader deliberately probes for files that may not exist (an item published before the photo
     * became a JPEG has no {@code original.jpg}).
     */
    private static void download(String baseUrl, String name, File target) throws Exception {
        //noinspection ResultOfMethodCallIgnored
        target.getParentFile().mkdirs();
        File tmp = new File(target.getPath() + ".part");
        String root = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        Exception last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(root + name).openConnection();
                c.setConnectTimeout(CONNECT_MS);
                c.setReadTimeout(READ_MS);
                int code = c.getResponseCode();
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw new IOException("404 " + name);          // expected; let the caller fall back
                }
                if (code / 100 != 2) {
                    throw new IOException("HTTP " + code + " for " + name);
                }
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                    copy(in, out);
                }
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                if (!tmp.renameTo(target)) {
                    throw new IOException("could not store " + name);
                }
                return;
            } catch (Exception e) {
                last = e;
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                if (e.getMessage() != null && e.getMessage().startsWith("404")) {
                    break;                                          // not worth retrying
                }
                if (attempt < ATTEMPTS) {
                    try {
                        Thread.sleep(400L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                if (c != null) c.disconnect();
            }
        }
        throw last != null ? last : new IOException("could not fetch " + name);
    }

    // ─── freshness ───

    private static void revalidate(File dir, String baseUrl) {
        File stamp = new File(dir, STAMP);
        if (!stamp.isFile()) {
            return;                                                 // nothing cached yet
        }
        if (System.currentTimeMillis() - dir.lastModified() < FRESH_MS) {
            return;                                                 // trusted
        }
        File probe = new File(dir, STAMP + ".probe");
        try {
            download(baseUrl, STAMP, probe);
            if (sameBytes(probe, stamp)) {
                //noinspection ResultOfMethodCallIgnored
                dir.setLastModified(System.currentTimeMillis());     // still current
            } else {
                Log.d(TAG, "republished — dropping the cached copy");
                wipe(dir);
            }
        } catch (Exception e) {
            // Offline, or the server is down. Keep what we have: a downloaded picture must stay
            // playable. It will be checked again on the next open.
            Log.d(TAG, "could not revalidate, using the cached copy: " + e);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            probe.delete();
        }
    }

    private static boolean sameBytes(File a, File b) {
        if (a.length() != b.length()) return false;
        byte[] x = new byte[8192], y = new byte[8192];
        try (InputStream ia = new FileInputStream(a); InputStream ib = new FileInputStream(b)) {
            int n;
            while ((n = ia.read(x)) > 0) {
                int filled = 0;
                while (filled < n) {
                    int r = ib.read(y, filled, n - filled);
                    if (r <= 0) return false;
                    filled += r;
                }
                for (int i = 0; i < n; i++) {
                    if (x[i] != y[i]) return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ─── housekeeping ───

    /** Drops whole pictures, oldest first, until the cache is inside its budget. Never the one
     *  being opened — evicting that would re-download it immediately. */
    private static void prune(File root, File keep) {
        File[] all = root == null ? null : root.listFiles(File::isDirectory);
        if (all == null || all.length == 0) return;
        long total = 0;
        for (File d : all) total += sizeOf(d);
        if (total <= BUDGET_BYTES) return;
        Arrays.sort(all, Comparator.comparingLong(File::lastModified));
        for (File d : all) {
            if (total <= BUDGET_BYTES) break;
            if (d.equals(keep)) continue;
            long s = sizeOf(d);
            if (wipe(d)) total -= s;
        }
    }

    private static long sizeOf(File dir) {
        File[] fs = dir.listFiles();
        long n = 0;
        if (fs != null) for (File f : fs) n += f.isDirectory() ? sizeOf(f) : f.length();
        return n;
    }

    private static boolean wipe(File dir) {
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) wipe(f);
                //noinspection ResultOfMethodCallIgnored
                else f.delete();
            }
        }
        return dir.delete();
    }

    private static File folderFor(Context context, String baseUrl) {
        File dir = new File(new File(context.getCacheDir(), "reveal"), key(baseUrl));
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /** A stable folder name for a URL. The base URL already carries the category and the globally
     *  unique fileName, so two pictures cannot collide. */
    private static String key(String baseUrl) {
        String s = baseUrl == null ? "" : baseUrl;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') b.append(c);
        }
        // ...plus a hash, so two URLs that differ only in punctuation cannot land on one folder.
        String cleaned = b.length() > 60 ? b.substring(b.length() - 60) : b.toString();
        return cleaned + "_" + Integer.toHexString(s.hashCode());
    }

    private static String safe(String name) {
        return name.replace('/', '_').replace('\\', '_').replace("..", "_");
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1 << 15];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
