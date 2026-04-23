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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

/**
 * 闹钟提醒弹窗Activity
 * 
 * 功能说明：
 * 1. 当笔记设置的提醒时间到达时，由AlarmReceiver启动此Activity
 * 2. 显示包含笔记内容的提醒对话框
 * 3. 播放系统默认的闹钟铃声（循环播放直到用户响应）
 * 4. 支持在锁屏状态下显示和唤醒屏幕
 * 5. 提供"查看笔记"和"关闭提醒"两个操作选项
 * 
 * 界面特点：
 * - 无标题栏的全屏对话框样式
 * - 锁屏状态下仍可显示（FLAG_SHOW_WHEN_LOCKED）
 * - 屏幕关闭时会自动点亮屏幕
 * 
 * 生命周期：
 * AlarmReceiver启动 → onCreate显示对话框 → 用户点击按钮 → 停止铃声 → finish()
 */
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissListener {
    
    // ==================== 成员变量 ====================
    
    private long mNoteId;                               // 触发提醒的笔记ID
    private String mSnippet;                            // 笔记内容摘要（用于对话框显示）
    private static final int SNIPPET_PREW_MAX_LEN = 60; // 摘要内容的最大显示长度
    MediaPlayer mPlayer;                                // 媒体播放器，用于播放提醒铃声

    // ==================== Activity生命周期方法 ====================

    /**
     * Activity创建时调用
     * 设置窗口属性、解析Intent数据、显示对话框并播放铃声
     * 
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 隐藏标题栏（对话框样式）
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // ===== 设置窗口标志 =====
        final Window win = getWindow();
        
        // FLAG_SHOW_WHEN_LOCKED: 允许在锁屏界面上方显示
        // 这样即使手机锁屏，提醒弹窗也能正常显示
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 如果屏幕当前是关闭状态，添加唤醒屏幕的标志
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON          // 保持屏幕常亮
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON             // 点亮屏幕
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON // 允许屏幕亮时仍可锁屏
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);       // 布局延伸到系统装饰区域
        }

        // ===== 解析Intent数据，获取笔记信息 =====
        Intent intent = getIntent();

        try {
            // 从Intent的Data URI中解析笔记ID
            // URI格式: content://net.micode.notes/note/123
            // getPathSegments()返回: ["note", "123"]
            // get(1)获取索引为1的元素即笔记ID "123"
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            
            // 根据笔记ID从数据库获取内容摘要
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            
            // 限制摘要显示长度，避免对话框内容过长
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN 
                    ? mSnippet.substring(0, SNIPPET_PREW_MAX_LEN) + getResources().getString(R.string.notelist_string_info)  // 截断并添加省略号
                    : mSnippet;
                    
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return;  // 解析失败则退出
        }

        // ===== 初始化媒体播放器并显示提醒 =====
        mPlayer = new MediaPlayer();
        
        // 检查笔记是否仍然存在于数据库中（可能已被用户删除）
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            showActionDialog();  // 显示操作选择对话框
            playAlarmSound();    // 播放提醒铃声
        } else {
            // 笔记已不存在，直接关闭Activity
            finish();
        }
    }

    // ==================== 屏幕状态检测 ====================

    /**
     * 检测屏幕是否处于亮屏状态
     * 
     * @return true=屏幕已亮，false=屏幕已关闭
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    // ==================== 铃声播放 ====================

    /**
     * 播放闹钟提醒铃声
     * 
     * 铃声来源：系统默认闹钟铃声
     * 播放模式：循环播放直到用户响应
     * 音频流：使用闹钟音频流（受勿扰模式等系统设置影响）
     */
    private void playAlarmSound() {
        // 获取系统默认的闹钟铃声URI
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 获取系统静音模式影响的音频流设置
        // MODE_RINGER_STREAMS_AFFECTED: 静音模式下哪些音频流会被静音
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 根据系统设置决定使用的音频流类型
        // 使用位运算检查闹钟音频流是否受静音模式影响
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        
        try {
            // 设置数据源（系统闹钟铃声）
            mPlayer.setDataSource(this, url);
            // 准备播放器（同步准备，铃声文件一般较小）
            mPlayer.prepare();
            // 设置循环播放（直到用户关闭弹窗）
            mPlayer.setLooping(true);
            // 开始播放
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== 对话框显示 ====================

    /**
     * 显示操作选择对话框
     * 
     * 对话框内容：
     * - 标题：应用名称（"小米便签"）
     * - 内容：笔记摘要
     * - 按钮：
     *   - 正面按钮（确定）：关闭提醒
     *   - 负面按钮（进入）：查看笔记详情（仅在屏幕亮时显示）
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name);           // 设置标题
        dialog.setMessage(mSnippet);                   // 设置内容（笔记摘要）
        dialog.setPositiveButton(R.string.notealert_ok, this);  // 确定按钮
        
        // 只有在屏幕亮时才显示"查看笔记"按钮
        // 如果屏幕是关闭状态刚被唤醒，只显示"确定"按钮，避免误操作
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);  // 查看笔记按钮
        }
        
        // 显示对话框并设置关闭监听器
        dialog.show().setOnDismissListener(this);
    }

    // ==================== 点击事件处理 ====================

    /**
     * 对话框按钮点击事件处理
     * 
     * @param dialog 对话框实例
     * @param which  点击的按钮标识
     *               - BUTTON_NEGATIVE: 负面按钮（"查看笔记"）
     *               - 其他: 正面按钮（"确定"）
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                // 用户点击"查看笔记"按钮
                // 跳转到NoteEditActivity查看笔记详情
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                intent.putExtra(Intent.EXTRA_UID, mNoteId);
                startActivity(intent);
                break;
            default:
                // 用户点击"确定"按钮，不做额外操作
                // 对话框关闭时会触发onDismiss回调
                break;
        }
    }

    // ==================== 对话框关闭处理 ====================

    /**
     * 对话框关闭时的回调
     * 无论是点击按钮关闭还是点击外部关闭，都会调用此方法
     * 
     * @param dialog 被关闭的对话框
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound();  // 停止播放铃声
        finish();          // 关闭Activity
    }

    // ==================== 铃声停止 ====================

    /**
     * 停止播放闹钟铃声并释放资源
     * 在对话框关闭时调用
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();     // 停止播放
            mPlayer.release();  // 释放MediaPlayer资源
            mPlayer = null;
        }
    }
}