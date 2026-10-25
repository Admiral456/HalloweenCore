/*
 * Integrated from SpookySeason 1.7.0 by BoondockSulfur under the MIT License.
 * Copyright (c) 2026 BoondockSulfur. See THIRD_PARTY_LICENSES/SpookySeason-MIT.txt.
 */
package org.example.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.example.lang.Lang;
import org.example.util.Scheduler;

public class UpdateChecker
implements Listener {
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/Admiral456/HalloweenCore/releases/latest";
    private final Plugin plugin;
    // Written on the async thread, read in the join event — volatile for visibility.
    private volatile String latestVersion;
    private volatile String downloadUrl;
    private volatile boolean updateAvailable;

    public UpdateChecker(Plugin plugin) {
        this.plugin = plugin;
    }

    public void checkAsync() {
        Scheduler.runAsync(this.plugin, this::check);
    }

    private void check() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(GITHUB_RELEASES_API))
                    .timeout(Duration.ofSeconds(15L))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "HalloweenCore/" + this.plugin.getDescription().getVersion())
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                // No GitHub release has been published yet; do not report the upstream SpookySeason version.
                return;
            }
            if (response.statusCode() != 200) {
                this.plugin.getLogger().fine("HalloweenCore update check returned HTTP " + response.statusCode());
                return;
            }
            JsonObject latest = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!latest.has("tag_name") || latest.get("tag_name").isJsonNull()) return;
            String remoteVersion = latest.get("tag_name").getAsString();
            String currentVersion = this.plugin.getDescription().getVersion();
            if (UpdateChecker.isNewer(remoteVersion, currentVersion)) {
                this.latestVersion = remoteVersion;
                this.downloadUrl = latest.has("html_url") && !latest.get("html_url").isJsonNull()
                        ? latest.get("html_url").getAsString()
                        : "https://github.com/Admiral456/HalloweenCore/releases";
                this.updateAvailable = true;
                this.plugin.getLogger().info("New HalloweenCore version available: " + remoteVersion
                        + " (current: " + currentVersion + ")");
            }
        } catch (Exception e) {
            this.plugin.getLogger().fine("Could not check for HalloweenCore updates: " + e.getMessage());
        }
    }
    /**
     * Compares the numeric blocks in order. A plain inequality test would report an "update" even
     * when the newest thing on Modrinth is actually an older version.
     */
    static boolean isNewer(String remote, String local) {
        int[] r = UpdateChecker.parseVersion(remote);
        int[] l = UpdateChecker.parseVersion(local);
        int len = Math.max(r.length, l.length);
        for (int i = 0; i < len; ++i) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv != lv) {
                return rv > lv;
            }
        }
        return false;
    }

    /** "v1.2.3-beta.2" becomes [1, 2, 3]; everything from the first suffix on is discarded. */
    private static int[] parseVersion(String version) {
        if (version == null) {
            return new int[0];
        }
        String cleaned = version.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }
        int cut = cleaned.length();
        for (int i = 0; i < cleaned.length(); ++i) {
            char c = cleaned.charAt(i);
            if (Character.isDigit(c) || c == '.') continue;
            cut = i;
            break;
        }
        String[] parts = cleaned.substring(0, cut).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; ++i) {
            try {
                out[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            }
            catch (NumberFormatException ex) {
                out[i] = 0;
            }
        }
        return out;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!this.updateAvailable) {
            return;
        }
        Player p = e.getPlayer();
        if (!p.hasPermission("spooky.admin")) {
            return;
        }
        Scheduler.runLater(this.plugin, () -> {
            if (p.isOnline()) {
                p.sendMessage(UpdateChecker.legacy(Lang.get("update.available", "version", this.latestVersion)));
                p.sendMessage(UpdateChecker.downloadLine(this.downloadUrl));
            }
        }, 40L);
    }

    /** Keep the HalloweenCore release URL clickable in the player notification. */
    private static Component downloadLine(String releaseUrl) {
        return UpdateChecker.legacy(Lang.get("update.download-prefix", new String[0]))
                .append(UpdateChecker.link("HalloweenCore releases", releaseUrl));
    }
    private static Component link(String label, String url) {
        return UpdateChecker.legacy(label)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url)));
    }

    /** The language files use section-sign codes; those have to be translated for components. */
    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
}

