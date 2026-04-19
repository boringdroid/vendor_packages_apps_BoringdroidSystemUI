// Copyright (C) 2026 The BoringDroid Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
package com.boringdroid.systemui.overview

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.unit.dp
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.boringdroid.systemui.theme.BdExpressiveMaterialTheme
import com.boringdroid.systemui.theme.BdMotion

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
    private var cardClickListener: ((RecentAppTask) -> Unit)? = null
    private var cardCloseListener: ((RecentAppTask) -> Unit)? = null
    private val composeView: ComposeView = ComposeView(context)

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

    fun positionOfTaskId(taskId: Int): Int = tasks.indexOfFirst { it.taskId == taskId }

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
            BdExpressiveMaterialTheme {
                OverviewPanel(
                    tasks = tasks,
                    snapshotVersion = snapshotVersion,
                    onCardClick = ::onCardClick,
                    onCardClose = ::onCardClose,
                )
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
    onCardClick: (RecentAppTask) -> Unit,
    onCardClose: (RecentAppTask) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    Box(
        modifier =
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).semantics {
                testTagsAsResourceId = true
                testTag = ID + "overview_root"
            }
    ) {
        AnimatedVisibility(
            visible = visible,
            enter =
                fadeIn(
                    animationSpec = tween(BdMotion.durationShort4, easing = BdMotion.easingStandard)
                ) +
                    scaleIn(
                        animationSpec =
                            tween(
                                BdMotion.durationMedium2,
                                easing = BdMotion.easingEmphasizedDecelerate,
                            ),
                        initialScale = 0.98f,
                    ),
        ) {
            OverviewContent(
                tasks = tasks,
                snapshotVersion = snapshotVersion,
                onCardClick = onCardClick,
                onCardClose = onCardClose,
            )
        }
    }
}

@Composable
private fun OverviewContent(
    tasks: List<RecentAppTask>,
    snapshotVersion: Int,
    onCardClick: (RecentAppTask) -> Unit,
    onCardClose: (RecentAppTask) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 48.dp, bottom = 104.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OverviewHead()
        Spacer(modifier = Modifier.height(28.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(items = tasks, key = { it.taskId }) { task ->
                    RecentCard(
                        task = task,
                        snapshotVersion = snapshotVersion,
                        onClick = { onCardClick(task) },
                        onClose = { onCardClose(task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewHead() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Recents",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Swipe across · Esc to exit",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RecentCard(
    task: RecentAppTask,
    snapshotVersion: Int,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val pm = remember(context) { context.packageManager }
    val appEntry = remember(task.packageName, pm) { resolveAppEntry(pm, task.packageName) }
    val thumbnail: Bitmap? =
        remember(task.taskId, snapshotVersion) { loadThumbnail(task.taskId) }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier.size(width = 320.dp, height = 200.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceContainerLow)
                .clickable(onClick = onClick)
    ) {
        ThumbnailSurface(bitmap = thumbnail)
        CloseAffordance(
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            onClose = onClose,
        )
        CardFooter(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            icon = appEntry.icon,
            label = appEntry.label,
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
    IconButton(
        onClick = onClose,
        modifier =
            modifier
                .size(28.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = 0.4f))
                .semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "overview_card_close"
                },
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Close",
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun CardFooter(modifier: Modifier, icon: Drawable?, label: String) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier.size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "overview_card_icon"
                    },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.weight(1f).semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "overview_card_label"
                    text = AnnotatedString(label)
                },
        )
    }
}

@Composable
private fun AppIcon(drawable: Drawable?, contentDescription: String) {
    val density = LocalDensity.current
    val sizeDp = 18
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

private fun resolveAppEntry(pm: PackageManager, packageName: String): AppEntry {
    return try {
        val info = pm.getApplicationInfo(packageName, 0)
        AppEntry(pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
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
