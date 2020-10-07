package com.android.launcher3.popup;


import android.app.ActivityOptions;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.systemui.shared.recents.model.Task;

/**
 * Represents a system shortcut for a given app. The shortcut should have a label and icon, and an
 * onClickListener that depends on the item that the shortcut services.
 * <p>
 * Example system shortcuts, defined as inner classes, include Widgets and AppInfo.
 *
 * @param <T>
 */
public abstract class SystemShortcut<T extends BaseDraggingActivity> extends ItemInfo {
    private final int mIconResId;
    private final int mLabelResId;
    private final Icon mIcon;
    private final CharSequence mLabel;
    private final CharSequence mContentDescription;
    private final int mAccessibilityActionId;

    public SystemShortcut(int iconResId, int labelResId) {
        mIconResId = iconResId;
        mLabelResId = labelResId;
        mAccessibilityActionId = labelResId;
        mIcon = null;
        mLabel = null;
        mContentDescription = null;
    }

    public SystemShortcut(SystemShortcut other) {
        mIconResId = other.mIconResId;
        mLabelResId = other.mLabelResId;
        mIcon = other.mIcon;
        mLabel = other.mLabel;
        mContentDescription = other.mContentDescription;
        mAccessibilityActionId = other.mAccessibilityActionId;
    }

    public void setIcon(View iconView) {
        if (mIcon != null) {
            mIcon.loadDrawableAsync(iconView.getContext(),
                    iconView::setBackground,
                    new Handler(Looper.getMainLooper()));
        } else {
            iconView.setBackgroundResource(mIconResId);
        }
    }

    public abstract View.OnClickListener getOnClickListener(T activity, Task task);

    public static class AppInfo extends SystemShortcut {
        public AppInfo() {
            super(R.drawable.ic_info_no_shadow, R.string.app_info_drop_target_label);
        }

        @Override
        public View.OnClickListener getOnClickListener(BaseDraggingActivity activity, Task task) {
            return (view) -> {
                dismissTaskMenuView(activity);
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                Rect sourceBounds = new Rect(
                        location[0],
                        location[1],
                        location[0] + view.getMeasuredWidth(),
                        location[1] + view.getMeasuredHeight()
                );
                new PackageManagerHelper(activity).startDetailsActivityForInfo(
                        task,
                        sourceBounds,
                        ActivityOptions.makeBasic().toBundle()
                );
                activity.finish();
            };
        }
    }

    protected static void dismissTaskMenuView(BaseDraggingActivity activity) {
        AbstractFloatingView.closeOpenViews(activity, true);
    }
}