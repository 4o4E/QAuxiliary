package cc.hicore.hook.stickerPanel.Provider;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import cc.hicore.Utils.FunConf;
import cc.hicore.Utils.XLog;
import cc.hicore.hook.stickerPanel.Hooker.StickerPanelEntryHooker;
import cc.hicore.hook.stickerPanel.ICreator;
import cc.hicore.hook.stickerPanel.StickerPanelAsync;
import cc.hicore.hook.stickerPanel.StickerPanelImageLoader;
import cc.hicore.message.chat.SessionUtils;
import cc.hicore.message.common.MsgSender;
import cc.ioctl.util.HostInfo;
import cc.ioctl.util.LayoutHelper;
import io.github.qauxv.R;
import io.github.qauxv.chainloader.api.emoticon.EmoticonItemInfo;
import io.github.qauxv.chainloader.api.emoticon.EmoticonPackInfo;
import io.github.qauxv.chainloader.api.emoticon.EmoticonProviderException;
import io.github.qauxv.chainloader.api.emoticon.IEmoticonProvider;
import io.github.qauxv.util.Toasts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 把单个外部 Provider 表情包适配为 QAux 现有表情面板项。
 */
public final class ProviderStickerImpl implements ICreator.IMainPanelItem {

    private final Context context;
    private final IEmoticonProvider provider;
    private final EmoticonPackInfo pack;
    private final ViewGroup root;
    private final LinearLayout itemContainer;
    private final List<ViewInfo> imageViews = new ArrayList<>();
    private boolean loading;
    private boolean created;

    public ProviderStickerImpl(@NonNull Context context, @NonNull IEmoticonProvider provider,
            @NonNull EmoticonPackInfo pack) {
        this.context = context;
        this.provider = provider;
        this.pack = pack;
        root = (ViewGroup) View.inflate(context, R.layout.sticker_panel_plus_pack_item, null);
        TextView title = root.findViewById(R.id.Sticker_Panel_Item_Name);
        title.setText(pack.getDisplayName());
        root.findViewById(R.id.Sticker_Panel_Set_Item).setVisibility(View.GONE);
        itemContainer = root.findViewById(R.id.Sticker_Item_Container);
    }

    @Override
    public View getView() {
        if (!created && !loading) {
            loadItems();
        }
        return root;
    }

    private void loadItems() {
        loading = true;
        showMessage("正在读取 " + pack.getDisplayName() + "…", null);
        StickerPanelAsync.run(this::readAllItems, items -> {
                    buildItems(items);
                    created = true;
                    loading = false;
                    root.postDelayed(this::notifyViewUpdate0, 50);
                }, cause -> {
                    XLog.e("ProviderStickerImpl.loadItems", cause);
                    loading = false;
                    showMessage(displayMessage(cause), this::retry);
                });
    }

    private List<EmoticonItemInfo> readAllItems() throws EmoticonProviderException {
        int expected = pack.getItemCount();
        List<EmoticonItemInfo> result = new ArrayList<>(expected);
        int offset = 0;
        while (offset < expected) {
            int limit = Math.min(200, expected - offset);
            List<EmoticonItemInfo> page = provider.listItems(pack.getId(), offset, limit);
            if (page.isEmpty()) {
                break;
            }
            result.addAll(page);
            offset += page.size();
        }
        result.sort(Comparator.comparingLong(EmoticonItemInfo::getOrder));
        return result;
    }

    private void buildItems(List<EmoticonItemInfo> items) {
        itemContainer.removeAllViews();
        imageViews.clear();
        if (items.isEmpty()) {
            showMessage("这个表情包暂时没有表情", null);
            return;
        }
        LinearLayout line = null;
        for (int index = 0; index < items.size(); index++) {
            if (index % COLUMN_COUNT == 0) {
                line = new LinearLayout(context);
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lineParams.bottomMargin = LayoutHelper.dip2px(context, 16);
                itemContainer.addView(line, lineParams);
            }
            if (line != null) {
                line.addView(createItemView(items.get(index), index % COLUMN_COUNT));
            }
        }
    }

    private View createItemView(EmoticonItemInfo item, int column) {
        int itemWidth = LayoutHelper.getScreenWidth(context) / 6;
        int distance = (LayoutHelper.getScreenWidth(context) - itemWidth * COLUMN_COUNT)
                / (COLUMN_COUNT - 1);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(itemWidth, itemWidth);
        if (column > 0) {
            params.leftMargin = distance;
        }
        image.setLayoutParams(params);

        ViewInfo info = new ViewInfo(image, item);
        imageViews.add(info);
        image.setOnClickListener(view -> send(info));
        image.setOnLongClickListener(view -> {
            preview(info);
            return true;
        });
        return image;
    }

