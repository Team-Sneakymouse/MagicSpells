package com.nisovin.magicspells.util;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.util.magicitems.MagicItemTags;

/**
 * Resolves a spell from exact {@link CastItem} maps, then {@code #tag} maps.
 */
public final class CastItemSpellResolver {

	private CastItemSpellResolver() {
	}

	public static Spell resolve(Map<CastItem, Spell> exact, Map<String, Spell> byTag, ItemStack stack) {
		if (stack == null) return null;
		if (exact != null) {
			Spell spell = exact.get(new CastItem(stack));
			if (spell != null) return spell;
		}
		if (byTag == null || byTag.isEmpty()) return null;
		for (String tag : MagicItemTags.read(stack)) {
			Spell spell = byTag.get(tag);
			if (spell != null) return spell;
		}
		return null;
	}

}
