package com.nisovin.magicspells.util.reagent;

import org.apache.commons.math4.core.jdkmath.AccurateMath;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.Inventory;

import com.sneakyitems.util.magicitems.MagicItemData;
import com.sneakyitems.util.magicitems.MagicItems;

public class DurabilityReagent extends Reagent {
    private int durability;
    private MagicItemData magicItem;

    public DurabilityReagent(int durability) {
        this.durability = durability;
        this.magicItem = null;
    }

    public DurabilityReagent(int durability, MagicItemData magicItem) {
        this.durability = durability;
        this.magicItem = magicItem;
    }

    @Override
    public Number get() {
        return durability;
    }

    public void add(int durability) {
        this.durability += durability;
    }

    public void set(int durability) {
        this.durability = durability;
    }

    @Override
    public boolean has(LivingEntity livingEntity) {
        if (durability > 0) {
            if (magicItem != null) {
                // Check for MagicItem in inventory
                if (!(livingEntity instanceof Player player)) return false;
                Inventory inventory = player.getInventory();
                
                // Find the first matching MagicItem
                ItemStack targetItem = findMagicItemInInventory(inventory);
                if (targetItem == null) return false;
                
                // Check if it has enough durability left
                if (!(targetItem.getItemMeta() instanceof Damageable damageable)) return false;
                int currentDamage = damageable.getDamage();
                int maxDurability = targetItem.getType().getMaxDurability();
                
                // Check if adding durability cost would exceed max durability
                return currentDamage + durability <= maxDurability;
            } else {
                // Original behavior: check main hand item
                EntityEquipment equipment = livingEntity.getEquipment();
                if (equipment == null) return false;
                ItemStack inHand = equipment.getItemInMainHand();
                if (!(inHand.getItemMeta() instanceof Damageable damageable)) return false;
                if (damageable.getDamage() >= inHand.getType().getMaxDurability()) return false;
            }
        }
        return true;
    }

    @Override
    public void remove(LivingEntity livingEntity) {
        if (durability != 0) {
            if (magicItem != null) {
                // Handle MagicItem durability
                if (!(livingEntity instanceof Player player)) return;
                Inventory inventory = player.getInventory();
                
                // Find the first matching MagicItem and its slot
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    ItemStack item = inventory.getItem(slot);
                    if (item == null) continue;
                    
                    if (!MagicItems.matches(magicItem, item)) continue;
                    
                    // Found matching MagicItem, apply durability damage
                    ItemMeta meta = item.getItemMeta();
                    int maxDurability = item.getType().getMaxDurability();
                    
                    if (maxDurability > 0 && meta instanceof Damageable damageable) {
                        int currentDamage = damageable.getDamage();
                        int newDamage = AccurateMath.max(AccurateMath.min(currentDamage + durability, maxDurability), 0);
                        
                        // If damage equals max durability, remove the item
                        if (newDamage >= maxDurability) {
                            inventory.setItem(slot, null);
                        } else {
                            damageable.setDamage(newDamage);
                            item.setItemMeta(meta);
                        }
                    }
                    return; // Only damage the first matching item
                }
            } else {
                // Original behavior: damage main hand item
                EntityEquipment eq = livingEntity.getEquipment();

                if (eq != null) {
                    ItemStack item = eq.getItemInMainHand();
                    ItemMeta meta = item.getItemMeta();

                    int maxDurability = item.getType().getMaxDurability();
                    if (maxDurability > 0 && meta instanceof Damageable damageable) {
                        int damage = damageable.getDamage() + durability;
                        damage = AccurateMath.max(AccurateMath.min(damage, maxDurability), 0);

                        damageable.setDamage(damage);
                        item.setItemMeta(meta);
                    }
                }
            }
        }
    }

    @Override
    public void multiply(float multiplier) {
        durability = Math.round(durability * multiplier);
    }

    private ItemStack findMagicItemInInventory(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item == null) continue;
            
            if (MagicItems.matches(magicItem, item)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public DurabilityReagent clone() {
        return new DurabilityReagent(durability, magicItem);
    }

    @Override
    public String toString() {
        if (magicItem != null) {
            return "durability=" + durability + ", magicItem=" + magicItem.toString();
        }
        return "durability=" + durability;
    }
}
