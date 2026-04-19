/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * GTaskASyncTask - 异步Google Tasks同步任务
 * 继承自AsyncTask，在后台线程执行同步操作
 * 主要功能：
 * 1. 在后台执行GTaskManager.sync()同步
 * 2. 通过publishProgress()发布同步进度到UI
 * 3. 同步完成后显示通知
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    /** 通知ID，用于同步状态通知 */
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 同步完成监听器接口
     * 同步完成后会调用此接口的onComplete()方法
     */
    public interface OnCompleteListener {
        void onComplete();
    }

    /** 应用上下文 */
    private Context mContext;

    /** 通知管理器，用于显示同步状态通知 */
    private NotificationManager mNotifiManager;

    /** GTask管理器，执行实际的同步操作 */
    private GTaskManager mTaskManager;

    /** 同步完成回调 */
    private OnCompleteListener mOnCompleteListener;

    /**
     * 构造函数
     * @param context 应用上下文
     * @param listener 同步完成监听器
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }

    /**
     * 取消正在进行的同步
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 发布同步进度
     * 会触发onProgressUpdate()回调
     * @param message 进度消息
     */
    public void publishProgress(String message) {
        publishProgress(new String[] {
                message
        });
    }

    /**
     * 显示同步状态通知
     * @param tickerId 通知的Ticker资源ID
     * @param content 通知内容文本
     */
    private void showNotification(int tickerId, String content) {
        PendingIntent pendingIntent;
        // 同步失败时打开设置页面，成功时打开笔记列表
        if (tickerId != R.string.ticker_success) {
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesPreferenceActivity.class), PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesListActivity.class), PendingIntent.FLAG_IMMUTABLE);
        }

        Notification.Builder builder = new Notification.Builder(mContext)
                .setSmallIcon(R.drawable.notification)
                .setTicker(mContext.getString(tickerId))
                .setWhen(System.currentTimeMillis())
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setAutoCancel(true)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(content)
                .setContentIntent(pendingIntent);

        Notification notification = builder.build();
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    /**
     * 后台同步执行
     * 调用GTaskManager.sync()执行实际的同步操作
     * @param unused 后台任务参数
     * @return 同步状态码
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        publishProgress(mContext.getString(R.string.sync_progress_login, NotesPreferenceActivity
                .getSyncAccountName(mContext)));
        return mTaskManager.sync(mContext, this);
    }

    /**
     * 更新同步进度
     * 在UI线程执行，显示进度通知
     * @param progress 进度消息数组
     */
    @Override
    protected void onProgressUpdate(String... progress) {
        showNotification(R.string.ticker_syncing, progress[0]);
        // 如果在Service中运行，发送广播更新UI
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    /**
     * 同步完成处理
     * 根据同步结果显示通知，并调用完成回调
     * @param result 同步状态码
     */
    @Override
    protected void onPostExecute(Integer result) {
        if (result == GTaskManager.STATE_SUCCESS) {
            // 同步成功
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            // 网络错误
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            // 内部错误
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            // 同步被取消
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }
        // 调用完成回调
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {

                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}