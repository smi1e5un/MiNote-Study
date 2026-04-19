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

        package net.micode.notes.data;

import android.net.Uri;

/**
 * 【数据库常量定义类】
 * 本文件定义了笔记应用的所有常量，包括：
 * 1. ContentProvider的Authority和基础URI
 * 2. 笔记类型常量（普通笔记/文件夹/系统）
 * 3. 系统文件夹ID常量
 * 4. Intent传递数据的Key常量
 * 5. 笔记表(note)和数据表(data)的字段定义
 * 6. 文本笔记和通话记录笔记的子类定义
 */
public class Notes {

    // ==================== ContentProvider 基础配置 ====================
    /** ContentProvider的授权字符串，用于唯一标识这个ContentProvider */
    public static final String AUTHORITY = "micode_notes";

    /** 日志标签，用于调试和日志输出 */
    public static final String TAG = "Notes";

    // ==================== 笔记类型常量 ====================
    /** 普通笔记类型 */
    public static final int TYPE_NOTE     = 0;
    /** 文件夹类型（用于分类整理笔记） */
    public static final int TYPE_FOLDER   = 1;
    /** 系统类型（如系统预置的特殊文件夹） */
    public static final int TYPE_SYSTEM   = 2;

    // ==================== 系统文件夹ID常量 ====================
    /**
     * 以下ID是系统文件夹的标识符
     * {@link Notes#ID_ROOT_FOLDER } 根文件夹，所有笔记/文件夹的顶层容器
     * {@link Notes#ID_TEMPARAY_FOLDER } 临时文件夹，存储不属于任何文件夹的笔记
     * {@link Notes#ID_CALL_RECORD_FOLDER} 存储通话记录笔记的专用文件夹
     * {@link Notes#ID_TRASH_FOLER} 垃圾箱文件夹，存放已删除但未永久清除的笔记
     */
    public static final int ID_ROOT_FOLDER = 0;          // 根文件夹ID
    public static final int ID_TEMPARAY_FOLDER = -1;     // 临时文件夹ID（不属于任何文件夹）
    public static final int ID_CALL_RECORD_FOLDER = -2;  // 通话记录文件夹ID
    public static final int ID_TRASH_FOLER = -3;         // 垃圾箱文件夹ID

    // ==================== Intent传递数据的Key常量 ====================
    /** 提醒日期 */
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    /** 背景颜色ID */
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    /** 小部件ID */
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    /** 小部件类型 */
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    /** 文件夹ID */
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    /** 通话日期 */
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // ==================== 桌面小部件类型常量 ====================
    /** 无效小部件类型 */
    public static final int TYPE_WIDGET_INVALIDE      = -1;
    /** 2x2尺寸的小部件 */
    public static final int TYPE_WIDGET_2X            = 0;
    /** 4x4尺寸的小部件 */
    public static final int TYPE_WIDGET_4X            = 1;

    /**
     * 【数据类型常量类】
     * 定义笔记支持的数据类型
     */
    public static class DataConstants {
        /** 普通文本笔记的MIME类型 */
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        /** 通话记录笔记的MIME类型 */
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    // ==================== ContentProvider URI常量 ====================
    /**
     * 查询所有笔记和文件夹的URI
     * 格式: content://micode_notes/note
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * 查询笔记数据内容的URI（笔记的详细内容，如文本、附件等）
     * 格式: content://micode_notes/data
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * 【笔记表字段定义接口】
     * 对应数据库中的note表，存储笔记和文件夹的基本信息
     * 表名: note
     */
    public interface NoteColumns {
        /**
         * 行记录的唯一ID（主键）
         * <P> 数据类型: INTEGER (long) </P>
         * 自增主键，用于唯一标识每条笔记或文件夹
         */
        public static final String ID = "_id";

        /**
         * 父文件夹ID，表示当前笔记/文件夹所属的文件夹
         * <P> 数据类型: INTEGER (long) </P>
         * 外键关联到同一张表的_id字段
         * 值为0表示根目录，值为-1表示临时文件夹
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * 创建时间戳
         * <P> 数据类型: INTEGER (long) </P>
         * 存储System.currentTimeMillis()的值
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最后修改时间戳
         * <P> 数据类型: INTEGER (long) </P>
         * 每次更新笔记时自动更新
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 提醒时间
         * <P> 数据类型: INTEGER (long) </P>
         * 设置提醒的日期时间戳，0表示无提醒
         */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * 摘要内容
         * <P> 数据类型: TEXT </P>
         * 对于文件夹：存储文件夹名称
         * 对于笔记：存储笔记内容的预览（前50个字符）
         */
        public static final String SNIPPET = "snippet";

        /**
         * 关联的桌面小部件ID
         * <P> 数据类型: INTEGER (long) </P>
         * 如果笔记添加到桌面小部件，存储小部件的ID
         */
        public static final String WIDGET_ID = "widget_id";

