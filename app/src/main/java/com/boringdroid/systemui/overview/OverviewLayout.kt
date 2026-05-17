// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.overview

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.boringdroid.systemui.theme.BdExpressiveMaterialTheme
import com.boringdroid.systemui.theme.BdMotion
import com.boringdroid.systemui.theme.LocalThemedIconLoader
import com.boringdroid.systemui.theme.ThemedIconLoader

/**
 * UiAutomator's `By.res(pkg, id)` matches the string Compose writes into
 * `AccessibilityNodeInfo.setViewIdResourceName` via `testTagsAsResourceId`. Every testTag must be
 * `pkg:id/`-prefixed so the existing OverviewTest keeps resolving.
 */
private const val ID = "com.boringdroid.systemui:id/"

/**
 * Compose-hosting `FrameLayout` used as the root of the Overview window. Replaces the legacy
 * RecyclerView-based layout. The data is mutated via [setTasks] / [setCallbacks]; snapshot refresh
 * for a specific task id is triggered via [bumpSnapshotVersion] so Compose can re-read
 * [ActivityManagerWrapper.getTaskThumbnail].
 */
class OverviewLayout
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    FrameLayout(context, attrs, defStyle) {
    private var tasks: List<RecentAppTask> by mutableStateOf(emptyList())
    private var snapshotVersion: Int by mutableStateOf(0)
    private var themedIconLoader: ThemedIconLoader? by mutableStateOf(null)
    private var cardClickListener: ((RecentAppTask) -> Unit)? = null
    private var cardCloseListener: ((RecentAppTask) -> Unit)? = null
    private val composeView: ComposeView = ComposeView(context)

    /**
     * Drives the Mission-Control-style expo animation. `true` → cards animate from their
     * source window bounds to the grid; `false` → cards animate back to their source bounds,
     * and [exitCompleteCallback] fires once the flight is done so [OverviewWindow] can
     * `removeView` only after the pixels have left the screen.
     */
    private var expanded: Boolean by mutableStateOf(false)
    private var exitCompleteCallback: (() -> Unit)? = null

    fun setData(newTasks: List<RecentAppTask>) {
        tasks = newTasks
    }

    fun setCallbacks(
        onCardClick: (RecentAppTask) -> Unit,
        onCardClose: (RecentAppTask) -> Unit,
    ) {
        cardClickListener = onCardClick
        cardCloseListener = onCardClose
    }

    fun bumpSnapshotVersion() {
        snapshotVersion++
    }

    fun setIconLoader(loader: ThemedIconLoader?) {
        themedIconLoader = loader
    }

    fun positionOfTaskId(taskId: Int): Int = tasks.indexOfFirst { it.taskId == taskId }

    /** Trigger the entrance animation — cards fly from their real windows to the grid. */
    fun beginShow() {
        exitCompleteCallback = null
        expanded = true
    }

    /**
     * Trigger the exit animation — cards fly back to their source window positions, then
     * [onComplete] fires once the animation lands so the attaching window can be removed.
     */
    fun beginHide(onComplete: () -> Unit) {
        exitCompleteCallback = onComplete
        expanded = false
    }

    private fun onCardClick(task: RecentAppTask) {
        cardClickListener?.invoke(task)
    }

    private fun onCardClose(task: RecentAppTask) {
        cardCloseListener?.invoke(task)
    }

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        composeView.setContent {
            CompositionLocalProvider(LocalThemedIconLoader provides themedIconLoader) {
                BdExpressiveMaterialTheme {
                    OverviewPanel(
                        tasks = tasks,
                        snapshotVersion = snapshotVersion,
                        expanded = expanded,
                        onExitAnimationComplete = {
                            exitCompleteCallback?.invoke()
                            exitCompleteCallback = null
                        },
                        onCardClick = ::onCardClick,
                        onCardClose = ::onCardClose,
                    )
                }
            }
        }
        addView(composeView)
    }

    companion object {
        private const val TAG = "OverviewLayout"
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun OverviewPanel(
    tasks: List<RecentAppTask>,
    snapshotVersion: Int,
    expanded: Boolean,
    onExitAnimationComplete: () -> Unit,
    onCardClick: (RecentAppTask) -> Unit,
    onCardClose: (RecentAppTask) -> Unit,
) {
    // Drives the enter/exit animation. 0f = cards sit at their real window rect; 1f =
    // cards sit at their grid position. The backdrop alpha follows the same value so the
    // dim lifts in sync with the windows flying into place.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        val target = if (expanded) 1f else 0f
        progress.animateTo(
            targetValue = target,
            animationSpec =
                tween(
                    durationMillis = if (expanded) 360 else 280,
                    easing = BdMotion.easingEmphasizedDecelerate,
                ),
        )
        if (!expanded) onExitAnimationComplete()
    }
    // macOS Mission Control-style backdrop: a darker, near-opaque black with a subtle
    // vertical gradient fade at the top so the top edge reads as a softer surface rather
    // than a hard mask. The dim scales with `progress` so the desktop fades under the
    // cards as they fly in (and reappears as they fly back out).
    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f * progress.value))
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.White.copy(alpha = 0.04f * progress.value),
                                Color.Transparent,
                                Color.Transparent,
                            ),
                    )
                )
                .semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "overview_root"
                }
    ) {
        OverviewContent(
            tasks = tasks,
            snapshotVersion = snapshotVersion,
            progress = progress.value,
            onCardClick = onCardClick,
            onCardClose = onCardClose,
        )
    }
}

