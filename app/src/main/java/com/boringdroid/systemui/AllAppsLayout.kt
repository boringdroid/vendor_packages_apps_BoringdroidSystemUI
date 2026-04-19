package com.boringdroid.systemui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boringdroid.systemui.theme.BdExpressiveMaterialTheme

/**
 * See the same comment in [com.boringdroid.systemui.taskbar.Taskbar]: prefix every
 * testTag with `pkg:id/` so UiAutomator's `By.res(pkg, id)` matches the string
 * Compose writes into `AccessibilityNodeInfo.setViewIdResourceName` via
 * `testTagsAsResourceId`.
 */
private const val ID = "com.boringdroid.systemui:id/"

class AllAppsLayout
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    FrameLayout(context, attrs, defStyle) {
    private var apps: List<AppData> by mutableStateOf(emptyList())
    private var handler: Handler? = null
    private val composeView: ComposeView = ComposeView(context)

    fun setData(apps: List<AppData?>?) {
        this.apps = apps.orEmpty().filterNotNull()
    }

    fun setHandler(handler: Handler?) {
        this.handler = handler
    }

    private fun launchApp(appData: AppData) {
        val componentName = appData.componentName ?: return
        val intent = Intent()
        intent.component = componentName
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        val h = handler
        if (h != null) {
            h.sendEmptyMessage(HandlerConstant.H_DISMISS_ALL_APPS_WINDOW)
        } else {
            Log.e(TAG, "Won't send dismiss event because of handler is null")
        }
    }

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        composeView.setContent {
            BdExpressiveMaterialTheme { AllAppsPanel(apps, ::launchApp) }
        }
        addView(composeView)
    }

    companion object {
        private const val TAG = "AllAppsLayout"
        private const val NUMBER_OF_COLUMNS = 5
    }
}

@Composable
private fun AllAppsPanel(apps: List<AppData>, onAppClick: (AppData) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxSize().background(colors.surfaceContainer),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = apps, key = { it.componentName?.flattenToShortString() ?: it.name ?: "" }) {
                appData ->
                AppTile(appData, onClick = { onAppClick(appData) })
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun AppTile(appData: AppData, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = appData.name ?: ""
    Column(
        modifier =
            Modifier.size(70.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 5.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(appData.icon, contentDescription = label)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(top = 4.dp).semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "app_info_name"
                    text = AnnotatedString(label)
                },
        )
    }
}

@Composable
private fun AppIcon(drawable: Drawable?, contentDescription: String) {
    val density = LocalDensity.current
    if (drawable == null) {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(30.dp),
        )
        return
    }
    val px = with(density) { 30.dp.toPx().toInt().coerceAtLeast(1) }
    val bitmap =
        try {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, px, px)
            drawable.draw(canvas)
            bmp
        } catch (t: Throwable) {
            null
        }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = Modifier.size(30.dp),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(30.dp),
        )
    }
}
