package com.wallpapermonet.companion;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Versioned interchange format. A system snapshot never acts as a write command. */
public final class ThemeConfig {
    public static final String[] GROUPS = {"accent1", "accent2", "accent3", "neutral1", "neutral2"};
    public static final int[] INDICES = {0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
    public static final int MAX_BYTES = 1024 * 1024;
    public final String type;
    public final Integer seed;
    public final String style;
    public final String source;
    /** Null entries denote unavailable system resources. */
    public final Integer[][] palette;
    private ThemeConfig(String type, Integer seed, String style, String source, Integer[][] palette) {
        this.type = type; this.seed = seed; this.style = style; this.source = source; this.palette = palette;
    }
    public boolean canEdit() { return seed != null && SystemThemeManager.validStyle(style); }
    public boolean matches(int[][] computed) {
        if (palette == null || computed == null || computed.length != 5) return false;
        for (int g = 0; g < 5; g++) {
            if (computed[g] == null || computed[g].length != 13) return false;
            for (int i = 0; i < 13; i++) if (palette[g][i] == null || palette[g][i] != computed[g][i]) return false;
        }
        return true;
    }
    public static int parseColor(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new IllegalArgumentException("颜色必须为 #RRGGBB 或 #AARRGGBB");
        long parsed = Long.parseLong(value.substring(1), 16);
        if (value.length() == 7) parsed |= 0xff000000L;
        if ((parsed >>> 24) != 255) throw new IllegalArgumentException("颜色必须不透明");
        return (int) parsed;
    }
    public static String color(int color) { return String.format(Locale.ROOT, "#%08X", color); }
    public static ThemeConfig read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size() + n > MAX_BYTES) throw new IllegalArgumentException("配置文件超过 1 MB");
            out.write(buf, 0, n);
        }
        return parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString());
    }
    public static ThemeConfig parse(String json) throws Exception {
        SystemThemeManager.checkJsonObject(json);
        JSONObject obj = new JSONObject(json);
        Object schema = obj.opt("schemaVersion");
        if (!"MonetColorPicker".equals(optionalString(obj, "format")) || !(schema instanceof Number) || !"1".equals(schema.toString())) throw new IllegalArgumentException("不支持的配置格式或版本");
        String type = optionalString(obj, "type");
        boolean editable = "editable-theme".equals(type);
        if (!editable && !"system-snapshot".equals(type)) throw new IllegalArgumentException("不支持的配置类型");
        String seedValue = optionalString(obj, "seedColor");
        Integer seed = seedValue == null ? null : parseColor(seedValue);
        String style = optionalString(obj, "themeStyle");
        String source = optionalString(obj, "colorSource");
        if (editable && (seed == null || !SystemThemeManager.validStyle(style) || !"preset".equals(source))) throw new IllegalArgumentException("缺少或无效的主色、样式或来源");
        JSONObject groups = obj.getJSONObject("palette"); Integer[][] colors = new Integer[5][13];
        for (int g = 0; g < 5; g++) {
            JSONArray values = groups.getJSONArray(GROUPS[g]);
            if (values.length() != 13) throw new IllegalArgumentException("每组必须包含 13 个色阶");
            for (int i = 0; i < 13; i++) {
                if (values.isNull(i)) {
                    if (editable) throw new IllegalArgumentException("可编辑配色不能包含空颜色");
                    colors[g][i] = null;
                } else {
                    Object value = values.get(i);
                    if (!(value instanceof String)) throw new IllegalArgumentException("调色板颜色必须是字符串");
                    colors[g][i] = parseColor((String) value);
                }
            }
        }
        return new ThemeConfig(type, seed, style, source, colors);
    }
    private static String optionalString(JSONObject obj, String key) throws Exception {
        if (!obj.has(key) || obj.isNull(key)) return null;
        Object value = obj.get(key);
        if (!(value instanceof String)) throw new IllegalArgumentException(key + " 必须是字符串");
        return (String) value;
    }
    public static String export(String type, Integer seed, String style, String source, Integer[][] palette) throws Exception {
        boolean editable = "editable-theme".equals(type);
        if (!editable && !"system-snapshot".equals(type)) throw new IllegalArgumentException("无效配置类型");
        if (editable && (seed == null || !SystemThemeManager.validStyle(style) || !"preset".equals(source))) throw new IllegalArgumentException("无效可编辑配置");
        if (seed != null && (seed >>> 24) != 255) throw new IllegalArgumentException("主色必须不透明");
        if (palette == null || palette.length != 5) throw new IllegalArgumentException("调色板必须包含五组");
        JSONObject obj = new JSONObject();
        obj.put("format", "MonetColorPicker"); obj.put("schemaVersion", 1); obj.put("type", type);
        obj.put("seedColor", seed == null ? JSONObject.NULL : color(seed));
        obj.put("themeStyle", style == null ? JSONObject.NULL : style);
        obj.put("colorSource", source == null ? JSONObject.NULL : source);
        obj.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).format(new Date()));
        JSONObject groups = new JSONObject();
        for (int g = 0; g < 5; g++) {
            if (palette[g] == null || palette[g].length != 13) throw new IllegalArgumentException("每组必须包含 13 个色阶");
            JSONArray values = new JSONArray();
            for (Integer value : palette[g]) {
                if (value == null && editable) throw new IllegalArgumentException("可编辑配色不能包含空颜色");
                if (value != null && (value >>> 24) != 255) throw new IllegalArgumentException("颜色必须不透明");
                values.put(value == null ? JSONObject.NULL : color(value));
            }
            groups.put(GROUPS[g], values);
        }
        obj.put("palette", groups);
        return obj.toString(2);
    }
    private ThemeConfig() { throw new AssertionError(); }
}
