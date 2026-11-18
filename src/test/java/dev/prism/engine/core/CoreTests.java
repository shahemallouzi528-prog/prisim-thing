package dev.prism.engine.core;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free tests (no JUnit) so they run anywhere: java -cp ... dev.prism.engine.core.CoreTests */
public final class CoreTests {
    private static int passed;

    public static void main(String[] args) throws Exception {
        frameStats();
        configRoundTrip();
        configBadInput();
        profiles();
        adaptiveDegradesAndRestores();
        adaptiveRespectsFloorAndToggle();
        adaptiveNeverTouchesPerformanceFeatures();
        System.out.println("All " + passed + " tests passed");
    }

    static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("FAILED: " + name);
        passed++;
    }

    static void frameStats() {
        FrameStats s = new FrameStats(100);
        for (int i = 0; i < 99; i++) s.record(10);
        s.record(50);
        check(Math.abs(s.averageMs() - 10.4) < 1e-9, "average ms");
        check(Math.abs(s.percentileMs(99) - 10) < 1e-9, "p99 nearest-rank");
        check(Math.abs(s.percentileMs(100) - 50) < 1e-9, "p100");
        s.record(Double.NaN); s.record(-3); s.record(0);
        check(s.size() == 100, "invalid samples ignored");
        double[] out = new double[5];
        check(s.copyRecent(out) == 5 && out[4] == 50, "copyRecent newest last");
        FrameStats small = new FrameStats(4);
        for (int i = 1; i <= 6; i++) small.record(i);
        check(small.size() == 4 && Math.abs(small.averageMs() - 4.5) < 1e-9, "ring overwrite");
    }

    static void configRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("prism");
        Path f = dir.resolve("sub/prism.properties");
        PrismConfig c = new PrismConfig();
        c.applyProfile(Profile.QUALITY);
        c.setLevel(Feature.WATER, 1);
        c.setEnabled(Feature.CLOUDS, false);
        c.setTargetFps(75);
        c.adaptiveEnabled = false;
        c.save(f);
        PrismConfig d = PrismConfig.load(f);
        check(d.profile == Profile.QUALITY && d.targetFps == 75 && !d.adaptiveEnabled, "scalars round-trip");
        check(d.getLevel(Feature.WATER) == 1 && !d.isEnabled(Feature.CLOUDS), "feature round-trip");
        check(d.configuredLevel(Feature.CLOUDS) == 0, "disabled means level 0");
    }

    static void configBadInput() throws Exception {
        Path f = Files.createTempFile("prism", ".properties");
        Files.writeString(f, "profile=NOPE\ntargetFps=abc\nwater.level=99\nclouds.enabled=maybe\n");
        PrismConfig c = PrismConfig.load(f);
        check(c.profile == Profile.BALANCED, "bad profile -> default");
        check(c.targetFps == Profile.BALANCED.defaultTargetFps, "bad int -> default");
        check(c.getLevel(Feature.WATER) == PrismConfig.MAX_LEVEL, "level clamped");
        check(PrismConfig.load(Path.of("/nonexistent/x.properties")).profile == Profile.BALANCED, "missing file");
        PrismConfig r = new PrismConfig();
        r.applyProfile(Profile.ULTRA); r.setTargetFps(1000);
        check(r.targetFps == 360, "fps clamped");
        r.resetToDefaults();
        check(r.profile == Profile.BALANCED && r.adaptiveEnabled, "reset");
    }

    static void profiles() {
        PrismConfig c = new PrismConfig();
        c.applyProfile(Profile.POTATO);
        for (Feature f : Feature.values()) check(c.getLevel(f) <= 1, "potato cap " + f);
        c.applyProfile(Profile.ULTRA);
        check(c.getLevel(Feature.WATER) == 3, "ultra max");
    }

    /** Runs simulated seconds of constant frame time; returns controller. */
    static long clock;
    static void run(AdaptiveController a, double frameMs, int seconds) {
        long step = (long) (frameMs * 1_000_000);
        long end = clock + seconds * 1_000_000_000L;
        while (clock < end) { a.onFrame(frameMs, clock); clock += step; }
    }

    static void adaptiveDegradesAndRestores() {
        clock = 0;
        PrismConfig c = new PrismConfig();
        c.applyProfile(Profile.ULTRA);
        c.targetFps = 60;
        AdaptiveController a = new AdaptiveController(c, new FrameStats(300));
        run(a, 1000.0 / 60, 5);
        check(a.degradedSteps() == 0, "no degrade at target");
        run(a, 1000.0 / 30, 20); // 30 fps vs 60 target
        int d = a.degradedSteps();
        check(d > 0, "degrades when slow");
        // Most costly feature goes first (cost 6 * level 3 = 18)
        check(a.effectiveLevel(Feature.LEAVES) < 3 || a.effectiveLevel(Feature.WATER) < 3
              || a.effectiveLevel(Feature.ENV_PARTICLES) < 3 || a.effectiveLevel(Feature.WEATHER) < 3,
              "costly feature degraded first");
        check(a.effectiveLevel(Feature.TERRAIN_COLORS) == 3, "cheap feature untouched early");
        run(a, 1000.0 / 144, 120); // plenty of headroom
        check(a.degradedSteps() < d, "restores with headroom");
        run(a, 1000.0 / 144, 600);
        check(a.degradedSteps() == 0, "fully restored");
        for (Feature f : Feature.values()) check(a.effectiveLevel(f) == c.configuredLevel(f), "level restored " + f);
    }

    static void adaptiveRespectsFloorAndToggle() {
        clock = 0;
        PrismConfig c = new PrismConfig();
        c.applyProfile(Profile.ULTRA);
        c.adaptiveFloor = 1;
        AdaptiveController a = new AdaptiveController(c, new FrameStats(300));
        run(a, 1000.0 / 10, 600);
        for (Feature f : Feature.values())
            if (f.adaptive()) check(a.effectiveLevel(f) >= 1, "floor respected " + f);
        c.setEnabled(Feature.WATER, false);
        check(a.effectiveLevel(Feature.WATER) == 0, "user-disabled stays off");
        c.adaptiveEnabled = false;
        run(a, 1000.0 / 10, 3);
        check(a.degradedSteps() == 0, "disabling adaptive resets");
    }

    static void adaptiveNeverTouchesPerformanceFeatures() {
        clock = 0;
        PrismConfig c = new PrismConfig();
        c.applyProfile(Profile.ULTRA);
        AdaptiveController a = new AdaptiveController(c, new FrameStats(300));
        run(a, 1000.0 / 5, 600);
        check(a.effectiveLevel(Feature.PARTICLE_CULLING) == c.configuredLevel(Feature.PARTICLE_CULLING), "culling untouched");
        check(a.effectiveLevel(Feature.ENTITY_DISTANCE_CULLING) == c.configuredLevel(Feature.ENTITY_DISTANCE_CULLING), "entity culling untouched");
    }
}
