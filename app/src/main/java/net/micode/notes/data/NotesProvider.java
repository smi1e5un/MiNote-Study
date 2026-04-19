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


import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/**
 * 【ContentProvider类】
 * 笔记应用的内容提供者，负责对外提供数据访问接口
 * 
 * 主要功能：
 * 1. 封装数据库操作，对外提供统一的URI访问方式
 * 2. 支持note和data两张表的CRUD操作
 * 3. 支持搜索功能（系统搜索和全局搜索建议）
 * 4. 自动维护note表的version字段（乐观锁）
 * 5. 数据变更时自动通知观察者（ContentResolver）
 * 
 * URI路由规则：
 * - content://micode_notes/note        → 操作note表
 * - content://micode_notes/note/#      → 操作note表中的特定行
 * - content://micode_notes/data        → 操作data表
 * - content://micode_notes/data/#      → 操作data表中的特定行
 * - content://micode_notes/search      → 搜索笔记
 * - content://micode_notes/search_suggest → 搜索建议（系统搜索框）
 */
public class NotesProvider extends ContentProvider {
    
    // ==================== URI匹配器 ====================
    /** URI匹配器，用于根据URI判断要操作的表和操作类型 */
    private static final UriMatcher mMatcher;
    
    /** 数据库帮助类实例 */
    private NotesDatabaseHelper mHelper;
    
    /** 日志标签 */
    private static final String TAG = "NotesProvider";
    
    // ==================== URI匹配码常量 ====================
    /** 匹配码：操作note表（整个表） */
    private static final int URI_NOTE            = 1;
    /** 匹配码：操作note表中的单条记录 */
    private static final int URI_NOTE_ITEM       = 2;
    /** 匹配码：操作data表（整个表） */
    private static final int URI_DATA            = 3;
    /** 匹配码：操作data表中的单条记录 */
    private static final int URI_DATA_ITEM       = 4;
    /** 匹配码：搜索笔记 */
    private static final int URI_SEARCH          = 5;
    /** 匹配码：搜索建议（用于系统快速搜索框） */
    private static final int URI_SEARCH_SUGGEST  = 6;
    
