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

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义便签编辑文本框
 * 
 * 功能说明：
 * 1. 继承自EditText，扩展了清单模式下的特殊按键处理
 * 2. 支持回车自动创建新的列表项
 * 3. 支持删除键合并/删除列表项
 * 4. 支持点击链接（电话、网址、邮箱）弹出上下文菜单
 * 5. 优化触摸点击光标定位
 * 
 * 主要用于NoteEditActivity的清单列表模式中
 */
public class NoteEditText extends EditText {
    
    private static final String TAG = "NoteEditText";          // 日志标签
    
    private int mIndex;                                         // 当前文本框在列表中的索引位置
    private int mSelectionStartBeforeDelete;                    // 删除操作前的光标位置

    // ==================== 支持的链接协议常量 ====================
    private static final String SCHEME_TEL = "tel:";            // 电话链接协议
    private static final String SCHEME_HTTP = "http:";          // 网页链接协议
    private static final String SCHEME_EMAIL = "mailto:";       // 邮件链接协议

    /**
     * 链接协议与对应菜单文字资源的映射表
     * 用于在长按链接时显示对应类型的菜单项（如"拨打电话"、"打开网页"等）
     */
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);      // 电话链接 -> "拨打电话"
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);     // 网页链接 -> "打开网页"
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);  // 邮件链接 -> "发送邮件"
    }

    /**
     * 文本视图变化监听器接口
     * 由NoteEditActivity实现，用于响应清单列表中的编辑操作
     */
    public interface OnTextViewChangeListener {
        
        /**
         * 当按下删除键且文本框内容为空时调用
         * 用于删除当前编辑项，并将内容合并到上一项
         * 
         * @param index 当前项的索引
         * @param text  当前项的文本内容（删除前为空）
         */
        void onEditTextDelete(int index, String text);

        /**
         * 当按下回车键时调用
         * 用于在当前项下方创建新的列表项
         * 
         * @param index 新项的插入位置（当前索引+1）
         * @param text  回车后光标右侧的文本内容
         */
        void onEditTextEnter(int index, String text);

        /**
         * 当文本内容改变时调用
         * 用于控制复选框的显示/隐藏（有内容时显示复选框，无内容时隐藏）
         * 
         * @param index   当前项的索引
         * @param hasText 是否有文本内容
         */
        void onTextChange(int index, boolean hasText);
    }

    private OnTextViewChangeListener mOnTextViewChangeListener;  // 文本变化监听器

    // ==================== 构造函数 ====================

    /**
     * 构造函数
     */
    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    /**
     * 构造函数（用于XML布局解析）
     */
    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    /**
     * 构造函数（用于XML布局解析，指定默认样式）
     */
    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    // ==================== 公共设置方法 ====================

    /**
     * 设置当前文本框在列表中的索引
     * 
     * @param index 索引值
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * 设置文本视图变化监听器
     * 
     * @param listener 监听器实例
     */
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    // ==================== 触摸事件处理 ====================

    /**
     * 处理触摸事件
     * 优化光标定位：根据触摸位置精确设置光标位置
     * 
     * @param event 触摸事件
     * @return 是否消费事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 计算触摸点相对于文本内容的坐标
                int x = (int) event.getX();
                int y = (int) event.getY();
                x -= getTotalPaddingLeft();      // 减去左边距
                y -= getTotalPaddingTop();       // 减去上边距
                x += getScrollX();               // 加上水平滚动偏移
                y += getScrollY();               // 加上垂直滚动偏移

                // 根据坐标计算光标应放置的位置
                Layout layout = getLayout();
                int line = layout.getLineForVertical(y);           // 获取触摸点所在的行
                int off = layout.getOffsetForHorizontal(line, x);  // 获取该行水平位置对应的字符偏移量
                Selection.setSelection(getText(), off);            // 设置光标位置
                break;
        }

        return super.onTouchEvent(event);
    }

    // ==================== 按键事件处理 ====================

    /**
     * 处理按键按下事件
     * 主要记录删除键按下前的光标位置
     * 
     * @param keyCode 按键码
     * @param event   按键事件
     * @return 是否消费事件
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                // 回车键按下：不处理，留给onKeyUp处理
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                // 删除键按下：记录当前光标位置，用于判断是否在开头删除
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 处理按键抬起事件
     * 实现回车创建新项、删除键删除空项的逻辑
     * 
     * @param keyCode 按键码
     * @param event   按键事件
     * @return 是否消费事件
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                // 删除键抬起
                if (mOnTextViewChangeListener != null) {
                    // 条件：光标在开头位置(0) 且 不是第一项(mIndex != 0)
                    // 此时文本框内容为空，按删除键应删除当前项并合并到上一项
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;  // 消费事件，阻止默认处理
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
                
            case KeyEvent.KEYCODE_ENTER:
                // 回车键抬起：在当前位置分割文本，创建新项
                if (mOnTextViewChangeListener != null) {
                    int selectionStart = getSelectionStart();
                    // 获取光标右侧的文本内容
                    String text = getText().subSequence(selectionStart, length()).toString();
                    // 保留光标左侧的文本，删除右侧内容
                    setText(getText().subSequence(0, selectionStart));
                    // 通知监听器创建新项，将右侧文本作为新项的内容
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
                
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    // ==================== 焦点变化处理 ====================

    /**
     * 处理焦点变化事件
     * 当焦点变化时，通知监听器当前文本框是否有内容
     * 用于控制复选框的显示/隐藏
     * 
     * @param focused             是否获得焦点
     * @param direction           焦点移动方向
     * @param previouslyFocusedRect 之前焦点视图的矩形区域
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                // 失去焦点且内容为空 -> 无文本
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                // 获得焦点或有内容 -> 有文本
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    // ==================== 上下文菜单处理 ====================

    /**
     * 创建上下文菜单
     * 当文本中包含链接（URLSpan）且用户长按时，显示"打开链接"等菜单选项
     * 
     * @param menu 上下文菜单
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        // 检查文本是否包含Span样式（如链接）
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            // 获取选中区域内的所有URLSpan
            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            
            // 只有一个链接时才显示菜单（避免多个链接的歧义）
            if (urls.length == 1) {
                int defaultResId = 0;
                
                // 根据链接协议确定菜单文字资源ID
                for (String schema : sSchemaActionResMap.keySet()) {
                    if (urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                // 如果没有匹配的协议，使用默认文字
                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;  // "打开链接"
                }

                // 添加菜单项并设置点击监听器
                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                // 点击时触发链接的点击事件（打开浏览器/拨号器等）
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}