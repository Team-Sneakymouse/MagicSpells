package com.nisovin.magicspells.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.ItemPdcKeys.Slot;

/**
 * Helpers for magic-item persistent-data keys and slotted item resolution.
 */
public final class ItemPdcUtil {

	public static final String REGULAR_PREFIX = ItemPdcKeys.REGULAR_PREFIX;
	public static final String PERMANENT_PREFIX = ItemPdcKeys.PERMANENT_PREFIX;

	private ItemPdcUtil() {
	}

	public static ItemStack resolveSlot(SpellData data, Slot slot) {
		if (slot == null) return null;
		return switch (slot) {
			case CAST -> data != null && data.hasCastItem() ? data.castItem() : null;
			case MAINHAND -> hand(data != null ? data.caster() : null, true);
			case OFFHAND -> hand(data != null ? data.caster() : null, false);
		};
	}

	public static ItemStack resolveSlot(LivingEntity entity, Slot slot) {
		if (slot == null) return null;
		return switch (slot) {
			case CAST -> null;
			case MAINHAND -> hand(entity, true);
			case OFFHAND -> hand(entity, false);
		};
	}

	private static ItemStack hand(LivingEntity entity, boolean main) {
		if (entity == null) return null;
		if (entity instanceof Player player) {
			return main ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
		}
		EntityEquipment equipment = entity.getEquipment();
		if (equipment == null) return null;
		return main ? equipment.getItemInMainHand() : equipment.getItemInOffHand();
	}

	public static String toPdcKeyName(String key) {
		return ItemPdcKeys.toPdcKeyName(key);
	}

	public static boolean hasKey(ItemStack stack, String key) {
		String pdcKey = toPdcKeyName(key);
		if (stack == null || !stack.hasItemMeta() || pdcKey == null) return false;
		NamespacedKey namespacedKey = new NamespacedKey(MagicSpells.getInstance(), pdcKey);
		return stack.getItemMeta().getPersistentDataContainer().has(namespacedKey, PersistentDataType.STRING);
	}

	public static String getString(ItemStack stack, String key) {
		String pdcKey = toPdcKeyName(key);
		if (stack == null || !stack.hasItemMeta() || pdcKey == null) return null;
		NamespacedKey namespacedKey = new NamespacedKey(MagicSpells.getInstance(), pdcKey);
		return stack.getItemMeta().getPersistentDataContainer().get(namespacedKey, PersistentDataType.STRING);
	}

	public static String stripQuotes(String value) {
		return ItemPdcKeys.stripQuotes(value);
	}

}
