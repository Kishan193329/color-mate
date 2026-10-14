package com.colourmate.paintbynumber.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.colourmate.paintbynumber.model.game.GifEncoder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records and replays time-lapse of coloring sessions.
 *
 * KEY FIX: Uses compact binary format instead of JSON to avoid OOM.
 *
 * Old JSON per step: {"x":500,"y":300,"color":-16711936,"timestamp":1234567890123}
 *                    = ~70 bytes
 *
 * New binary per step: [int x][int y][int color] = 12 bytes
 *                      (timestamp removed - not needed for replay)
 *
 * Memory savings: ~83% less file size, zero StringBuilder allocation
 */
public class TimeLapseRecorder {

    private static final String TAG = "TimeLapseRecorder";
    private static final String PREF_NAME = "timelapse_prefs";
    private static final String KEY_PREFIX = "timelapse_";
    private static final int BINARY_VERSION = 2;
    private static final byte[] MAGIC = {'T', 'L', 'R', BINARY_VERSION};

    // ─── Maximum steps to prevent unbounded memory growth ───
    private static final int MAX_STEPS = 500_000;
    // ─── Sampling: skip steps when approaching limit ───
    private static final int SAMPLING_THRESHOLD = 400_000;
    private int samplingRate = 1;

    // ─── Step storage (compact arrays instead of List<Object>) ───
    private int[] stepX;
    private int[] stepY;
    private int[] stepColor;
    private int stepCount;
    private int capacity;

    private String levelKey;
    private boolean recording;

    // ─── Write buffer for streaming saves ───
    private File activeFile;
    private DataOutputStream activeStream;
    private boolean streamingMode = false;
    private int streamedCount = 0;

    public TimeLapseRecorder() {
        this.recording = false;
        this.stepCount = 0;
        this.capacity = 0;
    }

    // ═══════════════════════════════════════════════════════════
    // ─── REPLAY CONTROLLER (Speed / Pause / Cancel) ───
    // ═══════════════════════════════════════════════════════════

    public static class ReplayController {
        private final AtomicInteger speedMultiplier = new AtomicInteger(1);
        private final AtomicBoolean isPaused = new AtomicBoolean(false);
        private final AtomicBoolean isCancelled = new AtomicBoolean(false);

        private static final int[] SPEED_OPTIONS = {1, 2, 4, 8, 16};
        private int currentSpeedIndex = 0;

        /**
         * Increase speed to next level. Returns new speed.
         */
        public int speedUp() {
            currentSpeedIndex = Math.min(currentSpeedIndex + 1, SPEED_OPTIONS.length - 1);
            int newSpeed = SPEED_OPTIONS[currentSpeedIndex];
            speedMultiplier.set(newSpeed);
            return newSpeed;
        }

        /**
         * Decrease speed to previous level. Returns new speed.
         */
        public int slowDown() {
            currentSpeedIndex = Math.max(currentSpeedIndex - 1, 0);
            int newSpeed = SPEED_OPTIONS[currentSpeedIndex];
            speedMultiplier.set(newSpeed);
            return newSpeed;
        }

        /**
         * Set speed to specific multiplier.
         */
        public void setSpeed(int multiplier) {
            for (int i = 0; i < SPEED_OPTIONS.length; i++) {
                if (SPEED_OPTIONS[i] == multiplier) {
                    currentSpeedIndex = i;
                    speedMultiplier.set(multiplier);
                    return;
                }
            }
            currentSpeedIndex = 0;
            speedMultiplier.set(1);
        }

        public int getSpeed() {
            return speedMultiplier.get();
        }

        public boolean canSpeedUp() {
            return currentSpeedIndex < SPEED_OPTIONS.length - 1;
        }

        public boolean canSlowDown() {
            return currentSpeedIndex > 0;
        }

        public void togglePause() {
            isPaused.set(!isPaused.get());
        }

        public void pause() {
            isPaused.set(true);
        }

        public void resume() {
            isPaused.set(false);
        }

        public boolean isPaused() {
            return isPaused.get();
        }

