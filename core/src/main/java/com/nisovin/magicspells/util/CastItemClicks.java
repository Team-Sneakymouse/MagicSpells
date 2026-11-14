package com.nisovin.magicspells.util;

import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Spellbook;
import com.sneakyitems.util.magicitems.MagicItemTags;

/**
 * Shared cast-item click logic for left/right cast listeners.
 */
public final class CastItemClicks {

	public enum ScheduleStyle {
		/** {@link MagicSpells#scheduleDelayedTask(Runnable, long)} */
		DEFAULT,
		/** {@link MagicSpells#scheduleDelayedTask(Spell, Runnable, long)} */
		WITH_SPELL_OWNER
	}

	private CastItemClicks() {
	}

	public static boolean isRegisteredMagicItem(ItemStack stack) {
		String name = MagicItemTags.magicItemName(stack);
		return name != null && !name.isEmpty();
	}

	public static boolean cast(
			Player player,
			ItemStack item,
			Map<CastItem, Spell> exact,
			Map<String, Spell> tags,
			Map<String, Long> lastCast,
			ScheduleStyle scheduleStyle) {
		if (item == null) return false;

		final Spell spell = CastItemSpellResolver.resolve(exact, tags, item);
		if (spell == null) return false;

		Spellbook spellbook = MagicSpells.getSpellbook(player);

		if (!spellbook.hasSpell(spell) || !spellbook.canCast(spell)) return false;

		if (!spell.isIgnoringGlobalCooldown()) {
			Long lastCastTime = lastCast.get(player.getName());
			if (lastCastTime != null && lastCastTime + MagicSpells.getGlobalCooldown() > System.currentTimeMillis()) return false;
			lastCast.put(player.getName(), System.currentTimeMillis());
		}

		PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
		String[] args = null;
		if (container.has(new NamespacedKey(MagicSpells.getInstance(), "creator_name"), PersistentDataType.STRING)) {
			args = new String[] {container.get(new NamespacedKey(MagicSpells.getInstance(), "creator_name"), PersistentDataType.STRING)};
		}
		final ItemStack castStack = item.clone();
		final String[] castArgs = args;
		Runnable task = () -> spell.hardCast(new SpellData(player, 1.0F, castArgs).castItem(castStack));
		if (scheduleStyle == ScheduleStyle.WITH_SPELL_OWNER) {
			MagicSpells.scheduleDelayedTask(spell, task, 0);
		} else {
			MagicSpells.scheduleDelayedTask(task, 0);
		}
		return true;
	}

}