/**
 * Pixel dimensions for a single grid card, given a task's real window bounds and the
 * common scale used across the whole row. `realWidth` / `realHeight` are the task's
 * on-screen window size; we shrink both by [scale] so narrow windows stay narrow and
 * wide windows stay wide — the Mission-Control "common scale" effect. A null window
 * bounds falls back to a default 16:10 landscape card.
 */
private data class CardSize(val widthDp: Float, val heightDp: Float)

private fun computeCommonScaleCardSizes(
    tasks: List<RecentAppTask>,
    densityPxPerDp: Float,
    maxCardWidthDp: Float,
    maxCardHeightDp: Float,
): Map<Int, CardSize> {
    if (tasks.isEmpty()) return emptyMap()
    // Find the biggest real window across the row and scale everything by the same factor
    // so portrait and landscape neighbours stay in proportion.
    var maxW = 0
    var maxH = 0
    tasks.forEach { t ->
        t.windowBounds?.let {
            if (it.width() > maxW) maxW = it.width()
            if (it.height() > maxH) maxH = it.height()
        }
    }
    if (maxW == 0 || maxH == 0) {
        // No bounds info at all — fall back to the old fixed 380×240 card.
        return tasks.associate { it.taskId to CardSize(380f, 240f) }
    }
    val maxWDp = maxW / densityPxPerDp
    val maxHDp = maxH / densityPxPerDp
    val scale = minOf(maxCardWidthDp / maxWDp, maxCardHeightDp / maxHDp).coerceAtMost(1f)
    return tasks.associate { t ->
        val bounds = t.windowBounds
        if (bounds == null) {
            t.taskId to CardSize(maxCardWidthDp, maxCardWidthDp * 10f / 16f)
        } else {
            val wDp = bounds.width() / densityPxPerDp
            val hDp = bounds.height() / densityPxPerDp
            t.taskId to CardSize(wDp * scale, hDp * scale)
        }
    }
}

