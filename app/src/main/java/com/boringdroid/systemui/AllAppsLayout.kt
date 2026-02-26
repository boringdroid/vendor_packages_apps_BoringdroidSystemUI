package com.boringdroid.systemui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView

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
        if (handler != null) {
            handler!!.sendEmptyMessage(HandlerConstant.H_DISMISS_ALL_APPS_WINDOW)
        } else {
            Log.e(TAG, "Won't send dismiss event because of handler is null")
        }
    }

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        composeView.setContent { AllAppsGrid(apps, ::launchApp) }
        addView(composeView)
    }

    @Composable
    private fun AllAppsGrid(apps: List<AppData>, onAppClick: (AppData) -> Unit) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(NUMBER_OF_COLUMNS),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps) { appData ->
                AndroidView(
                    factory = { context ->
                        LayoutInflater.from(context).inflate(R.layout.layout_app_info, null)
                            as ViewGroup
                    },
                    update = { appInfoLayout ->
                        val iconIV: ImageView = appInfoLayout.findViewById(R.id.app_info_icon)
                        val nameTV: TextView = appInfoLayout.findViewById(R.id.app_info_name)
                        iconIV.setImageDrawable(appData.icon)
                        nameTV.text = appData.name
                        appInfoLayout.setOnClickListener { onAppClick(appData) }
                    },
                )
            }
        }
    }

    companion object {
        private const val TAG = "AllAppsLayout"
        private const val NUMBER_OF_COLUMNS = 5
    }
}
