package com.nisovin.magicspells.util;

import com.nisovin.magicspells.util.performance.PerformanceDiagnostics;
import com.nisovin.magicspells.util.performance.PerformanceRecorder;

import java.util.Map;
import java.util.HashMap;

import com.nisovin.magicspells.MagicSpells;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.event.inventory.InventoryType;

import com.nisovin.magicspells.util.reagent.ItemReagent;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.magicitems.MagicItemData;

public class InventoryUtil {

	private static final String SERIALIZATION_KEY_SIZE = "size";
	private static final String SERIALIZATION_KEY_TYPE = "type";
	private static final String SERIALIZATION_KEY_TITLE = "title";
	private static final String SERIALIZATION_KEY_CONTENTS = "contents";

	/*
	 * type: INVENTORY_TYPE/string
	 * size: integer
	 * title: string
	 * contents:
	 *     slot number: serialized itemstack
	 *     slot number: serialized itemstack
	 */
	public static Map<Object, Object> serializeInventoryContents(Inventory inv, InventoryView view) {
		Map<Object, Object> ret = new HashMap<>();
		ItemStack[] contents = inv.getContents();
		String inventoryType = inv.getType().name();
		int size = inv.getSize();
		String title = Util.getStringFromComponent(view.title());
		
		// A map of slot to itemstack
		Map<Object, Object> serializedContents = createContentsMap(contents);
		
		ret.put(SERIALIZATION_KEY_SIZE, size);
		ret.put(SERIALIZATION_KEY_TYPE, inventoryType);
		ret.put(SERIALIZATION_KEY_TITLE, title);
		ret.put(SERIALIZATION_KEY_CONTENTS, serializedContents);

		return ret;
	}
	
	private static Map<Object, Object> createContentsMap(ItemStack[] items) {
		Map<Object, Object> serialized = new HashMap<>();
		int maxSlot = items.length - 1;
		for (int currentSlot = 0; currentSlot <= maxSlot; currentSlot++) {
			ItemStack currentItem = items[currentSlot];
			if (currentItem == null) continue;
			serialized.put(currentSlot, currentItem.serialize());
		}
		return serialized;
	}
	
	public static Inventory deserializeInventory(Map<Object, Object> serialized) {
		String strInventoryType = (String) serialized.get(SERIALIZATION_KEY_TYPE);
		int inventorySize = (Integer) serialized.get(SERIALIZATION_KEY_SIZE);
		String title = (String) serialized.get(SERIALIZATION_KEY_TITLE);
		Inventory ret;
		if (strInventoryType.equals(InventoryType.CHEST.name())) ret = Bukkit.createInventory(null, inventorySize, Util.getMiniMessage(title));
		else ret = Bukkit.createInventory(null, InventoryType.valueOf(strInventoryType), Util.getMiniMessage(title));

		// Handle the item contents
		Map<Object, Object> serializedItems = (Map<Object, Object>) serialized.get(SERIALIZATION_KEY_CONTENTS);
		ret.setContents(deserializeContentsMap(serializedItems, inventorySize));
		
		return ret;
	}
	
	private static ItemStack[] deserializeContentsMap(Map<Object, Object> contents, int size) {
		ItemStack[] ret = new ItemStack[size];
		
		// Can we exit early?
		if (contents == null) return ret;
		
		for (int i = 0; i < size; i++) {
			Map<String, Object> serializedStack = (Map<String, Object>) contents.get(i);
			if (serializedStack == null) continue;
			ret[i] = ItemStack.deserialize(serializedStack);
		}
		
		return ret;
	}
	