@Composable
private fun OverviewContent(
    tasks: List<RecentAppTask>,
    snapshotVersion: Int,
    progress: Float,
    onCardClick: (RecentAppTask) -> Unit,
    onCardClose: (RecentAppTask) -> Unit,
) {
    val density = LocalDensity.current
    val cardSizes =
        remember(tasks) {
            computeCommonScaleCardSizes(
                tasks = tasks,
                densityPxPerDp = density.density,
                maxCardWidthDp = 420f,
                maxCardHeightDp = 260f,
            )
        }
    // No header text — macOS Mission Control leaves the space empty so the thumbnails
    // do the talking. `overview_root` stays on the backdrop Box above.
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 96.dp, bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Regular Row (not LazyRow) so each card can have a per-task width without
            // fighting the LazyRow's caching, and so every card is composed on the first
            // frame — the flight animation reads each card's grid position on first layout.
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tasks.forEach { task ->
                    val size = cardSizes[task.taskId] ?: CardSize(380f, 240f)
                    RecentCard(
                        task = task,
                        snapshotVersion = snapshotVersion,
                        gridSize = size,
                        progress = progress,
                        onClick = { onCardClick(task) },
                        onClose = { onCardClose(task) },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RecentCard(
    task: RecentAppTask,
    snapshotVersion: Int,
    gridSize: CardSize,
    progress: Float,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val pm = remember(context) { context.packageManager }
    val themedIconLoader = LocalThemedIconLoader.current
    val appEntry =
        remember(task.packageName, pm, themedIconLoader) {
            resolveAppEntry(pm, task.packageName, themedIconLoader)
        }
    val thumbnail: Bitmap? =
        remember(task.taskId, snapshotVersion) { loadThumbnail(task.taskId) }
    val density = LocalDensity.current
    val densityPx = density.density

    // Remember where our thumbnail card lands in the window so we can drive the flight
    // transform from the laid-out position. `positionInWindow` matches on-screen coords
    // because the Overview window is pinned at Gravity.TOP / y=0, full-width.
    var thumbTopLeftPx by remember { mutableStateOf(Offset.Zero) }

    // Real window bounds (source of the flight). If the task didn't report bounds, skip
    // the flight — the card just fades in/out instead.
    val realBoundsPx = task.windowBounds
    val gridWidthPx = gridSize.widthDp * densityPx
    val gridHeightPx = gridSize.heightDp * densityPx

    val (scaleX, scaleY, translationX, translationY) =
        if (realBoundsPx != null && gridWidthPx > 0f && gridHeightPx > 0f) {
            val realScaleX = realBoundsPx.width() / gridWidthPx
            val realScaleY = realBoundsPx.height() / gridHeightPx
            val realTx = realBoundsPx.left - thumbTopLeftPx.x
            val realTy = realBoundsPx.top - thumbTopLeftPx.y
            CardTransform(
                scaleX = lerpF(realScaleX, 1f, progress),
                scaleY = lerpF(realScaleY, 1f, progress),
                translationX = lerpF(realTx, 0f, progress),
                translationY = lerpF(realTy, 0f, progress),
            )
        } else {
            // No bounds: just a gentle scale-in from 96%.
            val s = lerpF(0.96f, 1f, progress)
            CardTransform(scaleX = s, scaleY = s, translationX = 0f, translationY = 0f)
        }

    // macOS App Expose: stacked column — thumbnail on top, [icon · label] centred below.
    // Column is clickable so tapping either the thumbnail or the caption brings the task
    // to front (keeps `OverviewTest.cardClick…` passing — that test taps the label and
    // expects it to propagate).
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            Modifier.width(gridSize.widthDp.dp).clickable(onClick = onClick),
    ) {
        ThumbnailCard(
            thumbnail = thumbnail,
            widthDp = gridSize.widthDp,
            heightDp = gridSize.heightDp,
            onClose = onClose,
            modifier =
                Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    this.scaleX = scaleX
                    this.scaleY = scaleY
                    this.translationX = translationX
                    this.translationY = translationY
                }
                    .onGloballyPositioned { coords ->
                        thumbTopLeftPx = coords.positionInWindow()
                    },
        )
        // Caption fades in after the card has flown most of the way into the grid — it
        // doesn't exist in the source window state, so it would look wrong at progress=0.
        CardCaption(
            icon = appEntry.icon,
            label = appEntry.label,
            alpha = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f),
        )
    }
}

