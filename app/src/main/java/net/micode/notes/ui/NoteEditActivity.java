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
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 笔记编辑Activity - 小米便签的核心编辑界面
 * 
 * 功能说明：
 * 1. 创建和编辑文本笔记
 * 2. 支持清单模式（待办事项列表）
 * 3. 支持设置背景颜色、字体大小
 * 4. 支持设置提醒闹钟
 * 5. 支持分享笔记、发送到桌面快捷方式
 * 6. 支持涂鸦画板功能（自定义扩展）
 * 7. 支持通话记录笔记
 */
public class NoteEditActivity extends Activity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {
    
    /**
     * 头部视图持有者 - 用于缓存笔记头部的视图组件
     * 包含：修改时间、提醒图标、提醒时间、背景颜色按钮
     */
    private class HeadViewHolder {
        public TextView tvModified;      // 显示最后修改时间的文本视图
        public ImageView ivAlertIcon;    // 提醒图标
        public TextView tvAlertDate;     // 显示提醒时间的文本视图
        public ImageView ibSetBgColor;   // 设置背景颜色的按钮
    }

    // ==================== 静态映射表 ====================
    
    /**
     * 背景颜色按钮ID与颜色值ID的映射表
     * 将按钮点击事件映射到对应的背景颜色资源
     */
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);  // 黄色背景按钮
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);        // 红色背景按钮
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);      // 蓝色背景按钮
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);    // 绿色背景按钮
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);    // 白色背景按钮
    }

    /**
     * 背景颜色值与选中标记视图ID的映射表
     * 用于显示当前选中的背景颜色（显示一个勾选图标）
     */
    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    /**
     * 字体大小按钮ID与字体大小值ID的映射表
     */
    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);    // 大字体
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);    // 小字体
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);  // 正常字体
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);    // 超大字体
    }

    /**
     * 字体大小值与选中标记视图ID的映射表
     */
    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    // ==================== 常量定义 ====================
    
    private static final String TAG = "NoteEditActivity";                    // 日志标签
    
    // 清单模式下的标记符号
    public static final String TAG_CHECKED = String.valueOf('\u221A');       // 已选中的对勾符号 ✓
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');     // 未选中的方框符号 □
    
    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";     // SharedPreferences中保存字体大小的键名
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;               // 桌面快捷方式标题的最大长度

    // ==================== 成员变量 ====================
    
    private HeadViewHolder mNoteHeaderHolder;           // 头部视图持有者
    private View mHeadViewPanel;                        // 头部面板视图
    private View mNoteBgColorSelector;                  // 背景颜色选择器面板
    private View mFontSizeSelector;                     // 字体大小选择器面板
    private EditText mNoteEditor;                       // 笔记内容编辑框（普通模式）
    private View mNoteEditorPanel;                      // 笔记编辑面板容器
    private WorkingNote mWorkingNote;                   // 当前正在编辑的笔记数据模型
    private SharedPreferences mSharedPrefs;             // 共享偏好设置（用于保存字体大小等设置）
    private int mFontSizeId;                            // 当前字体大小ID
    private LinearLayout mEditTextList;                 // 清单模式下的列表容器
    private String mUserQuery;                          // 搜索关键词（用于高亮显示）
    private Pattern mPattern;                           // 正则表达式模式（用于搜索高亮）

    // ==================== Activity生命周期方法 ====================
    
    /**
     * Activity创建时调用
     * 初始化布局和Activity状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.note_edit);        // 设置布局文件

        // 如果不是从保存状态恢复，则初始化Activity状态
        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();                                   // 初始化失败则关闭Activity
            return;
        }
        initResources();                                // 初始化UI资源
    }

    /**
     * 初始化UI资源组件
     * 获取各种视图引用并设置点击监听器
     */
    private void initResources() {
        // 获取头部面板和视图组件
        mHeadViewPanel = findViewById(R.id.note_title);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = (TextView) findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = (ImageView) findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = (TextView) findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = (ImageView) findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);
        
        // 获取编辑区域组件
        mNoteEditor = (EditText) findViewById(R.id.note_edit_view);
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        
        // 初始化背景颜色选择器
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = (ImageView) findViewById(id);
            iv.setOnClickListener(this);
        }

        // 初始化字体大小选择器
        mFontSizeSelector = findViewById(R.id.font_size_selector);
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);
        }
        
        // 从SharedPreferences读取保存的字体大小设置
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        if (mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;  // 防止越界
        }
        
        // 获取清单模式列表容器
        mEditTextList = (LinearLayout) findViewById(R.id.note_edit_list);

        // ========== 动态添加涂鸦浮动按钮 ==========
        // 创建一个悬浮的涂鸦按钮，用于打开画板功能
        ImageButton drawingButton = new ImageButton(this);
        drawingButton.setImageResource(android.R.drawable.ic_menu_edit);  // 使用系统编辑图标
        drawingButton.setBackgroundColor(0xFFFF5722);                      // 设置橙色背景
        drawingButton.setScaleType(ImageView.ScaleType.CENTER);

        // 设置按钮的布局参数（大小和位置）
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                (int) (56 * getResources().getDisplayMetrics().density),   // 56dp宽度
                (int) (56 * getResources().getDisplayMetrics().density)    // 56dp高度
        );
        params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;  // 右下角对齐
        params.setMargins(0, 0,
                (int) (16 * getResources().getDisplayMetrics().density),   // 右边距16dp
                (int) (16 * getResources().getDisplayMetrics().density));  // 下边距16dp

        drawingButton.setLayoutParams(params);
        drawingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDrawingDialog();    // 点击时显示涂鸦对话框
            }
        });

        // 将按钮添加到根布局中
        ((android.widget.FrameLayout) findViewById(android.R.id.content)).addView(drawingButton);
    }

    /**
     * 恢复Activity保存的状态
     * 当Activity因内存不足被系统杀死后，用户再次进入时恢复之前的状态
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // 如果保存的状态中包含笔记ID，则恢复笔记
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    /**
     * 根据Intent初始化Activity状态
     * 支持两种操作：
     * 1. ACTION_VIEW - 查看/编辑已有笔记
     * 2. ACTION_INSERT_OR_EDIT - 新建笔记
     * 
     * @param intent 传入的Intent
     * @return 初始化是否成功
     */
    private boolean initActivityState(Intent intent) {
        mWorkingNote = null;
        
        // 情况1：查看/编辑已有笔记
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            // 处理来自搜索的结果
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            // 检查笔记是否存在于数据库中
            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                // 加载笔记数据
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
            // 隐藏软键盘
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                            
        // 情况2：新建笔记或编辑通话记录笔记
        } else if (TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // 检查是否是通话记录笔记
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            
            if (callDate != 0 && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long noteId = 0;
                // 查找是否已存在该通话记录的笔记
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    // 创建新的通话记录笔记
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                // 创建普通新笔记
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }

            // 显示软键盘
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }
        
        // 设置笔记设置变更监听器
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    /**
     * Activity恢复时调用
     * 刷新笔记界面显示
     */
    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();
    }

    /**
     * 初始化笔记屏幕显示
     * 根据当前笔记的状态设置UI显示
     */
    private void initNoteScreen() {
        // 设置字体大小
        mNoteEditor.setTextAppearance(this, TextAppearanceResources
                .getTexAppearanceResource(mFontSizeId));
        
        // 根据笔记模式显示不同的编辑界面
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote.getContent());    // 清单模式
        } else {
            // 普通文本模式，并高亮搜索关键词
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }
        
        // 隐藏所有背景颜色的选中标记
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }
        
        // 设置背景颜色
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        // 显示最后修改时间
        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        // 显示提醒信息
        showAlertHeader();
    }

    /**
     * 显示提醒头部信息
     * 如果笔记设置了提醒，显示提醒时间；否则隐藏
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long time = System.currentTimeMillis();
            if (time > mWorkingNote.getAlertDate()) {
                // 提醒已过期
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                // 显示相对时间（如"5分钟后"）
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        }
    }

    /**
     * 处理新的Intent（当Activity已存在时）
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    /**
     * 保存Activity状态
     * 在Activity可能被销毁前保存笔记数据
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 如果笔记尚未保存到数据库，先保存
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    /**
     * 分发触摸事件
     * 处理点击选择器外部时自动隐藏选择器的逻辑
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 如果背景颜色选择器可见且触摸点不在其范围内，则隐藏
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }

        // 如果字体大小选择器可见且触摸点不在其范围内，则隐藏
        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 判断触摸点是否在指定视图范围内
     * 
     * @param view 目标视图
     * @param ev   触摸事件
     * @return 是否在视图范围内
     */
    private boolean inRangeOfView(View view, MotionEvent ev) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
            return false;
        }
        return true;
    }

    /**
     * Activity暂停时调用
     * 保存笔记并清理选择器状态
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        clearSettingState();
    }

    // ==================== 控件事件处理 ====================

    /**
     * 点击事件处理
     * 处理背景颜色选择、字体大小选择等点击事件
     */
    public void onClick(View v) {
        int id = v.getId();
        
        // 点击设置背景颜色按钮 - 显示背景颜色选择器
        if (id == R.id.btn_set_bg_color) {
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.VISIBLE);
                    
        // 点击背景颜色选项 - 切换背景颜色
        } else if (sBgSelectorBtnsMap.containsKey(id)) {
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.GONE);
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
            
        // 点击字体大小选项 - 切换字体大小
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();  // 保存设置
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
            
            // 根据当前模式更新字体
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                mNoteEditor.setTextAppearance(this,
                        TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            }
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    /**
     * 返回键按下处理
     * 先尝试关闭选择器，如果选择器未打开则正常返回
     */
    @Override
    public void onBackPressed() {
        if (clearSettingState()) {
            return;  // 关闭了选择器，不退出Activity
        }

        saveNote();
        super.onBackPressed();
    }

    /**
     * 清理设置状态（关闭选择器面板）
     * 
     * @return 是否有选择器被关闭
     */
    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    // ==================== 监听器回调方法 ====================

    /**
     * 背景颜色改变时的回调（实现NoteSettingChangedListener接口）
     */
    public void onBackgroundColorChanged() {
        findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                View.VISIBLE);
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    /**
     * 提醒时间改变时的回调
     * 设置或取消闹钟提醒
     * 
     * @param date 提醒时间
     * @param set  是否设置提醒（true=设置，false=取消）
     */
    public void onClockAlertChanged(long date, boolean set) {
        // 确保笔记已保存
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        
        if (mWorkingNote.getNoteId() > 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));
            showAlertHeader();
            
            if (!set) {
                alarmManager.cancel(pendingIntent);     // 取消闹钟
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);  // 设置闹钟
            }
        } else {
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    /**
     * 桌面小部件改变时的回调
     * 更新桌面小部件显示
     */
    public void onWidgetChanged() {
        updateWidget();
    }

    /**
     * 更新桌面小部件
     * 发送广播通知小部件刷新
     */
    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{
                mWorkingNote.getWidgetId()
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    // ==================== 选项菜单相关 ====================

    /**
     * 准备选项菜单
     * 根据当前笔记状态动态构建菜单项
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return true;
        }
        clearSettingState();
        menu.clear();
        
        // 根据笔记类型加载不同的菜单
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);  // 通话记录笔记菜单
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);       // 普通笔记菜单
        }
        
        // 根据清单模式状态设置菜单项文字
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }
        
        // 根据是否有提醒显示/隐藏菜单项
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        return true;
    }

    /**
     * 选项菜单项点击处理
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.menu_new_note) {
            createNewNote();                    // 新建笔记
            return true;
        } else if (id == R.id.menu_delete) {
            deleteCurrentNote();                // 删除当前笔记
            finish();
            return true;
        } else if (id == R.id.menu_font_size) {
            // 显示字体大小选择器
            mFontSizeSelector.setVisibility(View.VISIBLE);
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
            return true;
        } else if (id == R.id.menu_list_mode) {
            // 切换清单模式
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                mWorkingNote.setCheckListMode(0);                       // 切换为普通模式
            } else {
                mWorkingNote.setCheckListMode(TextNote.MODE_CHECK_LIST); // 切换为清单模式
            }
            return true;
        } else if (id == R.id.menu_share) {
            sendTo(this, mWorkingNote.getContent());  // 分享笔记内容
            return true;
        } else if (id == R.id.menu_send_to_desktop) {
            sendToDesktop();                          // 发送到桌面快捷方式
            return true;
        } else if (id == R.id.menu_alert) {
            setReminder();                            // 设置提醒
            return true;
        } else if (id == R.id.menu_delete_remind) {
            mWorkingNote.setAlertDate(0, false);      // 删除提醒
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 显示日期时间选择器，用于设置提醒
     */
    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                mWorkingNote.setAlertDate(date, true);
            }
        });
        d.show();
    }

    /**
     * 分享笔记内容到其他应用
     * 
     * @param context 上下文
     * @param info    要分享的文本内容
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    /**
     * 创建新笔记
     * 保存当前笔记后打开新的编辑界面
     */
    private void createNewNote() {
        saveNote();
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    /**
     * 删除当前笔记
     * 根据同步模式决定是永久删除还是移动到回收站
     */
    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }
            
            // 根据是否开启同步模式选择删除方式
            if (!isSyncMode()) {
                if (!DataUtils.batchDeleteNotes(getContentResolver(), ids)) {
                    Log.e(TAG, "Delete Note error");
                }
            } else {
                // 同步模式下移动到回收站而非永久删除
                if (!DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER)) {
                    Log.e(TAG, "Move notes to trash folder error, should not happens");
                }
            }
        }
        mWorkingNote.markDeleted(true);
    }

    /**
     * 判断是否处于同步模式
     * 如果设置了同步账户，则处于同步模式
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    // ==================== 清单模式相关方法 ====================

    /**
     * 清单编辑项删除时的回调
     * 
     * @param index 被删除项的索引
     * @param text  被删除项的文本内容
     */
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) {
            return;  // 至少保留一项
        }

        // 更新后续项的索引
        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        mEditTextList.removeViewAt(index);
        
        // 将删除的内容追加到上一项
        NoteEditText edit = null;
        if (index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(R.id.et_edit_text);
        }
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(length);
    }

    /**
     * 清单编辑项回车时的回调（创建新项）
     * 
     * @param index 新项插入的位置
     * @param text  初始文本内容
     */
    public void onEditTextEnter(int index, String text) {
        if (index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundary, should not happen");
        }

        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        
        // 更新后续项的索引
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

    /**
     * 切换到清单模式
     * 将普通文本转换为清单列表显示
     * 
     * @param text 笔记内容
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");  // 按换行符分割
        int index = 0;
        for (String item : items) {
            if (!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }
        // 添加一个空项供用户输入
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        // 切换显示
        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    /**
     * 获取高亮显示搜索关键词的文本
     * 
     * @param fullText  完整文本
     * @param userQuery 搜索关键词
     * @return 带高亮效果的Spannable
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                // 对匹配到的关键词设置背景色高亮
                spannable.setSpan(
                        new BackgroundColorSpan(this.getResources().getColor(
                                R.color.user_query_highlight)), m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 获取清单列表项视图
     * 
     * @param item  项目文本内容
     * @param index 项目索引
     * @return 列表项视图
     */
    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = ((CheckBox) view.findViewById(R.id.cb_edit_item));
        
        // 设置复选框状态变化监听器
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);  // 添加删除线
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);     // 移除删除线
                }
            }
        });

        // 解析项目状态（是否已选中）
        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length(), item.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length(), item.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    /**
     * 文本内容改变时的回调
     * 控制复选框的显示/隐藏
     */
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if (hasText) {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    /**
     * 清单模式改变时的回调
     * 在普通模式和清单模式之间切换
     */
    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            if (!getWorkingText()) {
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ", ""));
            }
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 获取当前编辑的文本内容并更新到WorkingNote
     * 
     * @return 是否有已选中的项目
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    /**
     * 保存笔记
     * 
     * @return 保存是否成功
     */
    private boolean saveNote() {
        getWorkingText();
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            setResult(RESULT_OK);
        }
        return saved;
    }

    /**
     * 发送笔记到桌面（创建快捷方式）
     */
    private void sendToDesktop() {
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.putExtra("duplicate", true);
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    /**
     * 生成桌面快捷方式的标题
     * 清理特殊字符并限制长度
     */
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");
        content = content.replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ? content.substring(0,
                SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    // ==================== 工具方法 ====================

    /**
     * 显示Toast提示
     */
    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }

    // ==================== 涂鸦功能 ====================

    /**
     * 显示涂鸦画板对话框
     * 提供手绘涂鸦功能，可保存为图片并插入到笔记中
     */
    private void showDrawingDialog() {
        // 加载自定义对话框布局
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_drawing, null);
        final DrawingView drawingView = dialogView.findViewById(R.id.drawing_view);

        // 等待视图绘制完成后再获取尺寸（确保画板正确初始化）
        drawingView.post(new Runnable() {
            @Override
            public void run() {
                drawingView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            }
        });

        // ===== 颜色选择按钮 =====
        ImageButton btnBlack = dialogView.findViewById(R.id.btn_color_black);
        ImageButton btnRed = dialogView.findViewById(R.id.btn_color_red);
        ImageButton btnBlue = dialogView.findViewById(R.id.btn_color_blue);

        if (btnBlack != null) {
            btnBlack.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    drawingView.setColor(Color.BLACK);   // 黑色画笔
                }
            });
        }

        if (btnRed != null) {
            btnRed.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    drawingView.setColor(Color.RED);     // 红色画笔
                }
            });
        }

        if (btnBlue != null) {
            btnBlue.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    drawingView.setColor(Color.BLUE);    // 蓝色画笔
                }
            });
        }

        // ===== 画笔粗细选择按钮 =====
        Button btnSmall = dialogView.findViewById(R.id.btn_stroke_small);
        Button btnLarge = dialogView.findViewById(R.id.btn_stroke_large);

        if (btnSmall != null) {
            btnSmall.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    drawingView.setStrokeWidth(3f);      // 细画笔
                }
            });
        }

        if (btnLarge != null) {
            btnLarge.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    drawingView.setStrokeWidth(10f);     // 粗画笔
                }
            });
        }

        // ===== 操作按钮 =====
        Button btnUndo = dialogView.findViewById(R.id.btn_undo);
        if (btnUndo != null) {
            btnUndo.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    drawingView.undo();                   // 撤销上一步
                }
            });
        }

        Button btnClear = dialogView.findViewById(R.id.btn_clear);
        if (btnClear != null) {
            btnClear.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    drawingView.clear();                  // 清空画板
                }
            });
        }

        // 构建并显示对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("涂鸦画板")
                .setView(dialogView)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // 保存涂鸦为图片文件
                        String imagePath = drawingView.saveAsImage();
                        if (imagePath != null) {
                            // 将图片路径添加到笔记内容中
                            String currentText = mNoteEditor.getText().toString();
                            String drawingText = "\n\n[涂鸦图片: " + imagePath + "]\n";
                            mNoteEditor.setText(currentText + drawingText);
                            Toast.makeText(NoteEditActivity.this, "涂鸦已保存到: " + imagePath, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(NoteEditActivity.this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}