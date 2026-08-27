package cc.hicore.hook.stickerPanel;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import cc.hicore.Env;
import cc.hicore.Utils.Async;
import cc.hicore.Utils.FunConf;
import cc.hicore.hook.stickerPanel.MainItemImpl.InputFromLocalImpl;
import cc.hicore.hook.stickerPanel.MainItemImpl.LocalStickerImpl;
import cc.hicore.hook.stickerPanel.MainItemImpl.PanelSetImpl;
import cc.hicore.hook.stickerPanel.MainItemImpl.RecentStickerImpl;
import cc.hicore.hook.stickerPanel.Provider.ProviderCacheManager;
import cc.hicore.hook.stickerPanel.Provider.ProviderErrorItem;
import cc.hicore.hook.stickerPanel.Provider.ProviderStickerImpl;
import cc.ioctl.util.HostInfo;
import cc.ioctl.util.LayoutHelper;
import com.bumptech.glide.Glide;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;
import io.github.qauxv.R;
import io.github.qauxv.chainloader.api.emoticon.EmoticonPackInfo;
import io.github.qauxv.chainloader.api.emoticon.EmoticonProviderException;
import io.github.qauxv.chainloader.api.emoticon.EmoticonProviderRegistry;
import io.github.qauxv.chainloader.api.emoticon.IEmoticonProvider;
import io.github.qauxv.ui.CommonContextWrapper;
import io.github.qauxv.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressLint("ResourceType")
public class ICreator extends BottomPopupView{
    private static BasePopupView popupView;
    LinearLayout topSelectBar;
    private HorizontalScrollView topBarContainer;
    private ScrollView itemContainer;
    private IMainPanelItem currentTab;

    ArrayList<ViewGroup> mItems = new ArrayList<>();
    private final List<ProviderCover> providerCovers = new ArrayList<>();
    private final List<LocalCover> localCovers = new ArrayList<>();
    private boolean dismissed;

    private boolean open_last_select;
    private static long savedSelectID;
    private static long lastSelectTime;
    private static int savedScrollTo;

    private ViewGroup recentUse;

    public ICreator(@NonNull Context context) {
        super(context);
    }

    public static void createPanel(Context context) {
        Context fixContext = CommonContextWrapper.createAppCompatContext(context);
        XPopup.Builder NewPop = new XPopup.Builder(fixContext).moveUpToKeyboard(false).isDestroyOnDismiss(true);
        popupView = NewPop.asCustom(new ICreator(fixContext));
        popupView.show();
    }

    public static void dismissAll() {
        if (popupView != null) {
            popupView.dismiss();
        }
    }

