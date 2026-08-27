package cc.hicore.hook.stickerPanel.MainItemImpl;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cc.hicore.Utils.Async;
import cc.hicore.Utils.FunConf;
import cc.hicore.Utils.ImageUtils;
import cc.hicore.Utils.XLog;
import cc.hicore.hook.stickerPanel.Hooker.StickerPanelEntryHooker;
import cc.hicore.hook.stickerPanel.ICreator;
import cc.hicore.hook.stickerPanel.LocalDataHelper;
import cc.hicore.hook.stickerPanel.RecentStickerHelper;
import cc.hicore.hook.stickerPanel.StickerPanelAsync;
import cc.hicore.hook.stickerPanel.StickerPanelImageLoader;
import cc.hicore.message.chat.SessionUtils;
import cc.hicore.message.common.MsgSender;
import cc.hicore.ui.SimpleDragSortView;
import cc.ioctl.util.HostInfo;
import cc.ioctl.util.LayoutHelper;
import com.bumptech.glide.Glide;
import com.lxj.xpopup.XPopup;
import io.github.qauxv.R;
import io.github.qauxv.util.Toasts;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class LocalStickerImpl implements ICreator.IMainPanelItem {
    ViewGroup cacheView;
    Context mContext;
    LinearLayout panelContainer;
    HashSet<ViewInfo> cacheImageView = new HashSet<>();
    TextView tv_title;
    LocalDataHelper.LocalPath mPackInfo;
    List<LocalDataHelper.LocalPicItems> mPicItems;

    View setButton;

    int showControlType;
    boolean dontAutoClose;
    boolean isCreated;

    public LocalStickerImpl(LocalDataHelper.LocalPath pathInfo, Context mContext) {
        mPackInfo = pathInfo;
        this.mContext = mContext;


        cacheView = (ViewGroup) View.inflate(mContext, R.layout.sticker_panel_plus_pack_item, null);
        tv_title = cacheView.findViewById(R.id.Sticker_Panel_Item_Name);
        panelContainer = cacheView.findViewById(R.id.Sticker_Item_Container);
        tv_title.setText(mPackInfo.Name);

        setButton = cacheView.findViewById(R.id.Sticker_Panel_Set_Item);
        setButton.setOnClickListener(v -> onSetButtonClick());
    }
    private void createMainView(){
        try {
            mPicItems = LocalDataHelper.getPicItems(mPackInfo.storePath);
            LinearLayout itemLine = null;
            for (int i = 0; i < mPicItems.size(); i++) {
                LocalDataHelper.LocalPicItems item = mPicItems.get(i);
                if (i % 5 == 0) {
                    itemLine = new LinearLayout(mContext);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.bottomMargin = LayoutHelper.dip2px(mContext, 16);
                    panelContainer.addView(itemLine, params);
                }
                itemLine.addView(getItemContainer(mContext, LocalDataHelper.getLocalItemPath(mPackInfo, item), i % 5, item));
            }
            isCreated = true;
        } catch (Exception e) {
            XLog.e("LocalStickerImpl", e);
        }
    }

    private void onSetButtonClick() {
        new AlertDialog.Builder(mContext)
                .setTitle("选择你的操作").setItems(new String[]{
                        "删除该表情包","修改表情包名字","排序表情包"
                }, (dialog, which) -> {
                    if (which == 0) {
                        new AlertDialog.Builder(mContext)
                                .setTitle("提示")
                                .setMessage("是否删除该表情包(" + tv_title.getText() + "),该表情包内的本地表情将被删除并不可恢复")
                                .setNeutralButton("确定删除", (dialog1, which1) -> {
                                    LocalDataHelper.deletePath(mPackInfo);
                                    ICreator.dismissAll();
                                })
                                .setNegativeButton("取消", (dialog12, which12) -> {

                                }).show();
                    }else if (which == 1){
                        EditText editText = new EditText(mContext);
                        editText.setHint("输入表情包的名字");
                        editText.setText(mPackInfo.Name);
                        new AlertDialog.Builder(mContext)
                                .setTitle("修改表情包名字")
                                .setView(editText)
                                .setNegativeButton("确定修改", (dialog1, which1) -> {
                                    String text = editText.getText().toString();
                                    if (text.isEmpty()){
                                        Toasts.show("输入的名字不能为空");
                                        return;
                                    }
                                    mPackInfo.Name = text;
                                    LocalDataHelper.setPathName(mPackInfo, text);
                                    ICreator.dismissAll();
                                })
                                .show();
                    }else if (which == 2){
                        Async.runAsyncLoading(mContext,"正在处理图片中...",()->{
                            ArrayList<String> fileList = new ArrayList<>();
                            int width_item = LayoutHelper.getScreenWidth(mContext) / 6;
                            for (LocalDataHelper.LocalPicItems item : mPicItems) {
                                fileList.add(ImageUtils.getResizePicPath(LocalDataHelper.getLocalItemPath(mPackInfo, item),width_item));
                            }
                            ArrayList<String> sourceInfo = new ArrayList<>();
                            for (LocalDataHelper.LocalPicItems item : mPicItems) {
                                sourceInfo.add(item.MD5);
                            }
                            Async.runOnUi(()-> SimpleDragSortView.createDrag(mContext,fileList,sourceInfo,()->{
                                for (LocalDataHelper.LocalPicItems item : mPicItems){
                                    LocalDataHelper.deletePicLog(mPackInfo,item);
                                }
                                for (int i = 0; i < sourceInfo.size(); i++) {
                                    for (LocalDataHelper.LocalPicItems item : mPicItems){
                                        if (item.MD5.equals(sourceInfo.get(i))){
                                            LocalDataHelper.addPicItem(mPackInfo.storePath,item);
                                        }
                                    }
                                }
                                ICreator.dismissAll();
                            }));


                        });

                    }
                }).show();
    }

    @Override
    public View getView() {
        if (!isCreated) {
            createMainView();
        }
        showControlType = FunConf.getInt("global", "sticker_panel_set_rb_show_anim", 1);
        dontAutoClose = FunConf.getBoolean("global", "sticker_panel_set_dont_close_panel", false);
        onViewDestroy();
        return cacheView;
    }

    private View getItemContainer(Context context, String coverView, int count, LocalDataHelper.LocalPicItems item) {
        int width_item = LayoutHelper.getScreenWidth(context) / 6;
        int item_distance = (LayoutHelper.getScreenWidth(context) - width_item * 5) / 4;

        ImageView img = new ImageView(context);
        ViewInfo info = new ViewInfo();
        info.view = img;
        info.status = 0;

        cacheImageView.add(info);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width_item, width_item);
        if (count > 0) params.leftMargin = item_distance;
        img.setLayoutParams(params);

        img.setTag(coverView);
        img.setOnClickListener(v -> {
            MsgSender.send_pic_by_contact(SessionUtils.AIOParam2Contact(StickerPanelEntryHooker.AIOParam),
                        LocalDataHelper.getLocalItemPath(mPackInfo, item));
            RecentStickerHelper.addPicItemToRecentRecord(mPackInfo, item);
            if (!dontAutoClose){
                ICreator.dismissAll();
            }

        });

        img.setOnLongClickListener(v -> {
            ImageView preView = new ImageView(context);
            preView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            preView.setLayoutParams(new ViewGroup.LayoutParams(LayoutHelper.getScreenWidth(HostInfo.getApplication()) / 2, LayoutHelper.getScreenWidth(HostInfo.getApplication()) / 2));
            File previewSource = new File(coverView);
            new AlertDialog.Builder(mContext)
                    .setTitle("选择你对该表情的操作")
                    .setView(preView)
                    .setOnDismissListener(dialog -> {
                        StickerPanelImageLoader.clear(preView);
                    }).setNegativeButton("删除该表情", (dialog, which) -> {
                        LocalDataHelper.deletePicItem(mPackInfo, item);

                        mPicItems.remove(item);

                        cacheImageView.clear();
                        panelContainer.removeAllViews();

                        onViewDestroy();
                        createMainView();
                        Async.runOnUi(this::notifyViewUpdate0);


                    }).setNeutralButton("设置为标题预览", (dialog, which) -> {
                        LocalDataHelper.setPathCover(mPackInfo, item);
                        ICreator.dismissAll();
                    }).show();
            int previewSize = LayoutHelper.getScreenWidth(HostInfo.getApplication()) / 2;
            StickerPanelAsync.run(() -> StickerPanelImageLoader.prepare(
                            previewSource, previewSize, previewSize, true),
                    prepared -> {
                        if (preView.isAttachedToWindow()) {
                            StickerPanelImageLoader.display(preView, prepared, false);
                        } else {
                            prepared.discard();
                        }
                    }, error -> {
                        XLog.e("LocalStickerImpl.preview", error);
                        if (preView.isAttachedToWindow()) {
                            Glide.with(HostInfo.getApplication()).load(previewSource).dontAnimate()
                                    .fitCenter().into(preView);
                        }
                    });
            return true;
        });

        return img;

    }

    @Override
    public void onViewDestroy() {
        for (ViewInfo img : cacheImageView) {
            img.view.setImageBitmap(null);
            img.status = 0;
            StickerPanelImageLoader.clear(img.view);
        }

    }

    @Override
    public long getID() {
        return mPackInfo.storePath.hashCode();
    }

    @Override
    public void notifyViewUpdate0() {
        for (ViewInfo v : cacheImageView) {
            XLog.d("NotifyUpdate","update->"+LayoutHelper.isSmallWindowNeedPlay(v.view));
            if (LayoutHelper.isSmallWindowNeedPlay(v.view)) {
                if (v.status == 0) {
                    v.status = 1;

                    String coverView = (String) v.view.getTag();
                    File thumbnail = new File(coverView + "_thumb");
                    File source = thumbnail.isFile() ? thumbnail : new File(coverView);
                    boolean animate = showControlType == 0
                            || (showControlType == 1 && source.length() <= 2 * 1024 * 1024);
                    loadPreview(v, source, animate);
                }

            } else {
                if (v.status != 0) {
                    StickerPanelImageLoader.clear(v.view);
                    v.status = 0;
                }
            }
        }
    }

    private void loadPreview(ViewInfo info, File source, boolean animate) {
        int size = info.view.getLayoutParams().width;
        StickerPanelAsync.run(() -> StickerPanelImageLoader.prepare(
                source, size, size, animate), prepared -> {
            if (info.status == 1) {
                StickerPanelImageLoader.display(info.view, prepared, true);
                info.status = 2;
            } else {
                prepared.discard();
            }
        }, error -> {
            XLog.e("LocalStickerImpl.loadPreview", error);
            if (info.status == 1) {
                Glide.with(HostInfo.getApplication()).load(source).skipMemoryCache(true)
                        .dontAnimate().fitCenter().into(info.view);
                info.status = 2;
            }
        });
    }

    public static class ViewInfo {
        ImageView view;
        volatile int status = 0;
    }


}
