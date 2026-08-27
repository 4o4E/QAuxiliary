package cc.hicore.hook.stickerPanel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GifLoopControllerTest {

    @Test
    public void stopsAfterSingleIterationWithoutLoopExtension() {
        GifLoopController controller = new GifLoopController(1);

        assertTrue(controller.onLastFrameElapsed());
    }

    @Test
    public void stopsAfterEncodedFiniteIterations() {
        GifLoopController controller = new GifLoopController(3);

        assertFalse(controller.onLastFrameElapsed());
        assertFalse(controller.onLastFrameElapsed());
        assertTrue(controller.onLastFrameElapsed());
    }

    @Test
    public void neverStopsInfiniteAnimation() {
        GifLoopController controller = new GifLoopController(0);

        for (int index = 0; index < 1_000; index++) {
            assertFalse(controller.onLastFrameElapsed());
        }
    }
}
