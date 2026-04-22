package net.micode.notes.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 自定义涂鸦画板视图
 * 
 * 功能说明：
 * 1. 支持手指触摸绘制自由线条
 * 2. 支持设置画笔颜色和粗细
 * 3. 支持撤销上一步操作
 * 4. 支持清空画板
 * 5. 支持将画板内容保存为PNG图片文件
 * 6. 使用贝塞尔曲线实现平滑绘制效果
 * 
 * 主要用于NoteEditActivity中的涂鸦功能
 */
public class DrawingView extends View {

    // ==================== 绘制相关成员变量 ====================
    
    private Paint mPaint;                    // 当前画笔（用于绘制当前路径）
    private Path mCurrentPath;               // 当前正在绘制的路径（尚未完成）
    private List<DrawingPath> mPaths = new ArrayList<>();  // 已完成绘制的路径列表
    private float mTouchX, mTouchY;          // 上一个触摸点的坐标（用于平滑绘制）
    private float mStrokeWidth = 5f;          // 默认画笔粗细（5像素）

    // ==================== 内部类：封装路径和画笔信息 ====================
    
    /**
     * 绘制路径数据类
     * 封装一条完整的绘制路径及其对应的画笔属性
     * 
     * 设计目的：
     * - 每条路径可以有不同的颜色和粗细
     * - 支持撤销操作（按路径为单位删除）
     * - 支持保存和恢复绘制状态
     */
    private static class DrawingPath {
        Path path;      // 路径对象（记录绘制轨迹）
        Paint paint;    // 该路径使用的画笔（包含颜色、粗细等属性）
    }

    // ==================== 构造函数 ====================

    /**
     * 构造函数（用于代码动态创建）
     */
    public DrawingView(Context context) {
        super(context);
        init();
    }

    /**
     * 构造函数（用于XML布局解析）
     */
    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // ==================== 初始化方法 ====================

    /**
     * 初始化画笔和路径
     * 设置画笔的默认属性
     */
    private void init() {
        mPaint = new Paint();
        mPaint.setColor(Color.BLACK);                    // 默认黑色画笔
        mPaint.setStyle(Paint.Style.STROKE);             // 描边样式（只绘制轮廓，不填充）
        mPaint.setStrokeWidth(mStrokeWidth);             // 设置线条粗细
        mPaint.setAntiAlias(true);                       // 开启抗锯齿，使线条更平滑
        mPaint.setStrokeCap(Paint.Cap.ROUND);            // 线条端点样式为圆形
        mPaint.setStrokeJoin(Paint.Join.ROUND);          // 线条连接处样式为圆形

        mCurrentPath = new Path();                        // 初始化当前绘制路径
    }

    // ==================== 触摸事件处理 ====================

