package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical mapping from stable HalloweenCore content IDs to platform-facing item metadata.
 *
 * Gameplay stores the logical ID in item persistent data. ItemsAdder IDs and texture keys
 * are integration details that can change without changing an item's gameplay identity.
 */
public final class HalloweenContentRegistry {
    private final Map<String, Definition> definitions;

    public HalloweenContentRegistry(HalloweenCore plugin) {
        Map<String, Definition> loaded = new LinkedHashMap<>();
        try (InputStream input = plugin.getResource("content-registry.yml")) {
            if (input == null) {
                throw new IllegalStateException("Bundled content-registry.yml is missing");
            }
            YamlConfiguration root = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection items = root.getConfigurationSection("items");
            if (items == null) {
                throw new IllegalStateException("content-registry.yml must define an items section");
            }

            for (String id : items.getKeys(false)) {
                ConfigurationSection section = items.getConfigurationSection(id);
                if (section == null) {
                    throw new IllegalStateException("Content definition '" + id + "' must be a mapping");
                }
                String category = required(section, "category", id);
                String itemsAdderId = required(section, "itemsadder-id", id);
                String runtimeIdentity = required(section, "runtime-identity", id);
                String materialName = required(section, "fallback-material", id);
                String texture = required(section, "texture", id);
                String displayName = required(section, "display-name", id);
                Material fallback = Material.matchMaterial(materialName);
                if (fallback == null || fallback.isAir()) {
                    throw new IllegalStateException("Invalid fallback material '" + materialName + "' for " + id);
                }
                if (NamespacedKey.fromString(itemsAdderId) == null) {
                    throw new IllegalStateException("Invalid ItemsAdder namespaced ID '" + itemsAdderId + "' for " + id);
                }
                if (!texture.matches("[a-z0-9_/-]+")) {
                    throw new IllegalStateException("Invalid logical texture key '" + texture + "' for " + id);
                }
                List<String> lore = Collections.unmodifiableList(
                        new ArrayList<>(section.getStringList("lore")));
                Definition definition = new Definition(
                        id, category, itemsAdderId, runtimeIdentity, fallback, texture, displayName, lore);
                if (loaded.putIfAbsent(id, definition) != null) {
                    throw new IllegalStateException("Duplicate Halloween content ID: " + id);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load Halloween content registry", exception);
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("Halloween content registry is empty");
        }
        this.definitions = Collections.unmodifiableMap(loaded);
        plugin.getLogger().info("Loaded " + definitions.size() + " canonical Halloween content definitions.");
    }

    private static String required(ConfigurationSection section, String key, String id) {
        String value = section.getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Content definition '" + id + "' is missing '" + key + "'");
        }
        return value.trim();
    }

    public Definition require(String internalId) {
        Definition definition = definitions.get(internalId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown Halloween content ID: " + internalId);
        }
        return definition;
    }

    public Definition findByRuntimeIdentity(String identity) {
        if (identity == null || identity.isBlank()) return null;
        for (Definition definition : definitions.values()) {
            if (definition.runtimeIdentity().equals(identity)) return definition;
        }
        return null;
    }

    public Definition findByItemsAdderId(String itemsAdderId) {
        if (itemsAdderId == null || itemsAdderId.isBlank()) return null;
        for (Definition definition : definitions.values()) {
            if (definition.itemsAdderId().equalsIgnoreCase(itemsAdderId)) return definition;
        }
        return null;
    }

    public Collection<Definition> all() {
        return definitions.values();
    }

    public int size() {
        return definitions.size();
    }

    public record Definition(
            String internalId,
            String category,
            String itemsAdderId,
            String runtimeIdentity,
            Material fallbackMaterial,
            String texture,
            String displayName,
            List<String> lore
    ) {}
}
