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

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;

/**
 * 【笔记数据模型类】
 * 这是笔记应用的核心数据模型，封装了一条笔记的所有数据和操作
 * 
 * 职责：
 * 1. 封装笔记的所有属性（内容、提醒时间、背景色、小部件等）
 * 2. 提供笔记的加载和保存功能
 * 3. 管理笔记的本地修改状态和同步
 * 4. 支持普通笔记和通话记录笔记两种类型
 * 5. 通过监听器通知UI层数据变化
 * 
 * 设计思想：
 * - 采用"工作单元"(Unit of Work)模式，保存时一次性同步所有修改
 * - 内部使用Note辅助类来管理SQL操作
 * - 支持懒加载：只有在需要时才从数据库加载数据
 * 
 * 数据来源：
 * - note表：存储笔记的元数据（文件夹、提醒、背景色等）
 * - data表：存储笔记的具体内容（文本内容、模式等）
 */
public class WorkingNote {
    
    // ==================== 成员变量 ====================
    /** 内部Note对象，负责具体的数据库操作 */
    private Note mNote;
    
    /** 笔记ID（数据库主键） */
    private long mNoteId;
    
    /** 笔记的文本内容 */
    private String mContent;
    
    /** 笔记模式：0=普通文本，1=复选框列表模式（待办清单） */
    private int mMode;
    
    /** 提醒时间戳（毫秒），0表示无提醒 */
    private long mAlertDate;
    
    /** 最后修改时间戳 */
    private long mModifiedDate;
    
    /** 背景颜色ID（预设颜色的索引） */
    private int mBgColorId;
    
    /** 关联的桌面小部件ID */
    private int mWidgetId;
    
    /** 桌面小部件类型：-1=无效，0=2x2，1=4x4 */
    private int mWidgetType;
    
    /** 所属文件夹ID */
    private long mFolderId;
    
    /** 上下文对象，用于ContentResolver操作 */
    private Context mContext;
    
    /** 日志标签 */
    private static final String TAG = "WorkingNote";
    
    /** 标记笔记是否已被删除（软删除标记） */
    private boolean mIsDeleted;
    
    /** UI状态变化监听器，用于通知Activity更新界面 */
    private NoteSettingChangedListener mNoteSettingStatusListener;
    
    // ==================== 数据库查询投影 ====================
    
