package dev.prism.engine.fabric;

import dev.prism.engine.core.AdaptiveController;
import dev.prism.engine.core.FrameStats;
import dev.prism.engine.core.PrismConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Phase 1 client entrypoint: loads config and runs the adaptive controller once per client tick.
 * No mixins and no rendering hooks yet; those are added only after the 26.2 API names are verified
 * in CI (see README, "Verifying Minecraft 26.2 APIs").
 *
 * UNVERIFIED against real 26.2 jars: {@code client.getFps()}. If CI fails to compile, this is the first place to look.
 */
public final class PrismClient implements ClientModInitializer {
    public static final String MOD_ID = "prism_engine";

    private static PrismConfig config;
    private static AdaptiveController controller;
    private static FrameStats stats;

    public static PrismConfig config() { return config; }
    public static AdaptiveController controller() { return controller; }
    public static FrameStats stats() { return stats; }

    @Override
    public void onInitializeClient() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("prism_engine.properties");
        config = PrismConfig.load(file);
        stats = new FrameStats(600);
        controller = new AdaptiveController(config, stats);
        try {
            config.save(file); // writes defaults on first run
        } catch (IOException e) {
            System.err.println("[Prism Engine] could not write config: " + e.getMessage());
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            int fps = client.getFps();
            if (fps > 0) controller.onFrame(1000.0 / fps, System.nanoTime());
        });
    }
}
