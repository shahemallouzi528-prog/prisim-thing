package dev.prism.engine.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lowers the effective quality of costly visual features when FPS stays under target, and restores
 * them (last degraded, first restored) once there is sustained headroom. Time is injected so it is testable.
 * It only ever touches Prism's own visual features, never vanilla gameplay settings.
 */
public final class AdaptiveController {
    private static final long WINDOW_NANOS = 1_000_000_000L;

    private final PrismConfig config;
    private final FrameStats stats;
    private final Map<Feature, Integer> reduction = new EnumMap<>(Feature.class);
    private final Deque<Feature> degraded = new ArrayDeque<>();

    private long windowStart = -1;
    private int lowWindows;
    private int highWindows;
    private int cooldownWindows;

    public int degradeAfterWindows = 2;
    public int restoreAfterWindows = 8;
    public int cooldownAfterDegrade = 4;
    public double degradeBelow = 0.92;
    public double restoreAbove = 1.25;

    public AdaptiveController(PrismConfig config, FrameStats stats) {
        this.config = config;
        this.stats = stats;
    }

    /** Feed one finished frame. Returns true if effective levels changed. */
    public boolean onFrame(double frameMs, long nowNanos) {
        stats.record(frameMs);
        if (windowStart < 0) { windowStart = nowNanos; return false; }
        if (nowNanos - windowStart < WINDOW_NANOS) return false;
        windowStart = nowNanos;
        if (!config.adaptiveEnabled) { reset(); return false; }
        return evaluate();
    }

    boolean evaluate() {
        if (stats.size() < 30) return false;
        double fps = stats.averageFps();
        double low1 = stats.onePercentLowFps();
        int target = config.targetFps;
        if (cooldownWindows > 0) cooldownWindows--;

        if (fps < target * degradeBelow) {
            highWindows = 0;
            if (++lowWindows >= degradeAfterWindows) {
                lowWindows = 0;
                if (degradeOne()) { cooldownWindows = cooldownAfterDegrade; stats.clear(); return true; }
            }
        } else if (fps > target * restoreAbove && low1 > target * 0.9 && cooldownWindows == 0) {
            lowWindows = 0;
            if (++highWindows >= restoreAfterWindows) {
                highWindows = 0;
                if (restoreOne()) { stats.clear(); return true; }
            }
        } else {
            lowWindows = 0;
            highWindows = 0;
        }
        return false;
    }

    private boolean degradeOne() {
        Feature best = null;
        int bestScore = 0;
        for (Feature f : Feature.values()) {
            if (!f.adaptive()) continue;
            int eff = effectiveLevel(f);
            if (eff <= config.adaptiveFloor) continue;
            int score = f.cost * eff; // costly and high-level features go first
            if (score > bestScore) { bestScore = score; best = f; }
        }
        if (best == null) return false;
        reduction.merge(best, 1, Integer::sum);
        degraded.push(best);
        return true;
    }

    private boolean restoreOne() {
        Feature f = degraded.poll();
        if (f == null) return false;
        reduction.merge(f, -1, Integer::sum);
        return true;
    }

    public int effectiveLevel(Feature f) {
        int configured = config.configuredLevel(f);
        if (configured == 0) return 0;
        int r = reduction.getOrDefault(f, 0);
        int floor = Math.min(configured, config.adaptiveFloor);
        return Math.max(floor, configured - r);
    }

    public boolean isActive(Feature f) { return effectiveLevel(f) > 0; }
    public int degradedSteps() { return degraded.size(); }

    public void reset() {
        reduction.clear();
        degraded.clear();
        lowWindows = highWindows = cooldownWindows = 0;
    }
}
