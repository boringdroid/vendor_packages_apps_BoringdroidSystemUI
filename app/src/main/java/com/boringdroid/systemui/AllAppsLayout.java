package com.boringdroid.systemui;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class AllAppsLayout extends RecyclerView {
    private static final int NUMBER_OF_COLUMNS = 5;

    private final AppListAdapter mAdapter;

    public AllAppsLayout(@NonNull Context context) {
        this(context, null);
    }

    public AllAppsLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        GridLayoutManager layoutManager = new GridLayoutManager(context, NUMBER_OF_COLUMNS);
        setLayoutManager(layoutManager);
        mAdapter = new AppListAdapter(context);
        setAdapter(mAdapter);
    }

    public void setData(List<AppInfo> apps) {
        mAdapter.setData(apps);
        mAdapter.notifyDataSetChanged();
    }

    public void setHandler(Handler handler) {
        mAdapter.setHandler(handler);
    }

    private static class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {
        private static final String TAG = "AppListAdapter";
        private final List<AppInfo> mApps = new ArrayList<>();
        private final Context mContext;
        private Handler mHandler;

        public AppListAdapter(@NonNull Context context) {
            mContext = context;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ViewGroup appInfoLayout =
                    (ViewGroup) LayoutInflater
                            .from(mContext)
                            .inflate(R.layout.layout_app_info, parent, false);
            return new ViewHolder(appInfoLayout);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo appInfo = mApps.get(position);
            holder.iconIV.setImageDrawable(appInfo.getIcon());
            holder.nameTV.setText(appInfo.getName());
            holder.appInfoLayout.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setComponent(appInfo.getComponentName());
                intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
                if (mHandler != null) {
                    mHandler.sendEmptyMessage(HandlerConstant.H_DISMISS_ALL_APPS_WINDOW);
                } else {
                    Log.e(TAG, "Won't send dismiss event because of handler is null");
                }
            });
        }

        @Override
        public int getItemCount() {
            return mApps.size();
        }

        public void setData(List<AppInfo> apps) {
            mApps.clear();
            mApps.addAll(apps);
        }

        public void setHandler(Handler handler) {
            mHandler = handler;
        }

        private static class ViewHolder extends RecyclerView.ViewHolder {
            public final ViewGroup appInfoLayout;
            public final ImageView iconIV;
            public final TextView nameTV;

            public ViewHolder(@NonNull ViewGroup appInfoLayout) {
                super(appInfoLayout);
                this.appInfoLayout = appInfoLayout;
                iconIV = appInfoLayout.findViewById(R.id.app_info_icon);
                nameTV = appInfoLayout.findViewById(R.id.app_info_name);
            }
        }
    }
}
