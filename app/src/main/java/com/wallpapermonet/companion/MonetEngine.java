package com.wallpapermonet.companion;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.wallpapermonet.companion.mcu.hct.Hct;
import com.wallpapermonet.companion.mcu.quantize.QuantizerCelebi;
import com.wallpapermonet.companion.mcu.score.Score;
import com.wallpapermonet.companion.mcu.palettes.TonalPalette;

import java.util.List;

/** Local Material Color Utilities engine. Invoke its methods off the UI thread. */
public final class MonetEngine {
    public static final int[] INDICES = {0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
    public static final String[] NAMES = {"accent1", "accent2", "accent3", "neutral1", "neutral2"};
    public static final String[] STYLES = {"TONAL_SPOT", "VIBRANT", "EXPRESSIVE", "SPRITZ", "RAINBOW", "FRUIT_SALAD", "MONOCHROMATIC"};
    private static final int FALLBACK = 0xFF1B6EF3;

    private MonetEngine() {}

    public static boolean supportsStyle(String style) {
        for (String candidate : STYLES) if (candidate.equals(style)) return true;
        return false;
    }

    /** Returns five palettes in Android resource order, as opaque ARGB values. */
    public static int[][] palette(int seed, String style) {
        // 2021 scheme constants adapted from Google's ColorSpec2021 (Apache-2.0).
        double h = Hct.fromInt(seed | 0xFF000000).getHue();
        double[] hues = {h,h,h,h,h};
        double[] chroma;
        switch (style) {
            case "TONAL_SPOT": hues[2]=h+60; chroma=new double[]{36,16,24,6,8}; break;
            case "VIBRANT":
                hues[1]=rotated(h,new double[]{0,41,61,101,131,181,251,301,360},new double[]{18,15,10,12,15,18,15,12});
                hues[2]=rotated(h,new double[]{0,41,61,101,131,181,251,301,360},new double[]{35,30,20,25,30,35,30,25});
                chroma=new double[]{200,24,32,10,12}; break;
            case "EXPRESSIVE":
                hues[0]=h+240;
                hues[1]=rotated(h,new double[]{0,21,51,121,151,191,271,321,360},new double[]{45,95,45,20,45,90,45,45});
                hues[2]=rotated(h,new double[]{0,21,51,121,151,191,271,321,360},new double[]{120,120,20,45,20,15,20,120});
                hues[3]=h+15; hues[4]=h+15; chroma=new double[]{40,24,32,8,12}; break;
            case "SPRITZ": chroma=new double[]{12,8,16,2,2}; break;
            case "RAINBOW": hues[2]=h+60; chroma=new double[]{48,16,24,0,0}; break;
            case "FRUIT_SALAD": hues[0]=h-50; hues[1]=h-50; chroma=new double[]{48,36,36,10,16}; break;
            case "MONOCHROMATIC": chroma=new double[]{0,0,0,0,0}; break;
            default: throw new IllegalArgumentException("不支持的主题样式: " + style);
        }
        TonalPalette[] sources = new TonalPalette[5];
        for (int i=0; i<5; i++) sources[i] = TonalPalette.fromHueAndChroma((hues[i]%360+360)%360, chroma[i]);
        int[][] result = new int[sources.length][INDICES.length];
        for (int p = 0; p < sources.length; p++) {
            for (int i = 0; i < INDICES.length; i++) {
                result[p][i] = sources[p].tone(100 - INDICES[i] / 10);
            }
        }
        return result;
    }

    private static double rotated(double hue, double[] boundaries, double[] rotation) {
        for (int i=0;i<rotation.length;i++) if (hue>=boundaries[i] && hue<boundaries[i+1]) return (hue+rotation[i])%360;
        return hue;
    }

    /** Quantizes a temporary <=520px copy; preserves the source bitmap unchanged. */
    public static List<Integer> candidates(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) throw new IllegalArgumentException("图片不可用");
        double scale = Math.min(1.0, 520.0 / Math.max(bitmap.getWidth(), bitmap.getHeight()));
        int width = Math.max(1, (int) Math.round(bitmap.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(bitmap.getHeight() * scale));
        Bitmap small = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(small);
            canvas.drawColor(Color.WHITE);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(bitmap, null, new android.graphics.Rect(0, 0, width, height), paint);
            int[] pixels = new int[width * height];
            small.getPixels(pixels, 0, width, 0, 0, width, height);
            return Score.score(QuantizerCelebi.quantize(pixels, 128), 8, FALLBACK);
        } finally {
            small.recycle();
        }
    }
}