    /**
     * 处理触摸事件，实现手绘功能
     * 
     * 绘制流程：
     * 1. ACTION_DOWN：开始新路径，移动到起始点
     * 2. ACTION_MOVE：添加路径点，使用二次贝塞尔曲线实现平滑连接
     * 3. ACTION_UP：保存完成的路径，准备开始新路径
     * 
     * @param event 触摸事件
     * @return 始终返回true，表示消费所有触摸事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();      // 当前触摸点的X坐标
        float y = event.getY();      // 当前触摸点的Y坐标

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // ===== 手指按下：开始新路径 =====
                mCurrentPath = new Path();
                mCurrentPath.moveTo(x, y);    // 将路径起点移动到按下位置
                mTouchX = x;                  // 记录当前坐标作为上一个点
                mTouchY = y;
                invalidate();                 // 触发重绘
                return true;

            case MotionEvent.ACTION_MOVE:
                // ===== 手指移动：绘制路径 =====
                // 计算移动距离，只有移动超过阈值(2像素)时才添加点
                // 这样可以避免微小抖动产生的过多点，同时保证绘制平滑
                float dx = Math.abs(x - mTouchX);
                float dy = Math.abs(y - mTouchY);
                if (dx >= 2 || dy >= 2) {
                    // 使用二次贝塞尔曲线连接上一个点和当前点
                    // quadTo(x1, y1, x2, y2)：
                    // - (mTouchX, mTouchY) 是控制点（上一个触摸点）
                    // - ((x+mTouchX)/2, (y+mTouchY)/2) 是终点（中点位置）
                    // 使用中点作为终点可以使曲线更加平滑自然
                    mCurrentPath.quadTo(mTouchX, mTouchY, (x + mTouchX) / 2, (y + mTouchY) / 2);
                    mTouchX = x;              // 更新上一个点坐标
                    mTouchY = y;
                }
                invalidate();                 // 触发重绘
                return true;

            case MotionEvent.ACTION_UP:
                // ===== 手指抬起：保存完成路径 =====
                DrawingPath drawingPath = new DrawingPath();
                drawingPath.path = mCurrentPath;
                drawingPath.paint = new Paint(mPaint);  // 复制当前画笔状态（保存颜色和粗细）
                mPaths.add(drawingPath);                // 添加到已完成路径列表
                mCurrentPath = new Path();              // 创建新路径准备下次绘制
                invalidate();                           // 触发重绘
                return true;
        }
        return super.onTouchEvent(event);
    }

    // ==================== 绘制方法 ====================

    /**
     * 绘制画板内容
     * 
     * 绘制顺序：
     * 1. 先绘制所有已完成的路径
     * 2. 最后绘制当前正在绘制的路径（保证在最上层）
     * 
     * @param canvas 画布对象
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 绘制所有已完成的路径（每条路径使用其保存的画笔属性）
        for (DrawingPath drawingPath : mPaths) {
            canvas.drawPath(drawingPath.path, drawingPath.paint);
        }
        
        // 绘制当前正在绘制的路径（使用当前画笔）
        canvas.drawPath(mCurrentPath, mPaint);
    }

    // ==================== 公共操作方法 ====================

    /**
     * 清空画板
     * 清除所有已绘制的路径，重置当前路径
     */
    public void clear() {
        mPaths.clear();                // 清空已完成路径列表
        mCurrentPath = new Path();     // 重置当前路径
        invalidate();                  // 触发重绘
    }

    /**
     * 设置画笔颜色
     * 
     * @param color 颜色值（如 Color.RED, Color.BLUE 等）
     */
    public void setColor(int color) {
        mPaint.setColor(color);
    }

    /**
     * 设置画笔粗细
     * 
     * @param width 线条宽度（像素），值越大线条越粗
     */
    public void setStrokeWidth(float width) {
        mStrokeWidth = width;
        mPaint.setStrokeWidth(width);
    }

    /**
     * 获取画板内容的Bitmap对象
     * 用于导出画板内容为图片
     * 
     * 注意：调用此方法前请确保View已经完成布局（宽高不为0）
     * 
     * @return 包含画板内容的Bitmap对象，背景为白色
     */
    public Bitmap getBitmap() {
        // 创建与View相同尺寸的空白Bitmap（ARGB_8888格式支持透明度）
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // 设置白色背景（画板默认是透明背景，保存时需要白色底）
        canvas.drawColor(Color.WHITE);
        
        // 将View的内容绘制到Bitmap上
        draw(canvas);
        
        return bitmap;
    }

    /**
     * 保存画板内容为PNG图片文件
     * 
     * 保存路径：/storage/emulated/0/Android/data/包名/files/drawings/
     * 文件命名：drawing_时间戳.png
     * 
     * @return 保存成功返回文件绝对路径，失败返回null
     */
    public String saveAsImage() {
        // 检查View是否已完成布局（宽高是否为0）
        if (getWidth() == 0 || getHeight() == 0) {
            return null;
        }

        // 创建Bitmap并绘制画板内容
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);    // 设置白色背景
        draw(canvas);                      // 绘制路径内容

        // 生成文件名（使用时间戳确保唯一性）
        String fileName = "drawing_" + System.currentTimeMillis() + ".png";
        
        // 创建保存目录（使用应用私有外部存储目录）
        File dir = new File(getContext().getExternalFilesDir(null), "drawings");
        if (!dir.exists()) {
            dir.mkdirs();                  // 递归创建目录
        }
        File file = new File(dir, fileName);

        // 保存Bitmap为PNG文件
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);  // 100%质量压缩
            fos.flush();
            return file.getAbsolutePath();  // 返回文件完整路径
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 撤销上一步操作
     * 删除最后一条已完成的路径
     * 
     * 注意：只能撤销已完成的路径，当前正在绘制的路径无法撤销
     */
    public void undo() {
        if (mPaths.size() > 0) {
            mPaths.remove(mPaths.size() - 1);  // 移除最后一条路径
            invalidate();                       // 触发重绘
        }
    }
}