	public static boolean isNothing(ItemStack itemStack) {
		if (itemStack == null) return true;
		if (BlockUtils.isAir(itemStack.getType())) return true;
		return itemStack.getAmount() == 0;
	}

//	public static boolean inventoryContains(EntityEquipment entityEquipment, SpellReagents.ReagentItem item) {
//		if (entityEquipment == null) return false;
//		MagicItemData itemData = item.getMagicItemData();
//		if (itemData == null) return false;
//
//		int count = 0;
//		ItemStack[] armorContents = entityEquipment.getArmorContents();
//		ItemStack mainHand = entityEquipment.getItemInMainHand();
//		ItemStack offHand = entityEquipment.getItemInOffHand();
//		ItemStack[] equipment = new ItemStack[6];
//
//		// first 4 slots are filled with armor
//		System.arraycopy(armorContents, 0, equipment, 0, 4);
//		equipment[4] = mainHand;
//		equipment[5] = offHand;
//
//		for (ItemStack itemInside : equipment) {
//			if (itemInside == null) continue;
//
//			MagicItemData magicItemData = MagicItems.getMagicItemDataFromItemStack(itemInside);
//			if (magicItemData == null) continue;
//
//			if (itemData.matches(magicItemData)) count += itemInside.getAmount();
//			if (count >= item.getAmount()) return true;
//		}
//		return false;
//	}

//	public static boolean inventoryContains(Inventory inventory, SpellReagents.ReagentItem item) {
//		if (inventory == null) return false;
//		MagicItemData itemData = item.getMagicItemData();
//		if (itemData == null) return false;
//		int count = 0;
//		ItemStack[] items = inventory.getContents();
//		for (ItemStack itemStack : items) {
//			if (itemStack == null) continue;
//
//			MagicItemData magicItemData = MagicItems.getMagicItemDataFromItemStack(itemStack);
//			if (magicItemData == null) continue;
//
//			if (itemData.matches(magicItemData)) count += itemStack.getAmount();
//			if (count >= item.getAmount()) return true;
//		}
//		return false;
//	}

	public static boolean inventoryContains(Inventory inventory, Map.Entry<MagicItemData, Integer> item) {
		if (inventory == null || item == null) return false;
		MagicItemData itemData = item.getKey();
		if (itemData == null) return false;
		return inventoryCount(inventory, itemData) >= item.getValue();
	}

	/**
	 * Counts how many items in {@code inventory} match {@code itemData}.
	 */
	public static int inventoryCount(Inventory inventory, MagicItemData itemData) {
		try (var scope = PerformanceDiagnostics.RECORDER
				.enter("inventory_scan", null, "")) {
			return inventoryCountMeasured(inventory, itemData, scope);
		}
	}

	private static int inventoryCountMeasured(Inventory inventory, MagicItemData itemData,
			PerformanceRecorder.Scope scope) {
		if (inventory == null || itemData == null) return 0;
		int count = 0;
		ItemStack[] items = inventory.getContents();
		scope.add(PerformanceRecorder.Counter.INVENTORY_SLOTS, items.length);
		for (ItemStack itemStack : items) {
			if (itemStack == null) continue;

			if (MagicItems.matches(itemData, itemStack)) count += itemStack.getAmount();
		}
		return count;
	}

	public static boolean inventoryContains(EntityEquipment entityEquipment, Map.Entry<MagicItemData, Integer> item) {
		if (entityEquipment == null) return false;
		MagicItemData itemData = item.getKey();
		if (itemData == null) return false;

		int count = 0;
		ItemStack[] armorContents = entityEquipment.getArmorContents();
		ItemStack mainHand = entityEquipment.getItemInMainHand();
		ItemStack offHand = entityEquipment.getItemInOffHand();
		ItemStack[] equipment = new ItemStack[6];

		// first 4 slots are filled with armor
		System.arraycopy(armorContents, 0, equipment, 0, 4);
		equipment[4] = mainHand;
		equipment[5] = offHand;

		for (ItemStack itemInside : equipment) {
			if (itemInside == null) continue;

			if (MagicItems.matches(itemData, itemInside)) count += itemInside.getAmount();
			if (count >= item.getValue()) return true;
		}
		return false;
	}

	public static ItemStack[] getEquipmentItems(EntityEquipment entityEquipment) {
		ItemStack[] armorContents = entityEquipment.getArmorContents();
		ItemStack mainHand = entityEquipment.getItemInMainHand();
		ItemStack offHand = entityEquipment.getItemInOffHand();
		ItemStack[] equipment = new ItemStack[6];

		// first 4 slots are filled with armor
		System.arraycopy(armorContents, 0, equipment, 0, 4);
		equipment[4] = mainHand;
		equipment[5] = offHand;

		return equipment;
	}
	
}
