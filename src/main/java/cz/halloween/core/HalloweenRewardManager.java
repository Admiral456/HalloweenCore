package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HalloweenRewardManager {
    private final HalloweenCore plugin;
    private final NamespacedKey rewardKey;

    public HalloweenRewardManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.rewardKey = new NamespacedKey(plugin, "halloween_reward");
    }

    public void listRewards(Player player) {
        player.sendMessage(plugin.color("&8&m--------------------------------"));
        player.sendMessage(plugin.color("&6&lHALLOWEEN ODMĚNY &8• &72026"));
        var section = plugin.getConfig().getConfigurationSection("rewards.shop");
        if (section == null) {
            player.sendMessage(plugin.color("&7Žádné odměny nejsou nakonfigurované."));
            player.sendMessage(plugin.color("&8&m--------------------------------"));
            return;
        }

        for (String id : section.getKeys(false)) {
            long cost = Math.max(0L, section.getLong(id + ".cost", 0L));
            String name = section.getString(id + ".name", id);
            boolean claimed = plugin.getDataManager().hasClaimedReward(player.getUniqueId(), id);
            int minCurse = Math.max(0, section.getInt(id + ".min-curse", 0));
            String requirement = minCurse > 0 ? " &8• &5prokletí " + minCurse : "";
            String status = claimed ? "&8[VYBRÁNO]" : "&a[DOSTUPNÉ]";
            player.sendMessage(plugin.color("&e" + id + " &8» &f" + name + " &7(" + cost + " fragmentů)" + requirement + " " + status));
        }

        player.sendMessage(plugin.color("&7Použití: &f/halloween claim <id>"));
        player.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    public boolean claim(Player player, String id) {
        if (id == null || id.isBlank()) {
            player.sendMessage(plugin.color("&cPoužij /halloween claim <id>."));
            return false;
        }

        String normalizedId = id.toLowerCase(Locale.ROOT);
        var section = plugin.getConfig().getConfigurationSection("rewards.shop." + normalizedId);
        if (section == null) {
            player.sendMessage(plugin.color("&cTahle odměna neexistuje. Dej /halloween rewards."));
            return false;
        }

        if (plugin.getDataManager().hasClaimedReward(player.getUniqueId(), normalizedId)) {
            player.sendMessage(plugin.color("&cTuhle limitovanou odměnu už máš."));
            return false;
        }

        int minCurse = Math.max(0, section.getInt("min-curse", 0));
        int currentCurse = plugin.getService().getCurseLevel(player.getUniqueId());
        if (currentCurse < minCurse) {
            player.sendMessage(plugin.color("&cTahle odměna vyžaduje prokletí alespoň &5" + minCurse + "&c. Tvoje úroveň: &e" + currentCurse + "&c."));
            return false;
        }

        long cost = Math.max(0L, section.getLong("cost", 0L));
        long current = plugin.getService().getFragments(player.getUniqueId());
        if (current < cost) {
            player.sendMessage(plugin.color("&cPotřebuješ &e" + cost + " &cfragmentů. Máš &e" + current + "&c."));
            return false;
        }

        Material material;
        try {
            material = Material.valueOf(section.getString("material", "PAPER").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(plugin.color("&cOdměna má špatně nastavený materiál: " + normalizedId));
            return false;
        }

        int amount = Math.max(1, section.getInt("amount", 1));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(section.getString("name", normalizedId)));
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                lore.add(plugin.color(line));
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(rewardKey, PersistentDataType.STRING, normalizedId);
            item.setItemMeta(meta);
        }

        if (!plugin.getDataManager().removeFragments(player.getUniqueId(), cost)) {
            player.sendMessage(plugin.color("&cOdměnu se nepodařilo bezpečně odečíst."));
            return false;
        }

        var overflow = player.getInventory().addItem(item);
        for (ItemStack overflowItem : overflow.values()) {
            if (overflowItem != null && !overflowItem.getType().isAir() && overflowItem.getAmount() > 0) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
            }
        }

        plugin.getDataManager().markRewardClaimed(player.getUniqueId(), normalizedId);
        player.sendMessage(plugin.color("&6HALLOWEEN &8» &aZískal jsi limitovanou odměnu &f" + section.getString("name", normalizedId) + "&a."));
        return true;
    }
}
