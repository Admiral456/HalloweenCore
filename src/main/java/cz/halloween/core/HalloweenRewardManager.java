package cz.halloween.core;

import org.bukkit.Bukkit;
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

    public void openMenu(Player player) {
        if (player == null) return;

        var section = plugin.getConfig().getConfigurationSection("rewards.shop");
        if (section == null) {
            listRewards(player);
            return;
        }

        HalloweenRewardMenuHolder holder = new HalloweenRewardMenuHolder();
        var inventory = Bukkit.createInventory(holder, 27, plugin.color("&6&lHALLOWEEN ODMĚNY &8• &72026"));
        holder.bind(inventory);

        ItemStack filler = namedItem(Material.BLACK_STAINED_GLASS_PANE, "&0");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        ItemStack balance = namedItem(Material.GOLD_NUGGET,
                "&6&lTvoje fragmenty");
        ItemMeta balanceMeta = balance.getItemMeta();
        if (balanceMeta != null) {
            balanceMeta.setLore(List.of(
                    plugin.color("&7K dispozici: &e" + plugin.getService().getFragments(player.getUniqueId())),
                    plugin.color("&7Celoživotně získáno: &e" + plugin.getDataManager().getLifetimeFragments(player.getUniqueId()))
            ));
            balance.setItemMeta(balanceMeta);
        }
        inventory.setItem(22, balance);

        ItemStack progress = namedItem(Material.CLOCK, "&6&lServerový progress");
        ItemMeta progressMeta = progress.getItemMeta();
        if (progressMeta != null) {
            progressMeta.setLore(List.of(
                    plugin.color("&7" + plugin.getService().getServerFragments() + " &8/ &7" + plugin.getService().getGlobalGoal()),
                    plugin.color("&7" + String.format("%.1f", plugin.getService().getGlobalProgressPercent()) + "%")
            ));
            progress.setItemMeta(progressMeta);
        }
        inventory.setItem(4, progress);

        int[] slots = {11, 13, 15};
        int index = 0;
        for (String id : section.getKeys(false)) {
            if (index >= slots.length) break;
            int slot = slots[index++];
            holder.bindReward(slot, id);
            inventory.setItem(slot, createRewardIcon(player, id, section.getConfigurationSection(id)));
        }

        player.openInventory(inventory);
    }

    private ItemStack createRewardIcon(Player player, String id, org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) return namedItem(Material.BARRIER, "&cChybná odměna");

        int amount = Math.max(1, section.getInt("amount", 1));
        String itemsAdderId = section.getString("itemsadder-id", "");
        ItemStack item = itemsAdderId.isBlank()
                ? null
                : plugin.getItemManager().getItemsAdderItem(itemsAdderId, amount);

        if (item == null) {
            Material material;
            try {
                material = Material.valueOf(section.getString("material", "PAPER").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return namedItem(Material.BARRIER, "&cChybný materiál");
            }
            item = new ItemStack(material, 1);
        } else {
            item.setAmount(1);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(section.getString("name", id)));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.color("&7Cena: &e" + Math.max(0L, section.getLong("cost", 0L)) + " fragmentů"));
            int minCurse = Math.max(0, section.getInt("min-curse", 0));
            if (minCurse > 0) {
                lore.add(plugin.color("&7Vyžaduje prokletí: &5" + minCurse));
            }
            lore.add("");
            boolean claimed = plugin.getDataManager().hasClaimedReward(player.getUniqueId(), id);
            if (claimed) {
                lore.add(plugin.color("&8UŽ VYZVEDNUTO"));
            } else if (plugin.getService().getFragments(player.getUniqueId()) < Math.max(0L, section.getLong("cost", 0L))) {
                lore.add(plugin.color("&cNemáš dost fragmentů."));
            } else if (plugin.getService().getCurseLevel(player.getUniqueId()) < minCurse) {
                lore.add(plugin.color("&5Nemáš dostatečné prokletí."));
            } else {
                lore.add(plugin.color("&aKlikni pro vyzvednutí"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(name));
            item.setItemMeta(meta);
        }
        return item;
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

    public double getBonusMultiplier(Player player) {
        if (player == null) return 1.0D;

        double bonus = 0.0D;
        var shop = plugin.getConfig().getConfigurationSection("rewards.shop");
        if (shop == null) return 1.0D;

        if (isEquippedReward(player, "hunter-mask")) {
            bonus += Math.max(0.0D, shop.getDouble("hunter-mask.bonus-multiplier", 0.05D));
        }
        if (isHeldReward(player, "cursed-talisman")) {
            bonus += Math.max(0.0D, shop.getDouble("cursed-talisman.bonus-multiplier", 0.10D));
        }
        return Math.max(1.0D, 1.0D + bonus);
    }

    private boolean isHeldReward(Player player, String rewardId) {
        return isRewardItem(player.getInventory().getItemInMainHand(), rewardId)
                || isRewardItem(player.getInventory().getItemInOffHand(), rewardId);
    }

    private boolean isEquippedReward(Player player, String rewardId) {
        return isRewardItem(player.getInventory().getHelmet(), rewardId);
    }

    private boolean isRewardItem(ItemStack item, String rewardId) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return false;
        String stored = item.getItemMeta().getPersistentDataContainer().get(rewardKey, PersistentDataType.STRING);
        return rewardId.equalsIgnoreCase(stored);
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

        int amount = Math.max(1, section.getInt("amount", 1));
        String itemsAdderId = section.getString("itemsadder-id", "");
        ItemStack item = itemsAdderId.isBlank() ? null : plugin.getItemManager().getItemsAdderItem(itemsAdderId, amount);

        if (item == null) {
            Material material;
            try {
                material = Material.valueOf(section.getString("material", "PAPER").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                player.sendMessage(plugin.color("&cOdměna má špatně nastavený materiál: " + normalizedId));
                return false;
            }
            item = new ItemStack(material, amount);

            if (!itemsAdderId.isBlank()) {
                plugin.getItemManager().warnIfMissing(itemsAdderId, "reward " + normalizedId);
            }
        }
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

        // A zero-cost reward is valid in config and must not call removeFragments(0), which rejects non-positive amounts.
        if (cost > 0L && !plugin.getDataManager().removeFragments(player.getUniqueId(), cost)) {
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
        // Persist limited claims immediately rather than waiting for the periodic save interval.
        plugin.getDataManager().save();
        player.sendMessage(plugin.color("&6HALLOWEEN &8» &aZískal jsi limitovanou odměnu &f" + section.getString("name", normalizedId) + "&a."));
        return true;
    }
}
