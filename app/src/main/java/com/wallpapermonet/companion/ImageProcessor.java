package com.wallpapermonet.companion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** External-image processing. All methods perform I/O and must run on a worker thread. */
public final class ImageProcessor {
    private ImageProcessor() {}
    public static final class Info {
        public final int width, height, orientation;
        Info(int width,int height,int orientation) { this.width=width; this.height=height; this.orientation=orientation; }
    }
    public static Info inspect(Context context, Uri uri) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
        try (InputStream in=context.getContentResolver().openInputStream(uri)) { if(in==null) throw new IOException("无法打开图片"); BitmapFactory.decodeStream(in,null,options); }
        if(options.outWidth<=0 || options.outHeight<=0) throw new IOException("图片格式无效或不受支持");
        int orientation=1;
        try (InputStream in=context.getContentResolver().openInputStream(uri)) { if(in!=null) orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,1); }
        catch (IOException ignored) { /* Most formats have no EXIF metadata. */ }
        return new Info(options.outWidth,options.outHeight,orientation);
    }
    private static Matrix orientation(int value) {
        Matrix m=new Matrix();
        switch(value) {
            case 2: m.setScale(-1,1); break;
            case 3: m.setRotate(180); break;
            case 4: m.setScale(1,-1); break;
            case 5: m.setRotate(90); m.postScale(-1,1); break;
            case 6: m.setRotate(90); break;
            case 7: m.setRotate(-90); m.postScale(-1,1); break;
            case 8: m.setRotate(-90); break;
        }
        return m;
    }
    private static Matrix fullTransform(Info info) {
        Matrix m=orientation(info.orientation); RectF bounds=new RectF(0,0,info.width,info.height); m.mapRect(bounds); m.postTranslate(-bounds.left,-bounds.top); return m;
    }
    /** Determine the output dimensions without ever enlarging the selected region. */
    static int[] outputDimensions(float width, float height, int targetWidth, int targetHeight) {
        if (!(width > 0) || !(height > 0) || !Float.isFinite(width) || !Float.isFinite(height)
                || targetWidth <= 0 || targetHeight <= 0) throw new IllegalArgumentException("图片尺寸无效");
        float scale=Math.min(1f,Math.min(targetWidth/width,targetHeight/height));
        return new int[]{Math.max(1,Math.min(targetWidth,Math.round(width*scale))),
                Math.max(1,Math.min(targetHeight,Math.round(height*scale)))};
    }
    public static Bitmap preview(Context context,Uri uri,Info info) throws IOException {
        BitmapFactory.Options o=new BitmapFactory.Options(); o.inSampleSize=1;
        while (Math.max(info.width,info.height)/o.inSampleSize > 2048) o.inSampleSize*=2;
        Bitmap bitmap;
        try (InputStream in=context.getContentResolver().openInputStream(uri)) { if(in==null) throw new IOException("无法打开图片"); bitmap=BitmapFactory.decodeStream(in,null,o); }
        if(bitmap==null) throw new IOException("图片解码失败");
        Bitmap result=null;
        try {
            result=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),orientation(info.orientation),true);
            return result;
        } finally { if(result!=bitmap) bitmap.recycle(); }
    }
    public static File crop(Context context,Uri uri,Info info,RectF normalized,int targetWidth,int targetHeight) throws IOException {
        Matrix transform=fullTransform(info); RectF full=new RectF(0,0,info.width,info.height); transform.mapRect(full);
        RectF wanted=new RectF(normalized.left*full.width(),normalized.top*full.height(),normalized.right*full.width(),normalized.bottom*full.height());
        Matrix inverse=new Matrix(); if(!transform.invert(inverse)) throw new IOException("图片方向无效");
        RectF source=new RectF(wanted); inverse.mapRect(source);
        Rect region=new Rect(Math.max(0,(int)Math.floor(source.left)),Math.max(0,(int)Math.floor(source.top)),Math.min(info.width,(int)Math.ceil(source.right)),Math.min(info.height,(int)Math.ceil(source.bottom)));
        if(region.width()<=0 || region.height()<=0) throw new IOException("裁切区域无效");
        int[] dimensions=outputDimensions(wanted.width(),wanted.height(),targetWidth,targetHeight);
        int outW=dimensions[0], outH=dimensions[1];
        BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=1;
        boolean swapped=info.orientation>=5 && info.orientation<=8;
        int desiredRawWidth=swapped?outH:outW, desiredRawHeight=swapped?outW:outH;
        while (region.width()/(opts.inSampleSize*2)>=desiredRawWidth && region.height()/(opts.inSampleSize*2)>=desiredRawHeight) opts.inSampleSize*=2;
        Bitmap decoded;
        try (InputStream in=context.getContentResolver().openInputStream(uri)) {
            if(in==null) throw new IOException("无法打开图片");
            BitmapRegionDecoder decoder=BitmapRegionDecoder.newInstance(in,false);
            try { decoded=decoder.decodeRegion(region,opts); } finally { decoder.recycle(); }
        }
        if(decoded==null) throw new IOException("裁切图片解码失败");
        Bitmap upright=null, output=null;
        File file=null;
        boolean success=false;
        try {
            upright=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),orientation(info.orientation),true);
            if(upright!=decoded) { decoded.recycle(); decoded=null; }
            RectF mappedRegion=new RectF(region); transform.mapRect(mappedRegion);
            float sx=upright.getWidth()/mappedRegion.width(), sy=upright.getHeight()/mappedRegion.height();
            RectF cropInBitmap=new RectF((wanted.left-mappedRegion.left)*sx,(wanted.top-mappedRegion.top)*sy,(wanted.right-mappedRegion.left)*sx,(wanted.bottom-mappedRegion.top)*sy);
            output=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(output); Matrix draw=new Matrix();
            draw.setRectToRect(cropInBitmap,new RectF(0,0,outW,outH),Matrix.ScaleToFit.FILL);
            canvas.drawBitmap(upright,draw,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            file=File.createTempFile("crop_", ".png",context.getCacheDir());
            try(FileOutputStream stream=new FileOutputStream(file)) { if(!output.compress(Bitmap.CompressFormat.PNG,100,stream)) throw new IOException("保存裁切图片失败"); }
            success=true;
            return file;
        } finally {
            if(output!=null) output.recycle();
            if(upright!=null) upright.recycle();
            if(decoded!=null && decoded!=upright) decoded.recycle();
            if(!success && file!=null) file.delete();
        }
    }
}