    /**
     * 【data表查询投影】
     * 查询笔记内容时需要返回的列
     * 包含：ID、内容、MIME类型、模式、通用数据列
     */
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,           // 数据记录ID
            DataColumns.CONTENT,      // 文本内容
            DataColumns.MIME_TYPE,    // 类型（text_note 或 call_note）
            DataColumns.DATA1,        // 模式标志（普通/列表）
            DataColumns.DATA2,        // 保留字段
            DataColumns.DATA3,        // 保留字段
            DataColumns.DATA4,        // 保留字段
    };
    
    /**
     * 【note表查询投影】
     * 查询笔记元数据时需要返回的列
     */
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,       // 父文件夹ID
            NoteColumns.ALERTED_DATE,    // 提醒时间
            NoteColumns.BG_COLOR_ID,     // 背景颜色ID
            NoteColumns.WIDGET_ID,       // 小部件ID
            NoteColumns.WIDGET_TYPE,     // 小部件类型
            NoteColumns.MODIFIED_DATE    // 修改时间
    };
    
    // ==================== 列索引常量（提高代码可读性） ====================
    
    /** data表中ID列的索引 */
    private static final int DATA_ID_COLUMN = 0;
    
    /** data表中内容列的索引 */
    private static final int DATA_CONTENT_COLUMN = 1;
    
    /** data表中MIME类型列的索引 */
    private static final int DATA_MIME_TYPE_COLUMN = 2;
    
    /** data表中模式列的索引（DATA1） */
    private static final int DATA_MODE_COLUMN = 3;
    
    /** note表中父文件夹ID列的索引 */
    private static final int NOTE_PARENT_ID_COLUMN = 0;
    
    /** note表中提醒时间列的索引 */
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;
    
    /** note表中背景颜色ID列的索引 */
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;
    
    /** note表中小部件ID列的索引 */
    private static final int NOTE_WIDGET_ID_COLUMN = 3;
    
    /** note表中小部件类型列的索引 */
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;
    
    /** note表中修改时间列的索引 */
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;
    
    // ==================== 构造函数 ====================
    
    /**
     * 【私有构造函数 - 新建笔记】
     * 创建一个空的笔记对象，用于新建笔记
     * 
     * @param context 上下文
     * @param folderId 目标文件夹ID
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;                                    // 默认无提醒
        mModifiedDate = System.currentTimeMillis();        // 设置当前时间为修改时间
        mFolderId = folderId;                              // 设置所属文件夹
        mNote = new Note();                                // 创建内部Note对象
        mNoteId = 0;                                       // 新笔记ID为0，保存后才会分配
        mIsDeleted = false;                                // 未删除
        mMode = 0;                                         // 默认普通文本模式
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;          // 默认无小部件
    }
    
    /**
     * 【私有构造函数 - 加载已有笔记】
     * 从数据库加载已存在的笔记
     * 
     * @param context 上下文
     * @param noteId 要加载的笔记ID
     * @param folderId 文件夹ID（此处传入0，实际从数据库读取）
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();      // 从数据库加载笔记数据
    }
    
    // ==================== 数据加载方法 ====================
    
    /**
     * 【加载笔记元数据】
     * 从note表加载笔记的基本信息
     * 包括：文件夹、背景色、小部件、提醒时间等
     * 
     * @throws IllegalArgumentException 如果找不到指定ID的笔记
     */
    private void loadNote() {
        // 查询note表，URI格式：content://micode_notes/note/{noteId}
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), 
                NOTE_PROJECTION, 
                null, null, null);
        
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                // 从Cursor中读取各字段值
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        
        // 加载笔记的具体内容数据
        loadNoteData();
    }
    
    /**
     * 【加载笔记内容数据】
     * 从data表加载笔记的详细内容
     * 一个笔记可能有多条data记录（文本、附件等）
     * 这里主要处理文本内容和通话记录
     */
    private void loadNoteData() {
        // 查询data表，条件：note_id = 当前笔记ID
        Cursor cursor = mContext.getContentResolver().query(
                Notes.CONTENT_DATA_URI, 
                DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", 
                new String[] { String.valueOf(mNoteId) }, 
                null);
        
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    
                    // 处理普通文本笔记
                    if (DataConstants.NOTE.equals(type)) {
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);     // 文本内容
                        mMode = cursor.getInt(DATA_MODE_COLUMN);               // 模式（普通/清单）
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));   // 记录数据ID
                    } 
                    // 处理通话记录笔记
                    else if (DataConstants.CALL_NOTE.equals(type)) {
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } 
                    else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }
    
    // ==================== 静态工厂方法 ====================
    
    /**
     * 【工厂方法 - 创建空笔记】
     * 创建一个新的空白笔记对象
     * 
     * @param context 上下文
     * @param folderId 目标文件夹ID
     * @param widgetId 小部件ID（如果从小部件创建）
     * @param widgetType 小部件类型
     * @param defaultBgColorId 默认背景颜色ID
     * @return 新创建的WorkingNote对象
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
            int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }
    
    /**
     * 【工厂方法 - 加载已有笔记】
     * 从数据库加载指定ID的笔记
     * 
     * @param context 上下文
     * @param id 笔记ID
     * @return 加载的WorkingNote对象
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }
    
    // ==================== 数据保存方法 ====================
    
    /**
     * 【保存笔记】
     * 将当前笔记的所有修改保存到数据库
     * 使用同步方法保证线程安全
     * 
     * 保存逻辑：
     * 1. 判断是否有值得保存的修改
     * 2. 如果是新笔记，先创建note记录获取ID
     * 3. 调用Note.syncNote()同步所有修改到数据库
     * 4. 如果有关联的小部件，通知更新
     * 
     * @return true表示保存成功，false表示无需保存或保存失败
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            // 如果是新笔记且尚未在数据库中，创建新记录
            if (!existInDatabase()) {
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }
            
            // 同步笔记所有数据到数据库
            mNote.syncNote(mContext, mNoteId);
            
            /**
             * 如果存在关联的桌面小部件，通知小部件更新内容
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * 【检查是否已在数据库中】
     * 
     * @return true表示笔记已存在于数据库（有有效的ID）
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }
    
    /**
     * 【判断是否值得保存】
     * 检查条件：
     * 1. 笔记未被删除
     * 2. 如果是新笔记，内容不能为空
     * 3. 如果是已有笔记，必须有本地修改
     * 
     * @return true表示有修改需要保存
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }
    
    // ==================== Setter方法（带自动同步） ====================
    
    /**
     * 设置UI状态变化监听器
     * @param l 监听器对象
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }
    
    /**
     * 【设置提醒时间】
     * 修改后自动标记笔记需要同步
     * 
     * @param date 提醒时间戳（毫秒）
     * @param set true表示设置提醒，false表示取消提醒
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }
    
    /**
     * 【标记删除】
     * 软删除标记，不会立即从数据库删除
     * 
     * @param mark true表示标记为已删除
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        // 如果有关联小部件，通知更新
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
        }
    }
    
    /**
     * 【设置背景颜色】
     * @param id 背景颜色ID（ResourceParser中定义的颜色索引）
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }
    
    /**
     * 【设置清单模式】
     * 在普通文本模式和复选框清单模式之间切换
     * 
     * @param mode 模式值：0=普通文本，1=复选框清单
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }
    
    /**
     * 设置小部件类型
     * @param type 小部件类型（2x2或4x4）
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }
    
    /**
     * 设置小部件ID
     * @param id AppWidgetManager分配的小部件ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }
    
    /**
     * 【设置笔记文本内容】
     * @param text 新的文本内容
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }
    
    /**
     * 【转换为通话记录笔记】
     * 将当前笔记转换为通话记录类型
     * 用于自动记录通话结束后生成的笔记
     * 
     * @param phoneNumber 电话号码
     * @param callDate 通话日期时间戳
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }
    
    // ==================== Getter方法 ====================
    
    /**
     * @return 是否有提醒
     */
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }
    
    public String getContent() {
        return mContent;
    }
    
    public long getAlertDate() {
        return mAlertDate;
    }
    
    public long getModifiedDate() {
        return mModifiedDate;
    }
    
    /**
     * @return 背景颜色资源ID（用于设置TextView背景）
     */
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }
    
    public int getBgColorId() {
        return mBgColorId;
    }
    
    /**
     * @return 标题栏背景颜色资源ID
     */
    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }
    
    public int getCheckListMode() {
        return mMode;
    }
    
    public long getNoteId() {
        return mNoteId;
    }
    
    public long getFolderId() {
        return mFolderId;
    }
    
    public int getWidgetId() {
        return mWidgetId;
    }
    
    public int getWidgetType() {
        return mWidgetType;
    }
    
    // ==================== 内部监听器接口 ====================
    
    /**
     * 【笔记设置变化监听器接口】
     * 用于通知UI层笔记的属性发生了变化
     * UI层实现此接口，当WorkingNote的属性改变时得到回调
     */
    public interface NoteSettingChangedListener {
        /**
         * 当笔记背景颜色改变时调用
         * UI层应更新背景色显示
         */
        void onBackgroundColorChanged();
        
        /**
         * 当用户设置或取消提醒时调用
         * @param date 提醒时间戳
         * @param set true=设置提醒，false=取消提醒
         */
        void onClockAlertChanged(long date, boolean set);
        
        /**
         * 当从小部件创建笔记或小部件关联的笔记内容改变时调用
         * UI层应更新桌面小部件的显示
         */
        void onWidgetChanged();
        
        /**
         * 当在普通模式和清单模式之间切换时调用
         * @param oldMode 切换前的模式
         * @param newMode 切换后的模式
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}