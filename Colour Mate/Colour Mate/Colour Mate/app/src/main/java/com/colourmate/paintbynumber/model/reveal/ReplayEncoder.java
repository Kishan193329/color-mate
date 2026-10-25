package com.colourmate.paintbynumber.model.reveal;

import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Writes a {@link ReplayRenderer} to an H.264 MP4 for sharing. Frames are drawn with a hardware
 * canvas straight onto the encoder's input surface (the platform documents lockCanvas on that
 * surface as unreliable). Presentation times are set from the frame number, not the wall clock,
 * since encoding runs much faster than real time. Call off the UI thread.
 */
public final class ReplayEncoder {

    private static final int BIT_RATE = 3_000_000;
    private static final long TIMEOUT_US = 10_000;

    private ReplayEncoder() {}

    public static void encode(ReplayRenderer r, File out) throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, r.width, r.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, ReplayRenderer.FPS);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        MediaMuxer muxer = null;
        Surface surface = null;
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = codec.createInputSurface();
            codec.start();
            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            int[] state = {-1, 0};   // track, frames written
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            r.reset();
            for (int i = 0; i < r.frameCount(); i++) {
                Canvas c = surface.lockHardwareCanvas();
                try {
                    r.draw(i, c);
                } finally {
                    surface.unlockCanvasAndPost(c);
                }
                drain(codec, muxer, info, state, false);
            }
            codec.signalEndOfInputStream();
            drain(codec, muxer, info, state, true);
        } finally {
            try { codec.stop(); } catch (Exception ignored) { }
            codec.release();
            if (surface != null) surface.release();
            if (muxer != null) {
                try { muxer.stop(); } catch (Exception ignored) { }
                muxer.release();
            }
        }
    }

    private static void drain(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info,
                              int[] state, boolean toEnd) {
        while (true) {
            int idx = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!toEnd) return;
                continue;
            }
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                state[0] = muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                continue;
            }
            if (idx < 0) continue;

            ByteBuffer data = codec.getOutputBuffer(idx);
            boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
            if (!config && info.size > 0 && data != null && state[0] >= 0) {
                info.presentationTimeUs = state[1]++ * 1_000_000L / ReplayRenderer.FPS;
                data.position(info.offset);
                data.limit(info.offset + info.size);
                muxer.writeSampleData(state[0], data, info);
            }
            codec.releaseOutputBuffer(idx, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
        }
    }
}