        public void cancel() {
            isCancelled.set(true);
        }

        public boolean isCancelled() {
            return isCancelled.get();
        }

        public int[] getSpeedOptions() {
            return SPEED_OPTIONS.clone();
        }

        public int getCurrentSpeedIndex() {
            return currentSpeedIndex;
        }

        /**
         * Reset controller to initial state.
         */
        public void reset() {
            currentSpeedIndex = 0;
            speedMultiplier.set(1);
            isPaused.set(false);
            isCancelled.set(false);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ─── CONTROLLABLE REPLAY CALLBACK ───
    // ═══════════════════════════════════════════════════════════

    public interface ControllableReplayCallback {
        void onFrame(Bitmap frame, int currentStep, int totalSteps);
        void onComplete(Bitmap finalFrame);
        void onSpeedChanged(int newSpeed);
        void onPauseStateChanged(boolean isPaused);
    }

    // ═══════════════════════════════════════════════════════════
    // ─── CONTROLLABLE REPLAY (List-based) ───
    // ═══════════════════════════════════════════════════════════

    /**
     * Controllable replay with speed/pause support.
     * Uses direct pixel array access for performance.
     *
     * @param baseBitmap    Original base image
     * @param steps         Recorded steps
     * @param stepsPerFrame Base steps per frame (at 1x speed)
     * @param frameDelayMs  Base delay between frames (at 1x speed)
     * @param controller    ReplayController for speed/pause/cancel
     * @param callback      Callback for frames and events
     */
    public static void replayControllable(
            Bitmap baseBitmap,
            List<TimeLapseStep> steps,
            int stepsPerFrame,
            long frameDelayMs,
            ReplayController controller,
            ControllableReplayCallback callback) {

        if (baseBitmap == null || steps == null || steps.isEmpty()) {
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(
                        () -> callback.onComplete(baseBitmap));
            }
            return;
        }

        new Thread(() -> {
            Bitmap canvas = null;
            try {
                int w = baseBitmap.getWidth();
                int h = baseBitmap.getHeight();

                canvas = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);

                // Use pixel array for batch updates (much faster)
                int[] pixels = new int[w * h];
                canvas.getPixels(pixels, 0, w, 0, 0, w, h);

                int total = steps.size();
                int idx = 0;
                Handler handler = new Handler(Looper.getMainLooper());

                while (idx < total) {
                    // ─── Check cancellation ───
                    if (controller.isCancelled()) {
                        Log.d(TAG, "Replay cancelled at step " + idx);
                        break;
                    }

                    // ─── Handle pause ───
                    while (controller.isPaused() && !controller.isCancelled()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    if (controller.isCancelled()) break;

                    // ─── Calculate effective steps based on speed ───
                    int speed = controller.getSpeed();
                    int effectiveSteps = stepsPerFrame * speed;

                    int end = Math.min(idx + effectiveSteps, total);

                    // ─── Batch pixel updates ───
                    for (int i = idx; i < end; i++) {
                        TimeLapseStep step = steps.get(i);
                        if (step != null
                                && step.x >= 0 && step.x < w
                                && step.y >= 0 && step.y < h) {
                            pixels[step.y * w + step.x] = step.color;
                        }
                    }
                    idx = end;

                    // ─── Update canvas from pixel array ───
                    canvas.setPixels(pixels, 0, w, 0, 0, w, h);

                    // ─── Create snapshot for UI ───
                    final Bitmap frame = canvas.copy(
                            Bitmap.Config.ARGB_8888, false);
                    final int currentIdx = idx;

                    handler.post(() -> {
                        if (callback != null) {
                            callback.onFrame(frame, currentIdx, total);
                        }
                    });

                    // ─── Frame delay (inversely proportional to speed) ───
                    long delay = Math.max(frameDelayMs / speed, 5);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // ─── Final frame ───
                if (!controller.isCancelled()) {
                    canvas.setPixels(pixels, 0, w, 0, 0, w, h);
                    Bitmap finalBmp = canvas.copy(
                            Bitmap.Config.ARGB_8888, false);
                    handler.post(() -> {
                        if (callback != null) {
                            callback.onComplete(finalBmp);
                        }
                    });
                }

            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "OOM during controllable replay", oom);
            } catch (Exception e) {
                Log.e(TAG, "Error during controllable replay", e);
            } finally {
                if (canvas != null && !canvas.isRecycled()) {
                    canvas.recycle();
                }
            }
        }, "TimeLapseReplay-Controllable").start();
    }

