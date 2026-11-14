package com.nisovin.magicspells.listeners;

import java.util.Map;
import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastItem;
import com.nisovin.magicspells.util.CastItemClicks;
import com.sneakyitems.events.MagicItemClickEvent;

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
		if (CastItemClicks.isRegisteredMagicItem(event.getItem())) return;

		if (cast(event.getPlayer(), event.getItem())) event.setCancelled(true);
	}

	@EventHandler
	public void onRightClickEntity(PlayerInteractEntityEvent event) {
		ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
		if (CastItemClicks.isRegisteredMagicItem(item)) return;

		if (cast(event.getPlayer(), item)) event.setCancelled(true);
	}

	@EventHandler
	public void onMagicItemClick(MagicItemClickEvent event) {
		if (!event.isRightClick() || event.isAlreadyDenied()) return;

		if (cast(event.getPlayer(), event.getItem())) event.setCancelled(true);
	}

	private boolean cast(Player player, ItemStack item) {
		return CastItemClicks.cast(
				player,
				item,
				rightClickCastItems,
				rightClickCastTags,
				lastCast,
				CastItemClicks.ScheduleStyle.WITH_SPELL_OWNER);
	}

}
