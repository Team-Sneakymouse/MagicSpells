package com.nisovin.magicspells.spells.buff;

import java.util.*;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.inventory.ClickType;

import net.kyori.adventure.text.Component;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.ColorUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;

public class ArmorSpell extends BuffSpell {

	private final Map<UUID, ArmorSet> entities;

	private boolean permanent;
	private boolean replace;
	private boolean revert;

	private ConfigData<String> helmetData;
	private ConfigData<String> chestplateData;
	private ConfigData<String> leggingsData;
	private ConfigData<String> bootsData;
	private ConfigData<String> offhandData;

	private String strHasArmor;

	public ArmorSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		permanent = getConfigBoolean("permanent", false);
		replace = getConfigBoolean("replace", false);
		revert = getConfigBoolean("revert", false);

		helmetData = getConfigDataString("helmet", "");
		chestplateData = getConfigDataString("chestplate", "");
		leggingsData = getConfigDataString("leggings", "");
		bootsData = getConfigDataString("boots", "");
		offhandData = getConfigDataString("offhand", "");

		strHasArmor = getConfigString("str-has-armor", "You cannot cast this spell if you are wearing armor.");

		entities = new HashMap<>();
	}

	@Override
	public void initialize() {
		super.initialize();
		if (!permanent) registerEvents(new ArmorListener());
	}

	public static Component getInvisibleLore(String s) {
		String lore = "";
		for (char c : s.toCharArray()) lore += ColorUtil.COLOR_CHAR + "" + c;
		return Util.getMiniMessage(lore);
	}

	private ItemStack getItem(String s) {
		if (s.isEmpty()) return null;

		MagicItem magicItem = MagicItems.getMagicItemFromString(s);
		if (magicItem == null) return null;

		ItemStack item = magicItem.getItemStack();
		if (item == null) {
			if (DebugHandler.isNullCheckEnabled()) {
				NullPointerException e = new NullPointerException("ItemStack is null");
				e.fillInStackTrace();
				DebugHandler.nullCheck(e);
			}
			return null;
		}

		item.setAmount(1);

		if (!permanent) {
			ItemMeta meta = item.getItemMeta();
			meta.getPersistentDataContainer().set(new NamespacedKey(MagicSpells.getInstance(), "MSArmorItem"), PersistentDataType.BOOLEAN, true);

			item.setItemMeta(meta);
		}

		return item;
	}

	@Override
	public boolean castBuff(LivingEntity entity, float power, String[] args) {
		EntityEquipment inv = entity.getEquipment();
		if (inv == null) return false;

		ItemStack helmet = getItem(helmetData.get(entity, power, args));
		ItemStack chestplate = getItem(chestplateData.get(entity, power, args));
		ItemStack leggings = getItem(leggingsData.get(entity, power, args));
		ItemStack boots = getItem(bootsData.get(entity, power, args));
		ItemStack offhand = getItem(offhandData.get(entity, power, args));

		ArmorSet armorSet = new ArmorSet(helmet, chestplate, leggings, boots, offhand);

		if (!replace && ((armorSet.helmet() != null && inv.getHelmet() != null) || (armorSet.chestplate() != null && inv.getChestplate() != null) || (armorSet.leggings() != null && inv.getLeggings() != null) || (armorSet.boots() != null && inv.getBoots() != null) || (armorSet.offhand() != null && inv.getItemInOffHand().getType() != Material.AIR))) {
			// error
			if (entity instanceof Player) sendMessage(strHasArmor, entity, args);
			return false;
		}

		setArmor(inv, armorSet);

		if (!permanent) entities.put(entity.getUniqueId(), armorSet);
		return true;
	}

	@Override
	public boolean recastBuff(LivingEntity entity, float power, String[] args) {
		return castBuff(entity, power, args);
	}

	@Override
	public boolean isActive(LivingEntity entity) {
		return entities.containsKey(entity.getUniqueId());
	}

	@Override
	public void turnOffBuff(LivingEntity entity) {
		ArmorSet armorSet = entities.remove(entity.getUniqueId());
		if (armorSet == null) return;
		if (!entity.isValid()) return;
		EntityEquipment inv = entity.getEquipment();
		if(revert) revertArmor(inv, entity.getUniqueId());
		else removeArmor(inv, armorSet);
	}

	@Override
	protected void turnOff() {
		for (Entry<UUID, ArmorSet> entry : entities.entrySet()) {
			Entity entity = Bukkit.getEntity(entry.getKey());
			if (entity == null) continue;
			if (!entity.isValid()) continue;
			EntityEquipment inv = ((LivingEntity) entity).getEquipment();
			if(revert) revertArmor(inv, entity.getUniqueId());
			else removeArmor(inv, entry.getValue());
		}
		entities.clear();
	}

	private static class EquipStore {
		public ItemStack helmet;
		public ItemStack chest;
		public ItemStack legs;
		public ItemStack boots;
		public ItemStack offhand;
	}
	private final Map<UUID, EquipStore> equipStore = new HashMap<>();

	private void setArmor(EntityEquipment inv, ArmorSet armorSet) {
		ItemStack helmet = armorSet.helmet();
		ItemStack chestplate = armorSet.chestplate();
		ItemStack leggings = armorSet.leggings();
		ItemStack boots = armorSet.boots();
		ItemStack offhand = armorSet.offhand();

		EquipStore eStore = new EquipStore();
		if (helmet != null) {
			eStore.helmet = inv.getHelmet();
			if (eStore.helmet == null) eStore.helmet = new ItemStack(Material.AIR);
			if (replace) inv.setHelmet(null);
			ItemStack stack = helmet.clone();
			if (duration > 0) {
				ItemMeta meta = stack.getItemMeta();
				long expiresAt = System.currentTimeMillis() + (long) (duration * 1000L);
				meta.getPersistentDataContainer().set(new NamespacedKey(MagicSpells.getInstance(), "expires_at"), PersistentDataType.LONG, expiresAt);
				stack.setItemMeta(meta);
			}
			inv.setHelmet(stack);
		}

		if (chestplate != null) {
			eStore.chest = inv.getChestplate();
			if (eStore.chest == null) eStore.chest = new ItemStack(Material.AIR);
			if (replace) inv.setChestplate(null);
			ItemStack stack = chestplate.clone();
			if (duration > 0) {
				ItemMeta meta = stack.getItemMeta();
				long expiresAt = System.currentTimeMillis() + (long) (duration * 1000L);
				meta.getPersistentDataContainer().set(new NamespacedKey(MagicSpells.getInstance(), "expires_at"), PersistentDataType.LONG, expiresAt);
				stack.setItemMeta(meta);
			}
			inv.setChestplate(stack);
		}

		if (leggings != null) {
			eStore.legs = inv.getLeggings();
			if (eStore.legs == null) eStore.legs = new ItemStack(Material.AIR);
			if (replace) inv.setLeggings(null);
			ItemStack stack = leggings.clone();
			if (duration > 0) {
				ItemMeta meta = stack.getItemMeta();
				long expiresAt = System.currentTimeMillis() + (long) (duration * 1000L);
				meta.getPersistentDataContainer().set(new NamespacedKey(MagicSpells.getInstance(), "expires_at"), PersistentDataType.LONG, expiresAt);
				stack.setItemMeta(meta);
			}
			inv.setLeggings(stack);
		}

		if (boots != null) {
			eStore.boots = inv.getBoots();
			if (eStore.boots == null) eStore.boots = new ItemStack(Material.AIR);
			if (replace) inv.setBoots(null);
			ItemStack stack = boots.clone();
			if (duration > 0) {
				ItemMeta meta = stack.getItemMeta();
				long expiresAt = System.currentTimeMillis() + (long) (duration * 1000L);
				meta.getPersistentDataContainer().set(new NamespacedKey(MagicSpells.getInstance(), "expires_at"), PersistentDataType.LONG, expiresAt);
				stack.setItemMeta(meta);
			}
			inv.setBoots(stack);
		}

		if (offhand != null) {
			eStore.offhand = inv.getItemInOffHand();
			if (eStore.offhand == null) eStore.offhand = new ItemStack(Material.AIR);
			if (replace) inv.setItemInOffHand(null);
			ItemStack stack = offhand.clone();
			if (duration > 0) {
				ItemMeta meta = stack.getItemMeta();
				long expiresAt = System.currentTimeMillis() + (long) (duration * 1000L);
				meta.getPersistentDataContainer().set(new NamespacedKey(MagicSpells.getInstance(), "expires_at"), PersistentDataType.LONG, expiresAt);
				stack.setItemMeta(meta);
			}
			inv.setItemInOffHand(stack);
		}

		equipStore.put(inv.getHolder().getUniqueId(), eStore);
	}

	private void removeArmor(EntityEquipment inv, ArmorSet armorSet) {
		ItemStack helmet = armorSet.helmet();
		ItemStack chestplate = armorSet.chestplate();
		ItemStack leggings = armorSet.leggings();
		ItemStack boots = armorSet.boots();
		ItemStack offhand = armorSet.offhand();

		ItemStack invHelmet = inv.getHelmet();
		if (helmet != null && invHelmet != null && invHelmet.getType() == helmet.getType()) {
			inv.setHelmet(null);
		}

		ItemStack invChestplate = inv.getChestplate();
		if (chestplate != null && invChestplate != null && invChestplate.getType() == chestplate.getType()) {
			inv.setChestplate(null);
		}

		ItemStack invLeggings = inv.getLeggings();
		if (leggings != null && invLeggings != null && invLeggings.getType() == leggings.getType()) {
			inv.setLeggings(null);
		}

		ItemStack invBoots = inv.getBoots();
		if (boots != null && invBoots != null && invBoots.getType() == boots.getType()) {
			inv.setBoots(null);
		}

		ItemStack invOffhand = inv.getItemInOffHand();
		if (offhand != null && invOffhand != null && invOffhand.getType() == offhand.getType()) {
			inv.setItemInOffHand(null);
		}
	}

	private void revertArmor(EntityEquipment inv, UUID id){
		if(equipStore.containsKey(id)){
			EquipStore eStore = equipStore.remove(id);
			if(eStore.helmet != null){
				inv.setHelmet(null);
				inv.setHelmet(eStore.helmet);
			}
			if(eStore.chest != null){
				inv.setChestplate(null);
				inv.setChestplate(eStore.chest);
			}
			if(eStore.legs != null){
				inv.setLeggings(null);
				inv.setLeggings(eStore.legs);
			}
			if(eStore.boots != null){
				inv.setBoots(null);
				inv.setBoots(eStore.boots);
			}
			if(eStore.offhand != null){
				inv.setItemInOffHand(null);
				inv.setItemInOffHand(eStore.offhand);
			}
		}
		ArmorSet armorSet = entities.remove(id);

		if (armorSet != null) removeArmor(inv, armorSet);
	}

	private class ArmorListener implements Listener {

		@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
		public void onEntityDamage(EntityDamageEvent event) {
			Entity entity = event.getEntity();
			if (!(entity instanceof LivingEntity livingEntity)) return;
			if (!isActive(livingEntity)) return;
			if (livingEntity.getNoDamageTicks() >= 10) return;
			addUseAndChargeCost(livingEntity);
		}

		@EventHandler(ignoreCancelled=true)
		public void onInventoryClick(InventoryClickEvent event) {
			if (event.getSlotType() != SlotType.ARMOR && event.getSlotType() != SlotType.QUICKBAR && event.getClick() != ClickType.SWAP_OFFHAND) return;
			HumanEntity entity = event.getWhoClicked();
			if (!(entity instanceof Player p)) return;
			if (!isActive(p)) return;

			ArmorSet armorSet = entities.get(p.getUniqueId());

			if (armorSet == null ||
				!(
					(event.getSlot() == 39 && armorSet.helmet() != null) ||
					(event.getSlot() == 38 && armorSet.chestplate() != null) ||
					(event.getSlot() == 37 && armorSet.leggings() != null) ||
					(event.getSlot() == 36 && armorSet.boots() != null) ||
					(event.getSlot() == 40 && armorSet.offhand() != null) ||
					(event.getClick() == ClickType.SWAP_OFFHAND && armorSet.offhand() != null)
				)
			) return;

			event.setCancelled(true);
		}

		@EventHandler
		public void onArmorSwap(PlayerInteractEvent event){
			Player player = event.getPlayer();
			PlayerInventory inventory = player.getInventory();

			if(!isActive(player)) return;

			ArmorSet armorSet = entities.get(player.getUniqueId());

			if (armorSet == null ||
				!(
					(armorSet.helmet() != null && (inventory.getItemInMainHand().getType().toString().toLowerCase().contains("helmet") || inventory.getItemInOffHand().getType().toString().toLowerCase().contains("helmet"))) || 
					(armorSet.chestplate() != null && (inventory.getItemInMainHand().getType().toString().toLowerCase().contains("chestplate") || inventory.getItemInOffHand().getType().toString().toLowerCase().contains("chestplate"))) || 
					(armorSet.leggings() != null && (inventory.getItemInMainHand().getType().toString().toLowerCase().contains("legging") || inventory.getItemInOffHand().getType().toString().toLowerCase().contains("legging"))) || 
					(armorSet.boots() != null && (inventory.getItemInMainHand().getType().toString().toLowerCase().contains("boots") || inventory.getItemInOffHand().getType().toString().toLowerCase().contains("boots")))
				)
			) return;

			event.setCancelled(true);
		}

		@EventHandler
		public void onPlayerSwapHands(PlayerSwapHandItemsEvent event) {
			Player player = event.getPlayer();

			if(!isActive(player)) return;

			ArmorSet armorSet = entities.get(player.getUniqueId());

			if (armorSet == null || armorSet.offhand() == null) return;

			event.setCancelled(true);
		}

		@EventHandler
		public void onEntityDeath(EntityDeathEvent event) {
			if (permanent) return;
			Iterator<ItemStack> drops = event.getDrops().iterator();
			while (drops.hasNext()) {
				ItemStack drop = drops.next();
				if (drop == null) continue;
				if (!drop.hasItemMeta()) continue;
				ItemMeta meta = drop.getItemMeta();
				Boolean MSArmorItem = meta.getPersistentDataContainer().get(new NamespacedKey(MagicSpells.getInstance(), "MSArmorItem"), PersistentDataType.BOOLEAN);
				if (MSArmorItem == null || !MSArmorItem) continue;
				drops.remove();
			}
		}

		@EventHandler
		public void onPlayerRespawn(PlayerRespawnEvent event) {
			Player player = event.getPlayer();
			if (!isActive(player)) return;
			if (isExpired(player)) return;

			final EntityEquipment inv = player.getEquipment();
			ArmorSet armorSet = entities.get(player.getUniqueId());
			if (armorSet != null) Bukkit.getScheduler().scheduleSyncDelayedTask(MagicSpells.plugin, () -> setArmor(inv, armorSet));
		}

		@EventHandler
		public void onPlayerQuit(PlayerQuitEvent event) {
			Player player = event.getPlayer();
			if (!isActive(player)) return;

			if (cancelOnLogout) turnOff(player);
			else if (entities.containsKey(player.getUniqueId())) removeArmor(player.getEquipment(), entities.get(player.getUniqueId()));
		}

		@EventHandler
		public void onPlayerJoin(PlayerJoinEvent event) {
			Player player = event.getPlayer();
			if (!isActive(player)) return;

			ArmorSet armorSet = entities.get(player.getUniqueId());
			if (!isExpired(player) && armorSet != null) setArmor(player.getEquipment(), armorSet);
			else turnOff(player);
		}

	}

}

record ArmorSet(
	ItemStack helmet,
	ItemStack chestplate,
	ItemStack leggings,
	ItemStack boots,
	ItemStack offhand) {}