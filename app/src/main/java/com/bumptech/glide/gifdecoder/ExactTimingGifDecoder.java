package com.bumptech.glide.gifdecoder;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 位于 Glide 解码器包内，以便在不复制其解码实现的前提下恢复原始帧延迟。
 */
public final class ExactTimingGifDecoder {

    private ExactTimingGifDecoder() {
        throw new AssertionError("no instance");
    }

    @NonNull
    public static Result decode(@NonNull GifDecoder.BitmapProvider bitmapProvider,
            @NonNull ByteBuffer source, @NonNull int[] exactDelaysMillis,
            int targetWidth, int targetHeight) throws IOException {
        GifHeader header = new GifHeaderParser().setData(source.asReadOnlyBuffer()).parseHeader();
        if (header.getStatus() != GifDecoder.STATUS_OK || header.getNumFrames() <= 1) {
            throw new IOException("GIF 动画头解析失败");
        }
        if (header.frames.size() != exactDelaysMillis.length) {
            throw new IOException("GIF 图像帧与时间块数量不一致");
        }
        int totalDurationMillis = 0;
        for (int index = 0; index < exactDelaysMillis.length; index++) {
            int delay = exactDelaysMillis[index];
            header.frames.get(index).delay = delay;
            totalDurationMillis += delay;
        }

        int sampleSize = sampleSize(header, targetWidth, targetHeight);
        StandardGifDecoder decoder = new StandardGifDecoder(
                bitmapProvider, header, source.asReadOnlyBuffer(), sampleSize);
        decoder.advance();
        Bitmap firstFrame = decoder.getNextFrame();
        if (firstFrame == null) {
            decoder.clear();
            throw new IOException("GIF 首帧解码失败");
        }
        return new Result(decoder, firstFrame, totalDurationMillis);
    }

    private static int sampleSize(GifHeader header, int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            return 1;
        }
        int exact = Math.min(header.getHeight() / targetHeight, header.getWidth() / targetWidth);
        return Math.max(1, Integer.highestOneBit(exact));
    }

    public static final class Result {
        private final GifDecoder decoder;
        private final Bitmap firstFrame;
        private final int durationMillis;

        private Result(GifDecoder decoder, Bitmap firstFrame, int durationMillis) {
            this.decoder = decoder;
            this.firstFrame = firstFrame;
            this.durationMillis = durationMillis;
        }

        public GifDecoder getDecoder() {
            return decoder;
        }

        public Bitmap getFirstFrame() {
            return firstFrame;
        }

        public int getDurationMillis() {
            return durationMillis;
        }
    }
}
