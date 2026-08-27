package io.github.qauxv.chainloader.api.emoticon;

import androidx.annotation.NonNull;
import java.util.Objects;

/**
 * 外部表情图片的不可变元数据。
 */
public final class EmoticonItemInfo {

    private final String id;
    private final String fileName;
    private final String mimeType;
    private final boolean animated;
    private final long order;

    public EmoticonItemInfo(@NonNull String id, @NonNull String fileName,
            @NonNull String mimeType, boolean animated, long order) {
        this.id = requireText(id, "id");
        this.fileName = requireText(fileName, "fileName");
        this.mimeType = requireText(mimeType, "mimeType");
        this.animated = animated;
        this.order = order;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }

    public boolean isAnimated() {
        return animated;
    }

    public long getOrder() {
        return order;
    }

    @NonNull
    private static String requireText(@NonNull String value, @NonNull String field) {
        String checked = Objects.requireNonNull(value, field);
        if (checked.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return checked;
    }
}
