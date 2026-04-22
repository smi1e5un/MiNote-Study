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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框
 * 
 * 功能说明：
 * 1. 继承自AlertDialog，提供日期和时间的选择界面
 * 2. 内部包含DateTimePicker控件用于具体的选择操作
 * 3. 支持24小时制和12小时制的时间显示格式
 * 4. 实时更新对话框标题显示当前选择的日期时间
 * 5. 通过回调接口将用户选择的日期时间返回给调用者
 * 
 * 主要用于NoteEditActivity中设置笔记提醒时间
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    // ==================== 成员变量 ====================
    
    private Calendar mDate = Calendar.getInstance();  // 日历对象，用于存储和操作选择的日期时间
    private boolean mIs24HourView;                     // 是否使用24小时制显示时间
    private OnDateTimeSetListener mOnDateTimeSetListener;  // 日期时间设置完成的回调监听器
    private DateTimePicker mDateTimePicker;            // 日期时间选择器控件（自定义View）

    // ==================== 回调接口定义 ====================
    
    /**
     * 日期时间设置完成监听器接口
     * 当用户点击"确定"按钮时，会回调此接口
     */
    public interface OnDateTimeSetListener {
        /**
         * 日期时间设置完成时的回调方法
         * 
         * @param dialog 触发回调的对话框对象
         * @param date   用户选择的时间戳（毫秒值，自1970-01-01 00:00:00 UTC）
         */
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    // ==================== 构造函数 ====================

    /**
     * 构造函数
     * 初始化对话框，设置日期时间选择器，配置按钮和标题
     * 
     * @param context 上下文对象
     * @param date    初始显示的日期时间（毫秒时间戳）
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);
        
        // 创建日期时间选择器控件并设置为对话框的内容视图
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);
        
        // 设置日期时间变化监听器
        // 当用户通过选择器修改日期或时间时，更新内部日历对象和对话框标题
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                    int dayOfMonth, int hourOfDay, int minute) {
                // 将选择的值同步到Calendar对象中
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);          // 注意：Calendar的月份是0-11
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                // 更新对话框标题显示
                updateTitle(mDate.getTimeInMillis());
            }
        });
        
        // 设置初始日期时间
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0);  // 将秒数置为0（提醒精度到分钟即可）
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());
        
        // 设置对话框按钮
        // 确定按钮 - 点击时触发本类的onClick方法
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        // 取消按钮 - 点击时关闭对话框（传入null使用默认行为）
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener) null);
        
        // 根据系统设置确定使用24小时制还是12小时制
        set24HourView(DateFormat.is24HourFormat(this.getContext()));
        
        // 初始化标题显示
        updateTitle(mDate.getTimeInMillis());
    }

    // ==================== 公共方法 ====================

    /**
     * 设置是否使用24小时制显示时间
     * 
     * @param is24HourView true=24小时制，false=12小时制（带AM/PM）
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 设置日期时间设置完成的回调监听器
     * 
     * @param callBack 回调接口实现
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 更新对话框标题
     * 将当前选择的日期时间格式化为可读字符串并设置为标题
     * 
     * @param date 要显示的日期时间（毫秒时间戳）
     */
    private void updateTitle(long date) {
        // 设置格式化标志：显示年份、日期、时间
        int flag =
            DateUtils.FORMAT_SHOW_YEAR |   // 显示年份
            DateUtils.FORMAT_SHOW_DATE |   // 显示日期（月/日）
            DateUtils.FORMAT_SHOW_TIME;    // 显示时间
            
        // 根据24小时制设置时间格式标志
        // 注：原代码中无论mIs24HourView为何值都使用FORMAT_24HOUR，这可能是个bug
        // 正确的写法应该是：flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_12HOUR
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;
        
        // 格式化日期时间并设置为标题
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    // ==================== 点击事件处理 ====================

    /**
     * 对话框按钮点击事件处理
     * 当用户点击"确定"按钮时，通过回调接口将选择的日期时间返回
     * 
     * @param arg0 对话框接口（未使用）
     * @param arg1 按钮标识（未使用，因为只有确定按钮会调用此方法）
     */
    public void onClick(DialogInterface arg0, int arg1) {
        // 如果设置了监听器，回调OnDateTimeSet方法
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }

}