package com.colourmate.paintbynumber.model.game;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Animated GIF encoder.
 *
 * Based on the classic NeuQuant + LZW GIF encoder adapted for Android Bitmaps.
 * No external dependencies required.
 *
 * Usage:
 *   GifEncoder encoder = new GifEncoder();
 *   encoder.setDelay(100);       // 100ms between frames
 *   encoder.setRepeat(0);        // Loop forever
 *   encoder.setQuality(10);      // Color quantization quality (1=best, 20=fastest)
 *   encoder.start(outputStream);
 *   encoder.addFrame(bitmap1);
 *   encoder.addFrame(bitmap2);
 *   encoder.finish();
 */
public class GifEncoder {

    // ─── Constants ───
    private static final int MAX_COLOR_DEPTH = 8;   // Max bits per color channel
    private static final int PALETTE_SIZE = 256;     // Number of colors in palette
    private static final int EOF_CODE = -1;

    // ─── State ───
    private int width;
    private int height;
    private int delay = 0;          // Frame delay in ms (÷10 for GIF units)
    private int repeat = -1;        // -1 = no repeat, 0 = loop forever, N = loop N times
    private int quality = 10;       // NeuQuant quality: 1 (best) to 30 (fastest)
    private int disposalCode = 0;   // 0 = unspecified
    private boolean firstFrame = true;
    private boolean started = false;
    private boolean sizeSet = false;

    private OutputStream out;
    private Bitmap currentFrame;
    private int[] currentPixels;
    private byte[] indexedPixels;
    private byte[] colorPalette;
    private boolean[] usedColors;
    private int colorDepth;
    private int paletteSize;

    // Transparent color handling
    private int transparentColor = -1;
    private boolean hasTransparency = false;
    private int transparentIndex = 0;

    // ─── Public API ───

    /**
     * Sets the delay time between frames.
     * @param ms delay in milliseconds
     */
    public void setDelay(int ms) {
        this.delay = Math.round(ms / 10f);
    }

    /**
     * Sets the number of times the GIF should loop.
     * @param count 0 = infinite loop, -1 = no loop, N = loop N times
     */
    public void setRepeat(int count) {
        if (count >= 0) {
            this.repeat = count;
        }
    }

    /**
     * Sets the color quantization quality.
     * @param quality 1 = best quality (slow), 20 = fastest (lower quality)
     */
    public void setQuality(int quality) {
        this.quality = Math.max(1, quality);
    }

    /**
     * Sets the disposal method for frames.
     * @param code 0=unspecified, 1=don't dispose, 2=restore to bg, 3=restore to prev
     */
    public void setDispose(int code) {
        if (code >= 0) {
            this.disposalCode = code;
        }
    }

    /**
     * Sets transparent color (pixels of this color become transparent).
     * @param color ARGB color value
     */
    public void setTransparent(int color) {
        this.transparentColor = color;
        this.hasTransparency = true;
    }

    /**
     * Sets the frame size explicitly.
     * If not called, size is taken from the first frame.
     */
    public void setSize(int width, int height) {
        if (width > 0 && height > 0) {
            this.width = width;
            this.height = height;
            this.sizeSet = true;
        }
    }