        /**
         * 桌面小部件的类型
         * <P> 数据类型: INTEGER (long) </P>
         * 参考TYPE_WIDGET_2X和TYPE_WIDGET_4X
         */
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * 笔记背景颜色ID
         * <P> 数据类型: INTEGER (long) </P>
         * 存储预设背景颜色的索引值
         */
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * 是否包含附件
         * <P> 数据类型: INTEGER </P>
         * 0: 无附件，1: 有附件（如图片、录音等）
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * 文件夹内的笔记数量
         * <P> 数据类型: INTEGER (long) </P>
         * 仅对文件夹类型的记录有效
         */
        public static final String NOTES_COUNT = "notes_count";

        /**
         * 记录类型：文件夹或笔记
         * <P> 数据类型: INTEGER </P>
         * 参考TYPE_NOTE、TYPE_FOLDER、TYPE_SYSTEM
         */
        public static final String TYPE = "type";

        /**
         * 最后一次同步的ID（用于云端同步）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String SYNC_ID = "sync_id";

        /**
         * 本地是否被修改的标志
         * <P> 数据类型: INTEGER </P>
         * 0: 未修改，1: 已修改（用于同步冲突处理）
         */
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * 移入临时文件夹前的原始父文件夹ID
         * <P> 数据类型: INTEGER </P>
         * 用于恢复笔记到原位置
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * Google Tasks的同步ID
         * <P> 数据类型: TEXT </P>
         * 与Google Tasks服务同步时使用
         */
        public static final String GTASK_ID = "gtask_id";

        /**
         * 版本号
         * <P> 数据类型: INTEGER (long) </P>
         * 用于乐观锁控制，每次更新时递增
         */
        public static final String VERSION = "version";
    }

    /**
     * 【数据内容表字段定义接口】
     * 对应数据库中的data表，存储笔记的具体内容
     * 表名: data
     * 一个笔记（note表的一条记录）可以对应多条data记录
     * 这种设计支持多媒体笔记（文本、图片、音频等混合存储）
     */
    public interface DataColumns {
        /**
         * 行记录的唯一ID（主键）
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * 数据的MIME类型
         * <P> 数据类型: Text </P>
         * 例如: text/plain, image/jpeg, audio/mpeg
         * 用于区分不同类型的数据内容
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * 所属笔记的ID（外键）
         * <P> 数据类型: INTEGER (long) </P>
         * 关联到note表的_id字段
         */
        public static final String NOTE_ID = "note_id";

        /**
         * 创建时间戳
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最后修改时间戳
         * <P> 数据类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 数据的文本内容
         * <P> 数据类型: TEXT </P>
         * 存储笔记的文本部分或文件的路径描述
         */
        public static final String CONTENT = "content";

        /**
         * 通用数据列1 - 整数类型
         * <P> 数据类型: INTEGER </P>
         * 根据MIME_TYPE的不同有不同含义
         * 例如：在文本笔记中表示列表模式标志
         */
        public static final String DATA1 = "data1";

        /**
         * 通用数据列2 - 整数类型
         * <P> 数据类型: INTEGER </P>
         * 根据MIME_TYPE的不同有不同含义
         */
        public static final String DATA2 = "data2";

        /**
         * 通用数据列3 - 文本类型
         * <P> 数据类型: TEXT </P>
         * 根据MIME_TYPE的不同有不同含义
         * 例如：在通话记录中存储电话号码
         */
        public static final String DATA3 = "data3";

        /**
         * 通用数据列4 - 文本类型
         * <P> 数据类型: TEXT </P>
         */
        public static final String DATA4 = "data4";

        /**
         * 通用数据列5 - 文本类型
         * <P> 数据类型: TEXT </P>
         */
        public static final String DATA5 = "data5";
    }

    /**
     * 【文本笔记子类】
     * 继承DataColumns接口，扩展文本笔记特有的字段
     * 用于存储纯文本类型的笔记内容
     */
    public static final class TextNote implements DataColumns {
        /**
         * 文本模式标志
         * <P> 数据类型: Integer </P>
         * 1: 复选框列表模式（待办事项清单）
         * 0: 普通文本模式
         * 该字段存储在DATA1列中
         */
        public static final String MODE = DATA1;

        /** 复选框列表模式常量 */
        public static final int MODE_CHECK_LIST = 1;

        /** 文本笔记的MIME类型 - 集合类型 */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";

        /** 文本笔记的MIME类型 - 单项类型 */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";

        /** 文本笔记的专用查询URI */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 【通话记录笔记子类】
     * 继承DataColumns接口，扩展通话记录特有的字段
     * 用于自动记录通话结束后生成的笔记
     */
    public static final class CallNote implements DataColumns {
        /**
         * 通话日期时间戳
         * <P> 数据类型: INTEGER (long) </P>
         * 存储在DATA1列中
         * 记录通话发生的时间
         */
        public static final String CALL_DATE = DATA1;

        /**
         * 电话号码
         * <P> 数据类型: TEXT </P>
         * 存储在DATA3列中
         * 记录通话对方的电话号码
         */
        public static final String PHONE_NUMBER = DATA3;

        /** 通话记录笔记的MIME类型 - 集合类型 */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";

        /** 通话记录笔记的MIME类型 - 单项类型 */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";

        /** 通话记录笔记的专用查询URI */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}