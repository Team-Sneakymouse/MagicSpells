package com.nisovin.magicspells.util.magicitems;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.nisovin.magicspells.MagicSpells;

public final class MagicItemBehaviorKeys {

	private static final NamespacedKey MAGIC_ITEM = new NamespacedKey(MagicSpells.getInstance(), "magicitem");
	private static final NamespacedKey MAGIC_ITEM_VERSION = new NamespacedKey(MagicSpells.getInstance(), "magicitem_version");
	private static final NamespacedKey SOULBOUND_OWNER = new NamespacedKey(MagicSpells.getInstance(), "soulbound_owner");
	private static final NamespacedKey EXPIRES_AT = new NamespacedKey(MagicSpells.getInstance(), "expires_at");
	private static final NamespacedKey CREATOR_NAME = new NamespacedKey(MagicSpells.getInstance(), "creator_name");
	private static final NamespacedKey IGNORED_ATTRIBUTES = new NamespacedKey(MagicSpells.getInstance(), "ignored_attributes");

	private MagicItemBehaviorKeys() {
	}

	public static NamespacedKey magicItem() {
		return MAGIC_ITEM;
	}

	public static NamespacedKey magicItemVersion() {
		return MAGIC_ITEM_VERSION;
	}

	public static NamespacedKey soulboundOwner() {
		return SOULBOUND_OWNER;
	}

	public static NamespacedKey expiresAt() {
		return EXPIRES_AT;
	}

	public static NamespacedKey creatorName() {
		return CREATOR_NAME;
	}

	public static NamespacedKey ignoredAttributes() {
		return IGNORED_ATTRIBUTES;
	}

	public static String getMagicItemName(ItemStack stack) {
		if (stack == null || !stack.hasItemMeta())
			return null;

		return stack.getItemMeta().getPersistentDataContainer()
				.get(MAGIC_ITEM, PersistentDataType.STRING);
	}

}
