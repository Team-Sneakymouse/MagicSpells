package com.nisovin.magicspells.listeners;

import java.util.Map;
import java.util.HashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastItem;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.CastItemSpellResolver;
import com.nisovin.magicspells.Spell.SpellCastState;
import com.nisovin.magicspells.Spell.SpellCastResult;

public class ConsumeListener implements Listener {

	private Map<CastItem, Spell> consumeCastItems = new HashMap<>();
	private Map<String, Spell> consumeCastTags = new HashMap<>();
	private Map<String, Long> lastCast = new HashMap<>();
	
	public ConsumeListener() {
		for (Spell spell : MagicSpells.getSpells().values()) {
			CastItem[] items = spell.getConsumeCastItems();
			if (items != null) {
				for (CastItem item : items) {
					if (item == null) continue;
					Spell old = consumeCastItems.put(item, spell);
					if (old == null) continue;
					MagicSpells.error("The spell '" + spell.getInternalName() + "' has same consume-cast-item as '" + old.getInternalName() + "'!");
				}
			}

			String[] tags = spell.getConsumeCastItemTags();
			if (tags != null) {
				for (String tag : tags) {
					if (tag == null || tag.isEmpty()) continue;
					Spell old = consumeCastTags.put(tag, spell);
					if (old == null) continue;
					MagicSpells.error("The spell '" + spell.getInternalName() + "' has same consume-cast-item tag #" + tag + " as '" + old.getInternalName() + "'!");
				}
			}
		}
	}
	
	public boolean hasConsumeCastItems() {
		return !consumeCastItems.isEmpty() || !consumeCastTags.isEmpty();
	}
	
	@EventHandler
	public void onConsume(final PlayerItemConsumeEvent event) {
		ItemStack item = event.getItem();
		final Spell spell = CastItemSpellResolver.resolve(consumeCastItems, consumeCastTags, item);
		if (spell == null) return;

		Player player = event.getPlayer();
		Long lastCastTime = lastCast.get(player.getName());
		if (lastCastTime != null && lastCastTime + MagicSpells.getGlobalCooldown() > System.currentTimeMillis()) return;
		lastCast.put(player.getName(), System.currentTimeMillis());

		if (MagicSpells.getSpellbook(player).canCast(spell)) {
			PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
			String[] args = null;
			if (container.has(new NamespacedKey(MagicSpells.getInstance(), "creator_name"), PersistentDataType.STRING)) {
				args = new String[] {container.get(new NamespacedKey(MagicSpells.getInstance(), "creator_name"), PersistentDataType.STRING)};
			}

			SpellCastResult result = spell.hardCast(new SpellData(player, 1.0F, args).castItem(item));
			if (result.state != SpellCastState.NORMAL) event.setCancelled(true);
		}
	}

}