    // ═══════════════════════════════════════════════════════════
    // ─── CONTROLLABLE REPLAY FROM FILE (No List allocation) ───
    // ═══════════════════════════════════════════════════════════

    /**
     * Controllable replay directly from binary file.
     * Avoids creating List<TimeLapseStep> entirely —
     * uses raw int arrays for zero-allocation replay.
     */
    public static void replayFromFileControllable(
            Context ctx,
            Bitmap baseBitmap,
            String levelKey,
            int stepsPerFrame,
            long frameDelayMs,
            ReplayController controller,
            ControllableReplayCallback callback) {

        new Thread(() -> {
            StepData data = loadBinary(ctx, levelKey);

            if (data == null || data.count == 0) {
                // Fallback to list-based
                List<TimeLapseStep> steps = load(ctx, levelKey);
                if (!steps.isEmpty()) {
                    replayControllable(baseBitmap, steps, stepsPerFrame,
                            frameDelayMs, controller, callback);
                } else {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(() -> {
                        if (callback != null) callback.onComplete(baseBitmap);
                    });
                }
                return;
            }

            Bitmap canvas = null;
            try {
                int w = baseBitmap.getWidth();
                int h = baseBitmap.getHeight();
                canvas = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);

                int[] pixels = new int[w * h];
                canvas.getPixels(pixels, 0, w, 0, 0, w, h);

                Handler handler = new Handler(Looper.getMainLooper());
                int total = data.count;
                int idx = 0;

                while (idx < total) {
                    // Check cancellation
                    if (controller.isCancelled()) break;

                    // Handle pause
                    while (controller.isPaused() && !controller.isCancelled()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }

                    if (controller.isCancelled()) break;

                    // Speed-adjusted batch size
                    int speed = controller.getSpeed();
                    int effectiveSteps = stepsPerFrame * speed;
                    int end = Math.min(idx + effectiveSteps, total);

                    // Direct array access — no object creation!
                    for (int i = idx; i < end; i++) {
                        int x = data.x[i];
                        int y = data.y[i];
                        if (x >= 0 && x < w && y >= 0 && y < h) {
                            pixels[y * w + x] = data.color[i];
                        }
                    }
                    idx = end;

                    canvas.setPixels(pixels, 0, w, 0, 0, w, h);
                    final Bitmap frame = canvas.copy(
                            Bitmap.Config.ARGB_8888, false);
                    final int ci = idx;
                    handler.post(() -> {
                        if (callback != null) {
                            callback.onFrame(frame, ci, total);
                        }
                    });

                    long delay = Math.max(frameDelayMs / speed, 5);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // Final frame
                if (!controller.isCancelled()) {
                    canvas.setPixels(pixels, 0, w, 0, 0, w, h);
                    Bitmap finalBmp = canvas.copy(
                            Bitmap.Config.ARGB_8888, false);
                    handler.post(() -> {
                        if (callback != null) callback.onComplete(finalBmp);
                    });
                }

            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "OOM during file controllable replay", oom);
            } catch (Exception e) {
                Log.e(TAG, "Error during file controllable replay", e);
            } finally {
                if (canvas != null && !canvas.isRecycled()) {
                    canvas.recycle();
                }
            }
        }, "TimeLapseReplay-File-Controllable").start();
    }

    // ═══════════════════════════════════════════════════════════
    // ─── Recording API (unchanged) ───
    // ═══════════════════════════════════════════════════════════

    public void startRecording(String levelKey) {
        this.levelKey = levelKey;
        clearSteps();
        this.recording = true;
        this.samplingRate = 1;
    }

