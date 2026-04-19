```java
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
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

/**
 * 【笔记列表主界面Activity】
 * 这是笔记应用的核心界面，负责展示笔记和文件夹列表，处理用户交互
 * 
 * 主要功能：
 * 1. 展示笔记/文件夹列表（支持根目录和子目录切换）
 * 2. 支持批量操作模式（多选删除、移动）
 * 3. 支持文件夹的创建、重命名、删除
 * 4. 支持笔记搜索、导出、同步等功能
 * 5. 首次使用时自动插入介绍笔记
 * 6. 支持通话记录文件夹的特殊处理
 * 
 * 状态管理：
 * - NOTE_LIST: 根目录状态，显示所有文件夹和笔记
 * - SUB_FOLDER: 子文件夹状态，显示某个文件夹内的笔记
 * - CALL_RECORD_FOLDER: 通话记录文件夹特殊状态
 */
public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {
    
    // ==================== 异步查询常量 ====================
    /** 查询笔记列表的请求token */
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0;
    
    /** 查询文件夹列表的请求token（用于移动笔记时选择目标文件夹） */
    private static final int FOLDER_LIST_QUERY_TOKEN      = 1;
    
    // ==================== 上下文菜单项ID ====================
    /** 删除文件夹菜单项ID */
    private static final int MENU_FOLDER_DELETE = 0;
    
    /** 查看文件夹菜单项ID */
    private static final int MENU_FOLDER_VIEW = 1;
    
    /** 重命名文件夹菜单项ID */
    private static final int MENU_FOLDER_CHANGE_NAME = 2;
    
    /** SharedPreferences中记录是否已添加介绍笔记的键 */
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";
    
    /**
     * 【列表编辑状态枚举】
     * 表示当前界面处于哪种展示模式
     */
    private enum ListEditState {
        NOTE_LIST,           // 根目录模式，显示所有笔记和文件夹
        SUB_FOLDER,          // 子文件夹模式，显示特定文件夹内的笔记
        CALL_RECORD_FOLDER   // 通话记录文件夹模式（特殊UI处理）
    };
    
    private ListEditState mState;                      // 当前界面状态
    private BackgroundQueryHandler mBackgroundQueryHandler;  // 后台查询处理器
    private NotesListAdapter mNotesListAdapter;        // 列表适配器
    private ListView mNotesListView;                   // 列表视图
    private Button mAddNewNote;                        // 新建笔记按钮
    private boolean mDispatch;                         // 触摸事件分发标志
    private int mOriginY;                              // 触摸起始Y坐标
    private int mDispatchY;                            // 分发的触摸Y坐标
    private TextView mTitleBar;                        // 标题栏（子文件夹时显示）
    private long mCurrentFolderId;                     // 当前打开的文件夹ID
    private ContentResolver mContentResolver;          // 内容解析器
    private ModeCallback mModeCallBack;                // 批量操作模式回调
    private static final String TAG = "NotesListActivity";  // 日志标签
    
    /** 列表滚动速率（用于触摸事件处理） */
    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30;
    
    private NoteItemData mFocusNoteDataItem;           // 当前长按选中的笔记/文件夹数据
    
    /**
     * 普通文件夹查询条件
     * 根据parent_id查询属于某个文件夹的笔记
     */
    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?";
    
    /**
     * 根目录查询条件
     * 查询所有非系统类型的笔记/文件夹，同时包含有内容的通话记录文件夹
     */
    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";
    
    /** 打开笔记的请求码 */
    private final static int REQUEST_CODE_OPEN_NODE = 102;
    
    /** 新建笔记的请求码 */
    private final static int REQUEST_CODE_NEW_NODE  = 103;
    
    /**
     * 【Activity生命周期 - onCreate】
     * 初始化界面、资源、首次使用介绍笔记
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);
        initResources();  // 初始化界面资源
        
        /**
         * 首次使用应用时，从raw资源文件读取介绍内容并自动创建一条介绍笔记
         * 通过SharedPreferences记录是否已添加，避免重复创建
         */
        setAppInfoFromRawRes();
    }
    
    /**
     * 【Activity生命周期 - onActivityResult】
     * 处理从笔记编辑界面返回的结果
     * 当笔记被修改或新建后，刷新列表
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null);  // 清空当前Cursor，触发重新查询
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
    
    /**
     * 从raw资源文件读取介绍内容并创建介绍笔记
     * 仅在首次启动时执行一次
     */
    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                // 读取raw目录下的introduction文件
                in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char [] buf = new char[1024];
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if(in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            
            // 创建一条红色的介绍笔记，内容为读取到的文本
            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
                return;
            }
        }
    }
    
    /**
     * 【Activity生命周期 - onStart】
     * 启动异步查询，刷新笔记列表
     */
    @Override
    protected void onStart() {
        super.onStart();
        startAsyncNotesListQuery();
    }
    
    /**
     * 初始化界面资源
     * 包括：ContentResolver、ListView、Adapter、按钮等
     */
    private void initResources() {
        mContentResolver = this.getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;  // 默认显示根目录
        mNotesListView = (ListView) findViewById(R.id.notes_list);
        // 添加列表底部视图
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());  // 自定义触摸监听
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        mState = ListEditState.NOTE_LIST;
        mModeCallBack = new ModeCallback();
    }
    
    /**
     * 【批量操作模式回调类】
     * 实现ListView的多选模式接口，处理批量删除、移动等操作
     * 进入此模式时，隐藏新建按钮，显示多选UI
     */
    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        private DropdownMenu mDropDownMenu;    // 下拉菜单（全选/取消全选）
        private ActionMode mActionMode;        // 当前ActionMode对象
        private MenuItem mMoveMenu;             // 移动菜单项
        
        /**
         * 创建ActionMode时调用
         * 初始化批量操作模式的UI
         */
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);
            mMoveMenu = menu.findItem(R.id.move);
            // 通话记录文件夹或无用户文件夹时，隐藏移动选项
            if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(this);
            }
            mActionMode = mode;
            mNotesListAdapter.setChoiceMode(true);   // 开启选择模式
            mNotesListView.setLongClickable(false);   // 禁用长按
            mAddNewNote.setVisibility(View.GONE);     // 隐藏新建按钮
            
            // 设置自定义视图显示选择数量
            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item) {
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());  // 全选/取消全选
                    updateMenu();  // 更新菜单标题
                    return true;
                }
            });
            return true;
        }
        
        /**
         * 更新菜单显示
         * 显示当前选中的数量，更新全选/取消全选按钮文字
         */
        private void updateMenu() {
            int selectedCount = mNotesListAdapter.getSelectedCount();
            // 更新下拉菜单标题
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            if (item != null) {
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);  // 全部选中时显示"取消全选"
                } else {
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);    // 未全选时显示"全选"
                }
            }
        }
        
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }
        
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }
        
        /**
         * 销毁ActionMode时调用
         * 恢复普通模式的UI
         */
        public void onDestroyActionMode(ActionMode mode) {
            mNotesListAdapter.setChoiceMode(false);   // 关闭选择模式
            mNotesListView.setLongClickable(true);     // 重新启用长按
            mAddNewNote.setVisibility(View.VISIBLE);   // 显示新建按钮
        }
        
        /**
         * 手动结束ActionMode
         */
        public void finishActionMode() {
            mActionMode.finish();
        }
        
        /**
         * 列表项选中状态改变时的回调
         * 更新选中状态并刷新菜单
         */
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                                              boolean checked) {
            mNotesListAdapter.setCheckedItem(position, checked);
            updateMenu();
        }
        
        /**
         * 菜单项点击处理
         * 处理批量删除和批量移动操作
         */
        public boolean onMenuItemClick(MenuItem item) {
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            
            int id = item.getItemId();
            if (id == R.id.delete) {
                // 弹出确认删除对话框
                AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_notes,
                        mNotesListAdapter.getSelectedCount()));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                batchDelete();  // 批量删除
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                return true;
            } else if (id == R.id.move) {
                startQueryDestinationFolders();  // 查询目标文件夹列表
                return true;
            }
            return false;
        }
    }
    
    /**
     * 【新建按钮触摸监听器】
     * 特殊处理：当点击按钮透明区域时，将触摸事件传递给底部的ListView
     * 这是为了解决UI设计的特殊需求，实现点击按钮上半部分时滚动列表的效果
     * 
     * 透明区域计算公式：y = -0.12x + 94（像素单位）
     */
    private class NewNoteOnTouchListener implements OnTouchListener {
        
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                Display display = getWindowManager().getDefaultDisplay();
                int screenHeight = display.getHeight();
                int newNoteViewHeight = mAddNewNote.getHeight();
                int start = screenHeight - newNoteViewHeight;
                int eventY = start + (int) event.getY();
                /**
                 * 子文件夹模式下需要减去标题栏高度
                 */
                if (mState == ListEditState.SUB_FOLDER) {
                    eventY -= mTitleBar.getHeight();
                    start -= mTitleBar.getHeight();
                }
                /**
                 * 点击"新建笔记"按钮的透明区域时，将事件分发给后方的ListView
                 * 透明区域公式：y=-0.12x+94，94表示透明部分的最大高度
                 * 注意：按钮背景改变时公式也需要相应调整
                 */
                if (event.getY() < (event.getX() * (-0.12) + 94)) {
                    View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                            - mNotesListView.getFooterViewsCount());
                    if (view != null && view.getBottom() > start
                            && (view.getTop() < (start + 94))) {
                        mOriginY = (int) event.getY();
                        mDispatchY = eventY;
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = true;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (mDispatch) {
                    mDispatchY += (int) event.getY() - mOriginY;
                    event.setLocation(event.getX(), mDispatchY);
                    return mNotesListView.dispatchTouchEvent(event);
                }
            } else {
                if (mDispatch) {
                    event.setLocation(event.getX(), mDispatchY);
                    mDispatch = false;
                    return mNotesListView.dispatchTouchEvent(event);
                }
            }
            return false;
        }
    };
    
    /**
     * 启动异步笔记列表查询
     * 根据当前文件夹ID和界面状态构建不同的查询条件
     */
    private void startAsyncNotesListQuery() {
        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER) ? ROOT_FOLDER_SELECTION
                : NORMAL_SELECTION;
        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION, selection, new String[] {
                        String.valueOf(mCurrentFolderId)
                }, NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC");
    }
    
    /**
     * 【后台查询处理器】
     * 继承AsyncQueryHandler，在后台线程执行数据库查询
     * 查询完成后自动更新UI
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }
        
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (token == FOLDER_NOTE_LIST_QUERY_TOKEN) {
                mNotesListAdapter.changeCursor(cursor);  // 更新列表适配器的Cursor
            } else if (token == FOLDER_LIST_QUERY_TOKEN) {
                if (cursor != null && cursor.getCount() > 0) {
                    showFolderListMenu(cursor);  // 显示文件夹选择菜单
                } else {
                    Log.e(TAG, "Query folder failed");
                }
            }
        }
    }
    
    /**
     * 显示文件夹选择菜单（用于移动笔记时选择目标文件夹）
     * @param cursor 包含文件夹列表的Cursor
     */
    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
        builder.setTitle(R.string.menu_title_select_folder);
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // 批量移动选中的笔记到选中的文件夹
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();
                mModeCallBack.finishActionMode();  // 退出批量操作模式
            }
        });
        builder.show();
    }
    
    /**
     * 创建新笔记
     * 启动NoteEditActivity，传入当前文件夹ID
     */
    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }
    
    /**
     * 批量删除笔记
     * 根据是否开启同步模式，执行直接删除或移动到垃圾箱操作
     * 同时更新相关的桌面小部件
     */
    private void batchDelete() {
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                if (!isSyncMode()) {
                    // 非同步模式：直接删除笔记
                    if (DataUtils.batchDeleteNotes(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds())) {
                    } else {
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    // 同步模式：移动到垃圾箱（软删除）
                    if (!DataUtils.batchMoveToFolder(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds(), Notes.ID_TRASH_FOLER)) {
                        Log.e(TAG, "Move notes to trash folder error, should not happens");
                    }
                }
                return widgets;
            }
            
            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);  // 更新桌面小部件
                        }
                    }
                }
                mModeCallBack.finishActionMode();
            }
        }.execute();
    }
    
    /**
     * 删除文件夹
     * @param folderId 要删除的文件夹ID
     */
    private void deleteFolder(long folderId) {
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }
        
        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);
        if (!isSyncMode()) {
            // 非同步模式：直接删除
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            // 同步模式：移动到垃圾箱
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }
    
    /**
     * 打开笔记（进入编辑界面）
     * @param data 笔记数据
     */
    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }
    
    /**
     * 打开文件夹（进入子目录）
     * @param data 文件夹数据
     */
    private void openFolder(NoteItemData data) {
        mCurrentFolderId = data.getId();
        startAsyncNotesListQuery();  // 刷新列表
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);  // 通话记录文件夹不能新建笔记
        } else {
            mState = ListEditState.SUB_FOLDER;
        }
        // 设置标题栏
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }
    
    /**
     * 点击事件处理
     */
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_new_note) {
            createNewNote();
        }
    }
    
    /**
     * 显示软键盘
     */
    private void showSoftInput() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }
    
    /**
     * 隐藏软键盘
     * @param view 当前获得焦点的视图
     */
    private void hideSoftInput(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    
    /**
     * 显示创建或修改文件夹的对话框
     * @param create true表示创建新文件夹，false表示重命名现有文件夹
     */
    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        showSoftInput();
        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }
        
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                hideSoftInput(etName);
            }
        });
        
        final Dialog dialog = builder.setView(view).show();
        final Button positive = (Button)dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput(etName);
                String name = etName.getText().toString();
                // 检查文件夹名是否已存在
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                            Toast.LENGTH_LONG).show();
                    etName.setSelection(0, etName.length());
                    return;
                }
                if (!create) {
                    // 重命名文件夹
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                                + "=?", new String[] {
                                String.valueOf(mFocusNoteDataItem.getId())
                        });
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    // 创建新文件夹
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });
        
        // 文件夹名为空时禁用确定按钮
        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }
        /**
         * 监听文本变化，动态控制确定按钮的可用性
         */
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false);
                } else {
                    positive.setEnabled(true);
                }
            }
            
            public void afterTextChanged(Editable s) {
            }
        });
    }
    
    /**
     * 返回键处理
     * 子文件夹模式下返回根目录，根目录下退出Activity
     */
    @Override
    public void onBackPressed() {
        if (mState == ListEditState.SUB_FOLDER) {
            mCurrentFolderId = Notes.ID_ROOT_FOLDER;
            mState = ListEditState.NOTE_LIST;
            startAsyncNotesListQuery();
            mTitleBar.setVisibility(View.GONE);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            mCurrentFolderId = Notes.ID_ROOT_FOLDER;
            mState = ListEditState.NOTE_LIST;
            mAddNewNote.setVisibility(View.VISIBLE);
            mTitleBar.setVisibility(View.GONE);
            startAsyncNotesListQuery();
        } else if (mState == ListEditState.NOTE_LIST) {
            super.onBackPressed();
        }
    }
    
    /**
     * 更新桌面小部件
     * @param appWidgetId 小部件ID
     * @param appWidgetType 小部件类型（2x2或4x4）
     */
    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }
        
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
                appWidgetId
        });
        
        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }
    
    /**
     * 文件夹的上下文菜单创建监听器
     * 长按文件夹时显示：查看、删除、重命名菜单
     */
    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            if (mFocusNoteDataItem != null) {
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };
    
    @Override
    public void onContextMenuClosed(Menu menu) {
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }
    
    /**
     * 上下文菜单项点击处理
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }
        int id = item.getItemId();
        if (id == MENU_FOLDER_VIEW) {
            openFolder(mFocusNoteDataItem);
        } else if (id == MENU_FOLDER_DELETE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.alert_title_delete));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.alert_message_delete_folder));
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            deleteFolder(mFocusNoteDataItem.getId());
                        }
                    });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else if (id == MENU_FOLDER_CHANGE_NAME) {
            showCreateOrModifyFolderDialog(false);
        }
        return true;
    }
    
    /**
     * 准备选项菜单
     * 根据不同状态（根目录/子文件夹/通话记录）显示不同的菜单
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            // 根据同步状态设置同步菜单文字
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }
        return true;
    }
    
    /**
     * 选项菜单项点击处理
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_new_folder) {
            showCreateOrModifyFolderDialog(true);
        } else if (id == R.id.menu_export_text) {
            exportNoteToText();  // 导出笔记为文本文件
        } else if (id == R.id.menu_sync) {
            if (isSyncMode()) {
                if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                    GTaskSyncService.startSync(this);  // 开始同步
                } else {
                    GTaskSyncService.cancelSync(this); // 取消同步
                }
            } else {
                startPreferenceActivity();  // 未登录则跳转设置
            }
        } else if (id == R.id.menu_setting) {
            startPreferenceActivity();
        } else if (id == R.id.menu_new_note) {
            createNewNote();
        } else if (id == R.id.menu_search) {
            onSearchRequested();
        }
        return true;
    }
    
    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, false);
        return true;
    }
    
    /**
     * 导出笔记为文本文件
     * 使用AsyncTask在后台执行，避免阻塞UI
     */
    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... unused) {
                return backup.exportToText();
            }
            
            @Override
            protected void onPostExecute(Integer result) {
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    // SD卡未挂载
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this.getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(R.string.error_sdcard_unmounted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    // 导出成功
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this.getString(R.string.success_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(
                            R.string.format_exported_file_location, backup
                                    .getExportedTextFileName(), backup.getExportedTextFileDir()));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    // 导出失败
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this.getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(R.string.error_sdcard_export));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
            }
        }.execute();
    }
    
    /**
     * 判断是否处于同步模式
     * @return true表示已配置同步账号
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }
    
    /**
     * 启动设置Activity
     */
    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }
    
    /**
     * 【列表项点击监听器】
     * 处理笔记/文件夹的点击事件
     */
    private class OnListItemClickListener implements OnItemClickListener {
        
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                // 批量选择模式下，处理选择逻辑
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }
                
                if (mState == ListEditState.NOTE_LIST) {
                    if (item.getType() == Notes.TYPE_FOLDER
                            || item.getType() == Notes.TYPE_SYSTEM) {
                        openFolder(item);      // 打开文件夹
                    } else if (item.getType() == Notes.TYPE_NOTE) {
                        openNode(item);        // 打开笔记
                    } else {
                        Log.e(TAG, "Wrong note type in NOTE_LIST");
                    }
                } else if (mState == ListEditState.SUB_FOLDER || mState == ListEditState.CALL_RECORD_FOLDER) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        openNode(item);
                    } else {
                        Log.e(TAG, "Wrong note type in SUB_FOLDER");
                    }
                }
            }
        }
    }
    
    /**
     * 启动查询目标文件夹列表（用于移动笔记）
     * 构建查询条件，排除当前文件夹和垃圾箱
     */
    private void startQueryDestinationFolders() {
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";
        selection = (mState == ListEditState.NOTE_LIST) ? selection:
                "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";
        
        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                FoldersListAdapter.PROJECTION,
                selection,
                new String[] {
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(mCurrentFolderId)
                },
                NoteColumns.MODIFIED_DATE + " DESC");
    }
    
    /**
     * 【长按事件处理】
     * 长按笔记时进入批量操作模式
     * 长按文件夹时显示上下文菜单
     */
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }
}