package com.wallpapermonet.companion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Fixed portrait frame: the bitmap moves beneath it. Coordinates exported are normalized. */
public final class CropImageView extends View {
    private final Matrix imageMatrix = new Matrix();
    private final RectF frame = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private Bitmap bitmap;
    private float aspect = 0.5f, minimumScale, currentScale, lastX, lastY;
    private int activePointer = -1;

    public CropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float next = Math.max(minimumScale, Math.min(minimumScale * 12, currentScale * detector.getScaleFactor()));
                float factor = next / currentScale;
                imageMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                currentScale = next;
                constrain(); invalidate(); return true;
            }
        });
        setContentDescription("拖动或双指缩放图片，调整裁切区域");
    }

    public void setImage(Bitmap image, int width, int height) {
        bitmap = image; aspect = (float) width / height; reset();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // A resize (for example, entering split-screen) must preserve the composition.
        RectF previous = bitmap != null && !frame.isEmpty() ? getNormalizedCrop() : null;
        super.onSizeChanged(w,h,oldw,oldh);
        reset();
        if (previous != null) restoreCrop(previous);
    }

    public void reset() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
        float availableW = Math.max(1, getWidth() - 32 * getResources().getDisplayMetrics().density);
        float availableH = Math.max(1, getHeight() - 32 * getResources().getDisplayMetrics().density);
        float w = Math.min(availableW, availableH * aspect), h = w / aspect;
        frame.set((getWidth()-w)/2, (getHeight()-h)/2, (getWidth()+w)/2, (getHeight()+h)/2);
        minimumScale = Math.max(w / bitmap.getWidth(), h / bitmap.getHeight());
        currentScale = minimumScale;
        imageMatrix.setScale(minimumScale, minimumScale);
        imageMatrix.postTranslate(frame.centerX() - bitmap.getWidth()*minimumScale/2, frame.centerY() - bitmap.getHeight()*minimumScale/2);
        invalidate();
    }

    private void constrain() {
        RectF bounds = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        imageMatrix.mapRect(bounds);
        float dx = bounds.left > frame.left ? frame.left-bounds.left : bounds.right < frame.right ? frame.right-bounds.right : 0;
        float dy = bounds.top > frame.top ? frame.top-bounds.top : bounds.bottom < frame.bottom ? frame.bottom-bounds.bottom : 0;
        imageMatrix.postTranslate(dx,dy);
    }

    /** Returns crop in the EXIF-oriented source's unit coordinate space. */
    public RectF getNormalizedCrop() {
        Matrix inverse = new Matrix();
        if (bitmap == null || frame.isEmpty() || !imageMatrix.invert(inverse)) throw new IllegalStateException("图片尚未加载");
        RectF crop = new RectF(frame); inverse.mapRect(crop);
        crop.left = Math.max(0, crop.left / bitmap.getWidth()); crop.right = Math.min(1, crop.right / bitmap.getWidth());
        crop.top = Math.max(0, crop.top / bitmap.getHeight()); crop.bottom = Math.min(1, crop.bottom / bitmap.getHeight());
        return crop;
    }

    public void restoreCrop(RectF crop) {
        if (bitmap == null || frame.isEmpty() || crop.width() <= 0 || crop.height() <= 0) return;
        currentScale = Math.max(minimumScale, frame.width() / (crop.width() * bitmap.getWidth()));
        imageMatrix.setScale(currentScale, currentScale);
        imageMatrix.postTranslate(frame.centerX() - crop.centerX() * bitmap.getWidth() * currentScale,
                frame.centerY() - crop.centerY() * bitmap.getHeight() * currentScale);
        constrain(); invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(Color.rgb(35,35,38));
        if (bitmap == null) return;
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE); canvas.drawBitmap(bitmap,imageMatrix,paint);
        paint.setColor(0x99000000);
        canvas.drawRect(0,0,getWidth(),frame.top,paint); canvas.drawRect(0,frame.bottom,getWidth(),getHeight(),paint);
        canvas.drawRect(0,frame.top,frame.left,frame.bottom,paint); canvas.drawRect(frame.right,frame.top,getWidth(),frame.bottom,paint);
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        canvas.drawRect(frame,paint); paint.setStrokeWidth(getResources().getDisplayMetrics().density); paint.setColor(0x99ffffff);
        for (int i=1;i<3;i++) { float x=frame.left+frame.width()*i/3, y=frame.top+frame.height()*i/3; canvas.drawLine(x,frame.top,x,frame.bottom,paint); canvas.drawLine(frame.left,y,frame.right,y,paint); }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) return false;
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: activePointer=event.getPointerId(0); lastX=event.getX(); lastY=event.getY(); return true;
            case MotionEvent.ACTION_MOVE:
                int index=event.findPointerIndex(activePointer);
                if (index>=0) { float x=event.getX(index), y=event.getY(index); if (!scaleDetector.isInProgress() && event.getPointerCount()==1) { imageMatrix.postTranslate(x-lastX,y-lastY); constrain(); invalidate(); } lastX=x; lastY=y; } break;
            case MotionEvent.ACTION_POINTER_UP:
                int lifted=event.getActionIndex(); if (event.getPointerId(lifted)==activePointer) { int next=lifted==0?1:0; activePointer=event.getPointerId(next); lastX=event.getX(next); lastY=event.getY(next); } else { int current=event.findPointerIndex(activePointer); if(current>=0){lastX=event.getX(current);lastY=event.getY(current);} } break;
            case MotionEvent.ACTION_UP: performClick(); activePointer=-1; break;
            case MotionEvent.ACTION_CANCEL: activePointer=-1; break;
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
