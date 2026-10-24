package com.colourmate.paintbynumber.model.reveal;

/**
 * The shared C++ game core (repo root {@code shared/cmcore}), seen from Java.
 *
 * <p>The same core is meant to run the iOS app, so the rules of the game — which region a tap
 * means, what counts as filled, progress, saving, hints — live there once instead of twice.
 * Drawing stays on each platform.
 *
 * <p>Region indices are positions in {@code regions.json} order, the same order
 * {@link RevealLevel#regions} is built in.
 */
final class RevealCore {

    static {
        // Already loaded by Manager for the URL getters; loading twice is a no-op.
        System.loadLibrary("colorbynumber");
    }

    private long handle;

    RevealCore(String regionsJson) {
        handle = nativeLoad(regionsJson);
    }

    int width() { return nativeWidth(h()); }
    int height() { return nativeHeight(h()); }
    String look() { return nativeLook(h()); }
    /** 8 ints per region: id, number, cx, cy, left, top, right, bottom. */
    int[] regionInts() { return nativeRegionInts(h()); }
    float[] regionRadii() { return nativeRegionRadii(h()); }
    int[] palette() { return nativePalette(h()); }

    int tapTarget(char[] idMap, int x, int y, int selected, int tolerance) {
        return nativeTapTarget(h(), idMap, x, y, selected, tolerance);
    }

    int nearestUnfilled(char[] idMap, int x, int y, int tolerance, int number, boolean onlyNumber) {
        return nativeNearestUnfilled(h(), idMap, x, y, tolerance, number, onlyNumber);
    }

    boolean reveal(int index) { return nativeReveal(h(), index); }
    int totalOf(int number) { return nativeTotalOf(h(), number); }
    int revealedOf(int number) { return nativeRevealedOf(h(), number); }
    int progressPercent() { return nativeProgressPercent(h()); }
    boolean isFinished() { return nativeIsFinished(h()); }
    int nextUnfinishedNumber(int after) { return nativeNextUnfinishedNumber(h(), after); }
    int hintRegion(int number) { return nativeHintRegion(h(), number); }
    String encodeProgress() { return nativeEncodeProgress(h()); }
    int[] restoreProgress(String encoded) { return nativeRestoreProgress(h(), encoded); }

    /** {int[]{w,h}, int[] region, int[] colour, int[] shapeStart, int[] pathStart, float[] points}. */
    static Object[] parseVector(byte[] bytes) { return nativeParseVector(bytes); }

    private long h() {
        if (handle == 0) throw new IllegalStateException("RevealCore already released");
        return handle;
    }

    synchronized void release() {
        if (handle != 0) {
            nativeFree(handle);
            handle = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            release();
        } finally {
            super.finalize();
        }
    }

    private static native long nativeLoad(String regionsJson);
    private static native void nativeFree(long handle);
    private static native int nativeWidth(long handle);
    private static native int nativeHeight(long handle);
    private static native String nativeLook(long handle);
    private static native int[] nativeRegionInts(long handle);
    private static native float[] nativeRegionRadii(long handle);
    private static native int[] nativePalette(long handle);
    private static native int nativeTapTarget(long handle, char[] idMap, int x, int y, int selected,
                                              int tolerance);
    private static native int nativeNearestUnfilled(long handle, char[] idMap, int x, int y,
                                                    int tolerance, int number, boolean onlyNumber);
    private static native boolean nativeReveal(long handle, int index);
    private static native int nativeTotalOf(long handle, int number);
    private static native int nativeRevealedOf(long handle, int number);
    private static native int nativeProgressPercent(long handle);
    private static native boolean nativeIsFinished(long handle);
    private static native int nativeNextUnfinishedNumber(long handle, int after);
    private static native int nativeHintRegion(long handle, int number);
    private static native String nativeEncodeProgress(long handle);
    private static native int[] nativeRestoreProgress(long handle, String encoded);
    private static native Object[] nativeParseVector(byte[] bytes);
}
