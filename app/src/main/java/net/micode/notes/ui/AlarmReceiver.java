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

package net.micode.notes.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 闹钟广播接收器
 * 
 * 功能说明：
 * 1. 继承自BroadcastReceiver，用于接收系统闹钟触发的广播
 * 2. 当笔记设置的提醒时间到达时，AlarmManager会发送广播
 * 3. 本接收器收到广播后，启动AlarmAlertActivity显示提醒界面
 * 
 * 工作流程：
 * NoteEditActivity设置提醒 → AlarmManager注册定时广播
 * → 时间到达时系统发送广播 → AlarmReceiver接收
 * → 启动AlarmAlertActivity显示提醒弹窗
 * 
 * 注册方式：
 * 在AndroidManifest.xml中静态注册，监听特定的Action
 */
public class AlarmReceiver extends BroadcastReceiver {
    
    /**
     * 广播接收回调方法
     * 当系统闹钟触发时，此方法会被调用
     * 
     * @param context 上下文对象，用于启动Activity
     * @param intent  广播Intent，包含了触发闹钟的笔记信息
     *                （通过setData设置了笔记的URI，如：content://net.micode.notes/note/123）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 将目标Activity设置为AlarmAlertActivity（提醒弹窗界面）
        intent.setClass(context, AlarmAlertActivity.class);
        
        // 添加FLAG_ACTIVITY_NEW_TASK标志
        // 因为BroadcastReceiver的context不是Activity，启动Activity必须添加此标志
        // 表示在新的任务栈中启动Activity
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        // 启动AlarmAlertActivity显示提醒界面
        context.startActivity(intent);
    }
}