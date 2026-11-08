package com.nisovin.magicspells.util.reagent;

import java.util.Map;
import java.util.HashMap;
import java.util.AbstractMap;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.InventoryUtil;
import com.nisovin.magicspells.util.compat.BagOfHoldingCompat;
import com.sneakyitems.util.magicitems.MagicItemData;

public class ItemReagent extends Reagent {
    private Map<MagicItemData, Integer> itemMap;
    private Map<MagicItemData, String> itemIds;
    private boolean consumeFromBagOfHolding;

    public ItemReagent() {
        this.itemMap = new HashMap<>();
        this.itemIds = new HashMap<>();
        this.consumeFromBagOfHolding = false;
    }

    public int get(MagicItemData magicItem) {
        return itemMap.getOrDefault(magicItem, 0);
    }

    public void add(MagicItemData magicItem, int quantity) {
        add(magicItem, quantity, null);
    }

    public void add(MagicItemData magicItem, int quantity, String itemId) {
        if (itemMap.containsKey(magicItem)) {
            int currentQuantity = itemMap.get(magicItem);
            itemMap.put(magicItem, currentQuantity + quantity);
        } else {
            itemMap.put(magicItem, quantity);
        }
        if (itemId != null && !itemId.isEmpty()) {
            itemIds.put(magicItem, itemId);
        }
    }

    public void set(MagicItemData magicItem, int quantity) {
        itemMap.put(magicItem, quantity);
    }

    public void setConsumeFromBagOfHolding(boolean consumeFromBagOfHolding) {
        this.consumeFromBagOfHolding = consumeFromBagOfHolding;
    }

    public boolean isConsumeFromBagOfHolding() {
        return consumeFromBagOfHolding;
    }

    public boolean isEmpty() {
        return itemMap.isEmpty();
    }

    @Override
    public boolean has(LivingEntity livingEntity) {
        if (!itemMap.isEmpty()) {
            if (livingEntity instanceof Player player) {
                Inventory inventory = player.getInventory();
                for (Map.Entry<MagicItemData, Integer> item : itemMap.entrySet()) {
                    if (item == null) continue;
                    MagicItemData itemData = item.getKey();
                    if (itemData == null) continue;
                    int needed = item.getValue();
                    int invCount = InventoryUtil.inventoryCount(inventory, itemData);
                    if (invCount >= needed) continue;
                    if (consumeFromBagOfHolding) {
                        String itemId = itemIds.get(itemData);
                        if (itemId != null) {
                            int bagCount = BagOfHoldingCompat.getStoredAmount(player, itemId);
                            if (invCount + bagCount >= needed) continue;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void remove(LivingEntity livingEntity) {
        if (!itemMap.isEmpty()) {
            for (Map.Entry<MagicItemData, Integer> item : itemMap.entrySet()) {
                if (item == null) continue;
                MagicItemData itemData = item.getKey();
                if (itemData == null) continue;
                int needed = item.getValue();
                if (livingEntity instanceof Player player) {
                    int remaining = needed;
                    if (consumeFromBagOfHolding) {
                        String itemId = itemIds.get(itemData);
                        if (itemId != null) {
                            int stored = BagOfHoldingCompat.getStoredAmount(player, itemId);
                            int fromBag = Math.min(remaining, stored);
                            if (fromBag > 0) {
                                remaining -= BagOfHoldingCompat.consume(player, itemId, fromBag);
                            }
                        }
                    }
                    if (remaining > 0) {
                        Util.removeFromInventory(player, player.getInventory(),
                                new AbstractMap.SimpleEntry<>(itemData, remaining));
                    }
                } else if (livingEntity.getEquipment() != null) {
                    Util.removeFromInventory(livingEntity.getEquipment(), item);
                }
            }
        }
    }

    @Override
    public void multiply(float multiplier) {
        for (MagicItemData key : itemMap.keySet()) {
            int newQuantity = Math.round(itemMap.get(key) * multiplier);
            itemMap.put(key, newQuantity);
        }
    }

    @Override
    public ItemReagent clone() {
        ItemReagent cloned = new ItemReagent();
        cloned.consumeFromBagOfHolding = consumeFromBagOfHolding;
        for (MagicItemData key : itemMap.keySet()) {
            cloned.add(key, itemMap.get(key), itemIds.get(key));
        }
        return cloned;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ItemReagent{");
        for (MagicItemData key : itemMap.keySet()) {
            sb.append(key.toString()).append("=").append(itemMap.get(key)).append(", ");
        }
        sb.delete(sb.length() - 2, sb.length()); // Remove last comma and space
        sb.append("}");
        return sb.toString();
    }

    @Override
    public Number get() {
        return isEmpty() ? 0.0 : 1.0;
    }
}
