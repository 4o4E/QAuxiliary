package cc.hicore.hook.stickerPanel;

import static org.junit.Assert.assertArrayEquals;

import java.nio.ByteBuffer;
import org.junit.Test;

public class GifFrameTimingTest {

    @Test
    public void preservesFastestGifFrameTiming() throws Exception {
        byte[] gif = gifWithDelays(0, 1, 2, 9);

        int[] result = GifFrameTiming.readDelayMillis(ByteBuffer.wrap(gif));

        assertArrayEquals(new int[]{10, 10, 20, 90}, result);
    }

    @Test
    public void ignoresGraphicControlSignatureInsideImageData() throws Exception {
        byte[] source = gifWithDelays(2);
        byte[] pattern = new byte[]{0x21, (byte) 0xf9, 0x04, 0x00, 0x01, 0x00, 0x00, 0x00};
        byte[] withPattern = new byte[source.length + pattern.length];
        int subBlockLengthOffset = 13 + 19;
        System.arraycopy(source, 0, withPattern, 0, subBlockLengthOffset);
        withPattern[subBlockLengthOffset] = 10;
        System.arraycopy(source, subBlockLengthOffset + 1, withPattern,
                subBlockLengthOffset + 1, 2);
        System.arraycopy(pattern, 0, withPattern, subBlockLengthOffset + 3, pattern.length);
        withPattern[withPattern.length - 1] = 0x3b;

        int[] result = GifFrameTiming.readDelayMillis(ByteBuffer.wrap(withPattern));

        assertArrayEquals(new int[]{20}, result);
    }

    @Test
    public void returnsNoFramesForStaticImage() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a};

        int[] result = GifFrameTiming.readDelayMillis(ByteBuffer.wrap(png));

        assertArrayEquals(new int[0], result);
    }

    private static byte[] gifWithDelays(int... delays) {
        byte[] header = new byte[]{
                'G', 'I', 'F', '8', '9', 'a',
                0x01, 0x00, 0x01, 0x00,
                0x00, 0x00, 0x00
        };
        byte[] frame = new byte[]{
                0x21, (byte) 0xf9, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x2c, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                0x02, 0x02, 0x44, 0x01, 0x00
        };
        byte[] result = new byte[header.length + frame.length * delays.length + 1];
        System.arraycopy(header, 0, result, 0, header.length);
        int offset = header.length;
        for (int delay : delays) {
            System.arraycopy(frame, 0, result, offset, frame.length);
            result[offset + 4] = (byte) (delay & 0xff);
            result[offset + 5] = (byte) ((delay >>> 8) & 0xff);
            offset += frame.length;
        }
        result[offset] = 0x3b;
        return result;
    }
}
