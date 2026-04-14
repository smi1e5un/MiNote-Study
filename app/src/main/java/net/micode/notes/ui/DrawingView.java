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

public class DrawingView extends View {

    private Paint mPaint;
    private Path mCurrentPath;
    private List<DrawingPath> mPaths = new ArrayList<>();
    private float mTouchX, mTouchY;
    private float mStrokeWidth = 5f;

    // 保存路径和画笔信息
    private static class DrawingPath {
        Path path;
        Paint paint;
    }

    public DrawingView(Context context) {
        super(context);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mPaint = new Paint();
        mPaint.setColor(Color.BLACK);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setAntiAlias(true);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);

        mCurrentPath = new Path();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCurrentPath = new Path();
                mCurrentPath.moveTo(x, y);
                mTouchX = x;
                mTouchY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                // 平滑绘制
                float dx = Math.abs(x - mTouchX);
                float dy = Math.abs(y - mTouchY);
                if (dx >= 2 || dy >= 2) {
                    mCurrentPath.quadTo(mTouchX, mTouchY, (x + mTouchX) / 2, (y + mTouchY) / 2);
                    mTouchX = x;
                    mTouchY = y;
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                DrawingPath drawingPath = new DrawingPath();
                drawingPath.path = mCurrentPath;
                drawingPath.paint = new Paint(mPaint);
                mPaths.add(drawingPath);
                mCurrentPath = new Path();
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 绘制所有已完成的路径
        for (DrawingPath drawingPath : mPaths) {
            canvas.drawPath(drawingPath.path, drawingPath.paint);
        }
        // 绘制当前正在画的路径
        canvas.drawPath(mCurrentPath, mPaint);
    }

    // 清除画板
    public void clear() {
        mPaths.clear();
        mCurrentPath = new Path();
        invalidate();
    }

    // 设置画笔颜色
    public void setColor(int color) {
        mPaint.setColor(color);
    }

    // 设置画笔粗细
    public void setStrokeWidth(float width) {
        mStrokeWidth = width;
        mPaint.setStrokeWidth(width);
    }

    // 获取画板内容为 Bitmap
    public Bitmap getBitmap() {
        // 创建一张空白画布
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // 设置白色背景
        canvas.drawColor(Color.WHITE);
        // 绘制画板内容
        draw(canvas);
        return bitmap;
    }

    // 保存画板为图片文件
    public String saveAsImage() {
        if (getWidth() == 0 || getHeight() == 0) {
            return null;
        }

        // 创建一张空白画布
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // 设置白色背景
        canvas.drawColor(Color.WHITE);
        // 绘制画板内容
        draw(canvas);

        // 保存到本地文件
        String fileName = "drawing_" + System.currentTimeMillis() + ".png";
        File dir = new File(getContext().getExternalFilesDir(null), "drawings");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, fileName);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // 撤销上一步
    public void undo() {
        if (mPaths.size() > 0) {
            mPaths.remove(mPaths.size() - 1);
            invalidate();
        }
    }
}