    public void resumeRecording(Context ctx, String levelKey) {
        this.levelKey = levelKey;

        StepData existing = loadBinary(ctx, levelKey);
        if (existing != null && existing.count > 0) {
            ensureCapacity(existing.count + 10000);
            System.arraycopy(existing.x, 0, stepX, 0, existing.count);
            System.arraycopy(existing.y, 0, stepY, 0, existing.count);
            System.arraycopy(existing.color, 0, stepColor, 0, existing.count);
            stepCount = existing.count;
            updateSamplingRate();
        } else {
            clearSteps();
        }

        this.recording = true;
    }

    public void stopRecording() {
        this.recording = false;
        closeStream();
    }

    public boolean isRecording() {
        return recording;
    }

    public void recordStep(int x, int y, int color) {
        if (!recording) return;
        if (stepCount >= MAX_STEPS) return;

        if (stepCount >= SAMPLING_THRESHOLD) {
            if (stepCount % samplingRate != 0) return;
        }

        ensureCapacity(stepCount + 1);
        stepX[stepCount] = x;
        stepY[stepCount] = y;
        stepColor[stepCount] = color;
        stepCount++;

        updateSamplingRate();
    }

    public void recordAreaFill(int color, List<int[]> pixels) {
        if (!recording || pixels == null || pixels.isEmpty()) return;

        int sampleRate = Math.max(1, pixels.size() / 100);

        for (int i = 0; i < pixels.size(); i += sampleRate) {
            if (stepCount >= MAX_STEPS) break;

            int[] px = pixels.get(i);
            ensureCapacity(stepCount + 1);
            stepX[stepCount] = px[0];
            stepY[stepCount] = px[1];
            stepColor[stepCount] = color;
            stepCount++;
        }
    }

    public int getStepCount() {
        return stepCount;
    }

    public List<TimeLapseStep> getSteps() {
        List<TimeLapseStep> list = new ArrayList<>(stepCount);
        for (int i = 0; i < stepCount; i++) {
            list.add(new TimeLapseStep(stepX[i], stepY[i], stepColor[i], 0));
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════
    // ─── Memory Management (unchanged) ───
    // ═══════════════════════════════════════════════════════════

    private void ensureCapacity(int needed) {
        if (stepX == null || needed > capacity) {
            int newCapacity = Math.max(needed, Math.max(capacity * 2, 4096));
            newCapacity = Math.min(newCapacity, MAX_STEPS);

            int[] newX = new int[newCapacity];
            int[] newY = new int[newCapacity];
            int[] newColor = new int[newCapacity];

            if (stepX != null && stepCount > 0) {
                System.arraycopy(stepX, 0, newX, 0, stepCount);
                System.arraycopy(stepY, 0, newY, 0, stepCount);
                System.arraycopy(stepColor, 0, newColor, 0, stepCount);
            }

            stepX = newX;
            stepY = newY;
            stepColor = newColor;
            capacity = newCapacity;
        }
    }

    private void clearSteps() {
        stepCount = 0;
    }

    private void updateSamplingRate() {
        if (stepCount > SAMPLING_THRESHOLD) {
            samplingRate = 1 + (stepCount - SAMPLING_THRESHOLD) / 50000;
        } else {
            samplingRate = 1;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ─── Binary Persistence (unchanged) ───
    // ═══════════════════════════════════════════════════════════

    public void save(Context ctx) {
        if (levelKey == null || stepCount == 0) return;

        final int count = stepCount;
        final int[] xCopy = new int[count];
        final int[] yCopy = new int[count];
        final int[] colorCopy = new int[count];
        System.arraycopy(stepX, 0, xCopy, 0, count);
        System.arraycopy(stepY, 0, yCopy, 0, count);
        System.arraycopy(stepColor, 0, colorCopy, 0, count);

        final String key = levelKey;
        final Context appCtx = ctx.getApplicationContext();

        new Thread(() -> {
            try {
                File dir = new File(appCtx.getFilesDir(), "timelapse");
                if (!dir.exists()) dir.mkdirs();

                File file = new File(dir, sanitizeFileName(key) + ".tlr");
                File tempFile = new File(dir, sanitizeFileName(key) + ".tlr.tmp");

                try (DataOutputStream dos = new DataOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(tempFile), 65536))) {
                    dos.write(MAGIC);
                    dos.writeInt(count);
                    for (int i = 0; i < count; i++) {
                        dos.writeInt(xCopy[i]);
                        dos.writeInt(yCopy[i]);
                        dos.writeInt(colorCopy[i]);
                    }
                    dos.flush();
                }

                if (file.exists()) file.delete();
                tempFile.renameTo(file);

                File jsonFile = new File(dir, key + ".json");
                if (jsonFile.exists()) jsonFile.delete();

                Log.d(TAG, "Saved " + count + " steps ("
                        + (count * 12 / 1024) + "KB) to " + file.getName());

            } catch (Exception e) {
                Log.e(TAG, "Error saving timelapse", e);
            }
        }).start();
    }

    public void saveStreaming(Context ctx) {
        if (levelKey == null) return;

        try {
            if (activeStream == null) {
                File dir = new File(ctx.getFilesDir(), "timelapse");
                if (!dir.exists()) dir.mkdirs();
                activeFile = new File(dir, sanitizeFileName(levelKey) + ".tlr");

                activeStream = new DataOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(activeFile), 32768));
                activeStream.write(MAGIC);
                activeStream.writeInt(0);
                streamedCount = 0;
                streamingMode = true;
            }

            for (int i = streamedCount; i < stepCount; i++) {
                activeStream.writeInt(stepX[i]);
                activeStream.writeInt(stepY[i]);
                activeStream.writeInt(stepColor[i]);
            }
            streamedCount = stepCount;
            activeStream.flush();

        } catch (Exception e) {
            Log.e(TAG, "Streaming save error", e);
            closeStream();
        }
    }

