package cz.halloween.mod;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class HalloweenConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_WORLD_IDS;
    public static final ModConfigSpec.BooleanValue EVENTS_ENABLED;
    public static final ModConfigSpec.BooleanValue WORLD_DECORATIONS_ENABLED;
    public static final ModConfigSpec.IntValue EVENT_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue EVENT_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_HALLOWEEN_MOBS;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("world-policy");
        ALLOWED_WORLD_IDS = BUILDER
                .comment("Exact allowed dimension IDs, for example [\"minecraft:overworld\"]. Empty means deny all. A world named Spawn is always denied.")
                .defineList("allowed-world-ids", List.of(), entry -> entry instanceof String);
        BUILDER.pop();

        BUILDER.push("events");
        EVENTS_ENABLED = BUILDER.define("enabled", true);
        EVENT_INTERVAL_SECONDS = BUILDER.defineInRange("interval-seconds", 900, 60, 86400);
        EVENT_DURATION_SECONDS = BUILDER.defineInRange("duration-seconds", 360, 30, 3600);
        MAX_ACTIVE_HALLOWEEN_MOBS = BUILDER.defineInRange("max-active-mobs-per-world", 40, 1, 100);
        BUILDER.pop();

        BUILDER.push("world-generation");
        WORLD_DECORATIONS_ENABLED = BUILDER.define("decorations-enabled", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private HalloweenConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "halloweencore-common.toml");
    }
}