    private void initTopSelectBar() {
        topSelectBar = findViewById(R.id.Sticker_Pack_Select_Bar);
        topBarContainer = findViewById(R.id.Sticker_Pack_Select_Bar_Container);
        topBarContainer.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) ->
                updateVisibleCovers());
    }

    long scrollTime = 0;
    private void initListView() {
        itemContainer = findViewById(R.id.sticker_panel_pack_container);

        itemContainer.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (System.currentTimeMillis() - scrollTime > 20){
                currentTab.notifyViewUpdate0();
                scrollTime = System.currentTimeMillis();
            }
        });
    }

    private void initStickerPacks() {
        List<LocalDataHelper.LocalPath> paths = LocalDataHelper.readPaths();
        for (LocalDataHelper.LocalPath path : paths) {
            IMainPanelItem newItem = new LocalStickerImpl(path, getContext());
            AtomicReference<ViewGroup> sItemView = new AtomicReference<>();
            ViewGroup sticker_pack_item = (ViewGroup) createPicImage(path.coverName, path.Name, v -> {
                itemContainer.scrollTo(0, 0);
                switchToItem(sItemView.get());
            }, path);
            sticker_pack_item.setTag(newItem);
            topSelectBar.addView(sticker_pack_item);
            sItemView.set(sticker_pack_item);
        }
    }

    private void initProviderPacks(int insertionIndex) {
        List<IEmoticonProvider> providers = EmoticonProviderRegistry.getProviders();
        if (providers.isEmpty()) {
            return;
        }
        StickerPanelAsync.run(() -> loadProviderTabs(providers), tabs -> {
                    if (dismissed) {
                        return;
                    }
                    int index = insertionIndex;
                    for (ProviderTab tab : tabs) {
                        ViewGroup view = createProviderTab(tab);
                        topSelectBar.addView(view, Math.min(index, topSelectBar.getChildCount()));
                        index++;
                    }
                    restoreSelectedItem();
                    topBarContainer.post(this::updateVisibleCovers);
                }, Log::e);
    }

    private List<ProviderTab> loadProviderTabs(List<IEmoticonProvider> providers) {
        List<ProviderTab> tabs = new ArrayList<>();
        for (IEmoticonProvider provider : providers) {
            try {
                List<EmoticonPackInfo> packs = new ArrayList<>(provider.listPacks());
                packs.sort(Comparator.comparingLong(EmoticonPackInfo::getOrder));
                for (EmoticonPackInfo pack : packs) {
                    tabs.add(ProviderTab.pack(provider, pack));
                }
                if (packs.isEmpty()) {
                    tabs.add(ProviderTab.error(provider, "这个 Provider 暂时没有表情包"));
                }
            } catch (Throwable error) {
                Log.e(error);
                String message = error instanceof EmoticonProviderException && error.getMessage() != null
                        ? error.getMessage() : "外部表情 Provider 读取失败";
                tabs.add(ProviderTab.error(provider, message));
            }
        }
        return tabs;
    }

    private ViewGroup createProviderTab(ProviderTab tab) {
        AtomicReference<ViewGroup> tabView = new AtomicReference<>();
        IMainPanelItem item;
        String title;
        if (tab.pack != null) {
            item = new ProviderStickerImpl(getContext(), tab.provider, tab.pack);
            title = tab.pack.getDisplayName();
        } else {
            item = new ProviderErrorItem(getContext(), tab.provider.getProviderId(), tab.errorMessage,
                    this::reopenPanel);
            title = tab.provider.getDisplayName();
        }
        ViewGroup view = (ViewGroup) createPicImage(R.drawable.sticker_panel_recent_icon, title,
                clicked -> {
                    itemContainer.scrollTo(0, 0);
                    switchToItem(tabView.get());
                });
        tabView.set(view);
        view.setTag(item);
        if (tab.pack != null && tab.pack.getCoverItemId() != null) {
            ImageView image = (ImageView) view.getChildAt(0);
            loadProviderCover(tab.provider, tab.pack, image);
        }
        return view;
    }

    private void loadProviderCover(IEmoticonProvider provider, EmoticonPackInfo pack, ImageView image) {
        String coverId = pack.getCoverItemId();
        if (coverId == null) {
            return;
        }
        providerCovers.add(new ProviderCover(provider, pack, coverId, image));
    }

    private void updateVisibleCovers() {
        if (dismissed) {
            return;
        }
        for (ProviderCover cover : providerCovers) {
            if (isCoverVisible(cover.image)) {
                if (cover.status == 0) {
                    cover.status = 1;
                    int size = LayoutHelper.dip2px(getContext(), 30);
                    StickerPanelAsync.run(
                            () -> StickerPanelImageLoader.prepare(ProviderCacheManager.getOrCreate(
                                            cover.provider, cover.pack.getId(), cover.coverId,
                                            cover.coverId + ".bin"),
                                    size, size, true),
                            prepared -> {
                                if (!dismissed && isCoverVisible(cover.image)) {
                                    StickerPanelImageLoader.display(cover.image, prepared, false);
                                    cover.status = 2;
                                } else {
                                    prepared.discard();
                                    cover.status = 0;
                                }
                            },
                            error -> {
                                cover.status = 0;
                                Log.e(error);
                            });
                }
            } else if (cover.status == 2) {
                StickerPanelImageLoader.clear(cover.image);
                cover.status = 0;
            }
        }
        for (LocalCover cover : localCovers) {
            if (isCoverVisible(cover.image)) {
                if (cover.status == 0) {
                    cover.status = 1;
                    int size = LayoutHelper.dip2px(getContext(), 30);
                    StickerPanelAsync.run(
                            () -> StickerPanelImageLoader.prepare(
                                    cover.source, size, size, true),
                            prepared -> {
                                if (!dismissed && isCoverVisible(cover.image)) {
                                    StickerPanelImageLoader.display(cover.image, prepared, false);
                                    cover.status = 2;
                                } else {
                                    prepared.discard();
                                    cover.status = 0;
                                }
                            }, error -> {
                                cover.status = 0;
                                Log.e(error);
                                if (!dismissed && isCoverVisible(cover.image)) {
                                    Glide.with(HostInfo.getApplication()).load(cover.source)
                                            .dontAnimate().into(cover.image);
                                    cover.status = 2;
                                }
                            });
                }
            } else if (cover.status == 2) {
                StickerPanelImageLoader.clear(cover.image);
                cover.status = 0;
            }
        }
    }

    private boolean isCoverVisible(ImageView image) {
        Rect imageRect = new Rect();
        Rect containerRect = new Rect();
        return image.isShown() && image.getGlobalVisibleRect(imageRect)
                && topBarContainer.getGlobalVisibleRect(containerRect)
                && Rect.intersects(imageRect, containerRect);
    }

    private void reopenPanel() {
        Context context = getContext();
        dismissAll();
        Async.runOnUi(() -> createPanel(context), 150);
    }

    private void initDefItemsBefore() {
        IMainPanelItem newItem = new RecentStickerImpl(getContext());
        AtomicReference<ViewGroup> sItemView = new AtomicReference<>();
        ViewGroup recentUse = (ViewGroup) createPicImage(R.drawable.sticker_panel_recent_icon, "最近使用", v -> switchToItem(sItemView.get()));
        sItemView.set(recentUse);
        recentUse.setTag(newItem);
        topSelectBar.addView(recentUse);
        recentUse.setTag(newItem);

        this.recentUse = recentUse;
    }
    private void switchToItem(ViewGroup item){
        for (ViewGroup i : mItems) {
            IMainPanelItem mItem = (IMainPanelItem) i.getTag();
            mItem.onViewDestroy();
            i.findViewById(887533).setVisibility(GONE);
        }

        currentTab = (IMainPanelItem) item.getTag();
        itemContainer.removeAllViews();
        itemContainer.addView(currentTab.getView());
        item.findViewById(887533).setVisibility(VISIBLE);


        Async.runOnUi(currentTab::notifyViewUpdate0);



    }

    private boolean restoreSelectedItem() {
        if (savedSelectID == 0) {
            return false;
        }
        for (ViewGroup item : mItems) {
            IMainPanelItem panelItem = (IMainPanelItem) item.getTag();
            if (panelItem.getID() == savedSelectID) {
                switchToItem(item);
                Async.runOnUi(panelItem::notifyViewUpdate0, 100);
                Async.runOnUi(() -> itemContainer.scrollTo(0, savedScrollTo));
                return true;
            }
        }
        return false;
    }

    private void initDefItemsLast() {
        IMainPanelItem inputPic = new InputFromLocalImpl(getContext());
        AtomicReference<ViewGroup> sticker_panel_input_view = new AtomicReference<>();
        ViewGroup inputView = (ViewGroup) createPicImage(R.drawable.sticker_panel_input_icon, "导入图片", v -> switchToItem(sticker_panel_input_view.get()));
        sticker_panel_input_view.set(inputView);
        inputView.setTag(inputPic);
        topSelectBar.addView(inputView);

        IMainPanelItem setItem = new PanelSetImpl(getContext());
        AtomicReference<ViewGroup> sticker_panel_set_view = new AtomicReference<>();
        ViewGroup setView = (ViewGroup) createPicImage(R.drawable.sticker_panen_set_button_icon, "设置", v -> switchToItem(sticker_panel_set_view.get()));
        sticker_panel_set_view.set(setView);
        setView.setTag(setItem);
        topSelectBar.addView(setView);
    }

    //创建贴纸包面板的滑动按钮
    private View createPicImage(String imgPath, String title, OnClickListener clickListener, LocalDataHelper.LocalPath path) {
        ImageView img = new ImageView(getContext());
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout panel = new LinearLayout(getContext());
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setOnClickListener(clickListener);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutHelper.dip2px(getContext(), 30), LayoutHelper.dip2px(getContext(), 30));
        params.topMargin = LayoutHelper.dip2px(getContext(), 3);
        params.bottomMargin = LayoutHelper.dip2px(getContext(), 3);
        params.leftMargin = LayoutHelper.dip2px(getContext(), 3);
        params.rightMargin = LayoutHelper.dip2px(getContext(), 3);
        img.setLayoutParams(params);
        panel.addView(img);

        TextView titleView = new TextView(getContext());
        titleView.setText(title);
        titleView.setTextColor(getResources().getColor(R.color.global_font_color, null));
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextSize(10);
        titleView.setSingleLine();
        panel.addView(titleView);


        File cover = new File(Env.app_save_path + "本地表情包/" + path.storePath + "/" + imgPath);
        localCovers.add(new LocalCover(cover, img));


        LinearLayout.LayoutParams panel_param = new LinearLayout.LayoutParams(LayoutHelper.dip2px(getContext(), 50), ViewGroup.LayoutParams.WRAP_CONTENT);
        panel_param.leftMargin = LayoutHelper.dip2px(getContext(), 5);
        panel_param.rightMargin = LayoutHelper.dip2px(getContext(), 5);
        panel.setLayoutParams(panel_param);

        View greenTip = new View(getContext());
        greenTip.setBackgroundColor(Color.GREEN);
        panel_param = new LinearLayout.LayoutParams(LayoutHelper.dip2px(getContext(), 30), 50);
        panel_param.leftMargin = LayoutHelper.dip2px(getContext(), 5);
        panel_param.rightMargin = LayoutHelper.dip2px(getContext(), 5);
        greenTip.setLayoutParams(panel_param);
        greenTip.setId(887533);
        greenTip.setVisibility(GONE);
        panel.addView(greenTip);

        mItems.add(panel);
        return panel;
    }

    @SuppressLint("ResourceType")
    private View createPicImage(int resID, String title, OnClickListener clickListener) {
        ImageView img = new ImageView(getContext());
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout panel = new LinearLayout(getContext());
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setOnClickListener(clickListener);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutHelper.dip2px(getContext(), 30), LayoutHelper.dip2px(getContext(), 30));
        params.topMargin = LayoutHelper.dip2px(getContext(), 3);
        params.bottomMargin = LayoutHelper.dip2px(getContext(), 3);
        params.leftMargin = LayoutHelper.dip2px(getContext(), 3);
        params.rightMargin = LayoutHelper.dip2px(getContext(), 3);
        img.setLayoutParams(params);
        panel.addView(img);

        TextView titleView = new TextView(getContext());
        titleView.setText(title);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleView.setTextColor(getContext().getColor(R.color.global_font_color));
        titleView.setTextSize(10);
        titleView.setSingleLine();
        panel.addView(titleView);

        Glide.with(HostInfo.getApplication()).load(resID).into(img);

        LinearLayout.LayoutParams panel_param = new LinearLayout.LayoutParams(LayoutHelper.dip2px(getContext(), 50), ViewGroup.LayoutParams.WRAP_CONTENT);
        panel_param.leftMargin = LayoutHelper.dip2px(getContext(), 5);
        panel_param.rightMargin = LayoutHelper.dip2px(getContext(), 5);
        panel.setLayoutParams(panel_param);

        View greenTip = new View(getContext());
        greenTip.setBackgroundColor(Color.GREEN);
        panel_param = new LinearLayout.LayoutParams(LayoutHelper.dip2px(getContext(), 30), 50);
        panel_param.leftMargin = LayoutHelper.dip2px(getContext(), 5);
        panel_param.rightMargin = LayoutHelper.dip2px(getContext(), 5);
        greenTip.setLayoutParams(panel_param);
        greenTip.setId(887533);
        greenTip.setVisibility(GONE);
        panel.addView(greenTip);

        mItems.add(panel);
        return panel;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dismissed) {
                return;
            }

            initTopSelectBar();


            initListView();


            initDefItemsBefore();


            initStickerPacks();
            topBarContainer.post(this::updateVisibleCovers);

            int providerInsertionIndex = topSelectBar.getChildCount();
            initDefItemsLast();

            open_last_select = FunConf.getBoolean("global", "sticker_panel_set_open_last_select", false);
            if (open_last_select) savedSelectID = Long.parseLong(FunConf.getString("global", "sticker_panel_set_last_select", String.valueOf(savedSelectID)));
            if (!restoreSelectedItem()) {
                switchToItem(recentUse);
            }
            initProviderPacks(providerInsertionIndex);

        }, 50);


    }

    @Override
    protected int getMaxHeight() {
        return (int) (XPopupUtils.getScreenHeight(getContext()) * .7f);
    }

    @Override
    protected int getPopupHeight() {
        return (int) (XPopupUtils.getScreenHeight(getContext()) * .7f);
    }

    @Override
    protected void beforeDismiss() {
        if (currentTab != null){
            savedScrollTo = itemContainer.getScrollY();
            savedSelectID = currentTab.getID();
            if (open_last_select) FunConf.setString("global", "sticker_panel_set_last_select",String.valueOf(savedSelectID));
            lastSelectTime = System.currentTimeMillis();
        }
        super.beforeDismiss();
    }

    @Override
    protected void onDismiss() {
        dismissed = true;
        super.onDismiss();
        for (ViewGroup item : mItems){
            IMainPanelItem iMainPanelItem = (IMainPanelItem) item.getTag();
            iMainPanelItem.onViewDestroy();
        }
        for (ProviderCover cover : providerCovers) {
            StickerPanelImageLoader.clear(cover.image);
            cover.status = 0;
        }
        for (LocalCover cover : localCovers) {
            StickerPanelImageLoader.clear(cover.image);
            cover.status = 0;
        }

        Glide.get(HostInfo.getApplication()).clearMemory();
    }

    @Override
    protected int getImplLayoutId() {
        return io.github.qauxv.R.layout.sticker_panel_plus_main;
    }

    public interface IMainPanelItem {
        View getView();

        void onViewDestroy();

        long getID();

        void notifyViewUpdate0();
    }

    private static final class ProviderTab {
        private final IEmoticonProvider provider;
        private final EmoticonPackInfo pack;
        private final String errorMessage;

        private ProviderTab(IEmoticonProvider provider, EmoticonPackInfo pack, String errorMessage) {
            this.provider = provider;
            this.pack = pack;
            this.errorMessage = errorMessage;
        }

        private static ProviderTab pack(IEmoticonProvider provider, EmoticonPackInfo pack) {
            return new ProviderTab(provider, pack, null);
        }

        private static ProviderTab error(IEmoticonProvider provider, String message) {
            return new ProviderTab(provider, null, message);
        }
    }

    private static final class ProviderCover {
        private final IEmoticonProvider provider;
        private final EmoticonPackInfo pack;
        private final String coverId;
        private final ImageView image;
        private int status;

        private ProviderCover(IEmoticonProvider provider, EmoticonPackInfo pack,
                String coverId, ImageView image) {
            this.provider = provider;
            this.pack = pack;
            this.coverId = coverId;
            this.image = image;
        }
    }

    private static final class LocalCover {
        private final File source;
        private final ImageView image;
        private int status;

        private LocalCover(File source, ImageView image) {
            this.source = source;
            this.image = image;
        }
    }
}