    private void closeStream() {
        if (activeStream != null) {
            try {
                activeStream.flush();
                activeStream.close();

                if (activeFile != null && activeFile.exists() && streamedCount > 0) {
                    try (java.io.RandomAccessFile raf =
                                 new java.io.RandomAccessFile(activeFile, "rw")) {
                        raf.seek(4);
                        raf.writeInt(streamedCount);
                    }
                    Log.d(TAG, "Finalized streaming save: "
                            + streamedCount + " steps");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing stream", e);
            } finally {
                activeStream = null;
                activeFile = null;
                streamingMode = false;
                streamedCount = 0;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ─── Binary Load (unchanged) ───
    // ═══════════════════════════════════════════════════════════

    private static class StepData {
        int[] x, y, color;
        int count;
    }

    private static StepData loadBinary(Context ctx, String levelKey) {
        File dir = new File(ctx.getFilesDir(), "timelapse");
        File file = new File(dir, sanitizeFileName(levelKey) + ".tlr");

        if (!file.exists()) return null;

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(
                        new FileInputStream(file), 65536))) {

            byte[] magic = new byte[4];
            dis.readFully(magic);
            if (magic[0] != 'T' || magic[1] != 'L' || magic[2] != 'R') {
                Log.w(TAG, "Invalid TLR file format");
                return null;
            }

            int version = magic[3];
            int count = dis.readInt();

            if (count <= 0 || count > MAX_STEPS) {
                Log.w(TAG, "Invalid step count: " + count);
                return null;
            }

            StepData data = new StepData();
            data.count = count;
            data.x = new int[count];
            data.y = new int[count];
            data.color = new int[count];

            for (int i = 0; i < count; i++) {
                data.x[i] = dis.readInt();
                data.y[i] = dis.readInt();
                data.color[i] = dis.readInt();
            }

            Log.d(TAG, "Loaded " + count + " steps from binary");
            return data;

        } catch (Exception e) {
            Log.e(TAG, "Error loading binary timelapse", e);
            return null;
        }
    }

    public static List<TimeLapseStep> load(Context ctx, String levelKey) {
        StepData data = loadBinary(ctx, levelKey);
        if (data != null && data.count > 0) {
            List<TimeLapseStep> list = new ArrayList<>(data.count);
            for (int i = 0; i < data.count; i++) {
                list.add(new TimeLapseStep(
                        data.x[i], data.y[i], data.color[i], 0));
            }
            return list;
        }

        return loadLegacyJson(ctx, levelKey);
    }

    private static List<TimeLapseStep> loadLegacyJson(Context ctx, String levelKey) {
        File dir = new File(ctx.getFilesDir(), "timelapse");
        File jsonFile = new File(dir, levelKey + ".json");

        if (jsonFile.exists()) {
            return loadJsonFileStreaming(jsonFile);
        }

        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_PREFIX + levelKey, null);
        if (json != null && !json.isEmpty()) {
            try {
                return parseJsonStreaming(json);
            } catch (Exception e) {
                Log.e(TAG, "Legacy JSON parse failed", e);
            }
        }

        return new ArrayList<>();
    }

    private static List<TimeLapseStep> loadJsonFileStreaming(File file) {
        List<TimeLapseStep> steps = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             com.google.gson.stream.JsonReader reader =
                     new com.google.gson.stream.JsonReader(
                             new java.io.InputStreamReader(fis))) {

            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                int x = 0, y = 0, color = 0;
                long timestamp = 0;

                while (reader.hasNext()) {
                    String name = reader.nextName();
                    switch (name) {
                        case "x": x = reader.nextInt(); break;
                        case "y": y = reader.nextInt(); break;
                        case "color": color = reader.nextInt(); break;
                        case "timestamp": timestamp = reader.nextLong(); break;
                        default: reader.skipValue(); break;
                    }
                }
                reader.endObject();
                steps.add(new TimeLapseStep(x, y, color, timestamp));

                if (steps.size() >= MAX_STEPS) {
                    Log.w(TAG, "Hit max steps limit during JSON load");
                    break;
                }
            }
            reader.endArray();

            Log.d(TAG, "Loaded " + steps.size() + " steps from JSON file");

        } catch (Exception e) {
            Log.e(TAG, "Error streaming JSON file", e);
        }

