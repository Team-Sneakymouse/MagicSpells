package com.nisovin.magicspells.listeners;

import java.util.Map;
import java.util.HashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastItem;
import com.nisovin.magicspells.util.CastItemClicks;
import com.sneakyitems.events.MagicItemClickEvent;

public class LeftClickListener implements Listener {

	private final Map<CastItem, Spell> leftClickCastItems = new HashMap<>();
	private final Map<String, Spell> leftClickCastTags = new HashMap<>();
	private final Map<String, Long> lastCast = new HashMap<>();

	public LeftClickListener() {
		for (Spell spell : MagicSpells.getSpells().values()) {
			CastItem[] items = spell.getLeftClickCastItems();
			if (items != null) {
				for (CastItem item : items) {
					if (item == null) continue;

					Spell old = leftClickCastItems.put(item, spell);
					if (old != null)
						MagicSpells.error("The spell '" + spell.getInternalName() + "' has same left-click-cast-item as '" + old.getInternalName() + "'!");
				}
			}

			String[] tags = spell.getLeftClickCastItemTags();
			if (tags != null) {
				for (String tag : tags) {
					if (tag == null || tag.isEmpty()) continue;
					Spell old = leftClickCastTags.put(tag, spell);
					if (old != null)
						MagicSpells.error("The spell '" + spell.getInternalName() + "' has same left-click-cast-item tag #" + tag + " as '" + old.getInternalName() + "'!");
				}
			}
		}
	}

	public boolean hasLeftClickCastItems() {
		return !leftClickCastItems.isEmpty() || !leftClickCastTags.isEmpty();
	}

	@EventHandler
	public void onLeftClick(final PlayerInteractEvent event) {
		if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
		if (!event.hasItem()) return;
		if (CastItemClicks.isRegisteredMagicItem(event.getItem())) return;

		cast(event.getPlayer(), event.getItem());
	}

	@EventHandler
	public void onMeleeHit(EntityDamageByEntityEvent event) {
		if (event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player player)) return;

		ItemStack item = player.getInventory().getItemInMainHand();
		if (CastItemClicks.isRegisteredMagicItem(item)) return;

		if (cast(player, item)) {
			event.setCancelled(true);
			// The same attack also emits an arm swing; suppress the queued bound cast.
			CastListener bound = MagicSpells.getInstance().castListener;
			if (bound != null) bound.setNoCast(player);
		}
	}

	@EventHandler
	public void onMagicItemClick(MagicItemClickEvent event) {
		if (!event.isLeftClick()) return;

		switch (event.getTrigger()) {
			case ENTITY -> {
				if (event.isAlreadyDenied()) return;
				if (cast(event.getPlayer(), event.getItem())) {
					event.setCancelled(true);
					CastListener bound = MagicSpells.getInstance().castListener;
					if (bound != null) bound.setNoCast(event.getPlayer());
				}
			}
			case BLOCK, AIR, ARM_SWING -> cast(event.getPlayer(), event.getItem());
			default -> { }
		}
	}

	private boolean cast(Player player, ItemStack item) {
		return CastItemClicks.cast(
				player,
				item,
				leftClickCastItems,
				leftClickCastTags,
				lastCast,
				CastItemClicks.ScheduleStyle.DEFAULT);
	}

}
