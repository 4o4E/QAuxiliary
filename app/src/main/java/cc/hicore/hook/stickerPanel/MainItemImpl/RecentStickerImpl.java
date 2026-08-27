package cc.hicore.hook.stickerPanel.MainItemImpl;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cc.hicore.Utils.FunConf;
import cc.hicore.Utils.XLog;
import cc.hicore.hook.stickerPanel.Hooker.StickerPanelEntryHooker;
import cc.hicore.hook.stickerPanel.ICreator;
import cc.hicore.hook.stickerPanel.LocalDataHelper;
import cc.hicore.hook.stickerPanel.RecentStickerHelper;
import cc.hicore.hook.stickerPanel.StickerPanelAsync;
import cc.hicore.hook.stickerPanel.StickerPanelImageLoader;
import cc.hicore.message.chat.SessionUtils;
import cc.hicore.message.common.MsgSender;
import cc.ioctl.util.HostInfo;
import cc.ioctl.util.LayoutHelper;
import com.bumptech.glide.Glide;
import com.lxj.xpopup.XPopup;
import io.github.qauxv.R;
import java.io.File;
import java.util.HashSet;
import java.util.List;

public class RecentStickerImpl implements ICreator.IMainPanelItem {
    ViewGroup cacheView;
    Context mContext;
    LinearLayout panelContainer;
    HashSet<ViewInfo> cacheImageView = new HashSet<>();
    TextView tv_title;

    List<RecentStickerHelper.RecentItemInfo> items;

    boolean dontAutoClose;

    public RecentStickerImpl(Context mContext) {
        this.mContext = mContext;
        items = RecentStickerHelper.getAllRecentRecord();

        cacheView = (ViewGroup) View.inflate(mContext, R.layout.sticker_panel_plus_pack_item, null);
        tv_title = cacheView.findViewById(R.id.Sticker_Panel_Item_Name);
        panelContainer = cacheView.findViewById(R.id.Sticker_Item_Container);
        tv_title.setText("最近使用");

        View setButton = cacheView.findViewById(R.id.Sticker_Panel_Set_Item);
        setButton.setOnClickListener(v->{
            new XPopup.Builder(mContext)
                    .asConfirm("提示", "是否要清除最近发送列表", () -> {
                        RecentStickerHelper.cleanAllRecentRecord();
                        ICreator.dismissAll();
                    })
                    .show();
        });

        try {
            LinearLayout itemLine = null;
            for (int i = 0; i < items.size(); i++) {
                RecentStickerHelper.RecentItemInfo item = items.get(items.size() - i - 1);
                if (i % 5 == 0) {
                    itemLine = new LinearLayout(mContext);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.bottomMargin = (int) LayoutHelper.dip2px(mContext, 16);
                    panelContainer.addView(itemLine, params);
                }
                itemLine.addView(getItemContainer(mContext, LocalDataHelper.getLocalItemPath(item), i % 5, item));

            }
        } catch (Exception e) {

        }
    }

    @Override
    public View getView() {
        dontAutoClose = FunConf.getBoolean("global", "sticker_panel_set_dont_close_panel", false);
        onViewDestroy();
        cacheView.postDelayed(this::notifyViewUpdate0, 50);
        return cacheView;
    }

    private View getItemContainer(Context context, String coverView, int count, RecentStickerHelper.RecentItemInfo item) {
        int width_item = LayoutHelper.getScreenWidth(context) / 6;
        int item_distance = (LayoutHelper.getScreenWidth(context) - width_item * 5) / 4;

        ImageView img = new ImageView(context);
        ViewInfo info = new ViewInfo(img, coverView);
        cacheImageView.add(info);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width_item, width_item);
        if (count > 0) params.leftMargin = item_distance;
        img.setLayoutParams(params);

        img.setTag(coverView);
        img.setOnClickListener(v -> {
            MsgSender.send_pic_by_contact(SessionUtils.AIOParam2Contact(StickerPanelEntryHooker.AIOParam), coverView);
            RecentStickerHelper.addPicItemToRecentRecord(item);
            if (!dontAutoClose){
                ICreator.dismissAll();
            }

        });

        return img;

    }

    @Override
    public void onViewDestroy() {
        for (ViewInfo info : cacheImageView) {
            info.requestId++;
            info.status = 0;
            info.view.setImageBitmap(null);
            StickerPanelImageLoader.clear(info.view);
        }
    }

    @Override
    public long getID() {
        return 1234;
    }

    @Override
    public void notifyViewUpdate0() {
        for (ViewInfo info : cacheImageView) {
            if (LayoutHelper.isSmallWindowNeedPlay(info.view)) {
                if (info.status == 0) {
                    info.status = 1;
                    loadVisibleImage(info);
                }
            } else if (info.status != 0) {
                info.requestId++;
                info.status = 0;
                StickerPanelImageLoader.clear(info.view);
            }
        }
    }

    private void loadVisibleImage(ViewInfo info) {
        File thumbnail = new File(info.coverView + "_thumb");
        File source = thumbnail.isFile() ? thumbnail : new File(info.coverView);
        int size = info.view.getLayoutParams().width;
        int requestId = ++info.requestId;
        StickerPanelAsync.run(() -> StickerPanelImageLoader.prepare(source, size, size, true),
                prepared -> {
                    if (info.status == 1 && info.requestId == requestId) {
                        StickerPanelImageLoader.display(info.view, prepared, false);
                        info.status = 2;
                    } else {
                        prepared.discard();
                    }
                }, error -> {
                    XLog.e("RecentStickerImpl.load", error);
                    if (info.status == 1 && info.requestId == requestId) {
                        Glide.with(HostInfo.getApplication()).load(source).dontAnimate()
                                .into(info.view);
                        info.status = 2;
                    }
                });
    }

    private static final class ViewInfo {
        private final ImageView view;
        private final String coverView;
        private int requestId;
        private int status;

        private ViewInfo(ImageView view, String coverView) {
            this.view = view;
            this.coverView = coverView;
        }
    }
}
