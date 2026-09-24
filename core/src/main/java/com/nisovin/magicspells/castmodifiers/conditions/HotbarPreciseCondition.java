package com.nisovin.magicspells.castmodifiers.conditions;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.nisovin.magicspells.castmodifiers.Condition;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.magicitems.MagicItemData;

public class HotbarPreciseCondition extends Condition {

	private MagicItemData itemData = null;

	@Override
	public boolean initialize(String var) {
		itemData = MagicItems.getMagicItemDataFromString(var);
		return itemData != null;
	}

	@Override
	public boolean check(LivingEntity caster) {
		return checkHotbar(caster);
	}

	@Override
	public boolean check(LivingEntity caster, LivingEntity target) {
		return checkHotbar(target);
	}

	@Override
	public boolean check(LivingEntity caster, Location location) {
		return false;
	}

	private boolean checkHotbar(LivingEntity target) {
		if (!(target instanceof Player player)) return false;

		PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < 9; i++) {
			ItemStack item = inventory.getItem(i);
			if (MagicItems.matches(itemData, item)) return true;
		}

		return MagicItems.matches(itemData, inventory.getItemInOffHand());
	}

}
