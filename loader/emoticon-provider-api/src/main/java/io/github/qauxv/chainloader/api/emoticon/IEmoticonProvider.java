package io.github.qauxv.chainloader.api.emoticon;

import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import java.util.List;

/**
 * 向 QAux 表情面板提供外部表情数据的稳定接口。
 */
public interface IEmoticonProvider {

    @NonNull
    String getProviderId();

    @NonNull
    String getDisplayName();

    long getRevision();

    @NonNull
    List<EmoticonPackInfo> listPacks() throws EmoticonProviderException;

    @NonNull
    List<EmoticonItemInfo> listItems(@NonNull String packId, int offset, int limit)
            throws EmoticonProviderException;

    @NonNull
    ParcelFileDescriptor openItem(@NonNull String packId, @NonNull String itemId)
            throws EmoticonProviderException;

    void recordUse(@NonNull String packId, @NonNull String itemId, long usedAtMillis)
            throws EmoticonProviderException;
}
