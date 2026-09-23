package com.nisovin.magicspells.spells.passive;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.events.MagicItemExpireEvent;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.MagicItems;

// Optional trigger variable that is a pipe separated list of items to accept
public class ExpireItemListener extends PassiveListener {

	private final Set<MagicItemData> items = new HashSet<>();

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		String[] split = var.split("\\|");
		for (String s : split) {
			s = s.trim();

			MagicItemData itemData = MagicItems.getMagicItemDataFromString(s);
			if (itemData == null) {
				MagicSpells.error("Invalid magic item '" + s + "' in expireitem trigger on passive spell '" + passiveSpell.getInternalName() + "'");
				continue;
			}

			items.add(itemData);
		}
	}

	@OverridePriority
	@EventHandler
	public void onExpire(MagicItemExpireEvent event) {
		Player caster = event.getPlayer();
		if (!hasSpell(caster) || !canTrigger(caster)) return;

		if (!items.isEmpty()) {
			ItemStack item = event.getItemStack();
			if (!contains(item)) return;
		}

		passiveSpell.activate(caster);
	}

	private boolean contains(ItemStack item) {
		for (MagicItemData data : items) {
			if (MagicItems.matches(data, item)) return true;
		}
		return false;
	}

}