private data class CardTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
)

private fun lerpF(start: Float, stop: Float, t: Float): Float = start + (stop - start) * t

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ThumbnailCard(
    thumbnail: Bitmap?,
    widthDp: Float,
    heightDp: Float,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier =
            modifier
                .size(width = widthDp.dp, height = heightDp.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = shape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                )
                .clip(shape)
                .background(colors.surfaceContainerLow)
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f), shape = shape)
    ) {
        ThumbnailSurface(bitmap = thumbnail)
        CloseAffordance(
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            onClose = onClose,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun BoxScope.ThumbnailSurface(bitmap: Bitmap?) {
    val colors = MaterialTheme.colorScheme
    val base =
        Modifier.fillMaxSize().semantics {
            testTagsAsResourceId = true
            testTag = ID + "overview_card_thumbnail"
        }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
    } else {
        Box(modifier = base.background(colors.surfaceContainerHighest))
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun CloseAffordance(modifier: Modifier, onClose: () -> Unit) {
    // Subtle macOS-style close button: small, low-contrast, only pops on hover. We keep
    // it at a low-alpha default because we don't wire hover detection here; the icon is
    // still discoverable but doesn't fight the thumbnail for attention.
    IconButton(
        onClick = onClose,
        modifier =
            modifier
                .size(24.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(percent = 50),
                )
                .semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "overview_card_close"
                },
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Close",
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun CardCaption(icon: Drawable?, label: String, alpha: Float = 1f) {
    // Icon + label centred below the thumbnail, the way macOS Mission Control labels
    // windows. Larger icon (36dp) for prominence, titleMedium text for readability. Alpha
    // is driven by the flight progress — the caption doesn't belong at progress=0 where
    // the "card" is overlapping the actual window.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 8.dp).graphicsLayer { this.alpha = alpha },
    ) {
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            AppIcon(icon, label, sizeDp = 36)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier =
                Modifier.semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "overview_card_label"
                    text = AnnotatedString(label)
                },
        )
    }
}

@Composable
private fun AppIcon(drawable: Drawable?, contentDescription: String, sizeDp: Int = 18) {
    val density = LocalDensity.current
    if (drawable == null) {
        FallbackAppIcon(Icons.Filled.Apps, contentDescription, sizeDp)
        return
    }
    val px = with(density) { sizeDp.dp.toPx().toInt().coerceAtLeast(1) }
    val bmp =
        try {
            val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            drawable.setBounds(0, 0, px, px)
            drawable.draw(canvas)
            out
        } catch (t: Throwable) {
            null
        }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier.size(sizeDp.dp),
        )
    } else {
        FallbackAppIcon(Icons.Filled.Apps, contentDescription, sizeDp)
    }
}

@Composable
private fun FallbackAppIcon(icon: ImageVector, contentDescription: String, sizeDp: Int) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = Color.White,
        modifier = Modifier.size(sizeDp.dp),
    )
}

private data class AppEntry(val label: String, val icon: Drawable?)

private fun resolveAppEntry(
    pm: PackageManager,
    packageName: String,
    themedIconLoader: ThemedIconLoader?,
): AppEntry {
    return try {
        val info = pm.getApplicationInfo(packageName, 0)
        val component = ComponentName(packageName, "")
        val raw = pm.getApplicationIcon(info)
        val icon = themedIconLoader?.load(component, raw) ?: raw
        AppEntry(pm.getApplicationLabel(info).toString(), icon)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w("OverviewLayout", "resolveAppEntry: missing package $packageName", e)
        AppEntry(packageName, null)
    }
}

private fun loadThumbnail(taskId: Int): Bitmap? {
    return try {
        ActivityManagerWrapper.getInstance()
            .getTaskThumbnail(taskId, /* isLowResolution= */ true)
            .thumbnail
    } catch (e: SecurityException) {
        Log.w("OverviewLayout", "loadThumbnail: denied taskId=$taskId", e)
        null
    }
}
