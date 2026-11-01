package com.wallpapermonet.companion;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThemeConfigTest {
    @Test public void preservesUnrelatedSettingsAndUsesUpstreamEncoding() throws Exception {
        String old = "{\"unrelated\":\"keep\",\"android.theme.customization.color_index\":2}";
        JSONObject obj = new JSONObject(SystemThemeManager.makePayload(old, 0xff12ab03, "VIBRANT"));
        assertEquals("keep", obj.getString("unrelated"));
        assertEquals("12AB03", obj.getString(SystemThemeManager.PALETTE));
        assertEquals("12AB03", obj.getString("android.theme.customization.accent_color"));
        assertEquals("preset", obj.getString(SystemThemeManager.SOURCE));
        assertFalse(obj.has("android.theme.customization.color_index"));
    }
    @Test public void rejectsMalformedOriginal() throws Exception {
        for (String invalid : new String[]{"{broken", "{unquoted:1}", "{\"a\":1} trailing", "{\"a\":1,}", "[]"}) {
            try { SystemThemeManager.makePayload(invalid, 0xff123456, "TONAL_SPOT"); fail(invalid); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void editableRoundTripAndMismatchDetection() throws Exception {
        Integer[][] colors = new Integer[5][13]; int[][] computed = new int[5][13];
        for (int g = 0; g < 5; g++) for (int i = 0; i < 13; i++) colors[g][i] = computed[g][i] = 0xff000000 | (g << 8) | i;
        ThemeConfig decoded = ThemeConfig.parse(ThemeConfig.export("editable-theme", 0xff123456, "TONAL_SPOT", "preset", colors));
        assertTrue(decoded.canEdit()); assertEquals(Integer.valueOf(0xff123456), decoded.seed); assertTrue(decoded.matches(computed));
        computed[1][2] ^= 1; assertFalse(decoded.matches(computed));
    }
    @Test public void snapshotUnavailableDoesNotInventColors() throws Exception {
        Integer[][] colors = new Integer[5][13];
        ThemeConfig decoded = ThemeConfig.parse(ThemeConfig.export("system-snapshot", null, null, null, colors));
        assertFalse(decoded.canEdit()); assertNull(decoded.palette[0][0]);
    }
    @Test public void rejectsInvalidColorsAndVersions() throws Exception {
        for (String color : new String[]{"#12345", "#00112233", "#GGHHII"}) {
            try { ThemeConfig.parseColor(color); fail(color); } catch (IllegalArgumentException expected) { }
        }
        try { ThemeConfig.parse("{\"format\":\"MonetColorPicker\",\"schemaVersion\":2}"); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void rejectsNonstandardOrTrailingJsonDuringImport() throws Exception {
        Integer[][] colors = new Integer[5][13];
        for (int g = 0; g < 5; g++) for (int i = 0; i < 13; i++) colors[g][i] = 0xff123456;
        String valid = ThemeConfig.export("editable-theme", 0xff123456, "TONAL_SPOT", "preset", colors);
        for (String invalid : new String[]{valid + " trailing", valid + " {}", valid.replace("\"format\"", "format"), valid.substring(0, valid.length() - 1) + ",}"}) {
            try { ThemeConfig.parse(invalid); fail(invalid); } catch (IllegalArgumentException expected) { }
        }
    }
}
