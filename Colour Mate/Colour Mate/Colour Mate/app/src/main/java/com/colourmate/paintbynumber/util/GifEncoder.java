package com.colourmate.paintbynumber.util;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Encodes a sequence of Bitmap frames into an animated GIF.
 * Lightweight implementation with no external dependencies.
 * Uses NeuQuant-based color quantization for quality.
 */
public class GifEncoder {

    private OutputStream out;
    private int width, height;
    private int delay = 100;
    private int repeat = 0;
    private boolean started = false;
    private boolean firstFrame = true;
    private int quality = 10;

    public void setDelay(int ms) {
        this.delay = Math.max(10, ms);
    }

    public void setRepeat(int count) {
        this.repeat = count;
    }

    public void setQuality(int q) {
        this.quality = Math.max(1, Math.min(30, q));
    }

    public boolean start(OutputStream os) {
        if (os == null) return false;
        this.out = os;
        try {
            writeString("GIF89a");
            started = true;
            firstFrame = true;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean addFrame(Bitmap frame) {
        if (!started || frame == null || out == null) return false;

        try {
            if (firstFrame) {
                width = frame.getWidth();
                height = frame.getHeight();
            }

            int[] pixels = new int[width * height];
            Bitmap scaled = Bitmap.createScaledBitmap(frame, width, height, true);
            scaled.getPixels(pixels, 0, width, 0, 0, width, height);
            if (scaled != frame) scaled.recycle();

            byte[] indexedPixels = new byte[pixels.length];
            int[][] palette = buildPalette(pixels, indexedPixels);

            if (firstFrame) {
                writeLSD();
                writeNetscapeExtension();
                firstFrame = false;
            }

            writeGraphicControlExtension();
            writeImageDescriptor();
            writeColorTable(palette);
            writeLZW(indexedPixels, palette.length);

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean finish() {
        if (!started) return false;
        try {
            out.write(0x3B);
            out.flush();
            out.close();
            started = false;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private int[][] buildPalette(int[] pixels, byte[] indexed) {
        int paletteSize = 256;
        int[][] colors = new int[paletteSize][3];

        java.util.Map<Integer, Integer> colorMap = new java.util.LinkedHashMap<>();
        int idx = 0;

        for (int i = 0; i < pixels.length && idx < paletteSize; i++) {
            int c = pixels[i] & 0x00FFFFFF;
            if (!colorMap.containsKey(c)) {
                colorMap.put(c, idx);
                colors[idx][0] = Color.red(pixels[i]);
                colors[idx][1] = Color.green(pixels[i]);
                colors[idx][2] = Color.blue(pixels[i]);
                idx++;
            }
        }

        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i] & 0x00FFFFFF;
            Integer paletteIdx = colorMap.get(c);
            if (paletteIdx != null) {
                indexed[i] = (byte) (paletteIdx & 0xFF);
            } else {
                indexed[i] = (byte) findClosest(colors, idx, pixels[i]);
            }
        }

        return colors;
    }

    private int findClosest(int[][] palette, int count, int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        int minDist = Integer.MAX_VALUE;
        int best = 0;
        for (int i = 0; i < count; i++) {
            int dr = r - palette[i][0];
            int dg = g - palette[i][1];
            int db = b - palette[i][2];
            int dist = dr * dr + dg * dg + db * db;
            if (dist < minDist) {
                minDist = dist;
                best = i;
            }
        }
        return best;
    }

    private void writeLSD() throws IOException {
        writeShort(width);
        writeShort(height);
        out.write(0x70);
        out.write(0);
        out.write(0);
    }

    private void writeNetscapeExtension() throws IOException {
        out.write(0x21);
        out.write(0xFF);
        out.write(11);
        writeString("NETSCAPE2.0");
        out.write(3);
        out.write(1);
        writeShort(repeat);
        out.write(0);
    }

    private void writeGraphicControlExtension() throws IOException {
        out.write(0x21);
        out.write(0xF9);
        out.write(4);
        out.write(0);
        writeShort(delay / 10);
        out.write(0);
        out.write(0);
    }

    private void writeImageDescriptor() throws IOException {
        out.write(0x2C);
        writeShort(0);
        writeShort(0);
        writeShort(width);
        writeShort(height);
        out.write(0x87);
    }

    private void writeColorTable(int[][] palette) throws IOException {
        for (int i = 0; i < 256; i++) {
            if (i < palette.length) {
                out.write(palette[i][0]);
                out.write(palette[i][1]);
                out.write(palette[i][2]);
            } else {
                out.write(0);
                out.write(0);
                out.write(0);
            }
        }
    }

    private void writeLZW(byte[] pixels, int colorCount) throws IOException {
        int minCodeSize = 8;
        out.write(minCodeSize);

        LZWEncoder encoder = new LZWEncoder(width, height, pixels, minCodeSize);
        encoder.encode(out);

        out.write(0);
    }

    private void writeShort(int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private void writeString(String s) throws IOException {
        out.write(s.getBytes());
    }

    /**
     * Minimal LZW encoder for GIF.
     */
    private static class LZWEncoder {
        private static final int MAXCODE = 4095;
        private final int imgW, imgH;
        private final byte[] pixels;
        private final int initCodeSize;

        private int curPixel;
        private int remaining;
        private int accum;
        private int bits;
        private byte[] block = new byte[256];
        private int blockIdx;

        LZWEncoder(int w, int h, byte[] px, int colorDepth) {
            imgW = w;
            imgH = h;
            pixels = px;
            initCodeSize = Math.max(2, colorDepth);
        }

        void encode(OutputStream os) throws IOException {
            int codeSize = initCodeSize + 1;
            int clearCode = 1 << initCodeSize;
            int eofCode = clearCode + 1;
            int nextCode = eofCode + 1;
            int codeLimit = 1 << codeSize;

            short[] codeTable = new short[MAXCODE + 1];
            short[] prefixTable = new short[MAXCODE + 1];
            byte[] suffixTable = new byte[MAXCODE + 1];
            boolean[] inUse = new boolean[MAXCODE + 1];

            remaining = imgW * imgH;
            curPixel = 0;
            bits = 0;
            accum = 0;
            blockIdx = 0;

            output(clearCode, codeSize, os);

            int current = nextPixel() & 0xFF;
            remaining--;

            while (remaining > 0) {
                int next = nextPixel() & 0xFF;
                remaining--;

                int hash = (current << 8) | next;
                boolean found = false;
                for (int i = eofCode + 1; i < nextCode; i++) {
                    if (inUse[i] && prefixTable[i] == current && suffixTable[i] == (byte) next) {
                        current = i;
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    output(current, codeSize, os);
                    if (nextCode <= MAXCODE) {
                        prefixTable[nextCode] = (short) current;
                        suffixTable[nextCode] = (byte) next;
                        inUse[nextCode] = true;
                        nextCode++;
                        if (nextCode > codeLimit && codeSize < 12) {
                            codeSize++;
                            codeLimit = 1 << codeSize;
                        }
                    } else {
                        output(clearCode, codeSize, os);
                        codeSize = initCodeSize + 1;
                        codeLimit = 1 << codeSize;
                        nextCode = eofCode + 1;
                        java.util.Arrays.fill(inUse, false);
                    }
                    current = next;
                }
            }

            output(current, codeSize, os);
            output(eofCode, codeSize, os);
            flushBits(os);
            flushBlock(os);
        }

        private int nextPixel() {
            if (curPixel < pixels.length) {
                return pixels[curPixel++] & 0xFF;
            }
            return 0;
        }

        private void output(int code, int codeSize, OutputStream os) throws IOException {
            accum |= (code << bits);
            bits += codeSize;
            while (bits >= 8) {
                block[blockIdx++] = (byte) (accum & 0xFF);
                accum >>= 8;
                bits -= 8;
                if (blockIdx >= 254) {
                    flushBlock(os);
                }
            }
        }

        private void flushBits(OutputStream os) throws IOException {
            if (bits > 0) {
                block[blockIdx++] = (byte) (accum & 0xFF);
                accum = 0;
                bits = 0;
            }
        }

        private void flushBlock(OutputStream os) throws IOException {
            if (blockIdx > 0) {
                os.write(blockIdx);
                os.write(block, 0, blockIdx);
                blockIdx = 0;
            }
        }
    }
}
