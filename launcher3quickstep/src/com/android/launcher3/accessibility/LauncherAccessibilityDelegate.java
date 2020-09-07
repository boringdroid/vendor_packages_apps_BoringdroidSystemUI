package com.android.launcher3.accessibility;

import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.launcher3.ButtonDropTarget;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragController.DragListener;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.util.Thunk;

public class LauncherAccessibilityDelegate extends AccessibilityDelegate implements DragListener {

    public static final int REMOVE = R.id.action_remove;
    public static final int UNINSTALL = R.id.action_uninstall;
    public static final int RECONFIGURE = R.id.action_reconfigure;
    protected static final int MOVE = R.id.action_move;
    protected static final int RESIZE = R.id.action_resize;

    public enum DragType {
        ICON,
    }

    public static class DragInfo {
        public DragType dragType;
        public ItemInfo info;
        public View item;
    }

    protected final SparseArray<AccessibilityAction> mActions = new SparseArray<>();
    @Thunk final Launcher mLauncher;

    private DragInfo mDragInfo = null;

    public LauncherAccessibilityDelegate(Launcher launcher) {
        mLauncher = launcher;

        mActions.put(REMOVE, new AccessibilityAction(REMOVE,
                launcher.getText(R.string.remove_drop_target_label)));
        mActions.put(UNINSTALL, new AccessibilityAction(UNINSTALL,
                launcher.getText(R.string.uninstall_drop_target_label)));
        mActions.put(RECONFIGURE, new AccessibilityAction(RECONFIGURE,
                launcher.getText(R.string.gadget_setup_text)));
        mActions.put(MOVE, new AccessibilityAction(MOVE,
                launcher.getText(R.string.action_move)));
        mActions.put(RESIZE, new AccessibilityAction(RESIZE,
                        launcher.getText(R.string.action_resize)));
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        addSupportedActions(host, info, false);
    }

    public void addSupportedActions(View host, AccessibilityNodeInfo info, boolean fromKeyboard) {
        if (!(host.getTag() instanceof ItemInfo)) return;
        ItemInfo item = (ItemInfo) host.getTag();

        for (ButtonDropTarget target : mLauncher.getDropTargetBar().getDropTargets()) {
            if (target.supportsAccessibilityDrop(item, host)) {
                info.addAction(mActions.get(target.getAccessibilityAction()));
            }
        }

        // Do not add move actions for keyboard request as this uses virtual nodes.
        if (!fromKeyboard && itemSupportsAccessibleDrag(item)) {
            info.addAction(mActions.get(MOVE));
        }
    }

    private boolean itemSupportsAccessibleDrag(ItemInfo item) {
        return false;
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        if ((host.getTag() instanceof ItemInfo)
                && performAction(host, (ItemInfo) host.getTag(), action)) {
            return true;
        }
        return super.performAccessibilityAction(host, action, args);
    }

    public boolean performAction(final View host, final ItemInfo item, int action) {
        if (action == MOVE) {
            beginAccessibleDrag(host, item);
        } else if (action == RESIZE) {
            return true;
        } else {
            for (ButtonDropTarget dropTarget : mLauncher.getDropTargetBar().getDropTargets()) {
                if (dropTarget.supportsAccessibilityDrop(item, host) &&
                        action == dropTarget.getAccessibilityAction()) {
                    dropTarget.onAccessibilityDrop(host, item);
                    return true;
                }
            }
        }
        return false;
    }

    @Thunk void announceConfirmation(String confirmation) {
        mLauncher.getDragLayer().announceForAccessibility(confirmation);

    }

    public boolean isInAccessibleDrag() {
        return mDragInfo != null;
    }

    public DragInfo getDragInfo() {
        return mDragInfo;
    }

    /**
     * @param clickedTarget the actual view that was clicked
     * @param dropLocation relative to {@param clickedTarget}. If provided, its center is used
     * as the actual drop location otherwise the views center is used.
     */
    public void handleAccessibleDrop(View clickedTarget, Rect dropLocation,
            String confirmation) {
        if (!isInAccessibleDrag()) return;

        int[] loc = new int[2];
        if (dropLocation == null) {
            loc[0] = clickedTarget.getWidth() / 2;
            loc[1] = clickedTarget.getHeight() / 2;
        } else {
            loc[0] = dropLocation.centerX();
            loc[1] = dropLocation.centerY();
        }

        mLauncher.getDragLayer().getDescendantCoordRelativeToSelf(clickedTarget, loc);
        mLauncher.getDragController().completeAccessibleDrag(loc);

        if (!TextUtils.isEmpty(confirmation)) {
            announceConfirmation(confirmation);
        }
    }

    public void beginAccessibleDrag(View item, ItemInfo info) {
        mDragInfo = new DragInfo();
        mDragInfo.info = info;
        mDragInfo.item = item;
        mDragInfo.dragType = DragType.ICON;

        Rect pos = new Rect();
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(item, pos);
        mLauncher.getDragController().prepareAccessibleDrag(pos.centerX(), pos.centerY());
        mLauncher.getDragController().addDragListener(this);

        DragOptions options = new DragOptions();
        options.isAccessibleDrag = true;
    }

    @Override
    public void onDragStart(DragObject dragObject, DragOptions options) {
        // No-op
    }

    @Override
    public void onDragEnd() {
        mLauncher.getDragController().removeDragListener(this);
        mDragInfo = null;
    }
}
