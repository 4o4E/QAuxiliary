package cc.hicore.hook.stickerPanel.Provider;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import cc.hicore.hook.stickerPanel.ICreator;
import cc.ioctl.util.LayoutHelper;
import io.github.qauxv.R;

/**
 * Provider 元数据不可用时保留的可重试面板项。
 */
public final class ProviderErrorItem implements ICreator.IMainPanelItem {

    private final String providerId;
    private final View root;

    public ProviderErrorItem(Context context, String providerId, String message, Runnable retry) {
        this.providerId = providerId;
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(LayoutHelper.dip2px(context, 16), LayoutHelper.dip2px(context, 40),
                LayoutHelper.dip2px(context, 16), LayoutHelper.dip2px(context, 24));
        TextView text = new TextView(context);
        text.setText(message);
        text.setTextColor(context.getColor(R.color.global_font_color));
        panel.addView(text);
        Button button = new Button(context);
        button.setText("重试");
        button.setOnClickListener(view -> retry.run());
        panel.addView(button);
        root = panel;
    }

    @Override
    public View getView() {
        return root;
    }

    @Override
    public void onViewDestroy() {
    }

    @Override
    public long getID() {
        return ((long) providerId.hashCode() << 32) ^ 0x4552524fL;
    }

    @Override
    public void notifyViewUpdate0() {
    }
}
