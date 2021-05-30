package com.boringdroid.systemui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AllAppsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {
    private val mAdapter: AppListAdapter
    fun setData(apps: List<AppData?>?) {
        mAdapter.setData(apps)
        mAdapter.notifyDataSetChanged()
    }

    fun setHandler(handler: Handler?) {
        mAdapter.setHandler(handler)
    }

    private class AppListAdapter(private val mContext: Context) :
        Adapter<AppListAdapter.ViewHolder>() {
        private val mApps: MutableList<AppData?> = ArrayList()
        private var mHandler: Handler? = null
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val appInfoLayout = LayoutInflater.from(mContext)
                .inflate(R.layout.layout_app_info, parent, false) as ViewGroup
            return ViewHolder(appInfoLayout)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val appData = mApps[position]
            holder.iconIV.setImageDrawable(appData!!.icon)
            holder.nameTV.text = appData.name
            holder.appInfoLayout.setOnClickListener {
                val intent = Intent()
                intent.component = appData.componentName
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                mContext.startActivity(intent)
                if (mHandler != null) {
                    mHandler!!.sendEmptyMessage(HandlerConstant.H_DISMISS_ALL_APPS_WINDOW)
                } else {
                    Log.e(TAG, "Won't send dismiss event because of handler is null")
                }
            }
        }

        override fun getItemCount(): Int {
            return mApps.size
        }

        fun setData(apps: List<AppData?>?) {
            mApps.clear()
            mApps.addAll(apps!!)
        }

        fun setHandler(handler: Handler?) {
            mHandler = handler
        }

        private class ViewHolder(val appInfoLayout: ViewGroup) : RecyclerView.ViewHolder(
            appInfoLayout
        ) {
            val iconIV: ImageView = appInfoLayout.findViewById(R.id.app_info_icon)
            val nameTV: TextView = appInfoLayout.findViewById(R.id.app_info_name)
        }

        companion object {
            private const val TAG = "AppListAdapter"
        }
    }

    companion object {
        private const val NUMBER_OF_COLUMNS = 5
    }

    init {
        val layoutManager = GridLayoutManager(context, NUMBER_OF_COLUMNS)
        setLayoutManager(layoutManager)
        mAdapter = AppListAdapter(context)
        adapter = mAdapter
    }
}
