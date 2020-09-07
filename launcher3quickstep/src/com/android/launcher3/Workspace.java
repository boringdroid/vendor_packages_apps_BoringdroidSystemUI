/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_TRANSITION_MS;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.SPRING_LOADED;
import static com.android.launcher3.config.FeatureFlags.ADAPTIVE_ICON_WINDOW_ANIM;
import static com.android.launcher3.dragndrop.DragLayer.ALPHA_INDEX_OVERLAY;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.android.launcher3.Launcher.LauncherOverlay;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.accessibility.AccessibleDragListenerAdapter;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.SpringLoadedDragController;
import com.android.launcher3.graphics.DragPreviewProvider;
import com.android.launcher3.graphics.PreloadIconDrawable;
import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.pageindicators.WorkspacePageIndicator;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.touch.WorkspaceTouchListener;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.WallpaperOffsetInterpolator;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * The workspace is a wide area with a wallpaper and a finite number of pages.
 * Each page contains a number of icons, folders or widgets the user can
 * interact with. A workspace is meant to be used with a fixed width only.
 */
public class Workspace extends PagedView<WorkspacePageIndicator>
        implements DropTarget, DragSource, View.OnTouchListener,
        DragController.DragListener, Insettable, LauncherStateManager.StateHandler,
        WorkspaceLayoutManager {

    /** The value that {@link #mTransitionProgress} must be greater than for
     * {@link #transitionStateShouldAllowDrop()} to return true. */
    private static final float ALLOW_DROP_TRANSITION_PROGRESS = 0.25f;

    /** The value that {@link #mTransitionProgress} must be greater than for
     * {@link #isFinishedSwitchingState()} ()} to return true. */
    private static final float FINISHED_SWITCHING_STATE_TRANSITION_PROGRESS = 0.5f;

    private static final boolean ENFORCE_DRAG_EVENT_ORDER = false;

    private static final int SNAP_OFF_EMPTY_SCREEN_DURATION = 400;
    private static final int FADE_EMPTY_SCREEN_DURATION = 150;

    private static final int ADJACENT_SCREEN_DROP_DURATION = 300;

    private static final int DEFAULT_PAGE = 0;

    private LayoutTransition mLayoutTransition;
    @Thunk final WallpaperManager mWallpaperManager;

    @Thunk final IntSparseArrayMap<CellLayout> mWorkspaceScreens = new IntSparseArrayMap<>();
    @Thunk final IntArray mScreenOrder = new IntArray();

    @Thunk Runnable mRemoveEmptyScreenRunnable;
    @Thunk boolean mDeferRemoveExtraEmptyScreen = false;

    /**
     * CellInfo for the cell that is currently being dragged
     */
    private CellLayout.CellInfo mDragInfo;

    /**
     * Target drop area calculated during last acceptDrop call.
     */
    @Thunk int[] mTargetCell = new int[2];
    private int mDragOverX = -1;
    private int mDragOverY = -1;

    /**
     * The CellLayout that is currently being dragged over
     */
    @Thunk CellLayout mDragTargetLayout = null;
    /**
     * The CellLayout that we will show as highlighted
     */
    private CellLayout mDragOverlappingLayout = null;

    /**
     * The CellLayout which will be dropped to
     */
    private CellLayout mDropToLayout = null;

    @Thunk final Launcher mLauncher;
    @Thunk DragController mDragController;

    private final Rect mTempRect = new Rect();
    private final int[] mTempXY = new int[2];
    @Thunk float[] mDragViewVisualCenter = new float[2];
    private final float[] mTempTouchCoordinates = new float[2];

    private SpringLoadedDragController mSpringLoadedDragController;

    private boolean mIsSwitchingState = false;

    boolean mChildrenLayersEnabled = true;

    private boolean mStripScreensOnPageStopMoving = false;

    private DragPreviewProvider mOutlineProvider = null;
    private boolean mWorkspaceFadeInAdjacentScreens;

    final WallpaperOffsetInterpolator mWallpaperOffset;
    private boolean mUnlockWallpaperFromDefaultPageOnLayout;

    public static final int REORDER_TIMEOUT = 650;
    private final Alarm mFolderCreationAlarm = new Alarm();
    private final Alarm mReorderAlarm = new Alarm();

    // Variables relating to touch disambiguation (scrolling workspace vs. scrolling a widget)
    private float mXDown;
    private float mYDown;
    final static float START_DAMPING_TOUCH_SLOP_ANGLE = (float) Math.PI / 6;
    final static float MAX_SWIPE_ANGLE = (float) Math.PI / 3;
    final static float TOUCH_SLOP_DAMPING_FACTOR = 4;

    // Related to dragging, folder creation and reordering
    private static final int DRAG_MODE_NONE = 0;
    private static final int DRAG_MODE_REORDER = 3;
    private int mDragMode = DRAG_MODE_NONE;
    @Thunk int mLastReorderX = -1;
    @Thunk int mLastReorderY = -1;

    private SparseArray<Parcelable> mSavedStates;
    private final IntArray mRestoredPages = new IntArray();

    private float mCurrentScale;
    private float mTransitionProgress;

    // State related to Launcher Overlay
    LauncherOverlay mLauncherOverlay;
    boolean mScrollInteractionBegan;
    boolean mStartedSendingScrollEvents;
    float mLastOverlayScroll = 0;
    boolean mOverlayShown = false;
    private Runnable mOnOverlayHiddenCallback;

    // Total over scrollX in the overlay direction.
    private float mOverlayTranslation;

    // Handles workspace state transitions
    private final WorkspaceStateTransitionAnimation mStateTransitionAnimation;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public Workspace(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     * @param defStyle Unused.
     */
    public Workspace(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLauncher = Launcher.getLauncher(context);
        mStateTransitionAnimation = new WorkspaceStateTransitionAnimation(mLauncher, this);
        mWallpaperManager = WallpaperManager.getInstance(context);

        mWallpaperOffset = new WallpaperOffsetInterpolator(this);

        setHapticFeedbackEnabled(false);
        initWorkspace();

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(true);
        setOnTouchListener(new WorkspaceTouchListener(mLauncher, this));
    }

    @Override
    public void setInsets(Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        DeviceProfile stableGrid = mLauncher.getWallpaperDeviceProfile();

        mWorkspaceFadeInAdjacentScreens = grid.shouldFadeAdjacentWorkspaceScreens();

        Rect padding = stableGrid.workspacePadding;

        RotationMode rotationMode = mLauncher.getRotationMode();

        rotationMode.mapRect(padding, mTempRect);
        setPadding(mTempRect.left, mTempRect.top, mTempRect.right, mTempRect.bottom);
        rotationMode.mapRect(stableGrid.getInsets(), mInsets);

        if (mWorkspaceFadeInAdjacentScreens) {
            // In landscape mode the page spacing is set to the default.
            setPageSpacing(grid.edgeMarginPx);
        } else {
            // In portrait, we want the pages spaced such that there is no
            // overhang of the previous / next page into the current page viewport.
            // We assume symmetrical padding in portrait mode.
            setPageSpacing(Math.max(grid.edgeMarginPx, padding.left + 1));
        }


        int paddingLeftRight = stableGrid.cellLayoutPaddingLeftRightPx;
        int paddingBottom = stableGrid.cellLayoutBottomPaddingPx;
        for (int i = mWorkspaceScreens.size() - 1; i >= 0; i--) {
            CellLayout page = mWorkspaceScreens.valueAt(i);
            page.setRotationMode(rotationMode);
            page.setPadding(paddingLeftRight, 0, paddingLeftRight, paddingBottom);
        }
    }

    public float getWallpaperOffsetForCenterPage() {
        int pageScroll = getScrollForPage(getPageNearestToCenterOfScreen());
        return mWallpaperOffset.wallpaperOffsetForScroll(pageScroll);
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragStart", 0, 0);
        }

        if (mDragInfo != null && mDragInfo.cell != null) {
            CellLayout layout = (CellLayout) mDragInfo.cell.getParent().getParent();
            layout.markCellsAsUnoccupiedForView(mDragInfo.cell);
        }

        if (mOutlineProvider != null) {
            if (dragObject.dragView != null) {
                Bitmap preview = dragObject.dragView.getPreviewBitmap();

                // The outline is used to visualize where the item will land if dropped
                mOutlineProvider.generateDragOutline(preview);
            }
        }

        updateChildrenLayersEnabled();

        // Do not add a new page if it is a accessible drag which was not started by the workspace.
        // We do not support accessibility drag from other sources and instead provide a direct
        // action for move/add to homescreen.
        // When a accessible drag is started by the folder, we only allow rearranging withing the
        // folder.
        boolean addNewPage = !(options.isAccessibleDrag && dragObject.dragSource != this);

        if (addNewPage) {
            mDeferRemoveExtraEmptyScreen = false;
            addExtraEmptyScreenOnDrag();
        }

        mLauncher.getStateManager().goToState(SPRING_LOADED);
    }

    @Override
    public void onDragEnd() {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragEnd", 0, 0);
        }

        updateChildrenLayersEnabled();
        mDragInfo = null;
        mOutlineProvider = null;
    }

    /**
     * Initializes various states for this workspace.
     */
    protected void initWorkspace() {
        mCurrentPage = DEFAULT_PAGE;
        setClipToPadding(false);

        setupLayoutTransition();

        // Set the wallpaper dimensions when Launcher starts up
        setWallpaperDimension();
    }

    private void setupLayoutTransition() {
        // We want to show layout transitions when pages are deleted, to close the gap.
        mLayoutTransition = new LayoutTransition();
        mLayoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
        mLayoutTransition.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
        mLayoutTransition.disableTransitionType(LayoutTransition.APPEARING);
        mLayoutTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
        setLayoutTransition(mLayoutTransition);
    }

    void enableLayoutTransitions() {
        setLayoutTransition(mLayoutTransition);
    }
    void disableLayoutTransitions() {
        setLayoutTransition(null);
    }

    @Override
    public void onViewAdded(View child) {
        if (!(child instanceof CellLayout)) {
            throw new IllegalArgumentException("A Workspace can only have CellLayout children.");
        }
        CellLayout cl = ((CellLayout) child);
        cl.setOnInterceptTouchListener(this);
        cl.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        super.onViewAdded(child);
    }

    public void removeAllWorkspaceScreens() {
        // Disable all layout transitions before removing all pages to ensure that we don't get the
        // transition animations competing with us changing the scroll when we add pages
        disableLayoutTransitions();

        // Remove the pages and clear the screen models
        removeAllViews();
        mScreenOrder.clear();
        mWorkspaceScreens.clear();

        // Re-enable the layout transitions
        enableLayoutTransitions();
    }

    public void insertNewWorkspaceScreenBeforeEmptyScreen(int screenId) {
        // Find the index to insert this view into.  If the empty screen exists, then
        // insert it before that.
        int insertIndex = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
        if (insertIndex < 0) {
            insertIndex = mScreenOrder.size();
        }
        insertNewWorkspaceScreen(screenId, insertIndex);
    }

    public void insertNewWorkspaceScreen(int screenId) {
        insertNewWorkspaceScreen(screenId, getChildCount());
    }

    public CellLayout insertNewWorkspaceScreen(int screenId, int insertIndex) {
        if (mWorkspaceScreens.containsKey(screenId)) {
            throw new RuntimeException("Screen id " + screenId + " already exists!");
        }

        // Inflate the cell layout, but do not add it automatically so that we can get the newly
        // created CellLayout.
        CellLayout newScreen = (CellLayout) LayoutInflater.from(getContext()).inflate(
                        R.layout.workspace_screen, this, false /* attachToRoot */);
        DeviceProfile grid = mLauncher.getWallpaperDeviceProfile();
        int paddingLeftRight = grid.cellLayoutPaddingLeftRightPx;
        int paddingBottom = grid.cellLayoutBottomPaddingPx;
        newScreen.setRotationMode(mLauncher.getRotationMode());
        newScreen.setPadding(paddingLeftRight, 0, paddingLeftRight, paddingBottom);

        mWorkspaceScreens.put(screenId, newScreen);
        mScreenOrder.add(insertIndex, screenId);
        addView(newScreen, insertIndex);
        mStateTransitionAnimation.applyChildState(
                mLauncher.getStateManager().getState(), newScreen, insertIndex);

        if (mLauncher.getAccessibilityDelegate().isInAccessibleDrag()) {
            newScreen.enableAccessibleDrag(true, CellLayout.WORKSPACE_ACCESSIBILITY_DRAG);
        }

        return newScreen;
    }

    public void addExtraEmptyScreenOnDrag() {
        boolean lastChildOnScreen = false;
        boolean childOnFinalScreen = false;

        // Cancel any pending removal of empty screen
        mRemoveEmptyScreenRunnable = null;

        // If this is the last item on the final screen
        if (lastChildOnScreen && childOnFinalScreen) {
            return;
        }
        if (!mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_ID)) {
            insertNewWorkspaceScreen(EXTRA_EMPTY_SCREEN_ID);
        }
    }

    public boolean addExtraEmptyScreen() {
        if (!mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_ID)) {
            insertNewWorkspaceScreen(EXTRA_EMPTY_SCREEN_ID);
            return true;
        }
        return false;
    }

    private void convertFinalScreenToEmptyScreenIfNecessary() {
        if (mLauncher.isWorkspaceLoading()) {
            // Invalid and dangerous operation if workspace is loading
            return;
        }

        if (hasExtraEmptyScreen() || mScreenOrder.size() == 0) return;
        int finalScreenId = mScreenOrder.get(mScreenOrder.size() - 1);

        CellLayout finalScreen = mWorkspaceScreens.get(finalScreenId);

        // If the final screen is empty, convert it to the extra empty screen
        if (!finalScreen.isDropPending()) {
            mWorkspaceScreens.remove(finalScreenId);
            mScreenOrder.removeValue(finalScreenId);

            // if this is the last screen, convert it to the empty screen
            mWorkspaceScreens.put(EXTRA_EMPTY_SCREEN_ID, finalScreen);
            mScreenOrder.add(EXTRA_EMPTY_SCREEN_ID);
        }
    }

    public void removeExtraEmptyScreen(final boolean animate, boolean stripEmptyScreens) {
        removeExtraEmptyScreenDelayed(animate, null, 0, stripEmptyScreens);
    }

    public void removeExtraEmptyScreenDelayed(final boolean animate, final Runnable onComplete,
            final int delay, final boolean stripEmptyScreens) {
        if (mLauncher.isWorkspaceLoading()) {
            // Don't strip empty screens if the workspace is still loading
            return;
        }

        if (delay > 0) {
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    removeExtraEmptyScreenDelayed(animate, onComplete, 0, stripEmptyScreens);
                }
            }, delay);
            return;
        }

        convertFinalScreenToEmptyScreenIfNecessary();
        if (hasExtraEmptyScreen()) {
            int emptyIndex = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
            if (getNextPage() == emptyIndex) {
                snapToPage(getNextPage() - 1, SNAP_OFF_EMPTY_SCREEN_DURATION);
                fadeAndRemoveEmptyScreen(SNAP_OFF_EMPTY_SCREEN_DURATION, FADE_EMPTY_SCREEN_DURATION,
                        onComplete, stripEmptyScreens);
            } else {
                snapToPage(getNextPage(), 0);
                fadeAndRemoveEmptyScreen(0, FADE_EMPTY_SCREEN_DURATION,
                        onComplete, stripEmptyScreens);
            }
            return;
        } else if (stripEmptyScreens) {
            // If we're not going to strip the empty screens after removing
            // the extra empty screen, do it right away.
            stripEmptyScreens();
        }

        if (onComplete != null) {
            onComplete.run();
        }
    }

    private void fadeAndRemoveEmptyScreen(int delay, int duration, final Runnable onComplete,
            final boolean stripEmptyScreens) {
        // XXX: Do we need to update LM workspace screens below?
        final CellLayout cl = mWorkspaceScreens.get(EXTRA_EMPTY_SCREEN_ID);

        mRemoveEmptyScreenRunnable = new Runnable() {
            @Override
            public void run() {
                if (hasExtraEmptyScreen()) {
                    mWorkspaceScreens.remove(EXTRA_EMPTY_SCREEN_ID);
                    mScreenOrder.removeValue(EXTRA_EMPTY_SCREEN_ID);
                    removeView(cl);
                    if (stripEmptyScreens) {
                        stripEmptyScreens();
                    }
                    // Update the page indicator to reflect the removed page.
                    showPageIndicatorAtCurrentScroll();
                }
            }
        };

        ObjectAnimator oa = ObjectAnimator.ofFloat(cl, ALPHA, 0f);
        oa.setDuration(duration);
        oa.setStartDelay(delay);
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mRemoveEmptyScreenRunnable != null) {
                    mRemoveEmptyScreenRunnable.run();
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        oa.start();
    }

    public boolean hasExtraEmptyScreen() {
        return mWorkspaceScreens.containsKey(EXTRA_EMPTY_SCREEN_ID) && getChildCount() > 1;
    }

    public int commitExtraEmptyScreen() {
        if (mLauncher.isWorkspaceLoading()) {
            // Invalid and dangerous operation if workspace is loading
            return -1;
        }

        CellLayout cl = mWorkspaceScreens.get(EXTRA_EMPTY_SCREEN_ID);
        mWorkspaceScreens.remove(EXTRA_EMPTY_SCREEN_ID);
        mScreenOrder.removeValue(EXTRA_EMPTY_SCREEN_ID);

        int newId = LauncherSettings.Settings.call(getContext().getContentResolver(),
                LauncherSettings.Settings.METHOD_NEW_SCREEN_ID)
                .getInt(LauncherSettings.Settings.EXTRA_VALUE);
        mWorkspaceScreens.put(newId, cl);
        mScreenOrder.add(newId);

        return newId;
    }

    @Override
    public void onAddDropTarget(DropTarget target) {
        mDragController.addDropTarget(target);
    }

    @Override
    public CellLayout getScreenWithId(int screenId) {
        return mWorkspaceScreens.get(screenId);
    }

    public int getIdForScreen(CellLayout layout) {
        int index = mWorkspaceScreens.indexOfValue(layout);
        if (index != -1) {
            return mWorkspaceScreens.keyAt(index);
        }
        return -1;
    }

    public int getPageIndexForScreenId(int screenId) {
        return indexOfChild(mWorkspaceScreens.get(screenId));
    }

    public int getScreenIdForPageIndex(int index) {
        if (0 <= index && index < mScreenOrder.size()) {
            return mScreenOrder.get(index);
        }
        return -1;
    }

    public IntArray getScreenOrder() {
        return mScreenOrder;
    }

    public void stripEmptyScreens() {
        if (mLauncher.isWorkspaceLoading()) {
            // Don't strip empty screens if the workspace is still loading.
            // This is dangerous and can result in data loss.
            return;
        }

        if (isPageInTransition()) {
            mStripScreensOnPageStopMoving = true;
            return;
        }

        int currentPage = getNextPage();
        IntArray removeScreens = new IntArray();
        int total = mWorkspaceScreens.size();
        for (int i = 0; i < total; i++) {
            int id = mWorkspaceScreens.keyAt(i);
            // FIRST_SCREEN_ID can never be removed.
            if ((!FeatureFlags.QSB_ON_FIRST_SCREEN || id > FIRST_SCREEN_ID)) {
                removeScreens.add(id);
            }
        }

        boolean isInAccessibleDrag = mLauncher.getAccessibilityDelegate().isInAccessibleDrag();

        // We enforce at least one page to add new items to. In the case that we remove the last
        // such screen, we convert the last screen to the empty screen
        int minScreens = 1;

        int pageShift = 0;
        for (int i = 0; i < removeScreens.size(); i++) {
            int id = removeScreens.get(i);
            CellLayout cl = mWorkspaceScreens.get(id);
            mWorkspaceScreens.remove(id);
            mScreenOrder.removeValue(id);

            if (getChildCount() > minScreens) {
                if (indexOfChild(cl) < currentPage) {
                    pageShift++;
                }

                if (isInAccessibleDrag) {
                    cl.enableAccessibleDrag(false, CellLayout.WORKSPACE_ACCESSIBILITY_DRAG);
                }

                removeView(cl);
            } else {
                // if this is the last screen, convert it to the empty screen
                mRemoveEmptyScreenRunnable = null;
                mWorkspaceScreens.put(EXTRA_EMPTY_SCREEN_ID, cl);
                mScreenOrder.add(EXTRA_EMPTY_SCREEN_ID);
            }
        }

        if (pageShift >= 0) {
            setCurrentPage(currentPage - pageShift);
        }
    }

    /**
     * Called directly from a CellLayout (not by the framework), after we've been added as a
     * listener via setOnInterceptTouchEventListener(). This allows us to tell the CellLayout
     * that it should intercept touch events, which is not something that is normally supported.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return shouldConsumeTouch(v);
    }

    private boolean shouldConsumeTouch(View v) {
        return !workspaceIconsCanBeDragged()
                || (!workspaceInModalState() && indexOfChild(v) != mCurrentPage);
    }

    public boolean isSwitchingState() {
        return mIsSwitchingState;
    }

    /** This differs from isSwitchingState in that we take into account how far the transition
     *  has completed. */
    public boolean isFinishedSwitchingState() {
        return !mIsSwitchingState
                || (mTransitionProgress > FINISHED_SWITCHING_STATE_TRANSITION_PROGRESS);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        if (workspaceInModalState() || !isFinishedSwitchingState()) {
            // when the home screens are shrunken, shouldn't allow side-scrolling
            return false;
        }
        return super.dispatchUnhandledMove(focused, direction);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mXDown = ev.getX();
            mYDown = ev.getY();
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev) {
        if (!isFinishedSwitchingState()) return;

        float deltaX = ev.getX() - mXDown;
        float absDeltaX = Math.abs(deltaX);
        float absDeltaY = Math.abs(ev.getY() - mYDown);

        if (Float.compare(absDeltaX, 0f) == 0) return;

        float slope = absDeltaY / absDeltaX;
        float theta = (float) Math.atan(slope);

        if (absDeltaX > mTouchSlop || absDeltaY > mTouchSlop) {
            cancelCurrentPageLongPress();
        }

        if (theta > MAX_SWIPE_ANGLE) {
            // Above MAX_SWIPE_ANGLE, we don't want to ever start scrolling the workspace
            return;
        } else if (theta > START_DAMPING_TOUCH_SLOP_ANGLE) {
            // Above START_DAMPING_TOUCH_SLOP_ANGLE and below MAX_SWIPE_ANGLE, we want to
            // increase the touch slop to make it harder to begin scrolling the workspace. This
            // results in vertically scrolling widgets to more easily. The higher the angle, the
            // more we increase touch slop.
            theta -= START_DAMPING_TOUCH_SLOP_ANGLE;
            float extraRatio = (float)
                    Math.sqrt((theta / (MAX_SWIPE_ANGLE - START_DAMPING_TOUCH_SLOP_ANGLE)));
            super.determineScrollingStart(ev, 1 + TOUCH_SLOP_DAMPING_FACTOR * extraRatio);
        } else {
            // Below START_DAMPING_TOUCH_SLOP_ANGLE, we don't do anything special
            super.determineScrollingStart(ev);
        }
    }

    protected void onPageBeginTransition() {
        super.onPageBeginTransition();
        updateChildrenLayersEnabled();
    }

    protected void onPageEndTransition() {
        super.onPageEndTransition();
        updateChildrenLayersEnabled();

        if (mDragController.isDragging()) {
            if (workspaceInModalState()) {
                // If we are in springloaded mode, then force an event to check if the current touch
                // is under a new page (to scroll to)
                mDragController.forceTouchMove();
            }
        }

        if (mStripScreensOnPageStopMoving) {
            stripEmptyScreens();
            mStripScreensOnPageStopMoving = false;
        }
    }

    protected void onScrollInteractionBegin() {
        super.onScrollInteractionBegin();
        mScrollInteractionBegan = true;
    }

    protected void onScrollInteractionEnd() {
        super.onScrollInteractionEnd();
        mScrollInteractionBegan = false;
        if (mStartedSendingScrollEvents) {
            mStartedSendingScrollEvents = false;
            mLauncherOverlay.onScrollInteractionEnd();
        }
    }

    public void setLauncherOverlay(LauncherOverlay overlay) {
        mLauncherOverlay = overlay;
        // A new overlay has been set. Reset event tracking
        mStartedSendingScrollEvents = false;
        onOverlayScrollChanged(0);
    }


    private boolean isScrollingOverlay() {
        return mLauncherOverlay != null &&
                ((mIsRtl && getUnboundedScrollX() > mMaxScrollX)
                        || (!mIsRtl && getUnboundedScrollX() < mMinScrollX));
    }

    @Override
    protected void snapToDestination() {
        // If we're overscrolling the overlay, we make sure to immediately reset the PagedView
        // to it's baseline position instead of letting the overscroll settle. The overlay handles
        // it's own settling, and every gesture to the overlay should be self-contained and start
        // from 0, so we zero it out here.
        if (isScrollingOverlay()) {
            // We reset mWasInOverscroll so that PagedView doesn't zero out the overscroll
            // interaction when we call snapToPageImmediately.
            mWasInOverscroll = false;
            snapToPageImmediately(0);
        } else {
            super.snapToDestination();
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        // Update the page indicator progress.
        boolean isTransitioning = mIsSwitchingState
                || (getLayoutTransition() != null && getLayoutTransition().isRunning());
        if (!isTransitioning) {
            showPageIndicatorAtCurrentScroll();
        }
    }

    public void showPageIndicatorAtCurrentScroll() {
        if (mPageIndicator != null) {
            mPageIndicator.setScroll(getScrollX(), computeMaxScrollX());
        }
    }

    @Override
    protected void overScroll(int amount) {
        boolean shouldScrollOverlay = mLauncherOverlay != null && !mScroller.isSpringing() &&
                ((amount <= 0 && !mIsRtl) || (amount >= 0 && mIsRtl));

        boolean shouldZeroOverlay = mLauncherOverlay != null && mLastOverlayScroll != 0 &&
                ((amount >= 0 && !mIsRtl) || (amount <= 0 && mIsRtl));

        if (shouldScrollOverlay) {
            if (!mStartedSendingScrollEvents && mScrollInteractionBegan) {
                mStartedSendingScrollEvents = true;
                mLauncherOverlay.onScrollInteractionBegin();
            }

            mLastOverlayScroll = Math.abs(((float) amount) / getMeasuredWidth());
            mLauncherOverlay.onScrollChange(mLastOverlayScroll, mIsRtl);
        } else {
            dampedOverScroll(amount);
        }

        if (shouldZeroOverlay) {
            mLauncherOverlay.onScrollChange(0, mIsRtl);
        }
    }

    @Override
    protected boolean onOverscroll(int amount) {
        // Enforce overscroll on -1 direction
        if ((amount > 0 && !mIsRtl) || (amount < 0 && mIsRtl)) return false;
        return super.onOverscroll(amount);
    }

    @Override
    protected boolean shouldFlingForVelocity(int velocityX) {
        // When the overlay is moving, the fling or settle transition is controlled by the overlay.
        return Float.compare(Math.abs(mOverlayTranslation), 0) == 0 &&
                super.shouldFlingForVelocity(velocityX);
    }

    /**
     * The overlay scroll is being controlled locally, just update our overlay effect
     */
    public void onOverlayScrollChanged(float scroll) {
        if (Float.compare(scroll, 1f) == 0) {
            if (!mOverlayShown) {
                mLauncher.getUserEventDispatcher().logActionOnContainer(Action.Touch.SWIPE,
                        Action.Direction.LEFT, ContainerType.WORKSPACE, 0);
                mLauncher.getStatsLogManager().logSwipeOnContainer(true, 0);
            }
            mOverlayShown = true;
            // Not announcing the overlay page for accessibility since it announces itself.
        } else if (Float.compare(scroll, 0f) == 0) {
            if (mOverlayShown) {
                UserEventDispatcher ued = mLauncher.getUserEventDispatcher();
                if (!ued.isPreviousHomeGesture()) {
                    mLauncher.getUserEventDispatcher().logActionOnContainer(Action.Touch.SWIPE,
                        Action.Direction.RIGHT, ContainerType.WORKSPACE, -1);
                    mLauncher.getStatsLogManager().logSwipeOnContainer(false, -1);
                }
            } else if (Float.compare(mOverlayTranslation, 0f) != 0) {
                // When arriving to 0 overscroll from non-zero overscroll, announce page for
                // accessibility since default announcements were disabled while in overscroll
                // state.
                // Not doing this if mOverlayShown because in that case the accessibility service
                // will announce the launcher window description upon regaining focus after
                // switching from the overlay screen.
                announcePageForAccessibility();
            }
            mOverlayShown = false;
            tryRunOverlayCallback();
        }

        float offset = 0f;

        scroll = Math.max(scroll - offset, 0);
        scroll = Math.min(1, scroll / (1 - offset));

        float alpha = 1 - Interpolators.DEACCEL_3.getInterpolation(scroll);
        float transX = mLauncher.getDragLayer().getMeasuredWidth() * scroll;

        if (mIsRtl) {
            transX = -transX;
        }
        mOverlayTranslation = transX;

        // TODO(adamcohen): figure out a final effect here. We may need to recommend
        // different effects based on device performance. On at least one relatively high-end
        // device I've tried, translating the launcher causes things to get quite laggy.
        mLauncher.getDragLayer().setTranslationX(transX);
        mLauncher.getDragLayer().getAlphaProperty(ALPHA_INDEX_OVERLAY).setValue(alpha);
    }

    /**
     * @return false if the callback is still pending
     */
    private boolean tryRunOverlayCallback() {
        if (mOnOverlayHiddenCallback == null) {
            // Return true as no callback is pending. This is used by OnWindowFocusChangeListener
            // to remove itself if multiple focus handles were added.
            return true;
        }
        if (mOverlayShown || !hasWindowFocus()) {
            return false;
        }

        mOnOverlayHiddenCallback.run();
        mOnOverlayHiddenCallback = null;
        return true;
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        if (prevPage != mCurrentPage) {
            int swipeDirection = (prevPage < mCurrentPage) ? Action.Direction.RIGHT : Action.Direction.LEFT;
            mLauncher.getUserEventDispatcher().logActionOnContainer(Action.Touch.SWIPE,
                    swipeDirection, ContainerType.WORKSPACE, prevPage);
        }
    }

    protected void setWallpaperDimension() {
        Executors.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final Point size = LauncherAppState.getIDP(getContext()).defaultWallpaperSize;
                if (size.x != mWallpaperManager.getDesiredMinimumWidth()
                        || size.y != mWallpaperManager.getDesiredMinimumHeight()) {
                    mWallpaperManager.suggestDesiredDimensions(size.x, size.y);
                }
            }
        });
    }

    public void lockWallpaperToDefaultPage() {
        mWallpaperOffset.setLockToDefaultPage(true);
    }

    public void unlockWallpaperFromDefaultPageOnNextLayout() {
        if (mWallpaperOffset.isLockedToDefaultPage()) {
            mUnlockWallpaperFromDefaultPageOnLayout = true;
            requestLayout();
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        mWallpaperOffset.syncWithScroll();
    }

    public void computeScrollWithoutInvalidation() {
        computeScrollHelper(false);
    }

    @Override
    protected void determineScrollingStart(MotionEvent ev, float touchSlopScale) {
        if (!isSwitchingState()) {
            super.determineScrollingStart(ev, touchSlopScale);
        }
    }

    @Override
    public void announceForAccessibility(CharSequence text) {
        // Don't announce if apps is on top of us.
        if (!mLauncher.isInState(ALL_APPS)) {
            super.announceForAccessibility(text);
        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IBinder windowToken = getWindowToken();
        mWallpaperOffset.setWindowToken(windowToken);
        computeScroll();
        mDragController.setWindowToken(windowToken);
    }

    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mWallpaperOffset.setWindowToken(null);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mUnlockWallpaperFromDefaultPageOnLayout) {
            mWallpaperOffset.setLockToDefaultPage(false);
            mUnlockWallpaperFromDefaultPageOnLayout = false;
        }
        if (mFirstLayout && mCurrentPage >= 0 && mCurrentPage < getChildCount()) {
            mWallpaperOffset.syncWithScroll();
            mWallpaperOffset.jumpToFinal();
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    public int getDescendantFocusability() {
        if (workspaceInModalState()) {
            return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        }
        return super.getDescendantFocusability();
    }

    private boolean workspaceInModalState() {
        return !mLauncher.isInState(NORMAL);
    }

    private boolean workspaceInScrollableState() {
        return mLauncher.isInState(SPRING_LOADED) || !workspaceInModalState();
    }

    /** Returns whether a drag should be allowed to be started from the current workspace state. */
    public boolean workspaceIconsCanBeDragged() {
        return mLauncher.getStateManager().getState().workspaceIconsCanBeDragged;
    }

    private void updateChildrenLayersEnabled() {
        boolean enableChildrenLayers = mIsSwitchingState || isPageInTransition();

        if (enableChildrenLayers != mChildrenLayersEnabled) {
            mChildrenLayersEnabled = enableChildrenLayers;
        }
    }

    public void onWallpaperTap(MotionEvent ev) {
        final int[] position = mTempXY;
        getLocationOnScreen(position);

        int pointerIndex = ev.getActionIndex();
        position[0] += (int) ev.getX(pointerIndex);
        position[1] += (int) ev.getY(pointerIndex);

        mWallpaperManager.sendWallpaperCommand(getWindowToken(),
                ev.getAction() == MotionEvent.ACTION_UP
                        ? WallpaperManager.COMMAND_TAP : WallpaperManager.COMMAND_SECONDARY_TAP,
                position[0], position[1], 0, null);
    }

    private void onStartStateTransition(LauncherState state) {
        mIsSwitchingState = true;
        mTransitionProgress = 0;

        updateChildrenLayersEnabled();
    }

    private void onEndStateTransition() {
        mIsSwitchingState = false;
        mTransitionProgress = 1;

        updateChildrenLayersEnabled();
        updateAccessibilityFlags();
    }

    /**
     * Sets the current workspace {@link LauncherState} and updates the UI without any animations
     */
    @Override
    public void setState(LauncherState toState) {
        onStartStateTransition(toState);
        mStateTransitionAnimation.setState(toState);
        onEndStateTransition();
    }

    /**
     * Sets the current workspace {@link LauncherState}, then animates the UI
     */
    @Override
    public void setStateWithAnimation(LauncherState toState,
            AnimatorSetBuilder builder, AnimationConfig config) {
        StateTransitionListener listener = new StateTransitionListener(toState);
        mStateTransitionAnimation.setStateWithAnimation(toState, builder, config);

        // Invalidate the pages now, so that we have the visible pages before the
        // animation is started
        if (toState.hasMultipleVisiblePages) {
        }
        invalidate(); // This will call dispatchDraw(), which calls getVisiblePages().

        ValueAnimator stepAnimator = ValueAnimator.ofFloat(0, 1);
        stepAnimator.addUpdateListener(listener);
        stepAnimator.setDuration(config.duration);
        stepAnimator.addListener(listener);
        builder.play(stepAnimator);
    }

    public WorkspaceStateTransitionAnimation getStateTransitionAnimation() {
        return mStateTransitionAnimation;
    }

    public void updateAccessibilityFlags() {
        // TODO: Update the accessibility flags appropriately when dragging.
        int accessibilityFlag = mLauncher.getStateManager().getState().workspaceAccessibilityFlag;
        if (!mLauncher.getAccessibilityDelegate().isInAccessibleDrag()) {
            int total = getPageCount();
            for (int i = 0; i < total; i++) {
                updateAccessibilityFlags(accessibilityFlag, (CellLayout) getPageAt(i));
            }
            setImportantForAccessibility(accessibilityFlag);
        }
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo() {
        if (getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
            // TAPL tests verify that workspace is not present in Overview and AllApps states.
            // TAPL can work only if UIDevice is set up as setCompressedLayoutHeirarchy(false).
            // Hiding workspace from the tests when it's
            // IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS.
            return null;
        }
        return super.createAccessibilityNodeInfo();
    }

    private void updateAccessibilityFlags(int accessibilityFlag, CellLayout page) {
        page.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        page.setContentDescription(null);
        page.setAccessibilityDelegate(null);
    }

    public void startDrag(CellLayout.CellInfo cellInfo, DragOptions options) {
        View child = cellInfo.cell;

        mDragInfo = cellInfo;
        child.setVisibility(INVISIBLE);

        if (options.isAccessibleDrag) {
            mDragController.addDragListener(new AccessibleDragListenerAdapter(
                    this, CellLayout.WORKSPACE_ACCESSIBILITY_DRAG) {
                @Override
                protected void enableAccessibleDrag(boolean enable) {
                    super.enableAccessibleDrag(enable);
                }
            });
        }

        beginDragShared(child, this, options);
    }

    public void beginDragShared(View child, DragSource source, DragOptions options) {
        Object dragObject = child.getTag();
        if (!(dragObject instanceof ItemInfo)) {
            String msg = "Drag started with a view that has no tag set. This "
                    + "will cause a crash (issue 11627249) down the line. "
                    + "View: " + child + "  tag: " + child.getTag();
            throw new IllegalStateException(msg);
        }
        beginDragShared(child, source, (ItemInfo) dragObject,
                new DragPreviewProvider(child), options);
    }

    public DragView beginDragShared(View child, DragSource source, ItemInfo dragObject,
            DragPreviewProvider previewProvider, DragOptions dragOptions) {
        float iconScale = 1f;
        if (child instanceof BubbleTextView) {
            Drawable icon = ((BubbleTextView) child).getIcon();
            if (icon instanceof FastBitmapDrawable) {
                iconScale = ((FastBitmapDrawable) icon).getAnimatedScale();
            }
        }

        child.clearFocus();
        child.setPressed(false);
        mOutlineProvider = previewProvider;

        // The drag bitmap follows the touch point around on the screen
        final Bitmap b = previewProvider.createDragBitmap();
        int halfPadding = previewProvider.previewPadding / 2;

        float scale = previewProvider.getScaleAndPosition(b, mTempXY);
        int dragLayerX = mTempXY[0];
        int dragLayerY = mTempXY[1];

        DeviceProfile grid = mLauncher.getDeviceProfile();
        Point dragVisualizeOffset = null;
        Rect dragRect = null;
        if (child instanceof BubbleTextView) {
            dragRect = new Rect();
            BubbleTextView.getIconBounds(child, dragRect, grid.iconSizePx);
            dragLayerY += dragRect.top;
            // Note: The dragRect is used to calculate drag layer offsets, but the
            // dragVisualizeOffset in addition to the dragRect (the size) to position the outline.
            dragVisualizeOffset = new Point(- halfPadding, halfPadding);
        }
        // Clear the pressed state if necessary
        if (child instanceof BubbleTextView) {
            BubbleTextView icon = (BubbleTextView) child;
            icon.clearPressedBackground();
        }

        if (child instanceof BubbleTextView && !dragOptions.isAccessibleDrag) {
            PopupContainerWithArrow popupContainer = PopupContainerWithArrow
                    .showForIcon((BubbleTextView) child);
            if (popupContainer != null) {
                dragOptions.preDragCondition = popupContainer.createPreDragCondition();

                mLauncher.getUserEventDispatcher().resetElapsedContainerMillis("dragging started");
            }
        }

        DragView dv = mDragController.startDrag(b, dragLayerX, dragLayerY, source,
                dragObject, dragVisualizeOffset, dragRect, scale * iconScale, scale, dragOptions);
        dv.setIntrinsicIconScaleFactor(dragOptions.intrinsicIconScaleFactor);
        return dv;
    }

    private boolean transitionStateShouldAllowDrop() {
        return (!isSwitchingState() || mTransitionProgress > ALLOW_DROP_TRANSITION_PROGRESS) &&
                workspaceIconsCanBeDragged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptDrop(DragObject d) {
        return false;
    }

    @Override
    public void prepareAccessibilityDrop() { }

    public void onDrop(final DragObject d, DragOptions options) {
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        CellLayout dropTargetLayout = mDropToLayout;

        // We want the point to be mapped to the dragTarget.
        if (dropTargetLayout != null) {
            mapPointFromDropLayout(dropTargetLayout, mDragViewVisualCenter);
        }

        boolean droppedOnOriginalCell = false;

        int snapScreen = -1;
        if (d.dragSource != this || mDragInfo == null) {
            final int[] touchXY = new int[] { (int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1] };
            onDropExternal(touchXY, dropTargetLayout, d);
        } else {
            final View cell = mDragInfo.cell;
            boolean droppedOnOriginalCellDuringTransition = false;
            Runnable onCompleteRunnable = null;

            if (dropTargetLayout != null && !d.cancelled) {
                // Move internally
                int container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
                int screenId = (mTargetCell[0] < 0) ?
                        mDragInfo.screenId : getIdForScreen(dropTargetLayout);
                int spanX = mDragInfo != null ? mDragInfo.spanX : 1;
                int spanY = mDragInfo != null ? mDragInfo.spanY : 1;
                // First we find the cell nearest to point at which the item is
                // dropped, without any consideration to whether there is an item there.

                mTargetCell = findNearestArea((int) mDragViewVisualCenter[0], (int)
                        mDragViewVisualCenter[1], spanX, spanY, dropTargetLayout, mTargetCell);

                // Aside from the special case where we're dropping a shortcut onto a shortcut,
                // we need to find the nearest cell location that is vacant
                ItemInfo item = d.dragInfo;
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }

                droppedOnOriginalCell = item.screenId == screenId && item.container == container
                        && item.cellX == mTargetCell[0] && item.cellY == mTargetCell[1];
                droppedOnOriginalCellDuringTransition = droppedOnOriginalCell && mIsSwitchingState;

                // When quickly moving an item, a user may accidentally rearrange their
                // workspace. So instead we move the icon back safely to its original position.
                boolean returnToOriginalCellToPreventShuffling = !isFinishedSwitchingState()
                        && !droppedOnOriginalCellDuringTransition && !dropTargetLayout
                        .isRegionVacant(mTargetCell[0], mTargetCell[1], spanX, spanY);
                int[] resultSpan = new int[2];
                if (returnToOriginalCellToPreventShuffling) {
                    mTargetCell[0] = mTargetCell[1] = -1;
                } else {
                    mTargetCell = dropTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                            (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY, cell,
                            mTargetCell, resultSpan, CellLayout.MODE_ON_DROP);
                }

                boolean foundCell = mTargetCell[0] >= 0 && mTargetCell[1] >= 0;

                // if the widget resizes on drop
                if (foundCell && (cell instanceof AppWidgetHostView) &&
                        (resultSpan[0] != item.spanX || resultSpan[1] != item.spanY)) {
                    item.spanX = resultSpan[0];
                    item.spanY = resultSpan[1];
                }

                if (foundCell) {
                    if (getScreenIdForPageIndex(mCurrentPage) != screenId) {
                        snapScreen = getPageIndexForScreenId(screenId);
                        snapToPage(snapScreen);
                    }

                    final ItemInfo info = (ItemInfo) cell.getTag();

                    // update the item's position after drop
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    lp.cellX = lp.tmpCellX = mTargetCell[0];
                    lp.cellY = lp.tmpCellY = mTargetCell[1];
                    lp.cellHSpan = item.spanX;
                    lp.cellVSpan = item.spanY;
                    lp.isLockedToGrid = true;

                    mLauncher.getModelWriter().modifyItemInDatabase(info, container, screenId,
                            lp.cellX, lp.cellY, item.spanX, item.spanY);
                } else {
                    if (!returnToOriginalCellToPreventShuffling) {
                        onNoCellFound();
                    }

                    // If we can't find a drop location, we return the item to its original position
                    CellLayout.LayoutParams lp = (CellLayout.LayoutParams) cell.getLayoutParams();
                    mTargetCell[0] = lp.cellX;
                    mTargetCell[1] = lp.cellY;
                    CellLayout layout = (CellLayout) cell.getParent().getParent();
                    layout.markCellsAsOccupiedForView(cell);
                }
            }

            final CellLayout parent = (CellLayout) cell.getParent().getParent();
            if (d.dragView.hasDrawn()) {
                if (droppedOnOriginalCellDuringTransition) {
                    // Animate the item to its original position, while simultaneously exiting
                    // spring-loaded mode so the page meets the icon where it was picked up.
                    mLauncher.getDragController().animateDragViewToOriginalPosition(
                            onCompleteRunnable, cell, SPRING_LOADED_TRANSITION_MS);
                    mLauncher.getStateManager().goToState(NORMAL);
                    mLauncher.getDropTargetBar().onDragEnd();
                    parent.onDropChild(cell);
                    return;
                }
                int duration = snapScreen < 0 ? -1 : ADJACENT_SCREEN_DROP_DURATION;
                mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, cell, duration,
                        this);
            } else {
                d.deferDragViewCleanupPostAnimation = false;
                cell.setVisibility(VISIBLE);
            }
            parent.onDropChild(cell);

            mLauncher.getStateManager().goToState(
                    NORMAL, SPRING_LOADED_EXIT_DELAY, onCompleteRunnable);
        }

        if (d.stateAnnouncer != null && !droppedOnOriginalCell) {
            d.stateAnnouncer.completeAction(R.string.item_moved);
        }
    }

    public void onNoCellFound() {
        int strId = R.string.out_of_space;
        Toast.makeText(mLauncher, mLauncher.getString(strId), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDragEnter(DragObject d) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragEnter", 1, 1);
        }

        mDropToLayout = null;
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);
        setDropLayoutForDragObject(d, mDragViewVisualCenter[0]);
    }

    @Override
    public void onDragExit(DragObject d) {
        if (ENFORCE_DRAG_EVENT_ORDER) {
            enforceDragParity("onDragExit", -1, 0);
        }

        // Here we store the final page that will be dropped to, if the workspace in fact
        // receives the drop
        mDropToLayout = mDragTargetLayout;

        // Reset the previous drag target
        setCurrentDropLayout(null);
        setCurrentDragOverlappingLayout(null);

        mSpringLoadedDragController.cancel();
    }

    private void enforceDragParity(String event, int update, int expectedValue) {
        enforceDragParity(this, event, update, expectedValue);
        for (int i = 0; i < getChildCount(); i++) {
            enforceDragParity(getChildAt(i), event, update, expectedValue);
        }
    }

    private void enforceDragParity(View v, String event, int update, int expectedValue) {
        Object tag = v.getTag(R.id.drag_event_parity);
        int value = tag == null ? 0 : (Integer) tag;
        value += update;
        v.setTag(R.id.drag_event_parity, value);

        if (value != expectedValue) {
            Log.e(TAG, event + ": Drag contract violated: " + value);
        }
    }

    void setCurrentDropLayout(CellLayout layout) {
        if (mDragTargetLayout != null) {
            mDragTargetLayout.revertTempState();
            mDragTargetLayout.onDragExit();
        }
        mDragTargetLayout = layout;
        if (mDragTargetLayout != null) {
            mDragTargetLayout.onDragEnter();
        }
        cleanupReorder(true);
        cleanupFolderCreation();
        setCurrentDropOverCell(-1, -1);
    }

    void setCurrentDragOverlappingLayout(CellLayout layout) {
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(false);
        }
        mDragOverlappingLayout = layout;
        if (mDragOverlappingLayout != null) {
            mDragOverlappingLayout.setIsDragOverlapping(true);
        }
        // Invalidating the scrim will also force this CellLayout
        // to be invalidated so that it is highlighted if necessary.
        mLauncher.getDragLayer().getScrim().invalidate();
    }

    public CellLayout getCurrentDragOverlappingLayout() {
        return mDragOverlappingLayout;
    }

    void setCurrentDropOverCell(int x, int y) {
        if (x != mDragOverX || y != mDragOverY) {
            mDragOverX = x;
            mDragOverY = y;
            setDragMode(DRAG_MODE_NONE);
        }
    }

    void setDragMode(int dragMode) {
        if (dragMode != mDragMode) {
            if (dragMode == DRAG_MODE_NONE) {
                // We don't want to cancel the re-order alarm every time the target cell changes
                // as this feels to slow / unresponsive.
                cleanupReorder(false);
                cleanupFolderCreation();
            } else if (dragMode == DRAG_MODE_REORDER) {
                cleanupFolderCreation();
            }
            mDragMode = dragMode;
        }
    }

    private void cleanupFolderCreation() {
        mFolderCreationAlarm.setOnAlarmListener(null);
        mFolderCreationAlarm.cancelAlarm();
    }

    private void cleanupReorder(boolean cancelAlarm) {
        // Any pending reorders are canceled
        if (cancelAlarm) {
            mReorderAlarm.cancelAlarm();
        }
        mLastReorderX = -1;
        mLastReorderY = -1;
    }

   /*
    *
    * Convert the 2D coordinate xy from the parent View's coordinate space to this CellLayout's
    * coordinate space. The argument xy is modified with the return result.
    */
   private void mapPointFromSelfToChild(View v, float[] xy) {
       xy[0] = xy[0] - v.getLeft();
       xy[1] = xy[1] - v.getTop();
   }

    /**
     * Updates the point in {@param xy} to point to the co-ordinate space of {@param layout}
     * @param layout either hotseat of a page in workspace
     * @param xy the point location in workspace co-ordinate space
     */
   private void mapPointFromDropLayout(CellLayout layout, float[] xy) {
       mapPointFromSelfToChild(layout, xy);
   }

    public void onDragOver(DragObject d) {
        // Skip drag over events while we are dragging over side pages
        if (!transitionStateShouldAllowDrop()) return;

        ItemInfo item = d.dragInfo;
        if (item == null) {
            if (FeatureFlags.IS_DOGFOOD_BUILD) {
                throw new NullPointerException("DragObject has null info");
            }
            return;
        }

        // Ensure that we have proper spans for the item that we are dropping
        if (item.spanX < 0 || item.spanY < 0) throw new RuntimeException("Improper spans found");
        mDragViewVisualCenter = d.getVisualCenter(mDragViewVisualCenter);

        final View child = (mDragInfo == null) ? null : mDragInfo.cell;
        if (setDropLayoutForDragObject(d, mDragViewVisualCenter[0])) {
            mSpringLoadedDragController.setAlarm(mDragTargetLayout);
        }

        // Handle the drag over
        if (mDragTargetLayout != null) {
            // We want the point to be mapped to the dragTarget.
            mapPointFromDropLayout(mDragTargetLayout, mDragViewVisualCenter);

            int minSpanX = item.spanX;
            int minSpanY = item.spanY;
            if (item.minSpanX > 0 && item.minSpanY > 0) {
                minSpanX = item.minSpanX;
                minSpanY = item.minSpanY;
            }

            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY,
                    mDragTargetLayout, mTargetCell);
            int reorderX = mTargetCell[0];
            int reorderY = mTargetCell[1];

            setCurrentDropOverCell(mTargetCell[0], mTargetCell[1]);

            boolean nearestDropOccupied = mDragTargetLayout.isNearestDropLocationOccupied((int)
                    mDragViewVisualCenter[0], (int) mDragViewVisualCenter[1], item.spanX,
                    item.spanY, child, mTargetCell);

            if (!nearestDropOccupied) {
                mDragTargetLayout.visualizeDropLocation(child, mOutlineProvider,
                        mTargetCell[0], mTargetCell[1], item.spanX, item.spanY, false, d);
            } else if ((mDragMode == DRAG_MODE_NONE || mDragMode == DRAG_MODE_REORDER)
                    && !mReorderAlarm.alarmPending() && (mLastReorderX != reorderX ||
                    mLastReorderY != reorderY)) {

                int[] resultSpan = new int[2];
                mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, item.spanX, item.spanY,
                        child, mTargetCell, resultSpan, CellLayout.MODE_SHOW_REORDER_HINT);

                // Otherwise, if we aren't adding to or creating a folder and there's no pending
                // reorder, then we schedule a reorder
                ReorderAlarmListener listener = new ReorderAlarmListener(mDragViewVisualCenter,
                        minSpanX, minSpanY, item.spanX, item.spanY, d, child);
                mReorderAlarm.setOnAlarmListener(listener);
                mReorderAlarm.setAlarm(REORDER_TIMEOUT);
            }

            if (!nearestDropOccupied) {
                if (mDragTargetLayout != null) {
                    mDragTargetLayout.revertTempState();
                }
            }
        }
    }

    /**
     * Updates {@link #mDragTargetLayout} and {@link #mDragOverlappingLayout}
     * based on the DragObject's position.
     *
     * The layout will be:
     * - A side page if we are in spring-loaded mode and the drag object is over it
     * - The current page otherwise
     *
     * @return whether the layout is different from the current {@link #mDragTargetLayout}.
     */
    private boolean setDropLayoutForDragObject(DragObject d, float centerX) {
        CellLayout layout = null;
        int nextPage = getNextPage();
        if (layout == null && !isPageInTransition()) {
            // Check if the item is dragged over left page
            mTempTouchCoordinates[0] = Math.min(centerX, d.x);
            mTempTouchCoordinates[1] = d.y;
            layout = verifyInsidePage(nextPage + (mIsRtl ? 1 : -1), mTempTouchCoordinates);
        }

        if (layout == null && !isPageInTransition()) {
            // Check if the item is dragged over right page
            mTempTouchCoordinates[0] = Math.max(centerX, d.x);
            mTempTouchCoordinates[1] = d.y;
            layout = verifyInsidePage(nextPage + (mIsRtl ? -1 : 1), mTempTouchCoordinates);
        }

        // Always pick the current page.
        if (layout == null && nextPage >= 0 && nextPage < getPageCount()) {
            layout = (CellLayout) getChildAt(nextPage);
        }
        if (layout != mDragTargetLayout) {
            setCurrentDropLayout(layout);
            setCurrentDragOverlappingLayout(layout);
            return true;
        }
        return false;
    }

    /**
     * Returns the child CellLayout if the point is inside the page coordinates, null otherwise.
     */
    private CellLayout verifyInsidePage(int pageNo, float[] touchXy)  {
        if (pageNo >= 0 && pageNo < getPageCount()) {
            CellLayout cl = (CellLayout) getChildAt(pageNo);
            mapPointFromSelfToChild(cl, touchXy);
            if (touchXy[0] >= 0 && touchXy[0] <= cl.getWidth() &&
                    touchXy[1] >= 0 && touchXy[1] <= cl.getHeight()) {
                // This point is inside the cell layout
                return cl;
            }
        }
        return null;
    }

    class ReorderAlarmListener implements OnAlarmListener {
        final float[] dragViewCenter;
        final int minSpanX, minSpanY, spanX, spanY;
        final DragObject dragObject;
        final View child;

        public ReorderAlarmListener(float[] dragViewCenter, int minSpanX, int minSpanY, int spanX,
                int spanY, DragObject dragObject, View child) {
            this.dragViewCenter = dragViewCenter;
            this.minSpanX = minSpanX;
            this.minSpanY = minSpanY;
            this.spanX = spanX;
            this.spanY = spanY;
            this.child = child;
            this.dragObject = dragObject;
        }

        public void onAlarm(Alarm alarm) {
            int[] resultSpan = new int[2];
            mTargetCell = findNearestArea((int) mDragViewVisualCenter[0],
                    (int) mDragViewVisualCenter[1], minSpanX, minSpanY, mDragTargetLayout,
                    mTargetCell);
            mLastReorderX = mTargetCell[0];
            mLastReorderY = mTargetCell[1];

            mTargetCell = mDragTargetLayout.performReorder((int) mDragViewVisualCenter[0],
                (int) mDragViewVisualCenter[1], minSpanX, minSpanY, spanX, spanY,
                child, mTargetCell, resultSpan, CellLayout.MODE_DRAG_OVER);

            if (mTargetCell[0] < 0 || mTargetCell[1] < 0) {
                mDragTargetLayout.revertTempState();
            } else {
                setDragMode(DRAG_MODE_REORDER);
            }

            boolean resize = resultSpan[0] != spanX || resultSpan[1] != spanY;
            mDragTargetLayout.visualizeDropLocation(child, mOutlineProvider,
                mTargetCell[0], mTargetCell[1], resultSpan[0], resultSpan[1], resize, dragObject);
        }
    }

    @Override
    public void getHitRectRelativeToDragLayer(Rect outRect) {
        // We want the workspace to have the whole area of the display (it will find the correct
        // cell layout to drop to in the existing drag/drop logic.
        mLauncher.getDragLayer().getDescendantRectRelativeToSelf(this, outRect);
    }

    /**
     * Drop an item that didn't originate on one of the workspace screens.
     * It may have come from Launcher (e.g. from all apps or customize), or it may have
     * come from another app altogether.
     *
     * NOTE: This can also be called when we are outside of a drag event, when we want
     * to add an item to one of the workspace screens.
     */
    private void onDropExternal(final int[] touchXY, final CellLayout cellLayout, DragObject d) {
        ItemInfo info = d.dragInfo;
        int spanX = info.spanX;
        int spanY = info.spanY;
        if (mDragInfo != null) {
            spanX = mDragInfo.spanX;
            spanY = mDragInfo.spanY;
        }

        final int container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        final int screenId = getIdForScreen(cellLayout);
        if (screenId != getScreenIdForPageIndex(mCurrentPage)
                && !mLauncher.isInState(SPRING_LOADED)) {
            snapToPage(getPageIndexForScreenId(screenId));
        }

        if (info instanceof PendingAddItemInfo) {
            boolean findNearestVacantCell = true;
            final ItemInfo item = d.dragInfo;
            if (findNearestVacantCell) {
                int minSpanX = item.spanX;
                int minSpanY = item.spanY;
                if (item.minSpanX > 0 && item.minSpanY > 0) {
                    minSpanX = item.minSpanX;
                    minSpanY = item.minSpanY;
                }
                int[] resultSpan = new int[2];
                mTargetCell = cellLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], minSpanX, minSpanY, info.spanX, info.spanY,
                        null, mTargetCell, resultSpan, CellLayout.MODE_ON_DROP_EXTERNAL);

                item.spanX = resultSpan[0];
                item.spanY = resultSpan[1];
            }
        } else {
            // This is for other drag/drop cases, like dragging from All Apps
            mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);

            View view;

            switch (info.itemType) {
                case ITEM_TYPE_APPLICATION:
                    if (info instanceof AppInfo) {
                        // Came from all apps -- make a copy
                        info = ((AppInfo) info).makeWorkspaceItem();
                        d.dragInfo = info;
                    }
                    view = mLauncher.createShortcut(cellLayout, (WorkspaceItemInfo) info);
                    break;
                default:
                    throw new IllegalStateException("Unknown item type: " + info.itemType);
            }

            // First we find the cell nearest to point at which the item is
            // dropped, without any consideration to whether there is an item there.
            if (touchXY != null) {
                mTargetCell = findNearestArea(touchXY[0], touchXY[1], spanX, spanY,
                        cellLayout, mTargetCell);
            }

            if (touchXY != null) {
                // when dragging and dropping, just find the closest free spot
                mTargetCell = cellLayout.performReorder((int) mDragViewVisualCenter[0],
                        (int) mDragViewVisualCenter[1], 1, 1, 1, 1,
                        null, mTargetCell, null, CellLayout.MODE_ON_DROP_EXTERNAL);
            } else {
                cellLayout.findCellForSpan(mTargetCell, 1, 1);
            }
            // Add the item to DB before adding to screen ensures that the container and other
            // values of the info is properly updated.
            mLauncher.getModelWriter().addOrMoveItemInDatabase(info, container, screenId,
                    mTargetCell[0], mTargetCell[1]);

            addInScreen(view, container, screenId, mTargetCell[0], mTargetCell[1],
                    info.spanX, info.spanY);
            cellLayout.onDropChild(view);

            if (d.dragView != null) {
                // We wrap the animation call in the temporary set and reset of the current
                // cellLayout to its final transform -- this means we animate the drag view to
                // the correct final location.
                setFinalTransitionTransform();
                mLauncher.getDragLayer().animateViewIntoPosition(d.dragView, view, this);
                resetTransitionTransform();
            }
        }
    }

    public void setFinalTransitionTransform() {
        if (isSwitchingState()) {
            mCurrentScale = getScaleX();
            setScaleX(mStateTransitionAnimation.getFinalScale());
            setScaleY(mStateTransitionAnimation.getFinalScale());
        }
    }
    public void resetTransitionTransform() {
        if (isSwitchingState()) {
            setScaleX(mCurrentScale);
            setScaleY(mCurrentScale);
        }
    }

    /**
     * Calculate the nearest cell where the given object would be dropped.
     *
     * pixelX and pixelY should be in the coordinate system of layout
     */
    @Thunk int[] findNearestArea(int pixelX, int pixelY,
            int spanX, int spanY, CellLayout layout, int[] recycle) {
        return layout.findNearestArea(
                pixelX, pixelY, spanX, spanY, recycle);
    }

    void setup(DragController dragController) {
        mSpringLoadedDragController = new SpringLoadedDragController(mLauncher);
        mDragController = dragController;

        // hardware layers on children are enabled on startup, but should be disabled until
        // needed
        updateChildrenLayersEnabled();
    }

    /**
     * Called at the end of a drag which originated on the workspace.
     */
    public void onDropCompleted(final View target, final DragObject d,
            final boolean success) {
        if (success) {
            if (target != this && mDragInfo != null) {
                removeWorkspaceItem(mDragInfo.cell);
            }
        } else if (mDragInfo != null) {
            final CellLayout cellLayout = mLauncher.getCellLayout(
                    mDragInfo.screenId);
            if (cellLayout != null) {
                cellLayout.onDropChild(mDragInfo.cell);
            } else if (FeatureFlags.IS_DOGFOOD_BUILD) {
                throw new RuntimeException("Invalid state: cellLayout == null in "
                        + "Workspace#onDropCompleted. Please file a bug. ");
            }
        }
        View cell = getHomescreenIconByItemId(d.originalDragInfo.id);
        if (d.cancelled && cell != null) {
            cell.setVisibility(VISIBLE);
        }
        mDragInfo = null;
    }

    /**
     * For opposite operation. See {@link #addInScreen}.
     */
    public void removeWorkspaceItem(View v) {
        if (v instanceof DropTarget) {
            mDragController.removeDropTarget((DropTarget) v);
        }
    }

    public boolean isDropEnabled() {
        return true;
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        // We don't dispatch restoreInstanceState to our children using this code path.
        // Some pages will be restored immediately as their items are bound immediately, and
        // others we will need to wait until after their items are bound.
        mSavedStates = container;
    }

    public void restoreInstanceStateForChild(int child) {
        if (mSavedStates != null) {
            mRestoredPages.add(child);
            CellLayout cl = (CellLayout) getChildAt(child);
            if (cl != null) {
                cl.restoreInstanceState(mSavedStates);
            }
        }
    }

    public void restoreInstanceStateForRemainingPages() {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            if (!mRestoredPages.contains(i)) {
                restoreInstanceStateForChild(i);
            }
        }
        mRestoredPages.clear();
        mSavedStates = null;
    }

    @Override
    public boolean scrollLeft() {
        boolean result = false;
        if (!mIsSwitchingState && workspaceInScrollableState()) {
            result = super.scrollLeft();
        }
        return result;
    }

    @Override
    public boolean scrollRight() {
        boolean result = false;
        if (!mIsSwitchingState && workspaceInScrollableState()) {
            result = super.scrollRight();
        }
        return result;
    }

    /**
     * Returns a list of all the CellLayouts on the Homescreen.
     */
    private CellLayout[] getWorkspaceCellLayouts() {
        int screenCount = getChildCount();
        final CellLayout[] layouts = new CellLayout[screenCount];
        for (int screen = 0; screen < screenCount; screen++) {
            layouts[screen] = (CellLayout) getChildAt(screen);
        }
        return layouts;
    }

    /**
     * Similar to {@link #getFirstMatch} but optimized to finding a suitable view for the app close
     * animation.
     *
     * @param packageName The package name of the app to match.
     * @param user The user of the app to match.
     */
    public View getFirstMatchForAppClose(String packageName, UserHandle user) {
        final int curPage = getCurrentPage();
        final CellLayout currentPage = (CellLayout) getPageAt(curPage);
        final Workspace.ItemOperator packageAndUser = (ItemInfo info, View view) -> info != null
                && info.getTargetComponent() != null
                && TextUtils.equals(info.getTargetComponent().getPackageName(), packageName)
                && info.user.equals(user);
        final Workspace.ItemOperator packageAndUserAndApp = (ItemInfo info, View view) ->
                packageAndUser.evaluate(info, view) && info.itemType == ITEM_TYPE_APPLICATION;
        final Workspace.ItemOperator packageAndUserAndAppInFolder = (info, view) -> false;

        // Order: App icons, app in folder.
        if (ADAPTIVE_ICON_WINDOW_ANIM.get()) {
            return getFirstMatch(new CellLayout[] { currentPage },
                    packageAndUserAndApp, packageAndUserAndAppInFolder);
        } else {
            // Do not use Folder as a criteria, since it'll cause a crash when trying to draw
            // FolderAdaptiveIcon as the background.
            return getFirstMatch(new CellLayout[] { currentPage },
                    packageAndUserAndApp);
        }
    }

    public View getHomescreenIconByItemId(final int id) {
        return getFirstMatch((info, v) -> info != null && info.id == id);
    }

    public View getFirstMatch(final ItemOperator operator) {
        final View[] value = new View[1];
        mapOverItems(new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (operator.evaluate(info, v)) {
                    value[0] = v;
                    return true;
                }
                return false;
            }
        });
        return value[0];
    }

    /**
     * @param cellLayouts List of CellLayouts to scan, in order of preference.
     * @param operators List of operators, in order starting from best matching operator.
     * @return
     */
    private View getFirstMatch(CellLayout[] cellLayouts, final ItemOperator... operators) {
        // This array is filled with the first match for each operator.
        final View[] matches = new View[operators.length];
        // For efficiency, the outer loop should be CellLayout.
        for (CellLayout cellLayout : cellLayouts) {
            mapOverCellLayout(cellLayout, (info, v) -> {
                for (int i = 0; i < operators.length; ++i) {
                    if (matches[i] == null && operators[i].evaluate(info, v)) {
                        matches[i] = v;
                        if (i == 0) {
                            // We can return since this is the best match possible.
                            return true;
                        }
                    }
                }
                return false;
            });
            if (matches[0] != null) {
                break;
            }
        }
        for (View match : matches) {
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    void clearDropTargets() {
        mapOverItems(new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View v) {
                if (v instanceof DropTarget) {
                    mDragController.removeDropTarget((DropTarget) v);
                }
                // not done, process all the shortcuts
                return false;
            }
        });
    }

    /**
     * Removes items that match the {@param matcher}. When applications are removed
     * as a part of an update, this is called to ensure that other widgets and application
     * shortcuts are not removed.
     */
    public void removeItemsByMatcher(final ItemInfoMatcher matcher) {
        for (final CellLayout layoutParent: getWorkspaceCellLayouts()) {

            IntSparseArrayMap<View> idToViewMap = new IntSparseArrayMap<>();
            ArrayList<ItemInfo> items = new ArrayList<>();

            for (ItemInfo itemToRemove : matcher.filterItemInfos(items)) {
                View child = idToViewMap.get(itemToRemove.id);

                if (child != null) {
                    // Note: We can not remove the view directly from CellLayoutChildren as this
                    // does not re-mark the spaces as unoccupied.
                    layoutParent.removeViewInLayout(child);
                    if (child instanceof DropTarget) {
                        mDragController.removeDropTarget((DropTarget) child);
                    }
                }  // The item may belong to a folder.

            }
        }

        // Strip all the empty screens
        stripEmptyScreens();
    }

    public interface ItemOperator {
        /**
         * Process the next itemInfo, possibly with side-effect on the next item.
         *
         * @param info info for the shortcut
         * @param view view for the shortcut
         * @return true if done, false to continue the map
         */
        boolean evaluate(ItemInfo info, View view);
    }

    /**
     * Map the operator over the shortcuts and widgets, return the first-non-null value.
     *
     * @param op the operator to map over the shortcuts
     */
    public void mapOverItems(ItemOperator op) {
        for (CellLayout layout : getWorkspaceCellLayouts()) {
            if (mapOverCellLayout(layout, op)) {
                return;
            }
        }
    }

    private boolean mapOverCellLayout(CellLayout layout, ItemOperator op) {
        // TODO(b/128460496) Potential race condition where layout is not yet loaded
        if (layout == null) {
            return false;
        }
        return false;
    }

    void updateShortcuts(ArrayList<WorkspaceItemInfo> shortcuts) {
        final HashSet<WorkspaceItemInfo> updates = new HashSet<>(shortcuts);
        ItemOperator op = (info, v) -> {
            if (v instanceof BubbleTextView && updates.contains(info)) {
                WorkspaceItemInfo si = (WorkspaceItemInfo) info;
                BubbleTextView shortcut = (BubbleTextView) v;
                Drawable oldIcon = shortcut.getIcon();
                boolean oldPromiseState = (oldIcon instanceof PreloadIconDrawable)
                        && ((PreloadIconDrawable) oldIcon).hasNotCompleted();
                shortcut.applyFromWorkspaceItem(si, si.isPromise() != oldPromiseState);
            }
            // Iterate all items
            return false;
        };

        mapOverItems(op);
    }

    public void removeAbandonedPromise(String packageName, UserHandle user) {
        HashSet<String> packages = new HashSet<>(1);
        packages.add(packageName);
        ItemInfoMatcher matcher = ItemInfoMatcher.ofPackages(packages, user);
        mLauncher.getModelWriter().deleteItemsFromDatabase(matcher);
        removeItemsByMatcher(matcher);
    }

    public void updateRestoreItems(final HashSet<ItemInfo> updates) {
        ItemOperator op = (info, v) -> {
            if (info instanceof WorkspaceItemInfo && v instanceof BubbleTextView
                    && updates.contains(info)) {
                ((BubbleTextView) v).applyPromiseState(false /* promiseStateChanged */);
            }
            return false;
        };
        mapOverItems(op);
    }

    public boolean isOverlayShown() {
        return mOverlayShown;
    }

    void moveToDefaultScreen() {
        int page = DEFAULT_PAGE;
        if (!workspaceInModalState() && getNextPage() != page) {
            snapToPage(page);
        }
        View child = getChildAt(page);
        if (child != null) {
            child.requestFocus();
        }
    }

    @Override
    public int getExpectedHeight() {
        return getMeasuredHeight() <= 0 || !mIsLayoutValid
                ? mLauncher.getDeviceProfile().heightPx : getMeasuredHeight();
    }

    @Override
    public int getExpectedWidth() {
        return getMeasuredWidth() <= 0 || !mIsLayoutValid
                ? mLauncher.getDeviceProfile().widthPx : getMeasuredWidth();
    }

    @Override
    protected boolean canAnnouncePageDescription() {
        // Disable announcements while overscrolling potentially to overlay screen because if we end
        // up on the overlay screen, it will take care of announcing itself.
        return Float.compare(mOverlayTranslation, 0f) == 0;
    }

    @Override
    protected String getCurrentPageDescription() {
        int page = (mNextPage != INVALID_PAGE) ? mNextPage : mCurrentPage;
        return getPageDescription(page);
    }

    private String getPageDescription(int page) {
        int nScreens = getChildCount();
        int extraScreenId = mScreenOrder.indexOf(EXTRA_EMPTY_SCREEN_ID);
        if (extraScreenId >= 0 && nScreens > 1) {
            if (page == extraScreenId) {
                return getContext().getString(R.string.workspace_new_page);
            }
            nScreens--;
        }
        if (nScreens == 0) {
            // When the workspace is not loaded, we do not know how many screen will be bound.
            return getContext().getString(R.string.all_apps_home_button_label);
        }
        return getContext().getString(R.string.workspace_scroll_format, page + 1, nScreens);
    }

    @Override
    public void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent) {
        target.gridX = info.cellX;
        target.gridY = info.cellY;
        target.pageIndex = getCurrentPage();
        targetParent.containerType = ContainerType.WORKSPACE;
        if (info.container >= 0) {
            targetParent.containerType = ContainerType.FOLDER;
        }
    }

    private class StateTransitionListener extends AnimatorListenerAdapter
            implements AnimatorUpdateListener {

        private final LauncherState mToState;

        StateTransitionListener(LauncherState toState) {
            mToState = toState;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator anim) {
            mTransitionProgress = anim.getAnimatedFraction();
        }

        @Override
        public void onAnimationStart(Animator animation) {
            onStartStateTransition(mToState);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            onEndStateTransition();
        }
    }
}
