package com.boringdroid.systemui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.UserManager
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boringdroid.systemui.theme.BdExpressiveMaterialTheme
import com.boringdroid.systemui.theme.BdMotion
import com.boringdroid.systemui.theme.BdShape

/**
 * UiAutomator's `By.res(pkg, id)` matches the string Compose writes into
 * `AccessibilityNodeInfo.setViewIdResourceName` via `testTagsAsResourceId`. Every testTag must be
 * `pkg:id/`-prefixed so the existing test surface keeps resolving.
 */
private const val ID = "com.boringdroid.systemui:id/"

/** First N apps from the alphabetical list act as the "Pinned" row of the M3 Expressive design. */
private const val PINNED_COUNT = 12

/** Apps shown in the "Recommended" row directly under the pinned grid. */
private const val RECOMMENDED_COUNT = 6

class AllAppsLayout
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    FrameLayout(context, attrs, defStyle) {
    private var apps: List<AppData> by mutableStateOf(emptyList())
    // Named `dispatchHandler` instead of `handler` because `View.getHandler()` already exists and
    // a Kotlin `var handler` would clash with that JVM signature on the parent class.
    private var dispatchHandler: Handler? = null
    private val composeView: ComposeView = ComposeView(context)

    fun setData(apps: List<AppData?>?) {
        this.apps = apps.orEmpty().filterNotNull()
    }

    fun setDispatchHandler(handler: Handler?) {
        dispatchHandler = handler
    }

    private fun launchApp(appData: AppData) {
        val componentName = appData.componentName ?: return
        val intent = Intent()
        intent.component = componentName
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        dispatchDismiss()
    }

    private fun dispatchDismiss() {
        val h = dispatchHandler
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
            BdExpressiveMaterialTheme {
                StartMenuPanel(
                    apps = apps,
                    userName = resolveUserName(context),
                    onAppClick = ::launchApp,
                    onDismiss = ::dispatchDismiss,
                )
            }
        }
        addView(composeView)
    }

    companion object {
        private const val TAG = "AllAppsLayout"

        private fun resolveUserName(context: Context): String {
            val um = context.getSystemService(Context.USER_SERVICE) as? UserManager
            val name = um?.userName?.takeIf { it.isNotBlank() }
            return name ?: "Boringdroid"
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun StartMenuPanel(
    apps: List<AppData>,
    userName: String,
    onAppClick: (AppData) -> Unit,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(animationSpec = tween(BdMotion.durationShort4, easing = BdMotion.easingStandard)) +
                scaleIn(
                    animationSpec =
                        tween(BdMotion.durationMedium2, easing = BdMotion.easingSpringFast),
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                ) +
                slideInVertically(
                    animationSpec =
                        tween(BdMotion.durationMedium2, easing = BdMotion.easingEmphasizedDecelerate),
                    initialOffsetY = { it / 8 },
                ),
    ) {
        StartMenuContent(
            apps = apps,
            userName = userName,
            onAppClick = onAppClick,
            onDismiss = onDismiss,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun StartMenuContent(
    apps: List<AppData>,
    userName: String,
    onAppClick: (AppData) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val trimmedQuery = query.trim()
    val isSearching = trimmedQuery.isNotEmpty()
    val filtered =
        remember(apps, trimmedQuery) {
            if (trimmedQuery.isEmpty()) apps
            else apps.filter { (it.name ?: "").contains(trimmedQuery, ignoreCase = true) }
        }
    val pinned = remember(apps) { apps.take(PINNED_COUNT) }
    val recommended =
        remember(apps) { apps.drop(PINNED_COUNT).take(RECOMMENDED_COUNT) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // Plugin-owned windows can race Compose vs. WindowManager focus propagation; swallow so
        // the panel still renders if the field isn't attached yet.
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {}
    }
    Column(
        modifier = Modifier.fillMaxSize().background(colors.surfaceContainer).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SearchPill(
            query = query,
            onQueryChange = { query = it },
            focusRequester = focusRequester,
            onFocusGained = { keyboardController?.hide() },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isSearching) {
                FilteredAppsGrid(apps = filtered, onAppClick = onAppClick)
            } else {
                Column(
                    modifier =
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SectionBlock(title = "Pinned", linkText = "All apps") {
                        PinnedGrid(apps = pinned, onAppClick = onAppClick)
                    }
                    SectionBlock(title = "Recommended", linkText = "More") {
                        RecommendedGrid(apps = recommended, onAppClick = onAppClick)
                    }
                }
            }
        }
        UserRail(
            userName = userName,
            onLock = {
                Log.i("AllAppsLayout", "User rail: lock")
                onDismiss()
            },
            onSignOut = {
                Log.i("AllAppsLayout", "User rail: sign out")
                onDismiss()
            },
            onPower = {
                Log.i("AllAppsLayout", "User rail: power")
                onDismiss()
            },
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onFocusGained: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            Modifier.fillMaxWidth()
                .height(52.dp)
                .focusRequester(focusRequester)
                // Boringdroid assumes a HW keyboard; on a touch-only emulator Compose's default
                // `SHOW_SOFT_INPUT_BY_INSETS_API` fires on every focus gain and the IME occludes the
                // app grid. Suppress it so the panel is immediately usable — typing on a HW keyboard
                // still routes via the focused field.
                .onFocusChanged { if (it.isFocused) onFocusGained() }
                .semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "search_field_input"
                },
        placeholder = {
            Text(
                text = "Search apps, files, settings",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = colors.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Box(
                    modifier =
                        Modifier.size(40.dp)
                            .clickable { onQueryChange("") }
                            .semantics {
                                testTagsAsResourceId = true
                                testTag = ID + "search_field_clear"
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear search",
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        shape = BdShape.pill,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainerHigh,
                disabledContainerColor = colors.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
    )
}

@Composable
private fun SectionBlock(
    title: String,
    linkText: String,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = linkText,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        content()
    }
}

@Composable
private fun PinnedGrid(apps: List<AppData>, onAppClick: (AppData) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        userScrollEnabled = false,
    ) {
        items(items = apps, key = { tileKey(it) }) { app -> AppTile(app, onClick = { onAppClick(app) }) }
    }
}

@Composable
private fun RecommendedGrid(apps: List<AppData>, onAppClick: (AppData) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        userScrollEnabled = false,
    ) {
        items(items = apps, key = { tileKey(it) }) { app -> RecommendedRow(app, onClick = { onAppClick(app) }) }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun FilteredAppsGrid(apps: List<AppData>, onAppClick: (AppData) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = apps, key = { tileKey(it) }) { app -> AppTile(app, onClick = { onAppClick(app) }) }
    }
}

private fun tileKey(app: AppData): String =
    app.componentName?.flattenToShortString() ?: app.name ?: ""

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun AppTile(appData: AppData, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = appData.name ?: ""
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier.size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(appData.icon, contentDescription = label, sizeDp = 30)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.fillMaxWidth().semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "app_info_name"
                    text = AnnotatedString(label)
                },
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun RecommendedRow(appData: AppData, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = appData.name ?: ""
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier.size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(appData.icon, contentDescription = label, sizeDp = 20)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.semantics {
                        testTagsAsResourceId = true
                        testTag = ID + "app_info_name"
                        text = AnnotatedString(label)
                    },
            )
            Text(
                text = "Recently used",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun UserRail(
    userName: String,
    onLock: () -> Unit,
    onSignOut: () -> Unit,
    onPower: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = 12.dp)
                .semantics {
                    testTagsAsResourceId = true
                    testTag = ID + "start_menu_user_rail"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF8B6BE8), Color(0xFFEFBCE4))
                            )
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = userInitials(userName),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "boringdroid",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            UserRailButton(
                icon = Icons.Filled.Lock,
                contentDescription = "Lock",
                testTagId = "start_menu_lock",
                onClick = onLock,
            )
            UserRailButton(
                icon = Icons.Filled.Logout,
                contentDescription = "Sign out",
                testTagId = "start_menu_signout",
                onClick = onSignOut,
            )
            UserRailButton(
                icon = Icons.Filled.PowerSettingsNew,
                contentDescription = "Power",
                testTagId = "start_menu_power",
                onClick = onPower,
            )
        }
    }
    Spacer(modifier = Modifier.height(0.dp))
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun UserRailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTagId: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier.size(40.dp).semantics {
                testTagsAsResourceId = true
                testTag = ID + testTagId
            },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun userInitials(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "B"
    val parts = trimmed.split(" ", limit = 2).filter { it.isNotEmpty() }
    val first = parts.getOrNull(0)?.take(1) ?: ""
    val second = parts.getOrNull(1)?.take(1) ?: ""
    val combined = (first + second).uppercase()
    return combined.ifEmpty { trimmed.take(1).uppercase() }
}

@Composable
private fun AppIcon(drawable: Drawable?, contentDescription: String, sizeDp: Int = 30) {
    val density = LocalDensity.current
    if (drawable == null) {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(sizeDp.dp),
        )
        return
    }
    val px = with(density) { sizeDp.dp.toPx().toInt().coerceAtLeast(1) }
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
            modifier = Modifier.size(sizeDp.dp),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(sizeDp.dp),
        )
    }
}
