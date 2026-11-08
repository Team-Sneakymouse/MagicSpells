package com.nisovin.magicspells.castmodifiers.conditions;

import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.EntityEquipment;

import com.nisovin.magicspells.util.InventoryUtil;
import com.nisovin.magicspells.util.SpellData;
import com.sneakyitems.util.magicitems.MagicItem;
import com.sneakyitems.util.magicitems.MagicItemData;
import com.sneakyitems.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.ConfigDataUtil;
import com.nisovin.magicspells.castmodifiers.conditions.util.OperatorCondition;

public class HasItemAmountCondition extends OperatorCondition {

	private ConfigData<MagicItem> itemData;
	private ConfigData<String> amount;
	
	@Override
	public boolean initialize(String var) {
		String[] args = var.split(";");
		if (args.length < 2) return false;

		amount = ConfigDataUtil.getString(args[0]);
		itemData = ConfigDataUtil.getMagicItem(args[1]);
		return true;
	}

	@Override
	public boolean check(LivingEntity caster) {
		return check(caster, caster);
	}

	@Override
	public boolean check(LivingEntity caster, LivingEntity target) {
		if (target == null) return false;
		if (target instanceof InventoryHolder holder) return checkInventory(caster, target, holder.getInventory());
		else return checkEquipment(caster, target, target.getEquipment());
	}

	@Override
	public boolean check(LivingEntity caster, Location location) {
		BlockState targetState = location.getBlock().getState();
		return targetState instanceof InventoryHolder holder && checkInventory(caster, caster, holder.getInventory());
	}

	private boolean checkInventory(LivingEntity caster, LivingEntity target, Inventory inventory) {
		SpellData data = new SpellData(caster, target, 1f, new String[0]);
		int c = 0;

		String var = amount.get(data);
		if (!super.initialize(var)) return false;

		int amt = 0;
		try {
			amt = Integer.parseInt(var.substring(1));
		} catch (NumberFormatException e) {
			return false;
		}

		for (ItemStack i : inventory.getContents()) {
			if (!isSimilar(i, data)) continue;
			c += i.getAmount();

			if (moreThan && c > amt) return true;
			if (lessThan && c >= amt) return false;
		}

		if (equals) return c == amt;
		if (moreThan) return c > amt;
		if (lessThan) return c < amt;
		return false;
	}

	private boolean checkEquipment(LivingEntity caster, LivingEntity target, EntityEquipment entityEquipment) {
		SpellData data = new SpellData(caster, target, 1f, new String[0]);
		int c = 0;

		String var = amount.get(data);
		if (!super.initialize(var)) return false;

		int amt = 0;
		try {
			amt = Integer.parseInt(var.substring(1));
		} catch (NumberFormatException e) {
			return false;
		}
		for (ItemStack i : InventoryUtil.getEquipmentItems(entityEquipment)) {
			if (!isSimilar(i, data)) continue;
			c += i.getAmount();

			if (moreThan && c > amt) return true;
			if (lessThan && c >= amt) return false;
		}

		if (equals) return c == amt;
		if (moreThan) return c > amt;
		if (lessThan) return c < amt;
		return false;
	}

	private boolean isSimilar(ItemStack item, SpellData data) {
		if (item == null) return false;

		MagicItem magicItem = itemData.get(data);
		if (magicItem == null) return false;

		return MagicItems.matches(magicItem.getMagicItemData(), item);
	}

}
