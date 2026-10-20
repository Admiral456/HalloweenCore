package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

public final class HalloweenQuestManager {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,47}");

    private final HalloweenCore plugin;
    private final File dataFile;
    private final YamlConfiguration data;

    public HalloweenQuestManager(HalloweenCore plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data directory.");
        }
        this.dataFile = new File(plugin.getDataFolder(), "quest-data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public boolean isValidId(String id) {
        return id != null && VALID_ID.matcher(id.toLowerCase(java.util.Locale.ROOT)).matches();
    }

    public String normalizeId(String id) {
        return id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
    }

    public boolean start(Player player, String rawId) {
        String id = normalizeId(rawId);
        if (player == null || !isValidId(id) || !isConfiguredQuest(id)
                || isCompleted(player.getUniqueId(), id) || isActive(player.getUniqueId(), id)) {
            return false;
        }
        String root = questRoot(player.getUniqueId(), id);
        data.set(root + ".status", "active");
        data.set(root + ".progress", 0);
        data.set(root + ".reward-paid", false);
        save();
        return true;
    }

    public boolean addProgress(Player player, String rawId, int amount) {
        String id = normalizeId(rawId);
        if (player == null || !isValidId(id) || !isConfiguredQuest(id)
                || amount < 1 || amount > 10000 || !isActive(player.getUniqueId(), id)) {
            return false;
        }
        int previous = getProgress(player.getUniqueId(), id);
        int next = (int) Math.min(getTarget(id), (long) previous + amount);
        data.set(questRoot(player.getUniqueId(), id) + ".progress", next);
        save();
        return true;
    }

    /**
     * Completes an active quest after its configured target is reached. If a reward
     * is configured, it is paid once. A zero/empty reward does not consume the claim,
     * so a later-agreed reward can still be claimed without resetting the quest.
     */
    public boolean complete(Player player, String rawId) {
        String id = normalizeId(rawId);
        if (player == null || !isValidId(id) || !isActive(player.getUniqueId(), id)
                || getProgress(player.getUniqueId(), id) < getTarget(id)) {
            return false;
        }

        data.set(questRoot(player.getUniqueId(), id) + ".status", "completed");
        save();
        grantConfiguredReward(player, id);
        return true;
    }

    public boolean claimConfiguredReward(Player player, String rawId) {
        String id = normalizeId(rawId);
        if (player == null || !isValidId(id) || !isCompleted(player.getUniqueId(), id)) {
            return false;
        }
        return grantConfiguredReward(player, id);
    }

    private boolean grantConfiguredReward(Player player, String id) {
        UUID playerId = player.getUniqueId();
        String questRoot = questRoot(playerId, id);
        if (isRewardPaid(playerId, id) || !hasConfiguredReward(id)) {
            return false;
        }

        List<ItemStack> rewardItems = buildRewardStacks(id);
        if (rewardItems == null) {
            plugin.getLogger().warning("Cannot pay configured reward for quest '" + id
                    + "' to " + player.getName() + ": a reward item ID is invalid or unavailable.");
            return false;
        }

        long fragments = getConfiguredFragmentReward(id);
        // Save the once-only marker and fragment balance before giving items. This
        // prefers no duplicate payout after a crash; the player can never claim twice.
        data.set(questRoot + ".reward-paid", true);
        if (fragments > 0L) {
            data.set(playerRoot(playerId) + ".fragments",
                    addWithoutOverflow(getFragments(playerId), fragments));
        }
        save();

        for (ItemStack stack : rewardItems) {
            player.getInventory().addItem(stack).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        return true;
    }

    private List<ItemStack> buildRewardStacks(String id) {
        List<Map<?, ?>> definitions = plugin.getConfig().getMapList("quests.definitions." + id + ".reward-items");
        List<ItemStack> result = new ArrayList<>();
        for (Map<?, ?> definition : definitions) {
            Object rawId = definition.get("id");
            Object rawAmount = definition.get("amount");
            if (!(rawId instanceof String itemId) || itemId.isBlank() || !(rawAmount instanceof Number number)) {
                return null;
            }
            double numericAmount = number.doubleValue();
            if (numericAmount != Math.rint(numericAmount) || numericAmount < 1 || numericAmount > 2304) {
                return null;
            }
            ItemStack base = plugin.getKeyItemFactory().createConfiguredItem(itemId);
            if (base == null || base.getType().isAir()) {
                return null;
            }
            int remaining = (int) numericAmount;
            int maxStack = Math.max(1, base.getMaxStackSize());
            while (remaining > 0) {
                int size = Math.min(remaining, maxStack);
                ItemStack stack = base.clone();
                stack.setAmount(size);
                result.add(stack);
                remaining -= size;
            }
        }
        return result;
    }

    public boolean hasConfiguredReward(String id) {
        return getConfiguredFragmentReward(id) > 0L
                || !plugin.getConfig().getMapList("quests.definitions." + id + ".reward-items").isEmpty();
    }

    public boolean isRewardPaid(UUID playerId, String id) {
        return data.getBoolean(questRoot(playerId, id) + ".reward-paid", false);
    }

    public boolean isConfiguredQuest(String rawId) {
        String id = normalizeId(rawId);
        if (!isValidId(id)) return false;
        ConfigurationSection catalog = plugin.getConfig().getConfigurationSection("quests.definitions");
        return catalog != null && catalog.isConfigurationSection(id);
    }

    public Set<String> getCatalogIds() {
        ConfigurationSection catalog = plugin.getConfig().getConfigurationSection("quests.definitions");
        if (catalog == null) return Set.of();
        return new TreeSet<>(catalog.getKeys(false));
    }

    public String getDifficulty(String id) {
        return plugin.getConfig().getString("quests.definitions." + id + ".difficulty", "neurčeno");
    }

    public String getDescription(String id) {
        return plugin.getConfig().getString("quests.definitions." + id + ".description", "");
    }

    public String getObjectiveType(String id) {
        return plugin.getConfig().getString("quests.definitions." + id + ".objective-type", "delivery");
    }

    /**
     * Turn in configured items atomically: all requirements are checked before any
     * item is removed. Exploration quests require their automatic room-discovery
     * progress before allowing the player to turn in the quest.
     */
    public TurnInResult turnIn(Player player, String rawId) {
        String id = normalizeId(rawId);
        if (player == null || !isValidId(id) || !isConfiguredQuest(id)) {
            return new TurnInResult(false, "unknown");
        }
        UUID playerId = player.getUniqueId();
        if (isCompleted(playerId, id)) return new TurnInResult(false, "completed");
        if (!isActive(playerId, id)) return new TurnInResult(false, "not-active");

        String objectiveType = getObjectiveType(id);
        boolean requiresExploration = objectiveType.equalsIgnoreCase("explore-room")
                || objectiveType.equalsIgnoreCase("explore-and-deliver");
        if (requiresExploration && getProgress(playerId, id) < getTarget(id)) {
            return new TurnInResult(false, "objective-missing");
        }

        List<Map<?, ?>> requirements = plugin.getConfig().getMapList("quests.definitions." + id + ".required-items");
        List<RequiredItem> parsed = new ArrayList<>();
        for (Map<?, ?> requirement : requirements) {
            Object rawItemId = requirement.get("id");
            Object rawAmount = requirement.get("amount");
            if (!(rawItemId instanceof String itemId) || itemId.isBlank()
                    || !(rawAmount instanceof Number number)
                    || number.doubleValue() != Math.rint(number.doubleValue())
                    || number.longValue() < 1L || number.longValue() > 2304L) {
                return new TurnInResult(false, "invalid-config");
            }
            ItemStack template = plugin.getKeyItemFactory().createConfiguredItem(itemId);
            if (template == null || template.getType().isAir()) {
                return new TurnInResult(false, "invalid-item:" + itemId);
            }
            int amount = number.intValue();
            int found = countMatchingItems(player, template);
            if (found < amount) {
                return new TurnInResult(false, "missing:" + itemId + ":" + (amount - found));
            }
            parsed.add(new RequiredItem(itemId, amount, template));
        }

        // All preconditions passed, so there is no expected partial item removal.
        for (RequiredItem requirement : parsed) {
            ItemStack removal = requirement.template().clone();
            removal.setAmount(requirement.amount());
            Map<Integer, ItemStack> leftovers = player.getInventory().removeItem(removal);
            if (!leftovers.isEmpty()) {
                plugin.getLogger().severe("Quest turn-in inventory changed unexpectedly while removing items for "
                        + player.getName() + " / " + id + ".");
                return new TurnInResult(false, "inventory-changed");
            }
        }

        if (!requiresExploration) {
            data.set(questRoot(playerId, id) + ".progress", getTarget(id));
            save();
        }

        if (!complete(player, id)) {
            return new TurnInResult(false, "completion-failed");
        }
        return new TurnInResult(true, "completed");
    }

    private int countMatchingItems(Player player, ItemStack template) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir() || !stack.isSimilar(template)) continue;
            total += stack.getAmount();
        }
        return total;
    }

    public record TurnInResult(boolean success, String reason) {}
    private record RequiredItem(String id, int amount, ItemStack template) {}

    public boolean isActive(UUID playerId, String id) {
        return "active".equalsIgnoreCase(data.getString(questRoot(playerId, id) + ".status", "none"));
    }

    public boolean isCompleted(UUID playerId, String id) {
        return "completed".equalsIgnoreCase(data.getString(questRoot(playerId, id) + ".status", "none"));
    }

    public String getStatus(UUID playerId, String id) {
        return data.getString(questRoot(playerId, id) + ".status", "none");
    }

    public int getProgress(UUID playerId, String id) {
        return Math.max(0, data.getInt(questRoot(playerId, id) + ".progress", 0));
    }

    public int getTarget(String id) {
        int configured = plugin.getConfig().getInt("quests.definitions." + id + ".target", 1);
        return Math.max(1, Math.min(configured, 100000));
    }

    public long getConfiguredFragmentReward(String id) {
        return Math.max(0L, plugin.getConfig().getLong("quests.definitions." + id + ".reward-fragments", 0L));
    }

    public String getTitle(String id) {
        String configured = plugin.getConfig().getString("quests.definitions." + id + ".title");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String[] words = id.replace('-', ' ').replace('_', ' ').split("\\s+");
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (title.length() > 0) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }

    public long getFragments(UUID playerId) {
        return Math.max(0L, data.getLong(playerRoot(playerId) + ".fragments", 0L));
    }

    public Set<String> getQuestIds(UUID playerId) {
        ConfigurationSection section = data.getConfigurationSection(playerRoot(playerId) + ".quests");
        if (section == null) {
            return Set.of();
        }
        return new TreeSet<>(section.getKeys(false));
    }

    public void showStatus(Player player, String requestedId) {
        UUID playerId = player.getUniqueId();
        player.sendMessage(plugin.color("&8&m--------------------------------"));
        player.sendMessage(plugin.color("&5&lÚKOLY"));
        player.sendMessage(plugin.color("&7Fragmenty: &d" + getFragments(playerId)));

        if (requestedId != null && !requestedId.isBlank()) {
            String id = normalizeId(requestedId);
            if (!isValidId(id)) {
                player.sendMessage(plugin.color("&cNeplatné ID úkolu."));
            } else {
                String status = getStatus(playerId, id);
                player.sendMessage(plugin.color("&f" + getTitle(id) + " &8(" + id + ")"));
                player.sendMessage(plugin.color("&7Obtížnost: &e" + getDifficulty(id)));
                String description = getDescription(id);
                if (!description.isBlank()) player.sendMessage(plugin.color("&7" + description));
                player.sendMessage(plugin.color("&7Stav: &e" + status));
                player.sendMessage(plugin.color("&7Postup: &d" + getProgress(playerId, id) + "&7/&d" + getTarget(id)));
                String rewardStatus;
                if (!status.equals("completed")) {
                    rewardStatus = hasConfiguredReward(id) ? "nastavena v konfiguraci" : "zatím není domluvena";
                } else if (isRewardPaid(playerId, id)) {
                    rewardStatus = "vyplacena";
                } else if (hasConfiguredReward(id)) {
                    rewardStatus = "čeká na vyzvednutí";
                } else {
                    rewardStatus = "zatím není domluvena";
                }
                player.sendMessage(plugin.color("&7Jednorázová odměna: &d" + rewardStatus));
            }
            player.sendMessage(plugin.color("&8&m--------------------------------"));
            return;
        }

        Set<String> ids = getQuestIds(playerId);
        if (ids.isEmpty()) {
            player.sendMessage(plugin.color("&7Zatím nemáš přijaté žádné úkoly."));
        } else {
            for (String id : ids) {
                String status = getStatus(playerId, id);
                player.sendMessage(plugin.color((status.equals("completed") ? "&a✔ " : "&e• ")
                        + "&f" + getTitle(id) + " &8[" + id + "] &7"
                        + (status.equals("completed") ? "dokončeno" :
                        getProgress(playerId, id) + "/" + getTarget(id))));
            }
        }
        player.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save quest progress: " + ex.getMessage());
        }
    }

    private long addWithoutOverflow(long current, long addition) {
        if (addition <= 0L) return current;
        return Long.MAX_VALUE - current < addition ? Long.MAX_VALUE : current + addition;
    }

    private String playerRoot(UUID playerId) {
        return "players." + playerId;
    }

    private String questRoot(UUID playerId, String id) {
        return playerRoot(playerId) + ".quests." + id;
    }
}
