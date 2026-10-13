package cz.halloween.mod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent, server-authoritative Halloween progression.
 *
 * The Overworld owns this data so counters are shared across dimensions and survive restarts.
 * Keys are intentionally strings for forward-compatible additions without a save-format rewrite.
 */
public final class HalloweenProgressData extends SavedData {
    private static final Codec<Map<String, Long>> STATS_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.LONG);
    private static final Codec<Map<String, Map<String, Long>>> PLAYERS_CODEC =
            Codec.unboundedMap(Codec.STRING, STATS_CODEC);

    public static final Codec<HalloweenProgressData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PLAYERS_CODEC.optionalFieldOf("players", Map.<String, Map<String, Long>>of())
                    .forGetter(data -> data.players),
            STATS_CODEC.optionalFieldOf("global", Map.<String, Long>of())
                    .forGetter(data -> data.global)
    ).apply(instance, HalloweenProgressData::new));

    public static final SavedDataType<HalloweenProgressData> TYPE = new SavedDataType<>(
            "halloweencore_progress", HalloweenProgressData::new, CODEC);

    private final Map<String, Map<String, Long>> players = new HashMap<>();
    private final Map<String, Long> global = new HashMap<>();

    public HalloweenProgressData() {
    }

    private HalloweenProgressData(
            Map<String, Map<String, Long>> loadedPlayers,
            Map<String, Long> loadedGlobal) {
        if (loadedPlayers != null) {
            loadedPlayers.forEach((uuid, stats) ->
                    this.players.put(uuid, stats == null ? new HashMap<>() : new HashMap<>(stats)));
        }
        if (loadedGlobal != null) this.global.putAll(loadedGlobal);
    }

    public static HalloweenProgressData get(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            throw new IllegalStateException("Halloween progression requires a running server Overworld.");
        }
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public long getPlayerValue(UUID playerId, String key) {
        if (playerId == null || key == null) return 0L;
        return players.getOrDefault(playerId.toString(), Map.of()).getOrDefault(key, 0L);
    }

    public long addPlayerValue(UUID playerId, String key, long amount) {
        if (playerId == null || key == null || key.isBlank() || amount <= 0L) {
            return getPlayerValue(playerId, key);
        }
        Map<String, Long> stats = players.computeIfAbsent(playerId.toString(), ignored -> new HashMap<>());
        long current = stats.getOrDefault(key, 0L);
        long next = current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
        if (next != current) {
            stats.put(key, next);
            setDirty();
        }
        return next;
    }

    public long setPlayerValue(UUID playerId, String key, long value) {
        if (playerId == null || key == null || key.isBlank()) return getPlayerValue(playerId, key);
        long next = Math.max(0L, value);
        Map<String, Long> stats = players.computeIfAbsent(playerId.toString(), ignored -> new HashMap<>());
        if (stats.getOrDefault(key, 0L) != next) {
            stats.put(key, next);
            setDirty();
        }
        return next;
    }

    public long addFragments(UUID playerId, long amount) {
        if (amount <= 0L || playerId == null) return getPlayerValue(playerId, "fragments");
        long total = addPlayerValue(playerId, "fragments", amount);
        addGlobalValue("fragments_collected", amount);
        return total;
    }

    public long getGlobalValue(String key) {
        if (key == null) return 0L;
        return global.getOrDefault(key, 0L);
    }

    public long addGlobalValue(String key, long amount) {
        if (key == null || key.isBlank() || amount <= 0L) return getGlobalValue(key);
        long current = global.getOrDefault(key, 0L);
        long next = current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
        if (next != current) {
            global.put(key, next);
            setDirty();
        }
        return next;
    }
}
