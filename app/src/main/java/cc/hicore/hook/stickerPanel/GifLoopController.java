package cc.hicore.hook.stickerPanel;

/**
 * 按 Glide 解码器给出的总播放次数判断是否应停在当前末帧。
 */
final class GifLoopController {

    private final int totalIterationCount;
    private int completedIterations;

    GifLoopController(int totalIterationCount) {
        this.totalIterationCount = totalIterationCount;
    }

    boolean onLastFrameElapsed() {
        if (totalIterationCount == 0) {
            return false;
        }
        completedIterations++;
        return completedIterations >= totalIterationCount;
    }
}
