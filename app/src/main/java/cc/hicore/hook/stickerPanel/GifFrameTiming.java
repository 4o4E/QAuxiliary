package cc.hicore.hook.stickerPanel;

import androidx.annotation.NonNull;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 读取 GIF 每个图像帧的原始时间，避免播放器自行套用浏览器兼容延迟。
 */
public final class GifFrameTiming {

    private static final int FASTEST_FRAME_MILLIS = 10;

    private GifFrameTiming() {
        throw new AssertionError("no instance");
    }

    /**
     * 返回每个图像帧的毫秒延迟；零延迟按 GIF 可表达的最高 100 FPS 处理。
     */
    @NonNull
    public static int[] readDelayMillis(@NonNull ByteBuffer source) throws IOException {
        Reader input = new Reader(source);
        byte[] header = input.readBytes(6);
        String signature = new String(header, StandardCharsets.US_ASCII);
        if (!"GIF87a".equals(signature) && !"GIF89a".equals(signature)) {
            return new int[0];
        }
        input.skip(4);
        int packed = input.readUnsignedByte();
        input.skip(2);
        if ((packed & 0x80) != 0) {
            input.skip(colorTableSize(packed));
        }

        List<Integer> delays = new ArrayList<>();
        int pendingDelayCentiseconds = 0;
        while (input.hasRemaining()) {
            int marker = input.readUnsignedByte();
            if (marker == 0x3b) {
                return toIntArray(delays);
            }
            if (marker == 0x21) {
                int label = input.readUnsignedByte();
                if (label == 0xf9) {
                    pendingDelayCentiseconds = readGraphicControlExtension(input);
                } else {
                    input.skipSubBlocks();
                }
            } else if (marker == 0x2c) {
                delays.add(Math.max(FASTEST_FRAME_MILLIS,
                        pendingDelayCentiseconds * 10));
                pendingDelayCentiseconds = 0;
                readImage(input);
            } else {
                throw new IOException("GIF 包含未知的数据块: " + marker);
            }
        }
        throw new EOFException("GIF 缺少结束标记");
    }

    public static boolean isGif(@NonNull File source) throws IOException {
        byte[] header = new byte[6];
        try (FileInputStream input = new FileInputStream(source)) {
            int offset = 0;
            while (offset < header.length) {
                int read = input.read(header, offset, header.length - offset);
                if (read < 0) {
                    return false;
                }
                offset += read;
            }
        }
        String signature = new String(header, StandardCharsets.US_ASCII);
        return "GIF87a".equals(signature) || "GIF89a".equals(signature);
    }

    private static int readGraphicControlExtension(Reader input) throws IOException {
        int blockSize = input.readUnsignedByte();
        if (blockSize != 4) {
            throw new IOException("GIF 图形控制块长度无效: " + blockSize);
        }
        input.readUnsignedByte();
        int delay = input.readLittleEndianUnsignedShort();
        input.readUnsignedByte();
        input.requireBlockTerminator();
        return delay;
    }

    private static void readImage(Reader input) throws IOException {
        input.skip(8);
        int packed = input.readUnsignedByte();
        if ((packed & 0x80) != 0) {
            input.skip(colorTableSize(packed));
        }
        input.readUnsignedByte();
        input.skipSubBlocks();
    }

    private static int colorTableSize(int packed) {
        return 3 * (1 << ((packed & 0x07) + 1));
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }

    private static final class Reader {
        private final ByteBuffer data;

        private Reader(ByteBuffer source) {
            data = source.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
            data.position(0);
        }

        private boolean hasRemaining() {
            return data.hasRemaining();
        }

        private int readUnsignedByte() throws EOFException {
            if (!data.hasRemaining()) {
                throw new EOFException("GIF 数据不完整");
            }
            return data.get() & 0xff;
        }

        private int readLittleEndianUnsignedShort() throws EOFException {
            int low = readUnsignedByte();
            int high = readUnsignedByte();
            return low | (high << 8);
        }

        private byte[] readBytes(int size) throws EOFException {
            if (size < 0 || data.remaining() < size) {
                throw new EOFException("GIF 数据不完整");
            }
            byte[] result = new byte[size];
            data.get(result);
            return result;
        }

        private void skip(int size) throws EOFException {
            if (size < 0 || data.remaining() < size) {
                throw new EOFException("GIF 数据不完整");
            }
            data.position(data.position() + size);
        }

        private void skipSubBlocks() throws EOFException {
            while (true) {
                int size = readUnsignedByte();
                if (size == 0) {
                    return;
                }
                skip(size);
            }
        }

        private void requireBlockTerminator() throws IOException {
            if (readUnsignedByte() != 0) {
                throw new IOException("GIF 扩展块缺少结束标记");
            }
        }
    }
}
