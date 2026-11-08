package com.nisovin.magicspells.spells.passive;

import java.util.Set;
import java.util.HashSet;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.event.inventory.PrepareSmithingEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.OverridePriority;
import com.sneakyitems.util.magicitems.MagicItems;
import com.sneakyitems.util.magicitems.MagicItemData;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

public class SmithListener extends PassiveListener {

	private Set<MagicItemData> firstItem;
	private Set<MagicItemData> secondItem;
	private Set<MagicItemData> resultItem;

	@Override
	public void initialize(String var) {
		if (var == null || var.isEmpty()) return;

		String[] split = var.split(" ", 3);

		if (split.length > 0) {
			if (!split[0].equals("any")) {
				String[] items = split[0].split("\\|");
				firstItem = new HashSet<>();

				for (String item : items) {
					MagicItemData itemData = MagicItems.getMagicItemDataFromString(item);
					if (itemData == null) {
						MagicSpells.error("Invalid magic item '" + item + "' in smith trigger on passive spell '" + passiveSpell.getInternalName() + "'.");
						continue;
					}

					firstItem.add(itemData);
				}
			}
		}

		if (split.length > 1) {
			if (!split[1].equals("any")) {
				String[] items = split[1].split("\\|");
				secondItem = new HashSet<>();

				for (String item : items) {
					MagicItemData itemData = MagicItems.getMagicItemDataFromString(item);
					if (itemData == null) {
						MagicSpells.error("Invalid magic item '" + item + "' in smith trigger on passive spell '" + passiveSpell.getInternalName() + "'.");
						continue;
					}

					secondItem.add(itemData);
				}
			}
		}

		if (split.length > 2) {
			if (!split[2].equals("any")) {
				String[] items = split[2].split("\\|");
				resultItem = new HashSet<>();

				for (String item : items) {
					MagicItemData itemData = MagicItems.getMagicItemDataFromString(item);
					if (itemData == null) {
						MagicSpells.error("Invalid magic item '" + item + "' in smith trigger on passive spell '" + passiveSpell.getInternalName() + "'.");
						continue;
					}

					resultItem.add(itemData);
				}
			}
		}

		if (firstItem != null && firstItem.isEmpty()) firstItem = null;
		if (secondItem != null && secondItem.isEmpty()) secondItem = null;
		if (resultItem != null && resultItem.isEmpty()) resultItem = null;
	}

	@OverridePriority
	@EventHandler
	public void onSmith(PrepareSmithingEvent event) {
		SmithingInventory inventory = event.getInventory();

		LivingEntity caster = event.getView().getPlayer();
		if (!hasSpell(caster) || !canTrigger(caster)) return;

		if (firstItem != null && !contains(firstItem, inventory.getInputEquipment())) return;
		if (secondItem != null && !contains(secondItem, inventory.getInputMineral())) return;
		if (resultItem != null && !contains(resultItem, inventory.getResult())) return;

		boolean casted = passiveSpell.activate(caster);
		if (cancelDefaultAction(casted)) event.setResult(null);
	}

	private boolean contains(Set<MagicItemData> items, ItemStack item) {
		if (item == null) item = new ItemStack(Material.AIR);

		for (MagicItemData data : items)
			if (MagicItems.matches(data, item))
				return true;

		return false;
	}

}
