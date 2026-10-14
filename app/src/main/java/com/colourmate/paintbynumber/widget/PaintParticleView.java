package com.colourmate.paintbynumber.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Drifting paint for the splash screen.
 *
 * <p>Soft blobs of the brand's five paints rise and sway, and now and then one lands and spreads a
 * ring — the visual promise of the app, made with nothing but a Canvas. It replaced a 2 MB GIF
 * that was downloaded from a server on every cold start, so the splash now costs no network, no
 * cache and no wait.
 *
 * <p>Self-driving: it posts its own frames while attached and stops the moment it is detached.
 * Honours the system's "remove animations" setting by drawing a single static frame.
 */
public class PaintParticleView extends View {

    private static final int BLOB_COUNT = 14;
    private static final int RING_COUNT = 3;

    private static final int[] PAINTS = {
            0xFF7A5CFF,   // violet
            0xFF37A9FF,   // sky
            0xFFFF6BA9,   // blossom
            0xFF21D4C2,   // turquoise
            0xFFFFC24B    // gold
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(7);

    /** Reused every frame to move each blob's gradient into place — see {@link Blob#shader}. */
    private final android.graphics.Matrix shaderMatrix = new android.graphics.Matrix();

    private Blob[] blobs;
    private Ring[] rings;
    private boolean animate = true;
    private long lastFrame;

    public PaintParticleView(Context context) {
        this(context, null);
    }

    public PaintParticleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        animate = animationsEnabled(context);
    }

    private static boolean animationsEnabled(Context context) {
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w <= 0 || h <= 0) return;

        blobs = new Blob[BLOB_COUNT];
        for (int i = 0; i < BLOB_COUNT; i++) {
            blobs[i] = newBlob(w, h, random.nextFloat() * h);
        }

        rings = new Ring[RING_COUNT];
        for (int i = 0; i < RING_COUNT; i++) {
            rings[i] = newRing(w, h, i * 0.33f);
        }
    }

    private Blob newBlob(int w, int h, float y) {
        Blob b = new Blob();
        b.x = random.nextFloat() * w;
        b.y = y;
        b.radius = dp(18) + random.nextFloat() * dp(52);
        b.colour = PAINTS[random.nextInt(PAINTS.length)];
        b.alpha = 0.16f + random.nextFloat() * 0.22f;
        b.speed = dp(6) + random.nextFloat() * dp(16);       // px per second, upward
        b.sway = dp(8) + random.nextFloat() * dp(16);
        b.phase = random.nextFloat() * (float) Math.PI * 2f;
        b.rebuildShader();
        return b;
    }

    private Ring newRing(int w, int h, float progress) {
        Ring r = new Ring();
        r.x = dp(40) + random.nextFloat() * (w - dp(80));
        r.y = dp(80) + random.nextFloat() * (h - dp(160));
        r.colour = PAINTS[random.nextInt(PAINTS.length)];
        r.progress = progress;
        r.duration = 2.6f + random.nextFloat() * 2f;
        return r;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (blobs == null) return;

        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : (now - lastFrame) / 1_000_000_000f;
        lastFrame = now;
        if (dt > 0.05f) dt = 0.05f;          // a dropped frame must not teleport the paint

        int w = getWidth(), h = getHeight();

        for (Blob b : blobs) {
            if (animate) {
                b.y -= b.speed * dt;
                b.phase += dt * 0.9f;
                if (b.y + b.radius < 0) {
                    Blob fresh = newBlob(w, h, h + b.radius);
                    b.x = fresh.x; b.radius = fresh.radius; b.colour = fresh.colour;
                    b.alpha = fresh.alpha; b.speed = fresh.speed; b.sway = fresh.sway;
                    b.y = h + b.radius;
                    b.rebuildShader();       // radius and colour changed, so the gradient must too
                }
            }
            float x = b.x + (float) Math.sin(b.phase) * b.sway;

            shaderMatrix.setTranslate(x, b.y);
            b.shader.setLocalMatrix(shaderMatrix);
            paint.setShader(b.shader);
            canvas.drawCircle(x, b.y, b.radius, paint);
        }
        paint.setShader(null);

        // A dropped bead of paint spreading — the app's core gesture, ambient.
        paint.setStyle(Paint.Style.STROKE);
        for (Ring r : rings) {
            if (animate) {
                r.progress += dt / r.duration;
                if (r.progress > 1f) {
                    Ring fresh = newRing(w, h, 0f);
                    r.x = fresh.x; r.y = fresh.y; r.colour = fresh.colour;
                    r.duration = fresh.duration; r.progress = 0f;
                }
            }
            float eased = 1f - (1f - r.progress) * (1f - r.progress);
            float radius = dp(6) + eased * dp(90);
            paint.setStrokeWidth(dp(2) * (1f - eased) + dp(0.6f));
            paint.setColor(withAlpha(r.colour, 0.30f * (1f - eased)));
            canvas.drawCircle(r.x, r.y, radius, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        if (animate && isAttachedToWindow()) {
            postInvalidateOnAnimation();
        }
    }

    private static int withAlpha(int colour, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return Color.argb(a, Color.red(colour), Color.green(colour), Color.blue(colour));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrame = 0;
        postInvalidateOnAnimation();
    }

    private static final class Blob {
        float x, y, radius, alpha, speed, sway, phase;
        int colour;

        /**
         * Built once, at the origin, and moved into place each frame with a local matrix.
         *
         * <p>A RadialGradient is expensive to construct, and the old code built one per blob per
         * frame — on the splash, which is the very first thing the user sees. Only the position
         * changes frame to frame, and a shader can be translated, so it is built once and reused
         * until the blob is recycled with a new radius or colour.
         */
        RadialGradient shader;

        void rebuildShader() {
            shader = new RadialGradient(0f, 0f, radius,
                    withAlpha(colour, alpha), withAlpha(colour, 0f), Shader.TileMode.CLAMP);
        }
    }

    private static final class Ring {
        float x, y, progress, duration;
        int colour;
    }
}
