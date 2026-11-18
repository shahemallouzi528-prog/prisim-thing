package dev.prism.engine.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/** User configuration. Pure Java (java.util.Properties) so it can be unit-tested without Minecraft. */
public final class PrismConfig {
    public static final int MAX_LEVEL = 3;

    public Profile profile = Profile.BALANCED;
    public int targetFps = Profile.BALANCED.defaultTargetFps;
    public boolean adaptiveEnabled = true;
    public boolean showOverlay = false;
    /** Adaptive control never lowers a feature below this level (0 allows turning features off). */
    public int adaptiveFloor = 0;
    /** Visual effects farther than this many blocks may be skipped by features that honor it. */
    public int effectDistance = 48;

    private final Map<Feature, Boolean> enabled = new EnumMap<>(Feature.class);
    private final Map<Feature, Integer> level = new EnumMap<>(Feature.class);

    public PrismConfig() { applyProfile(Profile.BALANCED); }

    public void applyProfile(Profile p) {
        profile = p;
        targetFps = p.defaultTargetFps;
        for (Feature f : Feature.values()) {
            int lv = f.defaultLevel(p);
            level.put(f, lv);
            enabled.put(f, lv > 0);
        }
    }

    public void resetToDefaults() {
        adaptiveEnabled = true;
        showOverlay = false;
        adaptiveFloor = 0;
        effectDistance = 48;
        applyProfile(Profile.BALANCED);
    }

    public boolean isEnabled(Feature f) { return enabled.get(f); }
    public void setEnabled(Feature f, boolean v) { enabled.put(f, v); }
    public int getLevel(Feature f) { return level.get(f); }
    public void setLevel(Feature f, int v) { level.put(f, clamp(v, 0, MAX_LEVEL)); }

    /** Level the user configured, 0 when the feature is toggled off. */
    public int configuredLevel(Feature f) { return isEnabled(f) ? getLevel(f) : 0; }

    public void setTargetFps(int fps) { targetFps = clamp(fps, 20, 360); }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("profile", profile.name());
        p.setProperty("targetFps", Integer.toString(targetFps));
        p.setProperty("adaptiveEnabled", Boolean.toString(adaptiveEnabled));
        p.setProperty("showOverlay", Boolean.toString(showOverlay));
        p.setProperty("adaptiveFloor", Integer.toString(adaptiveFloor));
        p.setProperty("effectDistance", Integer.toString(effectDistance));
        for (Feature f : Feature.values()) {
            p.setProperty(f.key() + ".enabled", Boolean.toString(isEnabled(f)));
            p.setProperty(f.key() + ".level", Integer.toString(getLevel(f)));
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            p.store(w, "Prism Engine configuration");
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Loads a config; missing/invalid entries fall back to defaults and never throw. */
    public static PrismConfig load(Path file) {
        PrismConfig c = new PrismConfig();
        if (!Files.isRegularFile(file)) return c;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | RuntimeException e) {
            return c;
        }
        try { c.profile = Profile.valueOf(p.getProperty("profile", c.profile.name())); } catch (IllegalArgumentException ignored) { }
        c.targetFps = clamp(parseInt(p.getProperty("targetFps"), c.targetFps), 20, 360);
        c.adaptiveEnabled = parseBool(p.getProperty("adaptiveEnabled"), c.adaptiveEnabled);
        c.showOverlay = parseBool(p.getProperty("showOverlay"), c.showOverlay);
        c.adaptiveFloor = clamp(parseInt(p.getProperty("adaptiveFloor"), c.adaptiveFloor), 0, MAX_LEVEL);
        c.effectDistance = clamp(parseInt(p.getProperty("effectDistance"), c.effectDistance), 8, 256);
        // Profile defaults first, then per-feature overrides.
        Profile loaded = c.profile;
        int tf = c.targetFps;
        c.applyProfile(loaded);
        c.targetFps = tf;
        for (Feature f : Feature.values()) {
            c.enabled.put(f, parseBool(p.getProperty(f.key() + ".enabled"), c.enabled.get(f)));
            c.level.put(f, clamp(parseInt(p.getProperty(f.key() + ".level"), c.level.get(f)), 0, MAX_LEVEL));
        }
        return c;
    }

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }
    private static boolean parseBool(String s, boolean def) {
        if (s == null) return def;
        String t = s.trim();
        if (t.equalsIgnoreCase("true")) return true;
        if (t.equalsIgnoreCase("false")) return false;
        return def;
    }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
