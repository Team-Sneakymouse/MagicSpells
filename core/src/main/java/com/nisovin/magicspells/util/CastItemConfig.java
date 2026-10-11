package com.nisovin.magicspells.util;

/**
 * Parsed cast-item list: concrete {@link CastItem}s plus {@code #tag} references.
 */
public record CastItemConfig(CastItem[] items, String[] tags) {

	public static final CastItemConfig EMPTY = new CastItemConfig(new CastItem[0], new String[0]);

	public boolean isEmpty() {
		return (items == null || items.length == 0) && (tags == null || tags.length == 0);
	}

	public boolean matches(org.bukkit.inventory.ItemStack stack) {
		if (stack == null) return false;
		if (items != null) {
			CastItem held = new CastItem(stack);
			for (CastItem item : items) {
				if (item != null && item.equals(held)) return true;
			}
		}
		if (tags != null) {
			for (String tag : tags) {
				if (com.nisovin.magicspells.util.magicitems.MagicItemTags.has(stack, tag)) return true;
			}
		}
		return false;
	}

}
