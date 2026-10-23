package com.colourmate.paintbynumber.model.reveal;

import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * An SVG picture as shapes — the input of the vector engine (backend: svg_builder.write_vector).
 *
 * <p>The raster engine draws a 1254px id-map and border bitmap, which is why SVG pictures went
 * soft and pixelated once zoomed: the SVG's own crisp line was thrown away on the server. Here
 * every shape stays a polygon in picture coordinates, so it is drawn sharp at any zoom.
 *
 * <p>Each shape carries the region it belongs to — the SAME ids as regions.json / id_map.png,
 * which keep driving taps, numbers and progress — or 0 for outline ink, which is always drawn.
 */
public final class VectorArt {

    public final int width, height;
    /** Per shape, in drawing (painter's) order. */
    public final Path[] paths;
    public final RectF[] bounds;
    public final int[] regionOf;
    public final int[] colour;
    /** Shape indices per region id; ink shapes are not listed. */
    public final SparseArray<int[]> shapesOfRegion = new SparseArray<>();

    private VectorArt(int width, int height, Path[] paths, RectF[] bounds, int[] regionOf,
                      int[] colour) {
        this.width = width;
        this.height = height;
        this.paths = paths;
        this.bounds = bounds;
        this.regionOf = regionOf;
        this.colour = colour;

        SparseArray<List<Integer>> tmp = new SparseArray<>();
        for (int i = 0; i < paths.length; i++) {
            int r = regionOf[i];
            if (r == 0) continue;
            List<Integer> list = tmp.get(r);
            if (list == null) tmp.put(r, list = new ArrayList<>());
            list.add(i);
        }
        for (int i = 0; i < tmp.size(); i++) {
            List<Integer> list = tmp.valueAt(i);
            int[] arr = new int[list.size()];
            for (int j = 0; j < arr.length; j++) arr[j] = list.get(j);
            shapesOfRegion.put(tmp.keyAt(i), arr);
        }
    }

    /** The finished picture at 1:1 — every shape in its true colour, in the SVG's own order. */
    public void renderFinished(android.graphics.Canvas c, int outline) {
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setStyle(android.graphics.Paint.Style.FILL_AND_STROKE);
        p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        c.drawColor(Color.WHITE);
        for (int i = 0; i < paths.length; i++) {
            boolean ink = regionOf[i] == 0;
            p.setColor(ink ? outline : colour[i]);
            p.setStrokeWidth(ink ? 0.4f : 0.9f);
            c.drawPath(paths[i], p);
        }
    }

    /**
     * Reads vector.json.gz. The server may or may not have already undone the gzip in transit,
     * so the magic bytes decide — unzipping twice would fail and unzipping never would too.
     */
    public static VectorArt parse(InputStream raw) throws Exception {
        BufferedInputStream in = new BufferedInputStream(raw);
        in.mark(2);
        int b0 = in.read(), b1 = in.read();
        in.reset();
        InputStream body = (b0 == 0x1f && b1 == 0x8b) ? new GZIPInputStream(in) : in;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = body.read(buf)) != -1) out.write(buf, 0, n);

        JSONObject js = new JSONObject(out.toString("UTF-8"));
        int w = js.getInt("w"), h = js.getInt("h");
        JSONArray shapes = js.getJSONArray("s");
        int count = shapes.length();
        Path[] paths = new Path[count];
        RectF[] bounds = new RectF[count];
        int[] region = new int[count];
        int[] colour = new int[count];

        for (int i = 0; i < count; i++) {
            // [region, "rrggbb", [x0, y0, dx1, dy1, ...], ...]  coordinates are tenths of a pixel
            JSONArray s = shapes.getJSONArray(i);
            region[i] = s.getInt(0);
            colour[i] = Color.parseColor("#" + s.getString(1));
            Path p = new Path();
            p.setFillType(Path.FillType.EVEN_ODD);
            for (int k = 2; k < s.length(); k++) {
                JSONArray sub = s.getJSONArray(k);
                int x = sub.getInt(0), y = sub.getInt(1);
                p.moveTo(x / 10f, y / 10f);
                for (int j = 2; j + 1 < sub.length(); j += 2) {
                    x += sub.getInt(j);
                    y += sub.getInt(j + 1);
                    p.lineTo(x / 10f, y / 10f);
                }
                p.close();
            }
            RectF r = new RectF();
            p.computeBounds(r, true);
            paths[i] = p;
            bounds[i] = r;
        }
        return new VectorArt(w, h, paths, bounds, region, colour);
    }
}