    /**
     * Starts GIF encoding to the given output stream.
     * @param os output stream to write GIF data to
     * @return true if started successfully
     */
    public boolean start(OutputStream os) {
        if (os == null) return false;

        try {
            this.out = os;
            this.started = true;
            this.firstFrame = true;
            // Don't write header yet — wait for first frame to know dimensions
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Adds a frame to the GIF animation.
     * @param bitmap the frame image (will be read, not modified)
     * @return true if frame was added successfully
     */
    public boolean addFrame(Bitmap bitmap) {
        if (bitmap == null || !started || out == null) {
            return false;
        }

        try {
            if (!sizeSet || firstFrame) {
                width = bitmap.getWidth();
                height = bitmap.getHeight();
                sizeSet = true;
            }

            currentFrame = bitmap;
            extractPixels();
            quantizeColors();

            if (firstFrame) {
                writeHeader();
                writeLogicalScreenDescriptor();
                writeGlobalColorTable();

                if (repeat >= 0) {
                    writeNetscapeLoopExtension();
                }
            }

            writeGraphicControlExtension();
            writeImageDescriptor();
            writeLzwCompressedData();

            firstFrame = false;
            return true;

        } catch (IOException e) {
            return false;
        } catch (OutOfMemoryError oom) {
            return false;
        }
    }

    /**
     * Finishes the GIF and closes the stream.
     * @return true if finished successfully
     */
    public boolean finish() {
        if (!started || out == null) return false;

        try {
            out.write(0x3B); // GIF trailer
            out.flush();
            out.close();
            started = false;
            out = null;

            // Release references
            currentFrame = null;
            currentPixels = null;
            indexedPixels = null;
            colorPalette = null;
            usedColors = null;

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ─── Pixel Extraction ───

    private void extractPixels() {
        int count = width * height;

        if (currentPixels == null || currentPixels.length != count) {
            currentPixels = new int[count];
        }

        // Handle bitmap size mismatch
        if (currentFrame.getWidth() != width || currentFrame.getHeight() != height) {
            Bitmap resized = Bitmap.createScaledBitmap(currentFrame, width, height, true);
            resized.getPixels(currentPixels, 0, width, 0, 0, width, height);
            if (resized != currentFrame) {
                resized.recycle();
            }
        } else {
            currentFrame.getPixels(currentPixels, 0, width, 0, 0, width, height);
        }
    }

    // ─── Color Quantization (Median Cut) ───

    private void quantizeColors() {
        int pixelCount = width * height;

        if (indexedPixels == null || indexedPixels.length != pixelCount) {
            indexedPixels = new byte[pixelCount];
        }

        // Build color histogram
        int[] histogram = new int[32768]; // 15-bit color space (5 bits per channel)

        for (int i = 0; i < pixelCount; i++) {
            int pixel = currentPixels[i];
            int r = (Color.red(pixel) >> 3) & 0x1F;
            int g = (Color.green(pixel) >> 3) & 0x1F;
            int b = (Color.blue(pixel) >> 3) & 0x1F;
            int index15 = (r << 10) | (g << 5) | b;
            histogram[index15]++;
        }

        // Find most used colors (simple frequency-based palette)
        int[][] colorFreq = new int[32768][2]; // [color15bit, frequency]
        int uniqueColors = 0;

        for (int i = 0; i < 32768; i++) {
            if (histogram[i] > 0) {
                colorFreq[uniqueColors][0] = i;
                colorFreq[uniqueColors][1] = histogram[i];
                uniqueColors++;
            }
        }

        // Sort by frequency (descending)
        java.util.Arrays.sort(colorFreq, 0, uniqueColors,
                (a, b) -> Integer.compare(b[1], a[1]));

        // Build palette (top 256 colors)
        int paletteCount = Math.min(uniqueColors, PALETTE_SIZE);
        colorDepth = MAX_COLOR_DEPTH;
        paletteSize = PALETTE_SIZE;

        colorPalette = new byte[paletteSize * 3];
        int[] palette15 = new int[paletteCount]; // 15-bit palette colors

        for (int i = 0; i < paletteCount; i++) {
            int c15 = colorFreq[i][0];
            palette15[i] = c15;

            int r = ((c15 >> 10) & 0x1F) << 3;
            int g = ((c15 >> 5) & 0x1F) << 3;
            int b = (c15 & 0x1F) << 3;

            colorPalette[i * 3] = (byte) r;
            colorPalette[i * 3 + 1] = (byte) g;
            colorPalette[i * 3 + 2] = (byte) b;
        }

        // Build reverse lookup table (15-bit → palette index)
        byte[] lookupTable = new byte[32768];
        java.util.Arrays.fill(lookupTable, (byte) 0);

        for (int i = 0; i < paletteCount; i++) {
            lookupTable[palette15[i]] = (byte) i;
        }

        // For colors not in palette, find nearest
        // Pre-compute palette RGB for distance calculations
        int[][] paletteRGB = new int[paletteCount][3];
        for (int i = 0; i < paletteCount; i++) {
            int c15 = palette15[i];
            paletteRGB[i][0] = ((c15 >> 10) & 0x1F) << 3;
            paletteRGB[i][1] = ((c15 >> 5) & 0x1F) << 3;
            paletteRGB[i][2] = (c15 & 0x1F) << 3;
        }

        // Map all pixels to palette indices
        for (int i = 0; i < pixelCount; i++) {
            int pixel = currentPixels[i];
            int r = (Color.red(pixel) >> 3) & 0x1F;
            int g = (Color.green(pixel) >> 3) & 0x1F;
            int b = (Color.blue(pixel) >> 3) & 0x1F;
            int index15 = (r << 10) | (g << 5) | b;

            if (histogram[index15] > 0 && lookupTable[index15] != 0
                    || index15 == palette15[0]) {
                // Direct lookup
                indexedPixels[i] = lookupTable[index15];
            } else {
                // Find nearest color in palette
                indexedPixels[i] = (byte) findNearestColor(
                        r << 3, g << 3, b << 3, paletteRGB, paletteCount);
            }

            // Handle transparency
            if (hasTransparency && pixel == transparentColor) {
                indexedPixels[i] = (byte) transparentIndex;
            }
        }

        // Find transparent index if needed
        if (hasTransparency) {
            transparentIndex = findNearestColor(
                    Color.red(transparentColor),
                    Color.green(transparentColor),
                    Color.blue(transparentColor),
                    paletteRGB, paletteCount);
        }
    }

    private int findNearestColor(int r, int g, int b,
                                 int[][] paletteRGB, int paletteCount) {
        int minDist = Integer.MAX_VALUE;
        int minIndex = 0;

        for (int i = 0; i < paletteCount; i++) {
            int dr = r - paletteRGB[i][0];
            int dg = g - paletteRGB[i][1];
            int db = b - paletteRGB[i][2];
            int dist = dr * dr + dg * dg + db * db;

            if (dist < minDist) {
                minDist = dist;
                minIndex = i;
                if (dist == 0) break; // Exact match
            }
        }

        return minIndex;
    }

    // ─── GIF Writing ───

    private void writeHeader() throws IOException {
        writeString("GIF89a");
    }

    private void writeLogicalScreenDescriptor() throws IOException {
        writeShort(width);
        writeShort(height);

        // Packed field:
        // bit 7: global color table flag (1)
        // bits 4-6: color resolution (7 = 8 bits)
        // bit 3: sort flag (0)
        // bits 0-2: size of global color table (7 = 256 colors)
        int packed = 0x80          // Global Color Table Flag
                | ((colorDepth - 1) << 4) // Color Resolution
                | 0x00              // Sort Flag
                | (colorDepth - 1); // Size of GCT

        out.write(packed);
        out.write(0); // Background color index
        out.write(0); // Pixel aspect ratio
    }

    private void writeGlobalColorTable() throws IOException {
        out.write(colorPalette, 0, colorPalette.length);

        // Pad to full palette size if needed
        int remaining = (paletteSize * 3) - colorPalette.length;
        for (int i = 0; i < remaining; i++) {
            out.write(0);
        }
    }

    private void writeNetscapeLoopExtension() throws IOException {
        out.write(0x21);  // Extension introducer
        out.write(0xFF);  // Application extension label
        out.write(11);    // Block size
        writeString("NETSCAPE2.0");
        out.write(3);     // Sub-block size
        out.write(1);     // Loop sub-block ID
        writeShort(repeat);
        out.write(0);     // Block terminator
    }

    private void writeGraphicControlExtension() throws IOException {
        out.write(0x21);  // Extension introducer
        out.write(0xF9);  // Graphic control label
        out.write(4);     // Block size

        int packed;
        if (hasTransparency) {
            packed = (disposalCode << 2) | 0x01; // Transparent flag
        } else {
            packed = (disposalCode << 2);
        }

        out.write(packed);
        writeShort(delay);
        out.write(hasTransparency ? transparentIndex : 0);
        out.write(0);     // Block terminator
    }

    private void writeImageDescriptor() throws IOException {
        out.write(0x2C); // Image separator
        writeShort(0);   // Left position
        writeShort(0);   // Top position
        writeShort(width);
        writeShort(height);
        out.write(0);    // No local color table, not interlaced
    }

    // ─── LZW Compression ───

    private void writeLzwCompressedData() throws IOException {
        LzwEncoder encoder = new LzwEncoder(
                width, height, indexedPixels, colorDepth);
        encoder.encode(out);
    }

    // ─── Utility Methods ───

    private void writeString(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            out.write((byte) s.charAt(i));
        }
    }

    private void writeShort(int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    // ─── LZW Encoder (Inner Class) ───

    private static class LzwEncoder {

        private static final int BITS = 12;
        private static final int MAX_CODE = (1 << BITS) - 1;
        private static final int TABLE_SIZE = 5003; // Prime for hashing

        private final int imgWidth;
        private final int imgHeight;
        private final byte[] pixels;
        private final int initCodeSize;

        // LZW state
        private int clearCode;
        private int eofCode;
        private int freeCode;
        private int codeSize;
        private int maxCode;

        // Hash table
        private final int[] hashTable = new int[TABLE_SIZE];
        private final int[] codeTable = new int[TABLE_SIZE];

        // Bit output buffer
        private int curBits = 0;
        private int curAccum = 0;
        private final byte[] accum = new byte[256]; // Sub-block buffer
        private int accumCount = 0;

        // Pixel position
        private int remaining;
        private int curPixel;

        LzwEncoder(int width, int height, byte[] pixels, int colorDepth) {
            this.imgWidth = width;
            this.imgHeight = height;
            this.pixels = pixels;
            this.initCodeSize = Math.max(2, colorDepth);
        }

        void encode(OutputStream os) throws IOException {
            os.write(initCodeSize); // Write min code size

            remaining = imgWidth * imgHeight;
            curPixel = 0;

            compress(initCodeSize + 1, os);

            os.write(0); // Block terminator
        }

        private void compress(int initBits, OutputStream os) throws IOException {
            clearCode = 1 << (initBits - 1);
            eofCode = clearCode + 1;
            freeCode = clearCode + 2;
            codeSize = initBits;
            maxCode = (1 << codeSize) - 1;

            clearTable();
            output(clearCode, os);

            int ent = nextPixel();

            outer:
            while (remaining > 0) {
                int c = nextPixel();
                int fcode = (c << BITS) + ent;
                int hIndex = ((c << 8) ^ ent) % TABLE_SIZE;
                if (hIndex < 0) hIndex += TABLE_SIZE;

                // Check hash table
                if (hashTable[hIndex] == fcode) {
                    ent = codeTable[hIndex];
                    continue;
                }

                // Handle collision
                if (hashTable[hIndex] >= 0) {
                    int disp = TABLE_SIZE - hIndex;
                    if (hIndex == 0) disp = 1;

                    do {
                        hIndex -= disp;
                        if (hIndex < 0) hIndex += TABLE_SIZE;

                        if (hashTable[hIndex] == fcode) {
                            ent = codeTable[hIndex];
                            continue outer;
                        }
                    } while (hashTable[hIndex] >= 0);
                }

                // New entry
                output(ent, os);
                ent = c;

                if (freeCode <= MAX_CODE) {
                    codeTable[hIndex] = freeCode++;
                    hashTable[hIndex] = fcode;
                } else {
                    clearTable();
                    freeCode = clearCode + 2;
                    codeSize = initCodeSize + 1;
                    maxCode = (1 << codeSize) - 1;
                    output(clearCode, os);
                }
            }

            output(ent, os);
            output(eofCode, os);
            flushBits(os);
        }

        private void output(int code, OutputStream os) throws IOException {
            curAccum |= (code << curBits);
            curBits += codeSize;

            while (curBits >= 8) {
                accum[accumCount++] = (byte) (curAccum & 0xFF);
                curAccum >>= 8;
                curBits -= 8;

                if (accumCount >= 254) {
                    flushBlock(os);
                }
            }

            // Increase code size if needed
            if (freeCode > maxCode && codeSize < BITS) {
                codeSize++;
                maxCode = (1 << codeSize) - 1;
            }
        }

        private void flushBits(OutputStream os) throws IOException {
            if (curBits > 0) {
                accum[accumCount++] = (byte) (curAccum & 0xFF);
            }
            flushBlock(os);
        }

        private void flushBlock(OutputStream os) throws IOException {
            if (accumCount > 0) {
                os.write(accumCount);
                os.write(accum, 0, accumCount);
                accumCount = 0;
            }
        }

        private void clearTable() {
            java.util.Arrays.fill(hashTable, -1);
        }

        private int nextPixel() {
            if (remaining <= 0) return EOF_CODE;
            remaining--;
            int pixel = pixels[curPixel++] & 0xFF;
            return pixel;
        }
    }
}