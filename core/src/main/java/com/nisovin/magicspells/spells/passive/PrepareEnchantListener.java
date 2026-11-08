package com.nisovin.magicspells.spells.passive;

import java.util.Set;
import java.util.HashSet;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.OverridePriority;
import com.sneakyitems.util.magicitems.MagicItems;
import com.sneakyitems.util.magicitems.MagicItemData;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

public class PrepareEnchantListener extends PassiveListener {

	private Set<MagicItemData> items;

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		String[] data = var.split("\\|");
		items = new HashSet<>();

		for (String item : data) {
			MagicItemData itemData = MagicItems.getMagicItemDataFromString(item);
			if (itemData == null) {
				MagicSpells.error("Invalid magic item '" + item + "' in enchant trigger on passive spell '" + passiveSpell.getInternalName() + "'.");
				continue;
			}

			items.add(itemData);
		}

		if (items.isEmpty()) items = null;
	}

	@OverridePriority
	@EventHandler
	public void onEnchant(PrepareItemEnchantEvent event) {
		if (!isCancelStateOk(event.isCancelled())) return;

		LivingEntity caster = event.getEnchanter();
		if (!hasSpell(caster) || !canTrigger(caster)) return;

		if (items != null && !contains(event.getItem())) return;

		boolean casted = passiveSpell.activate(caster);
		if (cancelDefaultAction(casted)) event.setCancelled(true);
	}

	private boolean contains(ItemStack item) {
		if (item == null) item = new ItemStack(Material.AIR);

		for (MagicItemData data : items)
			if (MagicItems.matches(data, item))
				return true;

		return false;
	}

}
