package cc.hicore.hook.stickerPanel;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cc.hicore.Utils.XLog;
import com.bumptech.glide.gifdecoder.GifDecoder;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import io.github.qauxv.BuildConfig;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 直接驱动 Glide GIF 解码器，并用绝对截止时间避免逐帧调度开销累积。
 */
public final class StickerGifDrawable extends Drawable implements Animatable {

    private static final AtomicInteger THREAD_INDEX = new AtomicInteger();
    private static final ExecutorService DECODER_EXECUTOR = Executors.newFixedThreadPool(4,
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    return new Thread(() -> {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
                        runnable.run();
                    }, "QAux-StickerGif-" + THREAD_INDEX.incrementAndGet());
                }
            });

    private final Object lock = new Object();
    private final GifDecoder decoder;
    private final GifLoopController loopController;
    private final BitmapPool bitmapPool;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final int encodedDurationMillis;
    private Bitmap currentFrame;
    private Bitmap pendingFrame;
    private int pendingFrameIndex;
    private long pendingDeadline;
    private long nextFrameDeadline;
    private long loopStartedAt;
    private int loggedLoops;
    private boolean decodeInFlight;
    private boolean running;
    private boolean recycled;

    private final Runnable deliverFrame = this::deliverDecodedFrame;

    public StickerGifDrawable(GifDecoder decoder, BitmapPool bitmapPool,
            Bitmap firstFrame, int encodedDurationMillis) {
        this.decoder = decoder;
        this.loopController = new GifLoopController(decoder.getTotalIterationCount());
        this.bitmapPool = bitmapPool;
        this.currentFrame = firstFrame;
        this.encodedDurationMillis = encodedDurationMillis;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Bitmap frame = currentFrame;
        if (frame != null && !frame.isRecycled()) {
            canvas.drawBitmap(frame, null, getBounds(), paint);
        }
    }

    @Override
    public void start() {
        synchronized (lock) {
            if (running || recycled) {
                return;
            }
            running = true;
            long now = SystemClock.uptimeMillis();
            loopStartedAt = now;
            nextFrameDeadline = now + decoder.getDelay(decoder.getCurrentFrameIndex());
        }
        scheduleDecode();
    }

    @Override
    public void stop() {
        Bitmap abandoned = null;
        synchronized (lock) {
            running = false;
            mainHandler.removeCallbacks(deliverFrame);
            if (pendingFrame != null) {
                abandoned = pendingFrame;
                pendingFrame = null;
            }
        }
        release(abandoned);
    }

    @Override
    public boolean isRunning() {
        synchronized (lock) {
            return running;
        }
    }

    public void recycle() {
        stop();
        Bitmap displayed;
        synchronized (lock) {
            if (recycled) {
                return;
            }
            recycled = true;
            displayed = currentFrame;
            currentFrame = null;
            decoder.clear();
        }
        release(displayed);
    }

    @Override
    public int getIntrinsicWidth() {
        Bitmap frame = currentFrame;
        return frame == null ? -1 : frame.getWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        Bitmap frame = currentFrame;
        return frame == null ? -1 : frame.getHeight();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private void scheduleDecode() {
        synchronized (lock) {
            if (!running || recycled || decodeInFlight || pendingFrame != null) {
                return;
            }
            decodeInFlight = true;
        }
        DECODER_EXECUTOR.execute(this::decodeNextFrame);
    }

    private void decodeNextFrame() {
        Bitmap decoded = null;
        boolean shouldDeliver = false;
        synchronized (lock) {
            try {
                if (!running || recycled) {
                    return;
                }
                if (decoder.getCurrentFrameIndex() == decoder.getFrameCount() - 1
                        && loopController.onLastFrameElapsed()) {
                    running = false;
                    return;
                }
                decoder.advance();
                decoded = decoder.getNextFrame();
                if (decoded != null && running && !recycled) {
                    pendingFrame = decoded;
                    pendingFrameIndex = decoder.getCurrentFrameIndex();
                    pendingDeadline = nextFrameDeadline;
                    shouldDeliver = true;
                    decoded = null;
                }
            } finally {
                decodeInFlight = false;
            }
        }
        release(decoded);
        if (shouldDeliver) {
            mainHandler.postAtTime(deliverFrame, pendingDeadline);
        }
    }

    private void deliverDecodedFrame() {
        Bitmap next;
        Bitmap previous;
        int frameIndex;
        synchronized (lock) {
            if (!running || recycled || pendingFrame == null) {
                return;
            }
            next = pendingFrame;
            pendingFrame = null;
            frameIndex = pendingFrameIndex;
            previous = currentFrame;
            currentFrame = next;
            nextFrameDeadline = pendingDeadline + decoder.getDelay(frameIndex);
        }
        release(previous);
        invalidateSelf();
        logLoopIfNeeded(frameIndex);
        scheduleDecode();
    }

    private void logLoopIfNeeded(int frameIndex) {
        if (!BuildConfig.DEBUG || frameIndex != 0 || loggedLoops >= 2 || loopStartedAt == 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long actualDuration = now - loopStartedAt;
        loggedLoops++;
        XLog.d("StickerGifDrawable", "loop=" + loggedLoops + " encoded="
                + encodedDurationMillis + "ms actual=" + actualDuration
                + "ms frames=" + decoder.getFrameCount());
        loopStartedAt = now;
    }

    private void release(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmapPool.put(bitmap);
        }
    }
}
