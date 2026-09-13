package com.nisovin.magicspells.spells.instant;

import com.nisovin.magicspells.util.performance.PerformanceDiagnostics;
import com.nisovin.magicspells.util.performance.PerformanceRecorder;

import java.util.Map;
import java.util.HashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.MagicItemBehaviors;
import com.nisovin.magicspells.util.magicitems.MagicItemExpirationScheduler;
import com.nisovin.magicspells.util.magicitems.MagicItemUpdater;

public class ItemTagSpell extends InstantSpell implements Listener {

    private final Map<MagicItemData, TagMapping> mapping = new HashMap<>();
    private final NamespacedKey nameKey = new NamespacedKey(MagicSpells.getInstance(), "magicitem");

    public ItemTagSpell(MagicConfig config, String spellName) {
        super(config, spellName);

        ConfigurationSection mappingSection = getConfigSection("mapping");
        if (mappingSection != null) {
            for (String key : mappingSection.getKeys(false)) {
                ConfigurationSection itemSection = mappingSection.getConfigurationSection(key);
                if (itemSection == null)
                    continue;

                MagicItem magicItem = MagicItems.getMagicItemFromSection(itemSection);
                if (magicItem == null)
                    continue;

                // Ignore amount by default for tagging.
                // MAGIC_ITEM_NAME is always set from the mapping section by PersistentDataHandler; items
                // being tagged usually have no (or wrong) magicitem PDC yet, so matching would fail.
                magicItem.getMagicItemData().getIgnoredAttributes().add(MagicItemData.MagicItemAttribute.AMOUNT);
                magicItem.getMagicItemData().getIgnoredAttributes().add(MagicItemData.MagicItemAttribute.MAGIC_ITEM_NAME);

                String internalName = itemSection.getString("internal-name", key);
                mapping.put(magicItem.getMagicItemData(), new TagMapping(internalName, magicItem));
            }
        }
    }

    @Override
    public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
        if (caster instanceof Player player) {
            tagInventory(player.getInventory(), player);
        }
        return PostCastAction.HANDLE_NORMALLY;
    }

    /**
     * Event listener that checks any inventory being opened for matches.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (isBagOfHoldingGui(event.getInventory())) return;
        Player player = event.getPlayer() instanceof Player p ? p : null;
        tagInventory(event.getInventory(), player);
    }

    /** SneakyBagOfHolding builds look-alike icons without magicitem PDC; do not re-tag them. */
    private static boolean isBagOfHoldingGui(Inventory inventory) {
        if (inventory == null) return false;
        var holder = inventory.getHolder();
        if (holder == null) return false;
        String name = holder.getClass().getName();
        return name.startsWith("com.sneakybagofholding.gui.BagInventoryHolder");
    }

    /**
     * Scans an inventory for items matching the configured mapping.
     * If a match is found without the correct 'magicitem' tag, it tags and updates
     * the item.
     */
    private void tagInventory(Inventory inventory, Player player) {
        try (var scope = PerformanceDiagnostics.RECORDER
                .enter("item_tag", getInternalName(), inventory.getType().name())) {
            scope.add(PerformanceRecorder.Counter.MAPPING_COUNT, mapping.size());
            tagInventoryMeasured(inventory, player, scope);
        }
    }

    private void tagInventoryMeasured(Inventory inventory, Player player,
            PerformanceRecorder.Scope scope) {
        if (isBagOfHoldingGui(inventory))
            return;
        ItemStack[] contents = inventory.getContents();
        scope.add(PerformanceRecorder.Counter.INVENTORY_SLOTS, contents.length);
        boolean changed = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir())
                continue;

            ItemMeta meta = item.getItemMeta();
            scope.add(PerformanceRecorder.Counter.ITEMS_EXAMINED, 1);
            if (meta != null && meta.getPersistentDataContainer().has(
                    new NamespacedKey("sneakybagofholding", "gui_display"),
                    PersistentDataType.BYTE)) {
                continue;
            }

            MagicItemData itemData = MagicItems.getMagicItemDataFromItemStack(item);
            if (itemData == null)
                continue;
            for (Map.Entry<MagicItemData, TagMapping> entry : mapping.entrySet()) {
                MagicItemData targetData = entry.getKey();
                TagMapping tagMapping = entry.getValue();
                String internalName = tagMapping.internalName();
                MagicItem targetMagicItem = tagMapping.magicItem();
                if (MagicItems.matches(targetData, item)) {
                    if (meta == null)
                        continue;
                    String currentName = meta.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
                    if (internalName.equals(currentName))
                        continue;
                    // It's a match and needs a tag update.

                    // Priority 1: Try to update using a real item from the global items.yml
                    MagicItem globalItem = MagicItems.getMagicItemByInternalName(internalName);
                    MagicItem updateSource = globalItem != null ? globalItem : targetMagicItem;

                    ItemStack updated = MagicItemUpdater.updateItem(item, updateSource);

                    // Force the correct tag on the updated item (MagicItemUpdater might have
                    // overwritten it)
                    ItemMeta updatedMeta = updated.getItemMeta();
                    if (updatedMeta != null) {
                        updatedMeta.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, internalName);
                        updated.setItemMeta(updatedMeta);
                    }

                    contents[i] = updated;
                    scope.add(PerformanceRecorder.Counter.ITEMS_UPDATED, 1);
                    MagicItemBehaviors.applyFromData(updated, updateSource.getMagicItemData(), player);
                    if (player != null)
                        MagicItemExpirationScheduler.scheduleFromItem(player, updated);
                    changed = true;
                    break;
                }
            }
        }

        if (changed) {
            inventory.setContents(contents);
        }
    }

    private record TagMapping(String internalName, MagicItem magicItem) {
    }

}
