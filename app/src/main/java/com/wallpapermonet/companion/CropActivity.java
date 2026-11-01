package com.wallpapermonet.companion;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Receives a readable content URI as intent data; returns a private cache path on success. */
public final class CropActivity extends AppCompatActivity {
    public static final String EXTRA_RESULT_PATH="crop_result_path";
    public static final String EXTRA_SOURCE_WIDTH="crop_source_width";
    public static final String EXTRA_SOURCE_HEIGHT="crop_source_height";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private CropImageView image;
    private View progress, done, reset;
    private Uri uri;
    private ImageProcessor.Info info;
    private int targetWidth,targetHeight;
    private volatile boolean destroyed;
    private RectF savedCrop;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_crop);
        if(state!=null) { float[] r=state.getFloatArray("crop_rect"); if(r!=null && r.length==4) savedCrop=new RectF(r[0],r[1],r[2],r[3]); }
        View root=findViewById(R.id.crop_root);
        root.setOnApplyWindowInsetsListener((v,insets) -> { android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()); v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets; });
        MaterialToolbar toolbar=findViewById(R.id.crop_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material); toolbar.setNavigationOnClickListener(v -> finish());
        image=findViewById(R.id.crop_image); progress=findViewById(R.id.crop_progress); done=findViewById(R.id.crop_done); reset=findViewById(R.id.crop_reset);
        findViewById(R.id.crop_cancel).setOnClickListener(v -> finish()); reset.setOnClickListener(v -> image.reset()); done.setOnClickListener(v -> save());
        @SuppressWarnings("deprecation") Display display=((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        Display.Mode mode=display.getMode(); int a=mode.getPhysicalWidth(), b=mode.getPhysicalHeight();
        if(a<=0 || b<=0) { Point p=new Point(); display.getRealSize(p); a=p.x; b=p.y; }
        targetWidth=Math.min(a,b); targetHeight=Math.max(a,b);
        ((TextView)findViewById(R.id.crop_ratio)).setText("目标屏幕比例  " + targetWidth + " : " + targetHeight);
        uri=getIntent().getData(); if(uri==null) { failure("没有选择图片"); return; }
        busy(true);
        worker.execute(() -> {
            try { ImageProcessor.Info loaded=ImageProcessor.inspect(this,uri); Bitmap preview=ImageProcessor.preview(this,uri,loaded);
                if(destroyed) { preview.recycle(); return; }
                runOnUiThread(() -> { if(destroyed) { preview.recycle(); return; } info=loaded; image.setImage(preview,targetWidth,targetHeight); if(savedCrop!=null) image.post(() -> { image.restoreCrop(savedCrop); savedCrop=null; }); busy(false); });
            } catch (Exception | OutOfMemoryError error) { runOnUiThread(() -> failure("无法读取图片："+error.getMessage())); }
        });
    }
    private void save() {
        if(info==null) return;
        final RectF crop;
        try { crop=image.getNormalizedCrop(); }
        catch (IllegalStateException e) { Toast.makeText(this,"图片尚未准备好，请稍后重试",Toast.LENGTH_SHORT).show(); return; }
        busy(true);
        worker.execute(() -> {
            try { File file=ImageProcessor.crop(this,uri,info,crop,targetWidth,targetHeight);
                if(destroyed) { file.delete(); return; }
                runOnUiThread(() -> { if(destroyed) { file.delete(); return; } Intent result=new Intent(); result.putExtra(EXTRA_RESULT_PATH,file.getAbsolutePath()); result.putExtra(EXTRA_SOURCE_WIDTH,info.width); result.putExtra(EXTRA_SOURCE_HEIGHT,info.height); setResult(Activity.RESULT_OK,result); finish(); });
            } catch(Exception | OutOfMemoryError error) { runOnUiThread(() -> { if(!destroyed) { busy(false); Toast.makeText(this,"裁切失败："+error.getMessage(),Toast.LENGTH_LONG).show(); } }); }
        });
    }
    private void busy(boolean value) { progress.setVisibility(value?View.VISIBLE:View.GONE); done.setEnabled(!value); reset.setEnabled(!value); }
    private void failure(String message) { if(!destroyed) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); finish(); } }
    @Override protected void onSaveInstanceState(Bundle out) {
        if(info!=null) { try { RectF r=image.getNormalizedCrop(); out.putFloatArray("crop_rect",new float[]{r.left,r.top,r.right,r.bottom}); } catch (IllegalStateException ignored) { } }
        else if(savedCrop!=null) out.putFloatArray("crop_rect",new float[]{savedCrop.left,savedCrop.top,savedCrop.right,savedCrop.bottom});
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() { destroyed=true; worker.shutdownNow(); super.onDestroy(); }
}
