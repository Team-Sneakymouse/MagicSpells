package com.nisovin.magicspells.util.reagent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import com.sneakyitems.util.magicitems.MagicItemData;
import com.sneakyitems.util.magicitems.MagicItems;

public class ItemEntityReagent extends Reagent {

	private final MagicItemData itemData;
	private int amount;
	private final double radius;
	private final Material blockMaterial;

	private final int blockDistance;

	public ItemEntityReagent(MagicItemData itemData, int amount, double radius, Material blockMaterial, int blockDistance) {
		this.itemData = itemData;
		this.amount = amount;
		this.radius = radius;
		this.blockMaterial = blockMaterial;
		this.blockDistance = blockDistance;
	}

	@Override
	public boolean has(LivingEntity livingEntity) {
		if (livingEntity == null || amount <= 0 || itemData == null) return false;

		int total = 0;

		for (Entity entity : livingEntity.getNearbyEntities(radius, radius, radius)) {
			if (!(entity instanceof Item itemEntity)) continue;
			if (!matches(itemEntity)) continue;

			ItemStack stack = itemEntity.getItemStack();
			if (stack == null) continue;

			total += stack.getAmount();
			if (total >= amount) return true;
		}

		return false;
	}

	private boolean matches(Item itemEntity) {
		if (!matchesBlock(itemEntity)) return false;

		ItemStack stack = itemEntity.getItemStack();
		if (stack == null) return false;

		return MagicItems.matches(itemData, stack);
	}

	private boolean matchesBlock(Item itemEntity) {
		if (blockMaterial == null) return true;

		var location = itemEntity.getLocation();
		var world = location.getWorld();
		if (world == null) return false;

		int x = location.getBlockX();
		int y = location.getBlockY();
		int z = location.getBlockZ();

		if (blockDistance >= 0) {
			for (int dy = 0; dy <= blockDistance; dy++) {
				if (world.getBlockAt(x, y - dy, z).getType() == blockMaterial) {
					return true;
				}
			}
			return false;
		} else {
			int upDistance = -blockDistance;
			for (int dy = 0; dy <= upDistance; dy++) {
				if (world.getBlockAt(x, y + dy, z).getType() == blockMaterial) {
					return true;
				}
			}
			return false;
		}
	}

	@Override
	public void remove(LivingEntity livingEntity) {
		if (livingEntity == null || amount <= 0 || itemData == null) return;

		int remaining = amount;
		List<Item> matchingItems = new ArrayList<>();

		for (Entity entity : livingEntity.getNearbyEntities(radius, radius, radius)) {
			if (!(entity instanceof Item itemEntity)) continue;
			if (!matches(itemEntity)) continue;
			matchingItems.add(itemEntity);
		}

		// Prioritize items dropped by the livingEntity, then by stack size ascending
		matchingItems.sort(
			Comparator
				.comparing((Item i) -> isDroppedBy(i, livingEntity) ? 0 : 1)
				.thenComparingInt(i -> i.getItemStack().getAmount())
		);

		for (Item itemEntity : matchingItems) {
			if (remaining <= 0) break;

			ItemStack stack = itemEntity.getItemStack();
			if (stack == null) continue;

			int stackAmount = stack.getAmount();
			if (stackAmount <= remaining) {
				remaining -= stackAmount;
				itemEntity.remove();
			} else {
				stack.setAmount(stackAmount - remaining);
				itemEntity.setItemStack(stack);
				remaining = 0;
			}
		}
	}

	private boolean isDroppedBy(Item itemEntity, LivingEntity livingEntity) {
		var uuid = livingEntity.getUniqueId();
		var owner = itemEntity.getOwner();
		var thrower = itemEntity.getThrower();

		return (owner != null && owner.equals(uuid)) || (thrower != null && thrower.equals(uuid));
	}

	@Override
	public void multiply(float multiplier) {
		amount = Math.round(amount * multiplier);
		if (amount < 0) amount = 0;
	}

	@Override
	public ItemEntityReagent clone() {
		return new ItemEntityReagent(itemData.clone(), amount, radius, blockMaterial, blockDistance);
	}

	@Override
	public Number get() {
		return amount;
	}

	@Override
	public String toString() {
		return "ItemEntityReagent{" +
			"itemData=" + itemData +
			", amount=" + amount +
			", radius=" + radius +
			", blockMaterial=" + (blockMaterial != null ? blockMaterial.name() : "null") +
			", blockDistance=" + blockDistance +
			'}';
	}
}