    private void send(ViewInfo info) {
        StickerPanelAsync.run(() -> ProviderCacheManager.getOrCreate(provider, pack.getId(), info.item),
                file -> {
                    MsgSender.send_pic_by_contact(
                            SessionUtils.AIOParam2Contact(StickerPanelEntryHooker.AIOParam),
                            file.getAbsolutePath(),
                            (code, message) -> onSendResult(info.item, code, message));
                    if (!FunConf.getBoolean("global", "sticker_panel_set_dont_close_panel", false)) {
                        ICreator.dismissAll();
                    }
                }, cause -> {
                    XLog.e("ProviderStickerImpl.send", cause);
                    Toasts.error(context, displayMessage(cause));
                });
    }

    private void onSendResult(EmoticonItemInfo item, int code, String message) {
        if (code != 0) {
            String detail = message == null || message.isEmpty() ? String.valueOf(code) : message;
            Toasts.error(context, "表情发送失败：" + detail);
            return;
        }
        StickerPanelAsync.run(() -> {
            provider.recordUse(pack.getId(), item.getId(), System.currentTimeMillis());
            return null;
        }, ignored -> { }, error -> XLog.e("ProviderStickerImpl.recordUse", error));
    }

    private void preview(ViewInfo info) {
        int width = LayoutHelper.getScreenWidth(HostInfo.getApplication()) / 2;
        StickerPanelAsync.run(() -> StickerPanelImageLoader.prepare(
                        ProviderCacheManager.getOrCreate(provider, pack.getId(), info.item),
                        width, width, true),
                prepared -> {
                    ImageView preview = new ImageView(context);
                    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    preview.setLayoutParams(new ViewGroup.LayoutParams(width, width));
                    StickerPanelImageLoader.display(preview, prepared, false);
                    new AlertDialog.Builder(context)
                            .setTitle(pack.getDisplayName())
                            .setView(preview)
                            .setPositiveButton("关闭", null)
                            .setOnDismissListener(dialog -> StickerPanelImageLoader.clear(preview))
                            .show();
                }, error -> Toasts.error(context, displayMessage(error)));
    }

    private void retry() {
        created = false;
        loading = false;
        loadItems();
    }

    private void showMessage(String text, Runnable retry) {
        itemContainer.removeAllViews();
        imageViews.clear();
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(LayoutHelper.dip2px(context, 16), LayoutHelper.dip2px(context, 24),
                LayoutHelper.dip2px(context, 16), LayoutHelper.dip2px(context, 24));
        TextView message = new TextView(context);
        message.setText(text);
        message.setTextColor(context.getColor(R.color.global_font_color));
        panel.addView(message);
        if (retry != null) {
            Button button = new Button(context);
            button.setText("重试");
            button.setOnClickListener(view -> retry.run());
            panel.addView(button);
        }
        itemContainer.addView(panel);
    }

    @Override
    public void onViewDestroy() {
        for (ViewInfo info : imageViews) {
            StickerPanelImageLoader.clear(info.view);
            info.status = 0;
        }
    }

    @Override
    public long getID() {
        return ((long) provider.getProviderId().hashCode() << 32) ^ (pack.getId().hashCode() & 0xffffffffL);
    }

    @Override
    public void notifyViewUpdate0() {
        for (ViewInfo info : imageViews) {
            if (LayoutHelper.isSmallWindowNeedPlay(info.view)) {
                if (info.status == 0) {
                    info.status = 1;
                    loadImage(info);
                }
            } else if (info.status != 0) {
                StickerPanelImageLoader.clear(info.view);
                info.status = 0;
            }
        }
    }

    private void loadImage(ViewInfo info) {
        int size = info.view.getLayoutParams().width;
        StickerPanelAsync.run(() -> StickerPanelImageLoader.prepare(
                        ProviderCacheManager.getOrCreate(provider, pack.getId(), info.item),
                        size, size, true),
                prepared -> {
                    if (info.status == 1) {
                        StickerPanelImageLoader.display(info.view, prepared, false);
                        info.status = 2;
                    } else {
                        prepared.discard();
                    }
                }, error -> {
                    info.status = 0;
                    XLog.e("ProviderStickerImpl.loadImage", error);
                });
    }

    private static String displayMessage(Throwable error) {
        if (error instanceof EmoticonProviderException && error.getMessage() != null) {
            return error.getMessage();
        }
        return "外部表情 Provider 读取失败";
    }

    private static final int COLUMN_COUNT = 5;

    private static final class ViewInfo {
        private final ImageView view;
        private final EmoticonItemInfo item;
        private volatile int status;

        private ViewInfo(ImageView view, EmoticonItemInfo item) {
            this.view = view;
            this.item = item;
        }
    }
}
