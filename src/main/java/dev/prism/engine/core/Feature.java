package dev.prism.engine.core;

/**
 * Every user-toggleable feature. {@code cost} is a relative estimate (1-10) of GPU/CPU weight used only to
 * decide which feature the adaptive controller degrades first. It is a heuristic, not a measurement.
 * {@code potatoLevel}/{@code balancedLevel} are the profile defaults before the profile cap is applied.
 */
public enum Feature {
    // Lighting & atmosphere
    AMBIENT_LIGHTING("Ambient lighting", Category.LIGHTING, 3, 1, 2),
    DISTANCE_FOG("Distance fog", Category.LIGHTING, 2, 1, 2),
    ATMOSPHERE("Atmospheric effects", Category.LIGHTING, 4, 0, 2),
    SUNLIGHT("Sunlight effects", Category.LIGHTING, 4, 0, 2),
    CAVE_ATMOSPHERE("Cave atmosphere", Category.LIGHTING, 2, 1, 2),
    NIGHT_ATMOSPHERE("Night atmosphere", Category.LIGHTING, 2, 1, 2),
    COLOR_TRANSITIONS("Smooth color transitions", Category.LIGHTING, 2, 1, 2),
    // World appearance
    LEAVES("Leaves & vegetation", Category.WORLD, 6, 0, 1),
    WATER("Water appearance", Category.WORLD, 6, 0, 2),
    CLOUDS("Clouds", Category.WORLD, 5, 0, 1),
    BLOCK_SHADING("Block shading", Category.WORLD, 5, 0, 2),
    ENV_ANIMATION("Environmental animation", Category.WORLD, 4, 0, 1),
    TERRAIN_COLORS("Terrain colors", Category.WORLD, 1, 1, 2),
    BIOME_BLEND("Biome transitions", Category.WORLD, 3, 1, 2),
    // Particles & effects
    BLOCK_PARTICLES("Block particles", Category.PARTICLES, 5, 1, 2),
    FALLING_BLOCKS("Falling-block animation", Category.PARTICLES, 2, 1, 2),
    ITEM_ANIMATION("Item animation", Category.PARTICLES, 2, 1, 2),
    ENV_PARTICLES("Environmental particles", Category.PARTICLES, 6, 0, 1),
    WATER_SPLASH("Water splashes", Category.PARTICLES, 3, 1, 2),
    WEATHER("Weather effects", Category.PARTICLES, 6, 0, 2),
    // Performance (non-visual; level scales aggressiveness)
    PARTICLE_CULLING("Particle culling", Category.PERFORMANCE, 0, 2, 2),
    ENTITY_DISTANCE_CULLING("Entity distance culling", Category.PERFORMANCE, 0, 2, 2);

    public enum Category { LIGHTING, WORLD, PARTICLES, PERFORMANCE }

    public final String label;
    public final Category category;
    public final int cost;
    private final int potatoLevel;
    private final int balancedLevel;

    Feature(String label, Category category, int cost, int potatoLevel, int balancedLevel) {
        this.label = label;
        this.category = category;
        this.cost = cost;
        this.potatoLevel = potatoLevel;
        this.balancedLevel = balancedLevel;
    }

    /** Whether the adaptive controller may reduce this feature (performance features are never degraded). */
    public boolean adaptive() { return category != Category.PERFORMANCE; }

    public int defaultLevel(Profile p) {
        int base = switch (p) {
            case POTATO -> potatoLevel;
            case BALANCED -> balancedLevel;
            case QUALITY -> Math.max(balancedLevel, 2);
            case ULTRA -> 3;
        };
        return Math.min(base, p.levelCap);
    }

    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
}
