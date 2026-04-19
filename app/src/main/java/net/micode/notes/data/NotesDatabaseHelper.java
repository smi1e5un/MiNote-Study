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

package net.micode.notes.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 【数据库创建/升级辅助类】
 * 继承自SQLiteOpenHelper，负责数据库的创建、升级和版本管理
 * 
 * 数据库设计说明：
 * 1. 采用双表设计：note表（存储笔记/文件夹元数据）+ data表（存储具体内容）
 * 2. 使用触发器(Tigger)维护数据一致性：
 *    - 文件夹笔记数量的自动增减
 *    - 笔记摘要(snippet)的自动更新
 *    - 删除文件夹时自动删除子笔记
 *    - 移动到垃圾箱时自动移动子笔记
 * 3. 支持数据库版本升级（当前版本4）
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    
    // ==================== 数据库基础配置 ====================
    /** 数据库文件名 */
    private static final String DB_NAME = "note.db";
    
    /** 数据库版本号（当前为4） */
    private static final int DB_VERSION = 4;
    
    /**
     * 【表名定义接口】
     * 定义数据库中使用的表名常量
     */
    public interface TABLE {
        /** 笔记表 - 存储笔记和文件夹的基本信息 */
        public static final String NOTE = "note";
        
        /** 数据表 - 存储笔记的具体内容（支持多媒体混合存储） */
        public static final String DATA = "data";
    }
    
    /** 日志标签 */
    private static final String TAG = "NotesDatabaseHelper";
    
    /** 单例实例（确保全局只有一个数据库连接） */
    private static NotesDatabaseHelper mInstance;
    
    // ==================== SQL建表语句 ====================
    
    /**
     * 【note表建表语句】
     * 表名: note
     * 字段说明：
     * - _id: 主键，自增
     * - parent_id: 父文件夹ID，0表示根目录
     * - alert_date: 提醒时间戳，0表示无提醒
     * - bg_color_id: 背景颜色ID
     * - created_date: 创建时间戳，默认当前时间（毫秒）
     * - has_attachment: 是否有附件（0:无 1:有）
     * - modified_date: 修改时间戳，默认当前时间
     * - notes_count: 文件夹内的笔记数量（仅文件夹类型有效）
     * - snippet: 摘要内容（文件夹名 或 笔记预览）
     * - type: 类型（0:笔记 1:文件夹 2:系统）
     * - widget_id: 关联的桌面小部件ID
     * - widget_type: 小部件类型（-1:无效 0:2x2 1:4x4）
     * - sync_id: 云端同步ID
     * - local_modified: 本地修改标志（0:未修改 1:已修改）
     * - origin_parent_id: 原始父文件夹ID（用于从垃圾箱恢复）
     * - gtask_id: Google Tasks同步ID
     * - version: 版本号（乐观锁控制）
     */
    private static final String CREATE_NOTE_TABLE_SQL =
        "CREATE TABLE " + TABLE.NOTE + "(" +
            NoteColumns.ID + " INTEGER PRIMARY KEY," +
            NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +
            NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0" +
        ")";
    
    /**
     * 【data表建表语句】
     * 表名: data
     * 字段说明：
     * - _id: 主键，自增
     * - mime_type: MIME类型（区分文本、图片、音频等）
     * - note_id: 所属笔记ID（外键关联note表）
     * - created_date: 创建时间戳
     * - modified_date: 修改时间戳
     * - content: 数据内容（文本内容或文件路径）
     * - data1~data5: 通用数据列，根据mime_type有不同含义
     *   例如：文本笔记的data1表示列表模式标志
     *        通话记录的data1存储通话日期，data3存储电话号码
     */
    private static final String CREATE_DATA_TABLE_SQL =
        "CREATE TABLE " + TABLE.DATA + "(" +
            DataColumns.ID + " INTEGER PRIMARY KEY," +
            DataColumns.MIME_TYPE + " TEXT NOT NULL," +
            DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA1 + " INTEGER," +
            DataColumns.DATA2 + " INTEGER," +
            DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +
        ")";
    
    /**
     * 【索引创建语句】
     * 在data表的note_id字段上创建索引，加速根据笔记ID查询内容的操作
     */
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS note_id_index ON " +
        TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";
    
    // ==================== 数据库触发器（保证数据一致性） ====================
    
    /**
     * 【触发器】移动笔记时增加目标文件夹的笔记数量
     * 触发时机：更新note表的parent_id字段后
     * 作用：目标文件夹的notes_count自动+1
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_update "+
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";
    
    /**
     * 【触发器】移动笔记时减少原文件夹的笔记数量
     * 触发时机：更新note表的parent_id字段后
     * 作用：原文件夹的notes_count自动-1（确保不小于0）
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_update " +
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
        " END";
    
    /**
     * 【触发器】插入新笔记时增加父文件夹的笔记数量
     * 触发时机：向note表插入新记录后
     * 作用：父文件夹的notes_count自动+1
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_insert " +
        " AFTER INSERT ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";
    
    /**
     * 【触发器】删除笔记时减少父文件夹的笔记数量
     * 触发时机：从note表删除记录后
     * 作用：父文件夹的notes_count自动-1（确保不小于0）
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
        " END";
    
    /**
     * 【触发器】插入文本内容时自动更新笔记摘要
     * 触发时机：向data表插入MIME类型为文本笔记的数据后
     * 作用：将文本内容同步到note表的snippet字段
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER update_note_content_on_insert " +
        " AFTER INSERT ON " + TABLE.DATA +
        " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";
    
    /**
     * 【触发器】更新文本内容时自动更新笔记摘要
     * 触发时机：更新data表中文本笔记类型的数据后
     * 作用：同步更新note表的snippet字段
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_update " +
        " AFTER UPDATE ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";
    
    /**
     * 【触发器】删除文本内容时清空笔记摘要
     * 触发时机：删除data表中文本笔记类型的数据后
     * 作用：将note表的snippet字段清空
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_delete " +
        " AFTER delete ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=''" +
        "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
        " END";
    
    /**
     * 【触发器】删除笔记时同时删除其所有数据内容
     * 触发时机：从note表删除记录后
     * 作用：级联删除data表中关联的数据记录
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
        "CREATE TRIGGER delete_data_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.DATA +
        "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
        " END";
    
    /**
     * 【触发器】删除文件夹时同时删除其内部的所有笔记
     * 触发时机：从note表删除记录后（且被删除的是文件夹类型）
     * 作用：递归删除该文件夹下的所有子笔记
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
        "CREATE TRIGGER folder_delete_notes_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.NOTE +
        "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";
    
    /**
     * 【触发器】文件夹移至垃圾箱时，其中的笔记也自动移至垃圾箱
     * 触发时机：更新note表的parent_id为垃圾箱ID后
     * 作用：保持文件夹和内部笔记的位置一致性
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
        "CREATE TRIGGER folder_move_notes_on_trash " +
        " AFTER UPDATE ON " + TABLE.NOTE +
        " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";
    
    /**
     * 构造函数
     * @param context 上下文对象
     */
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }
    
    /**
     * 创建note表及相关的触发器、系统文件夹
     * @param db SQLiteDatabase对象
     */
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);          // 执行建表语句
        reCreateNoteTableTriggers(db);               // 创建所有触发器
        createSystemFolder(db);                      // 插入系统文件夹
        Log.d(TAG, "note table has been created");
    }
    
    /**
     * 重新创建note表相关的所有触发器
     * 步骤：先删除已存在的触发器（避免重复），再重新创建
     * @param db SQLiteDatabase对象
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 删除可能已存在的旧触发器
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");
        
        // 创建新的触发器
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }
    
    /**
     * 创建系统级文件夹
     * 包括：通话记录文件夹、根文件夹、临时文件夹、垃圾箱
     * @param db SQLiteDatabase对象
     */
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        
        /**
         * 通话记录文件夹 - 用于存储通话自动生成的笔记
         */
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
        
        /**
         * 根文件夹 - 默认的顶层文件夹
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
        
        /**
         * 临时文件夹 - 用于笔记移动过程中的临时存储
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
        
        /**
         * 创建垃圾箱文件夹 - 存放已删除但未永久清除的笔记
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }
    
    /**
     * 创建data表及相关的触发器、索引
     * @param db SQLiteDatabase对象
     */
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);           // 执行建表语句
        reCreateDataTableTriggers(db);                // 创建所有触发器
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);    // 创建索引
        Log.d(TAG, "data table has been created");
    }
    
    /**
     * 重新创建data表相关的所有触发器
     * @param db SQLiteDatabase对象
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        // 删除可能已存在的旧触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");
        
        // 创建新的触发器
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }
    
    /**
     * 获取单例实例（线程安全）
     * @param context 上下文对象
     * @return NotesDatabaseHelper单例
     */
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }
    
    /**
     * 【数据库创建回调】
     * 首次创建数据库时调用，初始化note表和data表
     * @param db SQLiteDatabase对象
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
    }
    
    /**
     * 【数据库升级回调】
     * 当DB_VERSION增加时调用，负责将旧版本数据库迁移到新版本
     * 升级路径：V1 → V2 → V3 → V4
     * @param db SQLiteDatabase对象
     * @param oldVersion 旧版本号
     * @param newVersion 新版本号
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;
        boolean skipV2 = false;
        
        // 从版本1升级到版本2
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true;  // 标记已包含v2→v3的升级
            oldVersion++;
        }
        
        // 从版本2升级到版本3
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;  // 需要重建触发器
            oldVersion++;
        }
        
        // 从版本3升级到版本4
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }
        
        // 如果升级过程中重建了触发器，则重新创建
        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }
        
        // 验证升级是否成功完成
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }
    
    /**
     * 升级数据库到版本2
     * 策略：删除旧表重建（简单粗暴，适用于数据结构大幅变更）
     * @param db SQLiteDatabase对象
     */
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }
    
    /**
     * 升级数据库到版本3
     * 变更内容：
     * 1. 删除无用的触发器
     * 2. 添加gtask_id字段（用于Google Tasks同步）
     * 3. 添加垃圾箱系统文件夹
     * @param db SQLiteDatabase对象
     */
    private void upgradeToV3(SQLiteDatabase db) {
        // 删除不再使用的触发器
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        // 添加gtask_id列
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        // 添加垃圾箱系统文件夹
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }
    
    /**
     * 升级数据库到版本4
     * 变更内容：添加version字段（用于乐观锁控制，防止并发修改冲突）
     * @param db SQLiteDatabase对象
     */
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }
}