package dev.prism.engine.core;

/** Quality profiles. Each carries a default FPS target and a level cap applied to every feature. */
public enum Profile {
    POTATO(60, 1),
    BALANCED(60, 2),
    QUALITY(60, 3),
    ULTRA(90, 3);

    public final int defaultTargetFps;
    /** Highest per-feature quality level this profile enables by default (0 = off, 3 = max). */
    public final int levelCap;

    Profile(int defaultTargetFps, int levelCap) {
        this.defaultTargetFps = defaultTargetFps;
        this.levelCap = levelCap;
    }
}
