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

package net.micode.notes.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;


/**
 * NoteWidgetProvider_4x - 4x4桌面小部件
 * 继承自NoteWidgetProvider，实现4x4尺寸小部件的特定配置
 */
public class NoteWidgetProvider_4x extends NoteWidgetProvider {
    /**
     * 更新小部件
     * 调用父类的update方法执行实际的更新逻辑
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 获取小部件布局文件ID
     * @return R.layout.widget_4x
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_4x;
    }

    /**
     * 获取小部件背景资源ID
     * @param bgId 背景颜色ID
     * @return 对应的4x小部件背景资源
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget4xBgResource(bgId);
    }

    /**
     * 获取小部件类型
     * @return TYPE_WIDGET_4X
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_4X;
    }
}
