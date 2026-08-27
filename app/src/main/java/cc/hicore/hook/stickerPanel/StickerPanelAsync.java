package cc.hicore.hook.stickerPanel;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 表情面板共用的固定后台执行器，避免图片准备和 Provider IPC 阻塞主线程。
 */
public final class StickerPanelAsync {

    private static final AtomicInteger THREAD_INDEX = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "QAux-StickerPanel-" + THREAD_INDEX.incrementAndGet());
        }
    });
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private StickerPanelAsync() {
        throw new AssertionError("no instance");
    }

    public static <T> void run(Callable<T> task, Consumer<T> success, Consumer<Throwable> failure) {
        EXECUTOR.execute(() -> {
            try {
                T result = task.call();
                MAIN_HANDLER.post(() -> success.accept(result));
            } catch (Throwable error) {
                MAIN_HANDLER.post(() -> failure.accept(error));
            }
        });
    }
}
