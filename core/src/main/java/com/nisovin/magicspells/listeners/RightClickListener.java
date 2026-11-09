package com.nisovin.magicspells.listeners;

import java.util.Map;
import java.util.HashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastItem;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.CastItemSpellResolver;

public class RightClickListener implements Listener {

	private final Map<CastItem, Spell> rightClickCastItems = new HashMap<>();
	private final Map<String, Spell> rightClickCastTags = new HashMap<>();
	private final Map<String, Long> lastCast = new HashMap<>();
	
	public RightClickListener() {
		for (Spell spell : MagicSpells.getSpells().values()) {
			CastItem[] items = spell.getRightClickCastItems();
			if (items != null) {
				for (CastItem item : items) {
					if (item == null) continue;

					Spell old = rightClickCastItems.put(item, spell);
					if (old != null)
						MagicSpells.error("The spell '" + spell.getInternalName() + "' has same right-click-cast-item as '" + old.getInternalName() + "'!");
				}
			}

			String[] tags = spell.getRightClickCastItemTags();
			if (tags != null) {
				for (String tag : tags) {
					if (tag == null || tag.isEmpty()) continue;
					Spell old = rightClickCastTags.put(tag, spell);
					if (old != null)
						MagicSpells.error("The spell '" + spell.getInternalName() + "' has same right-click-cast-item tag #" + tag + " as '" + old.getInternalName() + "'!");
				}
			}
		}
	}
	
	public boolean hasRightClickCastItems() {
		return !rightClickCastItems.isEmpty() || !rightClickCastTags.isEmpty();
	}
	
	@EventHandler
	public void onRightClick(final PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if (!event.hasItem()) return;

		if (cast(event.getPlayer(), event.getItem())) event.setCancelled(true);
	}

	@EventHandler
	public void onRightClickEntity(PlayerInteractEntityEvent event) {
		if (cast(event.getPlayer(), event.getPlayer().getInventory().getItem(event.getHand())))
			event.setCancelled(true);
	}

	private boolean cast(Player player, ItemStack item) {
		if (item == null) return false;

		final Spell spell = CastItemSpellResolver.resolve(rightClickCastItems, rightClickCastTags, item);
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
		MagicSpells.scheduleDelayedTask(
				() -> spell.hardCast(new SpellData(player, 1.0F, castArgs).castItem(castStack)), 0);
		return true;
	}
	
}