    /**
     * 静态初始化块：配置URI匹配规则
     * 将URI字符串与对应的匹配码关联起来
     */
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        // 添加note表相关URI
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        // 添加data表相关URI
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        // 添加搜索相关URI
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }
    
    // ==================== 搜索相关常量 ====================
    /**
     * 搜索结果的投影列定义
     * x'0A'代表SQLite中的换行符(\n)，为了在搜索结果中显示更多信息，需要去除换行符和空格
     * 
     * 列映射说明：
     * - _id: 笔记ID
     * - SUGGEST_COLUMN_INTENT_EXTRA_DATA: 额外数据（也使用ID）
     * - SUGGEST_COLUMN_TEXT_1: 搜索结果的第一行文本（标题/摘要）
     * - SUGGEST_COLUMN_TEXT_2: 搜索结果的第二行文本（详细信息）
     * - SUGGEST_COLUMN_ICON_1: 搜索结果的图标
     * - SUGGEST_COLUMN_INTENT_ACTION: 点击结果时的Intent Action
     * - SUGGEST_COLUMN_INTENT_DATA: Intent携带的数据类型
     */
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
        + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
        + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
        + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
        + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;
    
    /**
     * 搜索查询的SQL语句
     * 从note表中查询摘要内容匹配关键字的笔记
     * 过滤条件：
     * - 不在垃圾箱中（parent_id != -3）
     * - 类型为普通笔记（type = 0）
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
        + " FROM " + TABLE.NOTE
        + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
        + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
        + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;
    
    /**
     * 【ContentProvider生命周期方法】
     * 在ContentProvider创建时调用，用于初始化数据库帮助类
     * @return true表示初始化成功
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }
    
    /**
     * 【查询操作】
     * 根据不同的URI执行不同的查询逻辑
     * 
     * @param uri 查询的URI，决定查询哪张表
     * @param projection 要返回的列名数组
     * @param selection WHERE子句的条件
     * @param selectionArgs WHERE子句的参数
     * @param sortOrder 排序规则
     * @return 查询结果的Cursor游标
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;
        
        // 根据URI匹配码分发到不同的查询逻辑
        switch (mMatcher.match(uri)) {
            // 查询整个note表
            case URI_NOTE:
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
                
            // 查询note表中的单条记录（URI中包含ID）
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);  // 从URI中提取ID
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
                
            // 查询整个data表
            case URI_DATA:
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
                
            // 查询data表中的单条记录
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
                
            // 搜索笔记或搜索建议
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 搜索查询不允许指定排序、投影等参数
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query");
                }
                
                // 获取搜索关键字
                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    // 搜索建议：关键字在URI路径中
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    // 普通搜索：关键字在查询参数中
                    searchString = uri.getQueryParameter("pattern");
                }
                
                // 关键字为空则返回空
                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }
                
                // 执行模糊搜索（前后加%通配符）
                try {
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;
                
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        // 设置Cursor的观察URI，当数据变化时通知Cursor更新
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }
    
    /**
     * 【插入操作】
     * 向note表或data表中插入新记录
     * 
     * @param uri 插入的目标URI
     * @param values 要插入的数据（键值对）
     * @return 新插入记录的URI（包含自增ID）
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;
        
        switch (mMatcher.match(uri)) {
            // 插入note表
            case URI_NOTE:
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
                
            // 插入data表
            case URI_DATA:
                // 检查是否包含note_id字段
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
                
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        // 如果笔记插入成功，通知note URI的观察者
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }
        
        // 如果数据插入成功，通知data URI的观察者
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }
        
        // 返回新插入记录的完整URI
        return ContentUris.withAppendedId(uri, insertedId);
    }
    
    /**
     * 【删除操作】
     * 从note表或data表中删除记录
     * 
     * 注意：系统文件夹（ID <= 0）不允许被删除
     * 
     * @param uri 要删除的URI
     * @param selection WHERE子句的条件
     * @param selectionArgs WHERE子句的参数
     * @return 删除的记录数
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;  // 标记是否删除了data表的数据
        
        switch (mMatcher.match(uri)) {
            // 删除note表中的多条记录
            case URI_NOTE:
                // 添加条件：ID > 0，防止删除系统文件夹
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
                
            // 删除note表中的单条记录
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                /**
                 * ID小于等于0的是系统文件夹，不允许移入垃圾箱
                 */
                long noteId = Long.valueOf(id);
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
                
            // 删除data表中的多条记录
            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
                
            // 删除data表中的单条记录
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
                
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        // 如果有数据被删除，通知观察者
        if (count > 0) {
            if (deleteData) {
                // 删除data数据时，笔记内容可能已变化，通知note URI
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }
    
    /**
     * 【更新操作】
     * 更新note表或data表中的记录
     * 
     * 特别注意：更新note表时，会自动增加version版本号（乐观锁机制）
     * 
     * @param uri 要更新的URI
     * @param values 要更新的数据（键值对）
     * @param selection WHERE子句的条件
     * @param selectionArgs WHERE子句的参数
     * @return 更新的记录数
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;  // 标记是否更新了data表
        
        switch (mMatcher.match(uri)) {
            // 更新note表中的多条记录
            case URI_NOTE:
                increaseNoteVersion(-1, selection, selectionArgs);  // 增加版本号
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
                
            // 更新note表中的单条记录
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);  // 增加版本号
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;
                
            // 更新data表中的多条记录
            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
                
            // 更新data表中的单条记录
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
                
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        
        // 如果有数据被更新，通知观察者
        if (count > 0) {
            if (updateData) {
                // 更新data数据时，笔记内容可能已变化，通知note URI
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }
    
    /**
     * 【辅助方法】解析WHERE条件
     * 如果selection不为空，在前面加上" AND "连接符
     * 
     * @param selection 原始WHERE条件
     * @return 格式化后的WHERE条件
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }
    
    /**
     * 【辅助方法】增加笔记的版本号（乐观锁）
     * 在更新笔记时调用，每次更新version字段自动+1
     * 用于检测并发修改冲突
     * 
     * @param id 笔记ID，-1表示更新所有匹配的笔记
     * @param selection WHERE条件
     * @param selectionArgs WHERE条件的参数
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");
        
        // 构建WHERE子句
        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            // 将参数替换到SQL语句中（注意：这种方式存在SQL注入风险，但selectionArgs来自内部调用）
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }
        
        // 执行更新版本号的SQL
        mHelper.getWritableDatabase().execSQL(sql.toString());
    }
    
    /**
     * 【获取MIME类型】
     * 根据URI返回对应的MIME类型
     * 当前未实现，返回null
     * 
     * @param uri 查询的URI
     * @return MIME类型字符串
     */
    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }
}