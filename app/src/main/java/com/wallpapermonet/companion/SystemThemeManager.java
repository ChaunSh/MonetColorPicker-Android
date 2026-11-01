package com.wallpapermonet.companion;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Secure settings operations and private, durable pre-write backups. Call off the UI thread. */
public final class SystemThemeManager {
    public static final String KEY = "theme_customization_overlay_packages";
    public static final String PALETTE = "android.theme.customization.system_palette";
    public static final String STYLE = "android.theme.customization.theme_style";
    public static final String SOURCE = "android.theme.customization.color_source";
    private final Context context;
    private final File directory;

    public SystemThemeManager(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(this.context.getFilesDir(), "theme_backups");
    }
    public boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }
    public String readRaw() { return Settings.Secure.getString(context.getContentResolver(), KEY); }
    public static boolean validStyle(String style) {
        return Arrays.asList("TONAL_SPOT", "VIBRANT", "EXPRESSIVE", "SPRITZ", "RAINBOW", "FRUIT_SALAD", "MONOCHROMATIC").contains(style);
    }
    public static String makePayload(String previous, int seed, String style) throws Exception {
        if (!validStyle(style)) throw new IllegalArgumentException("不支持的主题样式");
        boolean empty = previous == null || previous.matches("[ \\t\\r\\n]*");
        if (!empty) checkJsonObject(previous);
        JSONObject obj = empty ? new JSONObject() : new JSONObject(previous);
        String rgb = String.format(Locale.ROOT, "%06X", seed & 0xffffff);
        obj.put(PALETTE, rgb);
        obj.put(STYLE, style);
        obj.put(SOURCE, "preset");
        obj.put("android.theme.customization.accent_color", rgb);
        obj.remove("android.theme.customization.color_index");
        obj.put("_applied_timestamp", System.currentTimeMillis());
        return obj.toString();
    }
    /** Returns the backup id. A backup failure always prevents writing. */
    public synchronized String apply(int seed, String style) throws Exception {
        requirePermission();
        if ((seed >>> 24) != 0xff) throw new IllegalArgumentException("主色必须是不透明颜色");
        String previous = readRaw();
        String replacement = makePayload(previous, seed, style);
        String id = createBackup(previous, seed, style, "apply");
        try {
            writeAndVerify(replacement);
            setResult(id, "success");
        } catch (Exception e) {
            try { setResult(id, "failed: " + e.getClass().getSimpleName()); } catch (Exception ignored) { }
            throw e;
        }
        return id;
    }
    /** UI must obtain explicit user confirmation before invoking this method. */
    public synchronized void restore(String id) throws Exception {
        requirePermission();
        JSONObject saved = readBackup(id);
        String raw = saved.getBoolean("previousWasNull") ? null : saved.getString("previousRawValue");
        // Restore is itself a write; capture the current state first.
        String guard = createBackup(readRaw(), 0, "", "restore");
        try {
            writeAndVerify(raw);
            setResult(guard, "success");
        } catch (Exception e) {
            try { setResult(guard, "failed: " + e.getClass().getSimpleName()); } catch (Exception ignored) { }
            throw e;
        }
    }
    private void requirePermission() {
        if (!hasPermission()) throw new SecurityException("缺少 WRITE_SECURE_SETTINGS 权限");
    }
    private void writeAndVerify(String value) throws Exception {
        if (!Settings.Secure.putString(context.getContentResolver(), KEY, value)) throw new IllegalStateException("系统设置写入失败");
        if (!Objects.equals(value, readRaw())) throw new IllegalStateException("系统设置回读不一致，请检查备份");
    }
    private String createBackup(String raw, int seed, String style, String operation) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("无法创建备份目录");
        String id = System.currentTimeMillis() + "-" + UUID.randomUUID() + ".json";
        JSONObject obj = new JSONObject();
        obj.put("createdAtMillis", System.currentTimeMillis());
        obj.put("previousWasNull", raw == null);
        obj.put("previousRawValue", raw == null ? JSONObject.NULL : raw);
        obj.put("targetSeed", operation.equals("apply") ? String.format(Locale.ROOT, "#%08X", seed) : JSONObject.NULL);
        obj.put("targetStyle", operation.equals("apply") ? style : JSONObject.NULL);
        obj.put("operation", operation);
        obj.put("result", "pending");
        writeBackup(id, obj);
        return id;
    }
    private File backupFile(String id) {
        if (id == null || !id.matches("[0-9]+-[0-9a-f-]{36}\\.json")) throw new IllegalArgumentException("无效备份标识");
        return new File(directory, id);
    }
    private void writeBackup(String id, JSONObject obj) throws Exception {
        AtomicFile file = new AtomicFile(backupFile(id));
        FileOutputStream out = file.startWrite();
        try {
            out.write(obj.toString(2).getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
            file.finishWrite(out);
        } catch (Exception e) { file.failWrite(out); throw e; }
    }
    private void setResult(String id, String result) throws Exception {
        JSONObject obj = readBackup(id); obj.put("result", result); writeBackup(id, obj);
    }
    public JSONObject readBackup(String id) throws Exception {
        try (FileInputStream in = new AtomicFile(backupFile(id)).openRead()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > 1024 * 1024) throw new IllegalArgumentException("备份过大");
                out.write(buffer, 0, n);
            }
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
    }
    public List<String> listBackups() {
        File[] files = directory.listFiles((dir, name) -> name.matches("[0-9]+-[0-9a-f-]{36}\\.json"));
        List<String> ids = new ArrayList<>();
        if (files != null) for (File file : files) ids.add(file.getName());
        Collections.sort(ids, Comparator.reverseOrder());
        return ids;
    }
    public synchronized boolean deleteBackup(String id) { return backupFile(id).delete(); }

    /** Require a complete JSON object before invoking Android's lenient JSONObject parser. */
    static void checkJsonObject(String text) { StrictJson.checkObject(text); }

    /** JSONObject is lenient on Android; validate the stored value before merging it. */
    private static final class StrictJson {
        private final String text; private int at;
        private StrictJson(String text) { this.text = text; }
        static void checkObject(String text) {
            if (text.length() > 1024 * 1024) throw new IllegalArgumentException("系统主题配置过大");
            StrictJson p = new StrictJson(text); p.space();
            if (p.at >= text.length() || text.charAt(p.at) != '{') p.bad();
            p.value(0); p.space(); if (p.at != text.length()) p.bad();
        }
        private void space() { while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) at++; }
        private boolean take(char c) { space(); if (at < text.length() && text.charAt(at) == c) { at++; return true; } return false; }
        private void require(char c) { if (!take(c)) bad(); }
        private void bad() { throw new IllegalArgumentException("系统主题配置不是有效 JSON，已停止写入"); }
        private void value(int depth) {
            if (depth > 128) { bad(); return; }
            space(); if (at >= text.length()) { bad(); return; }
            char c = text.charAt(at);
            if (c == '{') {
                at++; if (take('}')) return;
                do { space(); string(); require(':'); value(depth + 1); } while (take(','));
                require('}');
            } else if (c == '[') {
                at++; if (take(']')) return;
                do { value(depth + 1); } while (take(','));
                require(']');
            } else if (c == '"') string();
            else if (c == 't') literal("true");
            else if (c == 'f') literal("false");
            else if (c == 'n') literal("null");
            else {
                int start = at;
                while (at < text.length() && "0123456789+-.eE".indexOf(text.charAt(at)) >= 0) at++;
                if (!text.substring(start, at).matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) bad();
            }
        }
        private void literal(String s) { if (!text.startsWith(s, at)) bad(); at += s.length(); }
        private void string() {
            if (at >= text.length() || text.charAt(at++) != '"') { bad(); return; }
            while (at < text.length()) {
                char c = text.charAt(at++); if (c == '"') return;
                if (c < 0x20) bad();
                if (c == '\\') {
                    if (at >= text.length()) bad(); char escaped = text.charAt(at++);
                    if (escaped == 'u') {
                        if (at + 4 > text.length()) bad();
                        for (int i = 0; i < 4; i++) if (Character.digit(text.charAt(at++), 16) < 0) bad();
                    } else if ("\"\\/bfnrt".indexOf(escaped) < 0) bad();
                }
            }
            bad();
        }
    }
}
