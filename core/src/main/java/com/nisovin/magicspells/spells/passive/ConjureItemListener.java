package com.nisovin.magicspells.spells.passive;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.events.ConjureItemEvent;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.MagicItems;

// Optional trigger variable that is a pipe separated list of items to accept
public class ConjureItemListener extends PassiveListener {

	private final Set<MagicItemData> items = new HashSet<>();

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		String[] split = var.split("\\|");
		for (String s : split) {
			s = s.trim();

			MagicItemData itemData = MagicItems.getMagicItemDataFromString(s);
			if (itemData == null) {
				MagicSpells.error("Invalid magic item '" + s + "' in conjureitem trigger on passive spell '" + passiveSpell.getInternalName() + "'");
				continue;
			}

			items.add(itemData);
		}
	}

	@OverridePriority
	@EventHandler
	public void conConjure(ConjureItemEvent event) {
		if (!(event.getEntity() instanceof LivingEntity caster)) return;
		if (!hasSpell(caster) || !canTrigger(caster)) return;

		if (!items.isEmpty()) {
			ItemStack item = event.getItemStack();
			if (!contains(item)) return;
		}

		boolean casted = passiveSpell.activate(caster);
	}

	private boolean contains(ItemStack item) {
		for (MagicItemData data : items) {
			if (MagicItems.matches(data, item)) return true;
		}
		return false;
	}

}
