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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.android.systemui.shared.system.ActivityManagerWrapper
import com.boringdroid.systemui.R

/**
 * RecyclerView adapter that renders [RecentAppTask] entries as icon + label + thumbnail cards.
 * Tapping a card relaunches the task via `ActivityManagerWrapper.startActivityFromRecents`, then
 * invokes [onTaskLaunched] so the owner can dismiss the overview. The thumbnail is bound from
 * `ActivityManagerWrapper.getTaskThumbnail` at bind time.
 */
class OverviewCardAdapter(
    private val context: Context,
    private val tasks: List<RecentAppTask>,
    private val onTaskLaunched: () -> Unit,
) : RecyclerView.Adapter<OverviewCardAdapter.ViewHolder>() {
    private val pm: PackageManager = context.packageManager
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = inflater.inflate(R.layout.layout_overview_card, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = tasks[position]
        try {
            val appInfo = pm.getApplicationInfo(task.packageName, 0)
            holder.icon.setImageDrawable(pm.getApplicationIcon(appInfo))
            holder.label.text = pm.getApplicationLabel(appInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "onBindViewHolder: package missing: ${task.packageName}", e)
            holder.icon.setImageDrawable(null)
            holder.label.text = task.packageName
        }
        val bitmap: Bitmap? =
            try {
                ActivityManagerWrapper.getInstance()
                    .getTaskThumbnail(task.taskId, /* isLowResolution= */ true)
                    .thumbnail
            } catch (e: SecurityException) {
                // getTaskSnapshot() enforces READ_FRAME_BUFFER; if the manifest
                // declaration ever goes missing we still want the overview to
                // render the card without a thumbnail rather than crashing.
                Log.w(TAG, "onBindViewHolder: thumbnail denied taskId=${task.taskId}", e)
                null
            }
        if (bitmap != null) {
            holder.thumbnail.setImageBitmap(bitmap)
            Log.d(
                TAG,
                "onBindViewHolder: thumbnail ok taskId=${task.taskId} " +
                    "size=${bitmap.width}x${bitmap.height}",
            )
        } else {
            holder.thumbnail.setImageDrawable(null)
            Log.d(TAG, "onBindViewHolder: thumbnail missing taskId=${task.taskId}")
        }
        holder.itemView.setOnClickListener {
            val ok =
                ActivityManagerWrapper.getInstance().startActivityFromRecents(task.taskId, null)
            Log.i(TAG, "click: startActivityFromRecents taskId=${task.taskId} ok=$ok")
            if (ok) {
                onTaskLaunched()
            } else {
                // Leave the overview on screen so other cards stay tappable,
                // and surface the failure so the user isn't left wondering
                // why the tap "did nothing". Expected triggers: the task was
                // killed between RecentTasksProvider.snapshot() and this
                // click, or a future manifest regression that drops
                // START_TASKS_FROM_RECENTS.
                Toast.makeText(context, R.string.overview_launch_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Find the adapter position of a given task id.
     *
     * Used by the owning [OverviewWindow] when a `TaskStackChangeListener.onTaskSnapshotChanged`
     * fires, so it can call `notifyItemChanged(position)` and force `onBindViewHolder` to refetch
     * the thumbnail. Returns -1 if the task is no longer in the list.
     */
    fun positionOfTaskId(taskId: Int): Int = tasks.indexOfFirst { it.taskId == taskId }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.overview_card_icon)
        val label: TextView = view.findViewById(R.id.overview_card_label)
        val thumbnail: ImageView = view.findViewById(R.id.overview_card_thumbnail)
    }

    companion object {
        private const val TAG = "BoringdroidOverviewAdapter"
    }
}
