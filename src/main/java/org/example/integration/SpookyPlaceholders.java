/*
 * Integrated from SpookySeason 1.7.0 by BoondockSulfur under the MIT License.
 * Copyright (c) 2026 BoondockSulfur. See THIRD_PARTY_LICENSES/SpookySeason-MIT.txt.
 */
package org.example.integration;

import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.example.SpookySeason;

public class SpookyPlaceholders
extends PlaceholderExpansion {
    private final SpookySeason plugin;

    public SpookyPlaceholders(SpookySeason plugin) {
        this.plugin = plugin;
    }

    public String getIdentifier() {
        return "spooky";
    }

    public String getAuthor() {
        return "BoondockSulfur";
    }

    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, String params) {
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "active" -> {
                if (this.plugin.isSeasonActive()) {
                    yield "Yes";
                }
                yield "No";
            }
            case "treats" -> {
                if (player != null) {
                    yield String.valueOf(this.plugin.stats().getTreats(player.getUniqueId()));
                }
                yield "0";
            }
            case "optout" -> {
                if (player != null) {
                    if (this.plugin.prefs().isOptedOut(player.getUniqueId())) {
                        yield "Yes";
                    }
                    yield "No";
                }
                yield "No";
            }
            case "language" -> this.plugin.getConfig().getString("language", "en");
            default -> null;
        };
    }
}

