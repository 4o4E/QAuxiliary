package cc.hicore.hook.stickerPanel;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import cc.ioctl.util.HostInfo;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.gifdecoder.ExactTimingGifDecoder;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.gif.GifBitmapProvider;
import com.bumptech.glide.util.ByteBufferUtil;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 统一准备表情图片：GIF 使用精确时间轴，静态图片继续交给 Glide。
 */
public final class StickerPanelImageLoader {

    private StickerPanelImageLoader() {
        throw new AssertionError("no instance");
    }

    @NonNull
    public static PreparedImage prepare(@NonNull File source, int targetWidth, int targetHeight,
            boolean allowAnimation) throws IOException {
        if (!allowAnimation) {
            return PreparedImage.staticImage(source, true);
        }
        if (!GifFrameTiming.isGif(source)) {
            return PreparedImage.staticImage(source, false);
        }
        ByteBuffer data = ByteBufferUtil.fromFile(source);
        int[] delays = GifFrameTiming.readDelayMillis(data);
        if (delays.length <= 1) {
            return PreparedImage.staticImage(source, false);
        }

        Context context = HostInfo.getApplication();
        Glide glide = Glide.get(context);
        BitmapPool bitmapPool = glide.getBitmapPool();
        GifBitmapProvider bitmapProvider = new GifBitmapProvider(bitmapPool, glide.getArrayPool());
        ExactTimingGifDecoder.Result decoded = ExactTimingGifDecoder.decode(
                bitmapProvider, data, delays, targetWidth, targetHeight);
        StickerGifDrawable drawable = new StickerGifDrawable(decoded.getDecoder(), bitmapPool,
                decoded.getFirstFrame(), decoded.getDurationMillis());
        return PreparedImage.animated(source, drawable);
    }

    public static void display(@NonNull ImageView view, @NonNull PreparedImage image,
            boolean skipMemoryCache) {
        clear(view);
        if (image.drawable != null) {
            view.setImageDrawable(image.drawable);
            image.drawable.start();
            image.claimed = true;
            return;
        }
        RequestBuilder<Drawable> request = Glide.with(HostInfo.getApplication())
                .load(image.source)
                .fitCenter();
        if (image.dontAnimate) {
            request = request.dontAnimate();
        }
        if (skipMemoryCache) {
            request = request.skipMemoryCache(true);
        }
        request.into(view);
    }

    public static void clear(@NonNull ImageView view) {
        Drawable current = view.getDrawable();
        if (current instanceof StickerGifDrawable) {
            // 必须先解绑；回收后再由 ImageView 改可见性会触发 Glide 的保护异常。
            view.setImageDrawable(null);
            StickerGifDrawable gif = (StickerGifDrawable) current;
            gif.stop();
            gif.recycle();
            return;
        }
        Glide.with(HostInfo.getApplication()).clear(view);
        view.setImageDrawable(null);
    }

    public static final class PreparedImage {
        private final File source;
        private final StickerGifDrawable drawable;
        private final boolean dontAnimate;
        private boolean claimed;

        private PreparedImage(File source, StickerGifDrawable drawable, boolean dontAnimate) {
            this.source = source;
            this.drawable = drawable;
            this.dontAnimate = dontAnimate;
        }

        private static PreparedImage staticImage(File source, boolean dontAnimate) {
            return new PreparedImage(source, null, dontAnimate);
        }

        private static PreparedImage animated(File source, StickerGifDrawable drawable) {
            return new PreparedImage(source, drawable, false);
        }

        /**
         * 异步结果已过期时主动回收尚未交给 ImageView 的解码器。
         */
        public void discard() {
            if (!claimed && drawable != null) {
                drawable.stop();
                drawable.recycle();
                claimed = true;
            }
        }
    }
}
