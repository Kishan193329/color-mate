package com.colourmate.paintbynumber.model.reveal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/** Plays a {@link ReplayRenderer} once, fitted to the view; a tap skips to the end. */
public class ReplayView extends View {

    public interface Listener {
        void onReplayEnded();
    }

    private ReplayRenderer renderer;
    private Bitmap frame;
    private Canvas frameCanvas;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private long startedAt;
    private int shown = -1;
    private boolean playing;
    private Listener listener;

    public ReplayView(Context context) { super(context); }
    public ReplayView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void play(ReplayRenderer r, Listener l) {
        if (frame == null || frame.getWidth() != r.width || frame.getHeight() != r.height) {
            frame = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888);
            frameCanvas = new Canvas(frame);
        }
        renderer = r;
        listener = l;
        renderer.reset();
        shown = -1;
        startedAt = SystemClock.uptimeMillis();
        playing = true;
        setOnClickListener(v -> skip());
        postInvalidateOnAnimation();
    }

    public boolean isPlaying() {
        return playing;
    }

    /** Jump to the finished picture. */
    public void skip() {
        if (!playing) return;
        startedAt = SystemClock.uptimeMillis() - renderer.frameCount() * 1000L / ReplayRenderer.FPS;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.WHITE);
        if (renderer == null) return;

        int last = renderer.frameCount() - 1;
        int index = (int) Math.min(last,
                (SystemClock.uptimeMillis() - startedAt) * ReplayRenderer.FPS / 1000L);
        if (index != shown) {
            renderer.draw(index, frameCanvas);
            shown = index;
        }

        float s = Math.min(getWidth() / (float) frame.getWidth(), getHeight() / (float) frame.getHeight());
        float w = frame.getWidth() * s, h = frame.getHeight() * s;
        dst.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
        canvas.drawBitmap(frame, null, dst, paint);

        if (!playing) return;
        if (index < last) {
            postInvalidateOnAnimation();
        } else {
            playing = false;
            setOnClickListener(null);
            setClickable(false);
            if (listener != null) listener.onReplayEnded();
        }
    }
}