        return steps;
    }

    private static List<TimeLapseStep> parseJsonStreaming(String json) {
        List<TimeLapseStep> steps = new ArrayList<>();

        try (com.google.gson.stream.JsonReader reader =
                     new com.google.gson.stream.JsonReader(
                             new java.io.StringReader(json))) {

            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                int x = 0, y = 0, color = 0;

                while (reader.hasNext()) {
                    String name = reader.nextName();
                    switch (name) {
                        case "x": x = reader.nextInt(); break;
                        case "y": y = reader.nextInt(); break;
                        case "color": color = reader.nextInt(); break;
                        default: reader.skipValue(); break;
                    }
                }
                reader.endObject();
                steps.add(new TimeLapseStep(x, y, color, 0));

                if (steps.size() >= MAX_STEPS) break;
            }
            reader.endArray();

        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON stream", e);
        }

        return steps;
    }

    public static boolean hasRecording(Context ctx, String levelKey) {
        File dir = new File(ctx.getFilesDir(), "timelapse");
        File binaryFile = new File(dir, sanitizeFileName(levelKey) + ".tlr");
        if (binaryFile.exists()) return true;

        File jsonFile = new File(dir, levelKey + ".json");
        if (jsonFile.exists()) return true;

        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.contains(KEY_PREFIX + levelKey);
    }

    public static void delete(Context ctx, String levelKey) {
        File dir = new File(ctx.getFilesDir(), "timelapse");
        File binaryFile = new File(dir, sanitizeFileName(levelKey) + ".tlr");
        if (binaryFile.exists()) binaryFile.delete();

        File jsonFile = new File(dir, levelKey + ".json");
        if (jsonFile.exists()) jsonFile.delete();

        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_PREFIX + levelKey).apply();
    }

    private static String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ═══════════════════════════════════════════════════════════
    // ─── ORIGINAL REPLAY (unchanged, kept for compatibility) ───
    // ═══════════════════════════════════════════════════════════

    public interface ReplayCallback {
        void onFrame(Bitmap frame, int currentStep, int totalSteps);
        void onComplete(Bitmap finalFrame);
    }

    public static void replay(Bitmap baseBitmap, List<TimeLapseStep> steps,
                              int stepsPerFrame, long frameDelayMs,
                              ReplayCallback callback) {
        if (baseBitmap == null || steps == null || steps.isEmpty()) return;

        new Thread(() -> {
            Bitmap canvas = null;
            try {
                int w = baseBitmap.getWidth();
                int h = baseBitmap.getHeight();

                canvas = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
                int[] pixels = new int[w * h];
                canvas.getPixels(pixels, 0, w, 0, 0, w, h);

                int total = steps.size();
                int idx = 0;
                Handler handler = new Handler(Looper.getMainLooper());

                while (idx < total) {
                    int end = Math.min(idx + stepsPerFrame, total);

                    for (int i = idx; i < end; i++) {
                        TimeLapseStep step = steps.get(i);
                        if (step != null
                                && step.x >= 0 && step.x < w
                                && step.y >= 0 && step.y < h) {
                            pixels[step.y * w + step.x] = step.color;
                        }
                    }
                    idx = end;

                    canvas.setPixels(pixels, 0, w, 0, 0, w, h);
                    final Bitmap frame = canvas.copy(
                            Bitmap.Config.ARGB_8888, false);
                    final int currentIdx = idx;
                    handler.post(() -> callback.onFrame(frame, currentIdx, total));

                    try {
                        Thread.sleep(frameDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                canvas.setPixels(pixels, 0, w, 0, 0, w, h);
                Bitmap finalBmp = canvas.copy(Bitmap.Config.ARGB_8888, false);
                handler.post(() -> callback.onComplete(finalBmp));

            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "OOM during replay", oom);
            } catch (Exception e) {
                Log.e(TAG, "Error during replay", e);
            } finally {
                if (canvas != null && !canvas.isRecycled()) {
                    canvas.recycle();
                }
            }
        }).start();
    }

    public static void replayFromFile(Context ctx, Bitmap baseBitmap,
                                      String levelKey,
                                      int stepsPerFrame, long frameDelayMs,
                                      ReplayCallback callback) {
        new Thread(() -> {
            StepData data = loadBinary(ctx, levelKey);
            if (data == null || data.count == 0) {
                List<TimeLapseStep> steps = load(ctx, levelKey);
                if (!steps.isEmpty()) {
                    replay(baseBitmap, steps, stepsPerFrame, frameDelayMs, callback);
                }
                return;
            }

            Bitmap canvas = null;
            try {
                int w = baseBitmap.getWidth();
                int h = baseBitmap.getHeight();
                canvas = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
                int[] pixels = new int[w * h];
                canvas.getPixels(pixels, 0, w, 0, 0, w, h);

                Handler handler = new Handler(Looper.getMainLooper());
                int total = data.count;
                int idx = 0;

                while (idx < total) {
                    int end = Math.min(idx + stepsPerFrame, total);
                    for (int i = idx; i < end; i++) {
                        int x = data.x[i];
                        int y = data.y[i];
                        if (x >= 0 && x < w && y >= 0 && y < h) {
                            pixels[y * w + x] = data.color[i];
                        }
                    }
                    idx = end;

                    canvas.setPixels(pixels, 0, w, 0, 0, w, h);
                    final Bitmap frame = canvas.copy(
                            Bitmap.Config.ARGB_8888, false);
                    final int ci = idx;
                    handler.post(() -> callback.onFrame(frame, ci, total));

                    try {
                        Thread.sleep(frameDelayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                canvas.setPixels(pixels, 0, w, 0, 0, w, h);
                Bitmap finalBmp = canvas.copy(Bitmap.Config.ARGB_8888, false);
                handler.post(() -> callback.onComplete(finalBmp));

            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "OOM during file replay", oom);
            } catch (Exception e) {
                Log.e(TAG, "Error during file replay", e);
            } finally {
                if (canvas != null && !canvas.isRecycled()) {
                    canvas.recycle();
                }
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════
    // ─── Export (unchanged) ───
    // ═══════════════════════════════════════════════════════════

    public static File exportFrame(Context ctx, Bitmap frame, String fileName) {
        File dir = new File(ctx.getCacheDir(), "timelapse");
        if (!dir.exists()) dir.mkdirs();
        File output = new File(dir, fileName + "_timelapse.png");
        try (FileOutputStream fos = new FileOutputStream(output)) {
            frame.compress(Bitmap.CompressFormat.PNG, 100, fos);
            return output;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export frame", e);
            return null;
        }
    }

    public interface GifExportCallback {
        void onProgress(int percent);
        void onComplete(File gifFile);
        void onError(String message);
    }

    public static void exportGif(Context ctx, Bitmap baseBitmap,
                                 List<TimeLapseStep> steps,
                                 int maxFrames, int gifWidth, String levelKey,
                                 GifExportCallback callback) {
        if (baseBitmap == null || steps == null || steps.isEmpty()) {
            if (callback != null) callback.onError("No recording data");
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            Bitmap canvas = null;
            try {
                float scale = (float) gifWidth / baseBitmap.getWidth();
                int gifHeight = Math.round(baseBitmap.getHeight() * scale);
                int w = baseBitmap.getWidth();
                int h = baseBitmap.getHeight();

                canvas = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
                int[] pixels = new int[w * h];
                canvas.getPixels(pixels, 0, w, 0, 0, w, h);

                int stepsPerFrame = Math.max(1, steps.size() / maxFrames);

                File dir = new File(ctx.getCacheDir(), "timelapse");
                if (!dir.exists()) dir.mkdirs();
                File gifFile = new File(dir, levelKey + "_timelapse.gif");

                GifEncoder encoder = new GifEncoder();
                encoder.setDelay(100);
                encoder.setRepeat(0);
                encoder.setQuality(10);
                encoder.start(new FileOutputStream(gifFile));

                int totalFrames = Math.min(maxFrames, steps.size());
                int idx = 0;
                int frameCount = 0;

                while (idx < steps.size() && frameCount < maxFrames) {
                    int end = Math.min(idx + stepsPerFrame, steps.size());
                    for (int i = idx; i < end; i++) {
                        TimeLapseStep step = steps.get(i);
                        if (step != null
                                && step.x >= 0 && step.x < w
                                && step.y >= 0 && step.y < h) {
                            pixels[step.y * w + step.x] = step.color;
                        }
                    }
                    idx = end;

                    canvas.setPixels(pixels, 0, w, 0, 0, w, h);
                    Bitmap scaled = Bitmap.createScaledBitmap(
                            canvas, gifWidth, gifHeight, true);
                    encoder.addFrame(scaled);
                    if (scaled != canvas) scaled.recycle();

                    frameCount++;
                    int pct = (frameCount * 100) / totalFrames;
                    handler.post(() -> {
                        if (callback != null) callback.onProgress(pct);
                    });
                }

                canvas.setPixels(pixels, 0, w, 0, 0, w, h);
                Bitmap finalScaled = Bitmap.createScaledBitmap(
                        canvas, gifWidth, gifHeight, true);
                encoder.addFrame(finalScaled);
                if (finalScaled != canvas) finalScaled.recycle();

                encoder.finish();
                handler.post(() -> {
                    if (callback != null) callback.onComplete(gifFile);
                });

            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "OOM during GIF export", oom);
                handler.post(() -> {
                    if (callback != null) callback.onError("Memory limit reached");
                });
            } catch (Exception e) {
                Log.e(TAG, "GIF export failed", e);
                handler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            } finally {
                if (canvas != null && !canvas.isRecycled()) {
                    canvas.recycle();
                }
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════
    // ─── Step Model (unchanged) ───
    // ═══════════════════════════════════════════════════════════

    public static class TimeLapseStep {
        public int x;
        public int y;
        public int color;
        public long timestamp;

        public TimeLapseStep() {}

        public TimeLapseStep(int x, int y, int color, long timestamp) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.timestamp = timestamp;
        }
    }
}