package com.nisovin.magicspells.spells.instant;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.player.PlayerDropItemEvent;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.events.ConjureItemEvent;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.compat.BagOfHoldingCompat;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.InventoryUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.ConfigDataUtil;
import com.nisovin.magicspells.util.managers.AttributeManager;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.spells.command.TomeSpell;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItemBehaviorKeys;
import com.nisovin.magicspells.util.magicitems.MagicItemExpirationScheduler;
import com.nisovin.magicspells.util.magicitems.MagicItemBehaviors;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.MagicItemIgnoredAttributes;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spells.command.ScrollSpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class ConjureSpell extends InstantSpell implements TargetedEntitySpell, TargetedLocationSpell {

	private ConfigData<Integer> delay;
	private ConfigData<Integer> pickupDelay;
	private ConfigData<Integer> requiredSlot;
	private ConfigData<Integer> preferredSlot;

	private ConfigData<Double> expiration;

	private ConfigData<Float> randomVelocity;

	private boolean soulbound;
	private ConfigData<Boolean> offhand;
	private boolean autoEquip;
	private boolean stackExisting;
	private boolean itemHasGravity;
	private ConfigData<Boolean> addToInventory;
	private ConfigData<Boolean> addToBagOfHolding;
	private ConfigData<Boolean> addToEnderChest;
	private boolean ignoreMaxStackSize;
	private ConfigData<Boolean> powerAffectsChance;
	private ConfigData<Boolean> dropIfInventoryFull;
	private ConfigData<Boolean> powerAffectsQuantity;
	private boolean forceUpdateInventory;
	private boolean calculateDropsIndividually;
	private boolean saveConjurerName;
	private boolean safeEnchants;

	private ConfigData<Map<Enchantment, Integer>> enchantments;
	private ConfigData<Set<AttributeManager.AttributeInfo>> attributes;

	private ConfigData<List<String>> itemListData;
	private ConfigData<Boolean> omitSelectedSlot;

	public ConjureSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		delay = getConfigDataInt("delay", -1);
		pickupDelay = getConfigDataInt("pickup-delay", 0);
		requiredSlot = getConfigDataInt("required-slot", -1);
		preferredSlot = getConfigDataInt("preferred-slot", -1);

		expiration = getConfigDataDouble("expiration", 0);

		randomVelocity = getConfigDataFloat("random-velocity", 0F);

		soulbound = getConfigBoolean("soulbound", false);
		offhand = getConfigDataBoolean("offhand", false);
		autoEquip = getConfigBoolean("auto-equip", false);
		stackExisting = getConfigBoolean("stack-existing", true);
		itemHasGravity = getConfigBoolean("gravity", true);
		addToInventory = getConfigDataBoolean("add-to-inventory", false);
		addToBagOfHolding = getConfigDataBoolean("add-to-bag-of-holding", true);
		addToEnderChest = getConfigDataBoolean("add-to-ender-chest", false);
		ignoreMaxStackSize = getConfigBoolean("ignore-max-stack-size", false);
		powerAffectsChance = getConfigDataBoolean("power-affects-chance", true);
		dropIfInventoryFull = getConfigDataBoolean("drop-if-inventory-full", true);
		powerAffectsQuantity = getConfigDataBoolean("power-affects-quantity", false);
		forceUpdateInventory = getConfigBoolean("force-update-inventory", true);
		calculateDropsIndividually = getConfigBoolean("calculate-drops-individually", true);
		saveConjurerName = getConfigBoolean("save-conjurer-name", false);
		safeEnchants = getConfigBoolean("safe-enchants", true);

		itemListData = getConfigDataStringList("items", null);
		omitSelectedSlot = getConfigDataBoolean("omit-selected-slot", false);

		List<String> enchantmentList = getConfigStringList("enchantments", null);
		if (enchantmentList != null && !enchantmentList.isEmpty()) {
			enchantments = ConfigDataUtil.getEnchantmentsConfigData(enchantmentList);
		}

		List<String> attributeList = getConfigStringList("attributes", null);
		if (attributeList != null && !attributeList.isEmpty()) {
			attributes = MagicSpells.getAttributeManager().getAttributesConfigData(attributeList,
					internalName + ".attributes");
		}
	}

	private Object[] processItemList(List<String> itemList) {

		ItemStack[] itemTypes = null;
		MagicItemData[] itemDataTypes = null;
		int[] itemMinQuantities = null;
		int[] itemMaxQuantities = null;
		double[] itemChances = null;

		if (itemList != null && !itemList.isEmpty()) {
			itemTypes = new ItemStack[itemList.size()];
			itemDataTypes = new MagicItemData[itemList.size()];
			itemMinQuantities = new int[itemList.size()];
			itemMaxQuantities = new int[itemList.size()];
			itemChances = new double[itemList.size()];

			for (int i = 0; i < itemList.size(); i++) {
				try {
					String str = itemList.get(i);

					int brackets = 0;
					int closedBrackets = 0;
					for (int j = 0; j < str.length(); j++) {
						char ch = str.charAt(j);
						if (ch == '{')
							brackets++;
						if (ch == '}')
							closedBrackets++;
					}

					// checks if all brackets are properly closed
					if (brackets != closedBrackets) {
						MagicSpells
								.error("ConjureSpell '" + internalName + "' has an invalid item defined (e1): " + str);
						continue;
					}

					brackets = 0;
					closedBrackets = 0;

					String[] data = str.split(" ");
					String[] conjureData = null;

					StringBuilder itemData = new StringBuilder();

					for (int j = 0; j < data.length; j++) {
						for (char ch : data[j].toCharArray()) {
							if (ch == '{')
								brackets++;
							if (ch == '}')
								closedBrackets++;
						}

						itemData.append(data[j]).append(" ");
						// magicItemData is ready, add the conjureData
						if (brackets == closedBrackets) {
							int dataLeft = data.length - j - 1;
							conjureData = new String[dataLeft];

							// fill the conjureData array with stuff like amount and chance
							for (int d = 0; d < dataLeft; d++) {
								conjureData[d] = data[j + d + 1];
							}
							break;
						}
					}

					String strItemData = itemData.toString().trim();

					if (strItemData.startsWith("TOME:")) {
						String[] tomeData = strItemData.split(":");
						TomeSpell tomeSpell = (TomeSpell) MagicSpells.getSpellByInternalName(tomeData[1]);
						Spell spell = MagicSpells.getSpellByInternalName(tomeData[2]);
						int uses = tomeData.length > 3 ? Integer.parseInt(tomeData[3].trim()) : -1;
						itemTypes[i] = tomeSpell.createTome(spell, uses, null);
					} else if (strItemData.startsWith("SCROLL:")) {
						String[] scrollData = strItemData.split(":");
						ScrollSpell scrollSpell = (ScrollSpell) MagicSpells.getSpellByInternalName(scrollData[1]);
						Spell spell = MagicSpells.getSpellByInternalName(scrollData[2]);
						int uses = scrollData.length > 3 ? Integer.parseInt(scrollData[3].trim()) : -1;
						itemTypes[i] = scrollSpell.createScroll(spell, uses, null);
					} else {
						MagicItem magicItem = MagicItems.getMagicItemFromString(strItemData);
						if (magicItem == null)
							continue;
						itemTypes[i] = magicItem.getItemStack();
						itemDataTypes[i] = magicItem.getMagicItemData();
					}

					int minAmount = 1;
					int maxAmount = 1;

					double chance = 100;

					// add default values if there arent any specified
					if (conjureData == null) {
						itemMinQuantities[i] = minAmount;
						itemMaxQuantities[i] = maxAmount;
						itemChances[i] = chance;
						continue;
					}

					// parse minAmount, maxAmount
					if (conjureData.length >= 1) {
						String[] amount = conjureData[0].split("-");
						if (amount.length == 1) {
							minAmount = Integer.parseInt(amount[0].trim());
							maxAmount = minAmount;
						} else if (amount.length >= 2) {
							minAmount = Integer.parseInt(amount[0].trim());
							maxAmount = Integer.parseInt(amount[1].trim()) + 1;
						}
					}

					// parse chance
					if (conjureData.length >= 2) {
						chance = Double.parseDouble(conjureData[1].replace("%", "").trim());
					}

					itemMinQuantities[i] = minAmount;
					itemMaxQuantities[i] = maxAmount;
					itemChances[i] = chance;

				} catch (Exception e) {
					MagicSpells.error(
							"ConjureSpell '" + internalName + "' has specified invalid item (e2): " + itemList.get(i));
					itemTypes[i] = null;
				}
			}
		}
		itemList = null;
		return new Object[] { itemTypes, itemMinQuantities, itemMaxQuantities, itemChances, itemDataTypes };
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {

		if (itemListData == null)
			return PostCastAction.ALREADY_HANDLED;
		if (state == SpellCastState.NORMAL && caster instanceof Player) {
			int delayVal = delay.get(caster, power, args);
			if (delayVal >= 0)
				MagicSpells.scheduleDelayedTask(() -> conjureItems((Player) caster, power, args), delayVal);
			else if (!conjureItems((Player) caster, power, args))
				return PostCastAction.ALREADY_HANDLED;
		}
		return PostCastAction.HANDLE_NORMALLY;

	}

	private boolean conjureItems(Player player, float power, String[] args) {
		boolean succes = true;

		SpellData spellData = new SpellData(player, power, args);
		List<String> itemList = this.itemListData.get(spellData);

		Object[] itemResults = processItemList(itemList);

		ItemStack[] itemTypes = (ItemStack[]) itemResults[0];
		int[] itemMinQuantities = (int[]) itemResults[1];
		int[] itemMaxQuantities = (int[]) itemResults[2];
		double[] itemChances = (double[]) itemResults[3];
		MagicItemData[] itemDataTypes = (MagicItemData[]) itemResults[4];

		List<ItemStack> items = new ArrayList<>();
		if (calculateDropsIndividually)
			individual(items, spellData, itemTypes, itemDataTypes, itemChances, itemMinQuantities, itemMaxQuantities);
		else
			together(items, spellData, itemTypes, itemDataTypes, itemChances, itemMinQuantities, itemMaxQuantities);

		Location loc = player.getEyeLocation().add(player.getLocation().getDirection());
		boolean updateInv = false;
		boolean depositedToBag = false;
		boolean addToBag = Boolean.TRUE.equals(addToBagOfHolding.get(spellData));
		boolean addToEnder = Boolean.TRUE.equals(addToEnderChest.get(spellData));
		boolean addToInv = Boolean.TRUE.equals(addToInventory.get(spellData));
		boolean dropIfFull = Boolean.TRUE.equals(dropIfInventoryFull.get(spellData));
		boolean useOffhand = Boolean.TRUE.equals(offhand.get(spellData));
		int preferredSlotVal = preferredSlot.get(spellData);
		int requiredSlotVal = requiredSlot.get(spellData);
		int pickupDelayVal = Math.max(pickupDelay.get(spellData), 0);
		for (ItemStack itemOrg : items) {
			if (itemOrg == null)
				continue;
			ItemStack item = itemOrg.clone();

			if (saveConjurerName || item.getMaxStackSize() == 1) {
				ItemMeta meta = item.getItemMeta();
				meta.getPersistentDataContainer().set(MagicItemBehaviorKeys.creatorName(),
						PersistentDataType.STRING, player.getName());
				item.setItemMeta(meta);
			}

			boolean added = false;
			PlayerInventory inv = player.getInventory();
			if (autoEquip && item.getAmount() == 1) {
				if (item.getType().name().endsWith("HELMET") && InventoryUtil.isNothing(inv.getHelmet())) {
					inv.setHelmet(item);
					added = true;
				} else if (item.getType().name().endsWith("CHESTPLATE")
						&& InventoryUtil.isNothing(inv.getChestplate())) {
					inv.setChestplate(item);
					added = true;
				} else if (item.getType().name().endsWith("LEGGINGS") && InventoryUtil.isNothing(inv.getLeggings())) {
					inv.setLeggings(item);
					added = true;
				} else if (item.getType().name().endsWith("BOOTS") && InventoryUtil.isNothing(inv.getBoots())) {
					inv.setBoots(item);
					added = true;
				}
			}

			// Prefer bag of holding (soft dep) before ender chest / inventory / drop.
			// Commit bag only after the remainder is fully placed so we never leave a partial bag deposit.
			int bagTake = 0;
			String bagItemId = null;
			ItemStack placeItem = item;
			if (!added && addToBag) {
				bagItemId = BagOfHoldingCompat.resolveItemId(item);
				if (bagItemId != null && BagOfHoldingCompat.isAutopickupEnabled(player, bagItemId)) {
					bagTake = Math.min(item.getAmount(), BagOfHoldingCompat.getRemainingCapacity(player, bagItemId));
					if (bagTake > 0) {
						int remainder = item.getAmount() - bagTake;
						if (remainder <= 0) {
							placeItem = null;
						} else {
							placeItem = item.clone();
							placeItem.setAmount(remainder);
						}
					}
				}
			}

			if (!added && placeItem != null) {
				if (addToEnder)
					added = Util.addToInventory(player, player.getEnderChest(), placeItem, stackExisting,
							ignoreMaxStackSize);
				if (!added && addToInv) {

					ItemStack preferredItem = null;
					if (preferredSlotVal >= 0) {
						preferredItem = inv.getItem(preferredSlotVal);
					}

					if (useOffhand)
						player.getEquipment().setItemInOffHand(placeItem);
					else if (requiredSlotVal >= 0) {
						ItemStack old = inv.getItem(requiredSlotVal);
						if (old != null && Util.isSimilarNoFlags(placeItem, old))
							placeItem.setAmount(placeItem.getAmount() + old.getAmount());
						inv.setItem(requiredSlotVal, placeItem);
						added = true;
						updateInv = true;
					} else if (preferredSlotVal >= 0 && InventoryUtil.isNothing(preferredItem)) {
						inv.setItem(preferredSlotVal, placeItem);
						added = true;
						updateInv = true;
					} else if (preferredSlotVal >= 0 && Util.isSimilarNoFlags(placeItem, preferredItem)
							&& preferredItem.getAmount() + placeItem.getAmount() < placeItem.getType().getMaxStackSize()) {
						placeItem.setAmount(placeItem.getAmount() + preferredItem.getAmount());
						inv.setItem(preferredSlotVal, placeItem);
						added = true;
						updateInv = true;
					} else {
						Set<Integer> omittedSlots = null;
						if (Boolean.TRUE.equals(omitSelectedSlot.get(spellData))) {
							omittedSlots = Collections.singleton(player.getInventory().getHeldItemSlot());
						}
						added = Util.addToInventory(player, inv, placeItem, stackExisting, ignoreMaxStackSize, omittedSlots);
						if (added)
							updateInv = true;
					}
				}
				if (!added && (dropIfFull || !addToInv)) {
					int amt = placeItem.getAmount();
					while (amt > 0) {
						ItemStack drop = placeItem.clone();
						drop.setAmount(Math.min(drop.getMaxStackSize(), amt));

						Item i = player.getWorld().dropItem(loc, drop);

						PlayerDropItemEvent event = new PlayerDropItemEvent(player, i);
						EventUtil.call(event);

						if (!event.isCancelled()) {
							i.setItemStack(drop);
							i.setPickupDelay(pickupDelayVal);
							i.setGravity(itemHasGravity);
							UUID uuid = player.getUniqueId();
							i.setThrower(uuid);
							playSpellEffects(EffectPosition.SPECIAL, i);
						}

						amt -= drop.getMaxStackSize();
					}
					added = true;
				}
			}

			if (bagTake > 0 && bagItemId != null) {
				if (placeItem == null || added) {
					int deposited = BagOfHoldingCompat.deposit(player, bagItemId, bagTake);
					added = deposited >= bagTake;
					if (deposited > 0) depositedToBag = true;
				}
			} else if (added) {
				updateInv = true;
			}

			if (!added)
				succes = false;
			else
				EventUtil.call(new ConjureItemEvent(player, item));
		}

		if (succes) {
			if (updateInv && forceUpdateInventory)
				player.updateInventory();
			if (depositedToBag) BagOfHoldingCompat.playPickupFeedback(player);
			playSpellEffects(EffectPosition.CASTER, player);
		}
		return succes;
	}

	private void individual(List<ItemStack> items, SpellData spellData, ItemStack[] itemTypes,
			MagicItemData[] itemDataTypes, double[] itemChances, int[] itemMinQuantities, int[] itemMaxQuantities) {
		float power = spellData.power();
		boolean affectsChance = Boolean.TRUE.equals(powerAffectsChance.get(spellData));
		for (int i = 0; i < itemTypes.length; i++) {
			double r = random.nextDouble() * 100;
			if (affectsChance)
				r = r / power;
			if (itemTypes[i] != null && r < itemChances[i])
				addItem(i, items, spellData, itemTypes, itemDataTypes, itemMinQuantities, itemMaxQuantities);
		}
	}

	private void together(List<ItemStack> items, SpellData spellData, ItemStack[] itemTypes,
			MagicItemData[] itemDataTypes, double[] itemChances, int[] itemMinQuantities, int[] itemMaxQuantities) {
		double r = random.nextDouble() * Arrays.stream(itemChances).sum();
		double m = 0;
		for (int i = 0; i < itemTypes.length; i++) {
			if (itemTypes[i] != null && r < itemChances[i] + m) {
				addItem(i, items, spellData, itemTypes, itemDataTypes, itemMinQuantities, itemMaxQuantities);
				return;
			} else
				m += itemChances[i];
		}
	}

	private void addItem(int i, List<ItemStack> items, SpellData spellData, ItemStack[] itemTypes,
			MagicItemData[] itemDataTypes, int[] itemMinQuantities, int[] itemMaxQuantities) {
		float power = spellData.power();
		int quant = itemMinQuantities[i];
		if (itemMaxQuantities[i] > itemMinQuantities[i])
			quant = random.nextInt(itemMaxQuantities[i] - itemMinQuantities[i]) + itemMinQuantities[i];
		if (Boolean.TRUE.equals(powerAffectsQuantity.get(spellData)))
			quant = Math.round(quant * power);
		if (quant > 0) {
			ItemStack item = itemTypes[i].clone();
			item.setAmount(quant);
			applyEnchantments(item, spellData);
			applyAttributes(item, spellData);
			LivingEntity owner = spellData.caster();
			if (itemDataTypes != null && i < itemDataTypes.length)
				MagicItemBehaviors.applyFromData(item, itemDataTypes[i], owner);
			MagicItemBehaviors.applyConjureOverlay(item, soulbound, expiration.get(spellData), owner);
			applyStackIgnoredAttributes(item, spellData);
			if (owner instanceof Player player)
				MagicItemExpirationScheduler.scheduleFromItem(player, item);
			items.add(item);
		}
	}

	private void applyEnchantments(ItemStack item, SpellData spellData) {
		if (enchantments == null)
			return;
		Map<Enchantment, Integer> resolved = enchantments.get(spellData);
		if (resolved == null || resolved.isEmpty())
			return;
		for (Map.Entry<Enchantment, Integer> entry : resolved.entrySet()) {
			Enchantment enchant = entry.getKey();
			int level = entry.getValue();
			if (!enchant.canEnchantItem(item))
				continue;
			if (safeEnchants && level > enchant.getMaxLevel())
				level = enchant.getMaxLevel();
			if (level <= 0)
				item.removeEnchantment(enchant);
			else if (safeEnchants)
				item.addEnchantment(enchant, level);
			else
				item.addUnsafeEnchantment(enchant, level);
		}
	}

	private void applyStackIgnoredAttributes(ItemStack item, SpellData spellData) {
		if (!MagicItemIgnoredAttributes.isTaggedMagicItem(item))
			return;

		if (enchantments != null) {
			Map<Enchantment, Integer> resolved = enchantments.get(spellData);
			if (resolved != null && !resolved.isEmpty())
				MagicItemIgnoredAttributes.add(item, MagicItemData.MagicItemAttribute.ENCHANTS);
		}
		if (attributes != null) {
			Set<AttributeManager.AttributeInfo> resolved = attributes.get(spellData);
			if (resolved != null && !resolved.isEmpty())
				MagicItemIgnoredAttributes.add(item, MagicItemData.MagicItemAttribute.ATTRIBUTES);
		}
	}

	private void applyAttributes(ItemStack item, SpellData spellData) {
		if (attributes == null)
			return;
		Set<AttributeManager.AttributeInfo> resolved = attributes.get(spellData);
		if (resolved == null || resolved.isEmpty())
			return;
		MagicSpells.getAttributeManager().addItemAttributes(item, resolved);
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		return conjureItemsAtLocation(target, power, caster, null);
	}

	@Override
	public boolean castAtLocation(Location target, float power) {
		return conjureItemsAtLocation(target, power, null, null);
	}

	private boolean conjureItemsAtLocation(Location location, float power, @NotNull LivingEntity player,
			@NotNull LivingEntity target) {

		SpellData spellData = new SpellData(player, location, power);
		List<String> itemList = this.itemListData.get(spellData);

		Object[] itemResults = processItemList(itemList);
		ItemStack[] itemTypes = (ItemStack[]) itemResults[0];
		int[] itemMinQuantities = (int[]) itemResults[1];
		int[] itemMaxQuantities = (int[]) itemResults[2];
		double[] itemChances = (double[]) itemResults[3];
		MagicItemData[] itemDataTypes = (MagicItemData[]) itemResults[4];

		List<ItemStack> items = new ArrayList<>();
		if (calculateDropsIndividually)
			individual(items, spellData, itemTypes, itemDataTypes, itemChances, itemMinQuantities, itemMaxQuantities);
		else
			together(items, spellData, itemTypes, itemDataTypes, itemChances, itemMinQuantities, itemMaxQuantities);

		Location loc = location.clone();
		if (!BlockUtils.isAir(loc.getBlock().getType()))
			loc.add(0, 1, 0);
		if (!BlockUtils.isAir(loc.getBlock().getType()))
			loc.add(0, 1, 0);
		int pickupDelayVal = Math.max(pickupDelay.get(spellData), 0);
		float randomVelocityVal = randomVelocity.get(spellData);
		for (ItemStack item : items) {

			int amt = item.getAmount();
			while (amt > 0) {
				ItemStack drop = item.clone();
				drop.setAmount(Math.min(drop.getMaxStackSize(), amt));

				Item i = player.getWorld().dropItem(loc, drop);

				PlayerDropItemEvent event = null;

				if (player != null && player instanceof Player pl) {
					event = new PlayerDropItemEvent(pl, i);
					EventUtil.call(event);
				}

				if (event == null || !event.isCancelled()) {
					i.setItemStack(drop);
					i.setPickupDelay(pickupDelayVal);
					if (randomVelocityVal > 0) {
						Vector v = new Vector(random.nextDouble() - 0.5, random.nextDouble() / 2,
								random.nextDouble() - 0.5);
						v.normalize().multiply(randomVelocityVal);
						i.setVelocity(v);
					}
					i.setGravity(itemHasGravity);
					if (player != null)
						i.setThrower(player.getUniqueId());
					playSpellEffects(EffectPosition.SPECIAL, i);
					EventUtil.call(new ConjureItemEvent(player, item));
				}

				amt -= drop.getMaxStackSize();
			}
		}
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		if (!validTargetList.canTarget(caster, target))
			return false;

		if (target instanceof Player player)
			conjureItems(player, power, null);
		else
			return conjureItemsAtLocation(target.getLocation(), power, caster, target);

		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		if (!validTargetList.canTarget(target))
			return false;

		if (target instanceof Player player)
			conjureItems(player, power, null);
		else
			return conjureItemsAtLocation(target.getLocation(), power, null, target);

		return true;
	}

	/** Only meaningful when {@code delay} is a constant expression. */
	public int getDelay() {
		return delay.get(null, null, null, 1f, null);
	}

	public void setDelay(int delay) {
		this.delay = (caster, target, location, power, args) -> delay;
	}

	/** Only meaningful when {@code pickup-delay} is a constant expression. */
	public int getPickupDelay() {
		return pickupDelay.get(null, null, null, 1f, null);
	}

	public void setPickupDelay(int pickupDelay) {
		this.pickupDelay = (caster, target, location, power, args) -> Math.max(pickupDelay, 0);
	}

	/** Only meaningful when {@code required-slot} is a constant expression. */
	public int getRequiredSlot() {
		return requiredSlot.get(null, null, null, 1f, null);
	}

	public void setRequiredSlot(int requiredSlot) {
		this.requiredSlot = (caster, target, location, power, args) -> requiredSlot;
	}

	/** Only meaningful when {@code preferred-slot} is a constant expression. */
	public int getPreferredSlot() {
		return preferredSlot.get(null, null, null, 1f, null);
	}

	public void setPreferredSlot(int preferredSlot) {
		this.preferredSlot = (caster, target, location, power, args) -> preferredSlot;
	}

	/** Only meaningful when {@code expiration} is a constant expression. */
	public double getExpiration() {
		return expiration.get(null, null, null, 1f, null);
	}

	public void setExpiration(double expiration) {
		this.expiration = (caster, target, location, power, args) -> expiration;
	}

	/** Only meaningful when {@code random-velocity} is a constant expression. */
	public float getRandomVelocity() {
		return randomVelocity.get(null, null, null, 1f, null);
	}

	public void setRandomVelocity(float randomVelocity) {
		this.randomVelocity = (caster, target, location, power, args) -> randomVelocity;
	}

	/** Only meaningful when {@code offhand} is a constant expression. */
	public boolean shouldConjureInOffhand() {
		return Boolean.TRUE.equals(offhand.get(null, null, null, 1f, null));
	}

	public void setConjureInOffhand(boolean offhand) {
		this.offhand = (caster, target, location, power, args) -> offhand;
	}

	public boolean shouldAutoEquip() {
		return autoEquip;
	}

	public void setAutoEquip(boolean autoEquip) {
		this.autoEquip = autoEquip;
	}

	public boolean shouldStackExisting() {
		return stackExisting;
	}

	public void setStackExisting(boolean stackExisting) {
		this.stackExisting = stackExisting;
	}

	public boolean shouldItemHaveGravity() {
		return itemHasGravity;
	}

	public void setItemHasGravity(boolean itemHasGravity) {
		this.itemHasGravity = itemHasGravity;
	}

	/** Only meaningful when {@code add-to-inventory} is a constant expression. */
	public boolean shouldAddToInventory() {
		return Boolean.TRUE.equals(addToInventory.get(null, null, null, 1f, null));
	}

	public void setAddToInventory(boolean addToInventory) {
		this.addToInventory = (caster, target, location, power, args) -> addToInventory;
	}

	/** Only meaningful when {@code add-to-bag-of-holding} is a constant expression. */
	public boolean shouldAddToBagOfHolding() {
		return Boolean.TRUE.equals(addToBagOfHolding.get(null, null, null, 1f, null));
	}

	public void setAddToBagOfHolding(boolean addToBagOfHolding) {
		this.addToBagOfHolding = (caster, target, location, power, args) -> addToBagOfHolding;
	}

	/** Only meaningful when {@code add-to-ender-chest} is a constant expression. */
	public boolean shouldAddToEnderChest() {
		return Boolean.TRUE.equals(addToEnderChest.get(null, null, null, 1f, null));
	}

	public void setAddToEnderChest(boolean addToEnderChest) {
		this.addToEnderChest = (caster, target, location, power, args) -> addToEnderChest;
	}

	public boolean shouldIgnoreMaxStackSize() {
		return ignoreMaxStackSize;
	}

	public void setIgnoreMaxStackSize(boolean ignoreMaxStackSize) {
		this.ignoreMaxStackSize = ignoreMaxStackSize;
	}

	/** Only meaningful when {@code power-affects-chance} is a constant expression. */
	public boolean shouldPowerAffectChance() {
		return Boolean.TRUE.equals(powerAffectsChance.get(null, null, null, 1f, null));
	}

	public void setPowerAffectsChance(boolean powerAffectsChance) {
		this.powerAffectsChance = (caster, target, location, power, args) -> powerAffectsChance;
	}

	/** Only meaningful when {@code drop-if-inventory-full} is a constant expression. */
	public boolean shouldDropIfInventoryFull() {
		return Boolean.TRUE.equals(dropIfInventoryFull.get(null, null, null, 1f, null));
	}

	public void setDropIfInventoryFull(boolean dropIfInventoryFull) {
		this.dropIfInventoryFull = (caster, target, location, power, args) -> dropIfInventoryFull;
	}

	/** Only meaningful when {@code power-affects-quantity} is a constant expression. */
	public boolean shouldPowerAffectQuantity() {
		return Boolean.TRUE.equals(powerAffectsQuantity.get(null, null, null, 1f, null));
	}

	public void setPowerAffectsQuantity(boolean powerAffectsQuantity) {
		this.powerAffectsQuantity = (caster, target, location, power, args) -> powerAffectsQuantity;
	}

	public boolean shouldForceUpdateInventory() {
		return forceUpdateInventory;
	}

	public void setForceUpdateInventory(boolean forceUpdateInventory) {
		this.forceUpdateInventory = forceUpdateInventory;
	}

	public boolean shouldCalculateDropsIndividually() {
		return calculateDropsIndividually;
	}

	public void setCalculateDropsIndividually(boolean calculateDropsIndividually) {
		this.calculateDropsIndividually = calculateDropsIndividually;
	}

}
