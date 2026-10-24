package com.colourmate.paintbynumber.model.reveal;

import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.SparseArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
     * Reads vector.json.gz. Parsing (gzip or not, decided by the magic bytes, since a server may
     * or may not undo the gzip in transit) is the shared C++ core; this only turns its flat
     * arrays into Android paths.
     */
    public static VectorArt parse(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);

        Object[] parsed = RevealCore.parseVector(out.toByteArray());
        int[] size = (int[]) parsed[0];
        int[] region = (int[]) parsed[1];
        int[] colour = (int[]) parsed[2];
        int[] shapeStart = (int[]) parsed[3];
        int[] pathStart = (int[]) parsed[4];
        float[] points = (float[]) parsed[5];

        int count = region.length;
        Path[] paths = new Path[count];
        RectF[] bounds = new RectF[count];
        for (int i = 0; i < count; i++) {
            Path p = new Path();
            p.setFillType(Path.FillType.EVEN_ODD);
            for (int sub = shapeStart[i]; sub < shapeStart[i + 1]; sub++) {
                int first = pathStart[sub], end = pathStart[sub + 1];
                if (first >= end) continue;
                p.moveTo(points[first * 2], points[first * 2 + 1]);
                for (int k = first + 1; k < end; k++) p.lineTo(points[k * 2], points[k * 2 + 1]);
                p.close();
            }
            RectF r = new RectF();
            p.computeBounds(r, true);
            paths[i] = p;
            bounds[i] = r;
        }
        return new VectorArt(size[0], size[1], paths, bounds, region, colour);
    }
}
