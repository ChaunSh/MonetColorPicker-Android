package com.wallpapermonet.companion;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    private static final int PICK_IMAGE=1, CROP=2, IMPORT=3, EXPORT=4;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private SystemThemeManager settings;
    private LinearLayout setup, current, candidatesBox, previewBox, systemBox;
    private ImageView image; private TextView imageInfo, permission; private EditText seedInput;
    private ProgressBar progress; private FrameLayout content; private MaterialToolbar toolbar; private MaterialAutoCompleteTextView styleSpinner; private MaterialButton applyButton;
    private int seed=0xff4285f4; private String style="TONAL_SPOT", imagePath, source="无图片";
    private int[][] palette; private List<Integer> candidates=new ArrayList<>();
    private Integer[][] systemPalette; private Integer systemSeed; private String systemStyle, systemSource;
    private String pendingExport; private int sequence, imageGeneration, colorRevision; private boolean analysisPending, allowPendingAutoSeed;
    private final String[] styles=MonetEngine.STYLES;
    @Override public void onCreate(Bundle b) { super.onCreate(b); setContentView(R.layout.activity_main); settings=new SystemThemeManager(this);
        if(b!=null){seed=b.getInt("seed",seed);style=b.getString("style",style);imagePath=b.getString("imagePath");source=b.getString("source",source);pendingExport=b.getString("pendingExport");analysisPending=b.getBoolean("analysisPending");allowPendingAutoSeed=b.getBoolean("allowPendingAutoSeed");int[] a=b.getIntArray("candidates");if(a!=null)for(int c:a)candidates.add(c);}
        content=findViewById(R.id.content);toolbar=findViewById(R.id.toolbar);
        View root=findViewById(R.id.root); root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});
        setup=page();current=page();buildSetup();buildCurrent();showPage(false); BottomNavigationView nav=findViewById(R.id.navigation);nav.setOnItemSelectedListener(item->{showPage(item.getItemId()==R.id.current);return true;});if(b!=null&&b.getBoolean("currentPage"))nav.setSelectedItemId(R.id.current);calculate();if(analysisPending&&imagePath!=null)analyze(imagePath,allowPendingAutoSeed);
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("seed",seed);b.putString("style",style);b.putString("imagePath",imagePath);b.putString("source",source);b.putString("pendingExport",pendingExport);b.putBoolean("analysisPending",analysisPending);b.putBoolean("allowPendingAutoSeed",allowPendingAutoSeed);BottomNavigationView nav=findViewById(R.id.navigation);b.putBoolean("currentPage",nav.getSelectedItemId()==R.id.current);int[] a=new int[candidates.size()];for(int i=0;i<a.length;i++)a[i]=candidates.get(i);b.putIntArray("candidates",a);}
    @Override protected void onResume(){super.onResume();if(permission!=null) updatePermission();}
    @Override protected void onDestroy(){super.onDestroy();worker.shutdown();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private LinearLayout page(){ScrollView s=new ScrollView(this);s.setFillViewport(true); LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(20),dp(8),dp(20),dp(32));s.addView(p);p.setTag(s);return p;}
    private void showPage(boolean now){content.removeAllViews();View v=(View)(now?current:setup).getTag();content.addView(v);toolbar.setTitle(now?"当前配色":"设置配色");if(now)refreshSystem();}
    private TextView label(LinearLayout p,String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(0xff20232a);t.setPadding(0,dp(10),0,dp(6));p.addView(t);return t;}
    private MaterialButton button(LinearLayout p,String text,Runnable r){MaterialButton b=action(text,r,false);p.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private MaterialButton action(String text,Runnable r,boolean secondary){MaterialButton b=secondary?new MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle):new MaterialButton(this);b.setText(text);b.setSingleLine(false);b.setMaxLines(2);b.setMinHeight(dp(48));b.setOnClickListener(v->r.run());return b;}
    private void actions(LinearLayout parent,String left,Runnable leftAction,String right,Runnable rightAction){
        AdaptiveActions row=new AdaptiveActions(this);row.addView(action(left,leftAction,true));row.addView(action(right,rightAction,true));parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    /** Buttons stack when the available width cannot accommodate their actual scaled labels. */
    private final class AdaptiveActions extends LinearLayout {
        private Boolean stacked;
        AdaptiveActions(Context context){super(context);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec),widest=0;
            for(int i=0;i<getChildCount();i++){MaterialButton b=(MaterialButton)getChildAt(i);widest=Math.max(widest,Math.max(dp(100),(int)Math.ceil(b.getPaint().measureText(b.getText().toString()))+dp(48)));}
            int need=widest*getChildCount()+dp(8)*Math.max(0,getChildCount()-1);
            boolean vertical=width<need;
            if(stacked==null||stacked!=vertical){stacked=vertical;setOrientation(vertical?VERTICAL:HORIZONTAL);
                for(int i=0;i<getChildCount();i++){LayoutParams lp=new LayoutParams(vertical?LayoutParams.MATCH_PARENT:0,LayoutParams.WRAP_CONTENT,vertical?0:1);if(!vertical&&i>0)lp.setMarginStart(dp(8));getChildAt(i).setLayoutParams(lp);}}
            super.onMeasure(widthSpec,heightSpec);
        }
    }
    private LinearLayout box(LinearLayout p){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);p.addView(b);return b;}
    private LinearLayout section(LinearLayout parent,String title,String description){
        MaterialCardView card=new MaterialCardView(this);card.setRadius(dp(20));card.setCardElevation(0);card.setCardBackgroundColor(0xfff0f0f4);card.setStrokeColor(0xffdcdde2);card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(16);parent.addView(card,cp);
        LinearLayout inner=new LinearLayout(this);inner.setOrientation(LinearLayout.VERTICAL);inner.setPadding(dp(16),dp(8),dp(16),dp(16));card.addView(inner);
        TextView heading=label(inner,title,20);heading.setTypeface(null,android.graphics.Typeface.BOLD);
        if(description!=null){TextView subtitle=label(inner,description,13);subtitle.setTextColor(0xff5c6069);subtitle.setPadding(0,0,0,dp(8));}
        return inner;
    }
    private void buildSetup(){
        LinearLayout pictures=section(setup,"当前图片","读取壁纸，或选择图片后调整裁切区域");
        image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);pictures.addView(image,new LinearLayout.LayoutParams(-1,dp(200)));imageInfo=label(pictures,"",14);showImage();
        actions(pictures,"读取当前壁纸",this::wallpaper,"选择图片",()->{imageGeneration++;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_IMAGE);});
        progress=new ProgressBar(this);progress.setVisibility(View.GONE);LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(dp(36),dp(36));progressParams.gravity=Gravity.CENTER_HORIZONTAL;setup.addView(progress,progressParams);
        LinearLayout color=section(setup,"主色与样式","点击候选色选择，长按复制色值");
        candidatesBox=box(color);
        View controls=getLayoutInflater().inflate(R.layout.view_theme_controls,color,false);color.addView(controls);seedInput=controls.findViewById(R.id.seed_input);TextInputLayout seedLayout=controls.findViewById(R.id.seed_layout);controls.findViewById(R.id.seed_apply).setOnClickListener(v->{try{seed=ThemeConfig.parseColor(seedInput.getText().toString().trim());colorRevision++;allowPendingAutoSeed=false;seedLayout.setError(null);calculate();}catch(Exception e){seedLayout.setError("请输入有效的不透明 HEX 颜色");}});
        styleSpinner=controls.findViewById(R.id.style_input);styleSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,styles));styleSpinner.setText(style,false);styleSpinner.setOnItemClickListener((parent,view,position,id)->{if(!style.equals(styles[position])){style=styles[position];calculate();}});
        LinearLayout paletteSection=section(setup,"配色预览","左右滑动查看色阶，点击色块查看色值");previewBox=box(paletteSection);
        LinearLayout apply=section(setup,"应用到系统","写入前会自动备份当前系统配置");permission=label(apply,"",14);applyButton=button(apply,"应用到系统",()->{if(!settings.hasPermission()){toast("请先授予特殊权限");return;}int s=seed;String st=style;run(()->{settings.apply(s,st);return "配置写入并回读成功；系统颜色资源可能稍后更新。";},msg->{toast(msg);refreshSystem();});});buttonSecondary(apply,"复制 ADB 授权命令",()->copy("adb shell pm grant "+getPackageName()+" android.permission.WRITE_SECURE_SETTINGS"));
        LinearLayout manage=section(setup,"配置管理","导入只更新预览，不会自动修改系统");actions(manage,"导入配置",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),IMPORT),"导出配置",()->export(false));buttonSecondary(manage,"备份与恢复",this::backups);updatePermission();updateCandidates();
    }
    private void buttonSecondary(LinearLayout parent,String text,Runnable r){parent.addView(action(text,r,true),new LinearLayout.LayoutParams(-1,-2));}
    private void buildCurrent(){
        LinearLayout controls=section(current,"系统配色","显示从设备实际读取的主题和颜色资源");actions(controls,"刷新",this::refreshSystem,"导出当前配色",()->export(true));
        LinearLayout colors=section(current,"主题参数与色阶","左右滑动查看色阶，点击色块查看色值");systemBox=box(colors);
    }
    private void updatePermission(){boolean granted=settings.hasPermission();permission.setText(granted?"已授予系统写入权限":"尚未授予 WRITE_SECURE_SETTINGS。请在电脑运行下方命令；其他功能仍可使用。");if(applyButton!=null)applyButton.setEnabled(granted);}
    private void showImage(){if(image==null)return;if(imagePath==null){imageInfo.setText("尚未选择图片");return;}String path=imagePath;String label=source;imageInfo.setText("正在加载图片…");run(()->{BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,bounds);if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("无法读取图片预览");BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth/options.inSampleSize,bounds.outHeight/options.inSampleSize)>1024)options.inSampleSize*=2;Bitmap b=BitmapFactory.decodeFile(path,options);if(b==null)throw new IOException("无法解码图片预览");return new Object[]{b,bounds.outWidth,bounds.outHeight};},result->{if(!path.equals(imagePath)){((Bitmap)result[0]).recycle();return;}image.setImageBitmap((Bitmap)result[0]);imageInfo.setText(label+" · "+result[1]+" × "+result[2]);});}
    private void updateCandidates(){
        candidatesBox.removeAllViews();
        if(candidates.isEmpty()){TextView hint=label(candidatesBox,"选择图片后将在这里显示候选色",14);hint.setTextColor(0xff5c6069);return;}
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setFillViewport(false);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);scroll.addView(row);candidatesBox.addView(scroll);
        for(int c:candidates){MaterialButton b=action(ThemeConfig.color(c)+(c==seed?"  已选择":""),()->{seed=c;colorRevision++;allowPendingAutoSeed=false;calculate();},true);GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(c);circle.setStroke(dp(c==seed?3:1),c==seed?0xff20232a:0xff777777);b.setIcon(circle);b.setIconSize(dp(28));b.setIconTint(null);b.setContentDescription(ThemeConfig.color(c)+(c==seed?"，已选择":"，点击选择，长按复制"));b.setOnLongClickListener(v->{copy(ThemeConfig.color(c));return true;});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginEnd(dp(8));row.addView(b,lp);}
    }
    private void calculate(){final int generation=++sequence, s=seed;final String st=style;palette=null;seedInput.setText(ThemeConfig.color(seed));updateCandidates();run(()->MonetEngine.palette(s,st),p->{if(generation!=sequence)return;palette=p;displayPalette(previewBox,boxed(p));});}
    private Integer[][] boxed(int[][] p){Integer[][] out=new Integer[5][13];for(int g=0;g<5;g++)for(int i=0;i<13;i++)out[g][i]=p[g][i];return out;}
    private void displayPalette(LinearLayout parent,Integer[][] colors){parent.removeAllViews();for(int g=0;g<5;g++){label(parent,ThemeConfig.GROUPS[g],16);HorizontalScrollView h=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);h.addView(row);parent.addView(h);for(int i=0;i<13;i++){final Integer c=colors[g][i];final String name=ThemeConfig.GROUPS[g]+"_"+ThemeConfig.INDICES[i];TextView sw=new TextView(this);sw.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);sw.setText(c==null?"不可用":String.valueOf(ThemeConfig.INDICES[i]));sw.setTextSize(9);sw.setTextColor(c!=null&&Color.luminance(c)<.35?Color.WHITE:Color.BLACK);sw.setBackgroundColor(c==null?0xffdddddd:c);sw.setContentDescription(name+" "+(c==null?"不可用":ThemeConfig.color(c)));sw.setOnClickListener(v->{if(c!=null)new MaterialAlertDialogBuilder(this).setTitle(name).setMessage("HEX: "+String.format(Locale.ROOT,"#%06X",c&0xffffff)+"\nARGB: "+ThemeConfig.color(c)).setPositiveButton("复制 ARGB",(d,w)->copy(ThemeConfig.color(c))).setNegativeButton("关闭",null).show();});row.addView(sw,new LinearLayout.LayoutParams(dp(46),dp(56)));}}}
    private void wallpaper(){
        if (!Environment.isExternalStorageManager()) {
            new MaterialAlertDialogBuilder(this).setTitle("允许读取当前壁纸")
                .setMessage("Android 要求授予“所有文件访问权限”才能读取当前静态壁纸。该权限还允许本应用访问共享存储中的其他文件；本应用仅在你操作时读取所选文件和壁纸。拒绝后仍可使用图片选择和配色预览。")
                .setNegativeButton("取消",null).setPositiveButton("打开系统设置",(dialog,which)->{
                    try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName()))); }
                    catch (android.content.ActivityNotFoundException e) { try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); } catch (Exception ex) { error(ex); } }
                }).show();
            return;
        }
        final int generation=++imageGeneration;
        run(()->{
            WallpaperManager wm=WallpaperManager.getInstance(this);
            if(wm.getWallpaperInfo()!=null)throw new IOException("动态壁纸无法读取，请选择本地图片");
            File f=File.createTempFile("wallpaper-", ".img",getCacheDir());
            try {
                ParcelFileDescriptor descriptor;
                try { descriptor=wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM); }
                catch(SecurityException e){throw new IOException("系统拒绝读取壁纸，请检查所有文件访问权限或选择本地图片",e);}
                if(descriptor==null)throw new IOException("系统未提供静态壁纸文件，请选择本地图片");
                try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(descriptor);OutputStream out=new FileOutputStream(f)){
                    byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);
                }
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("系统壁纸文件无法解码，请选择本地图片");
                return f.getAbsolutePath();
            } catch(Exception | OutOfMemoryError e){f.delete();throw e;}
        },p->{if(generation!=imageGeneration){new File(p).delete();return;}imagePath=p;source="系统主屏幕壁纸";showImage();analyze(p);});
    }
    private void analyze(String path){analyze(path,true);}
    private void analyze(String path,boolean autoSeed){analysisPending=true;allowPendingAutoSeed=autoSeed;final int generation=imageGeneration, revision=colorRevision;run(()->{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);o.inJustDecodeBounds=false;o.inSampleSize=1;while(Math.max(o.outWidth/o.inSampleSize,o.outHeight/o.inSampleSize)>1040)o.inSampleSize*=2;Bitmap b=BitmapFactory.decodeFile(path,o);if(b==null)throw new IOException("无法解码图片");try{return MonetEngine.candidates(b);}finally{b.recycle();}},list->{if(generation!=imageGeneration||!path.equals(imagePath))return;analysisPending=false;candidates=list;if(autoSeed&&revision==colorRevision&&!list.isEmpty())seed=list.get(0);calculate();});}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null){if(request==EXPORT)pendingExport=null;return;}
        if(request==PICK_IMAGE){if(data.getData()==null){toast("未获取图片");return;}Intent i=new Intent(this,CropActivity.class).setData(data.getData()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,CROP);}
        else if(request==CROP){String path=data.getStringExtra(CropActivity.EXTRA_RESULT_PATH);if(path==null||!new File(path).isFile()){toast("无法读取裁切结果");return;}imageGeneration++;imagePath=path;source="外部图片（已裁切）";showImage();analyze(imagePath);}
        else if(request==IMPORT){Uri uri=data.getData();if(uri==null){toast("未选择配置文件");return;}final int importRevision=++colorRevision;allowPendingAutoSeed=false;run(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法打开配置文件");return ThemeConfig.read(in);}},c->{if(importRevision!=colorRevision)return;if(!c.canEdit()){toast("快照缺少可重建的主色或样式");return;}int position=Arrays.asList(styles).indexOf(c.style);if(position<0){toast("暂不支持该样式");return;}seed=c.seed;style=c.style;styleSpinner.setText(style,false);calculate();final int generation=sequence;run(()->MonetEngine.palette(c.seed,c.style),p->{if(generation==sequence&&!c.matches(p))toast("导入的色板与当前算法结果不同，已重新计算");});toast("已导入编辑状态，未写入系统");});}
        else if(request==EXPORT){String json=pendingExport;pendingExport=null;Uri uri=data.getData();if(json==null||uri==null){toast("导出状态不可用，请重试");return;}run(()->{try(OutputStream o=getContentResolver().openOutputStream(uri,"w")){if(o==null)throw new IOException("无法打开目标文件");o.write(json.getBytes(StandardCharsets.UTF_8));}return "导出成功";},this::toast);}
    }
    private void refreshSystem(){run(()->{String raw=settings.readRaw();JSONObject j=new JSONObject();boolean malformed=false;if(raw!=null&&!raw.trim().isEmpty())try{j=new JSONObject(raw);}catch(Exception e){malformed=true;}Integer s=null;try{String x=j.optString(SystemThemeManager.PALETTE);if(!x.isEmpty())s=ThemeConfig.parseColor("#"+x);}catch(Exception ignored){}Integer[][] colors=new Integer[5][13];for(int g=0;g<5;g++)for(int i=0;i<13;i++){int id=getResources().getIdentifier("system_"+ThemeConfig.GROUPS[g]+"_"+ThemeConfig.INDICES[i],"color","android");if(id!=0)try{colors[g][i]=getColor(id);}catch(Exception ignored){}}return new Object[]{s,j.optString(SystemThemeManager.STYLE,null),j.optString(SystemThemeManager.SOURCE,null),colors,malformed};},a->{systemSeed=(Integer)a[0];systemStyle=(String)a[1];systemSource=(String)a[2];systemPalette=(Integer[][])a[3];systemBox.removeAllViews();label(systemBox,((Boolean)a[4]?"系统主题配置格式无效，无法读取参数。\n":"")+"主色："+(systemSeed==null?"不可用":ThemeConfig.color(systemSeed))+"\n样式："+(systemStyle==null?"不可用":systemStyle)+"\n来源："+(systemSource==null?"不可用":systemSource),15);displayPalette(box(systemBox),systemPalette);});}
    private void export(boolean system){try{if(system&&systemPalette==null){toast("请先刷新系统配色");return;}if(!system&&palette==null){toast("配色计算中");return;}pendingExport=ThemeConfig.export(system?"system-snapshot":"editable-theme",system?systemSeed:Integer.valueOf(seed),system?systemStyle:style,system?systemSource:"preset",system?systemPalette:boxed(palette));String name=(system?"MonetSystem_":"MonetTheme_")+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+".json";startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,name),EXPORT);}catch(Exception e){error(e);}}
    private void backups(){List<String> ids=settings.listBackups();if(ids.isEmpty()){toast("暂无备份");return;}String[] names=ids.toArray(new String[0]);new MaterialAlertDialogBuilder(this).setTitle("备份历史（最新在前）").setItems(names,(d,which)->{String id=names[which];new MaterialAlertDialogBuilder(this).setTitle("备份操作").setMessage(id).setPositiveButton("恢复",(x,y)->new MaterialAlertDialogBuilder(this).setTitle("确认恢复？").setMessage("将完整写回该备份的系统配置。当前配置会先备份。").setNegativeButton("取消",null).setPositiveButton("确认恢复",(z,k)->run(()->{settings.restore(id);return "已恢复并回读验证";},msg->{toast(msg);refreshSystem();})).show()).setNeutralButton("删除",(x,y)->{settings.deleteBackup(id);toast("已删除");}).setNegativeButton("取消",null).show();}).show();}
    private void copy(String s){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Monet",s));toast("已复制");}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();} private void error(Throwable e){toast("操作失败："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
    interface Work<T>{T run() throws Exception;} interface Done<T>{void accept(T value);}
    private <T> void run(Work<T> w,Done<T> d){if(progress!=null)progress.setVisibility(View.VISIBLE);worker.execute(()->{try{T value=w.run();runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){if(progress!=null)progress.setVisibility(View.GONE);d.accept(value);}});}catch(Throwable e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){if(progress!=null)progress.setVisibility(View.GONE);error(e);}});}});}
}
