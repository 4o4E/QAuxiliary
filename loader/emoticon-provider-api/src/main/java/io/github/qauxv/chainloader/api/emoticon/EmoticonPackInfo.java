package io.github.qauxv.chainloader.api.emoticon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * 外部表情包的不可变元数据。
 */
public final class EmoticonPackInfo {

    private final String id;
    private final String displayName;
    private final String coverItemId;
    private final int itemCount;
    private final long order;

    public EmoticonPackInfo(@NonNull String id, @NonNull String displayName,
            @Nullable String coverItemId, int itemCount, long order) {
        this.id = requireText(id, "id");
        this.displayName = requireText(displayName, "displayName");
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount must not be negative");
        }
        this.coverItemId = coverItemId;
        this.itemCount = itemCount;
        this.order = order;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public String getCoverItemId() {
        return coverItemId;
    }

    public int getItemCount() {
        return itemCount;
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
