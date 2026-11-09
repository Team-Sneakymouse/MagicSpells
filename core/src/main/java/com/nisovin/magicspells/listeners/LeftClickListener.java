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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastItem;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.CastItemSpellResolver;

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

		cast(event.getPlayer(), event.getItem());
	}

	@EventHandler
	public void onMeleeHit(EntityDamageByEntityEvent event) {
		if (event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player player)) return;
		if (cast(player, player.getInventory().getItemInMainHand())) {
			event.setCancelled(true);
			// The same attack also emits an arm swing; suppress the queued bound cast.
			CastListener bound = MagicSpells.getInstance().castListener;
			if (bound != null) bound.setNoCast(player);
		}
	}

	private boolean cast(Player player, ItemStack item) {
		if (item == null) return false;

		final Spell spell = CastItemSpellResolver.resolve(leftClickCastItems, leftClickCastTags, item);
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
