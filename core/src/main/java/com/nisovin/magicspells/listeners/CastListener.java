package com.nisovin.magicspells.listeners;

import java.util.Map;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerAnimationEvent;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Spell.SpellCastState;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.BowSpell;
import com.nisovin.magicspells.util.BlockUtils;

@SuppressWarnings("deprecation") // Bukkit Metadata API; migrate to PDC later
public class CastListener implements Listener {

	private MagicSpells plugin;

	private Map<String, Long> noCastUntil;

	public CastListener(MagicSpells plugin) {
		this.plugin = plugin;
		noCastUntil = new HashMap<>();
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerInteract(PlayerInteractEvent event) {
		final Player player = event.getPlayer();

		// First check if player is interacting with a special block
		boolean noInteract = false;
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			Material m = event.getClickedBlock().getType();
			if (BlockUtils.isDoor(m) ||
					BlockUtils.isFenceGate(m) ||
					BlockUtils.isTrapdoor(m) ||
					BlockUtils.isShulkerBox(m) ||
					BlockUtils.isButton(m) ||
					BlockUtils.isBed(m) ||
					BlockUtils.isChest(m) ||
					m.name().contains("ANVIL") ||
					m == Material.BARREL ||
					m == Material.BEACON ||
					m == Material.BLAST_FURNACE ||
					m == Material.BREWING_STAND ||
					m == Material.CARTOGRAPHY_TABLE ||
					m == Material.COMPARATOR ||
					m == Material.CRAFTING_TABLE ||
					m == Material.DAYLIGHT_DETECTOR ||
					m == Material.DISPENSER ||
					m == Material.DROPPER ||
					m == Material.ENCHANTING_TABLE ||
					m == Material.ENDER_CHEST ||
					m == Material.FURNACE ||
					m == Material.GRINDSTONE ||
					m == Material.HOPPER ||
					m == Material.LEVER ||
					m == Material.LOOM ||
					m == Material.NOTE_BLOCK ||
					m == Material.POLISHED_BLACKSTONE_BUTTON ||
					m == Material.REPEATER ||
					m == Material.SMITHING_TABLE ||
					m == Material.SMOKER ||
					m == Material.STONECUTTER) {
				noInteract = true;
			} else if (event.hasItem() && event.getItem().getType().isBlock()) {
				noInteract = true;
			}

			// Force exp bar back to show exp when trying to enchant
			if (m == Material.ENCHANTING_TABLE) MagicSpells.getExpBarManager().update(player, player.getLevel(), player.getExp());
		}

		if (noInteract) {
			// Special block -- don't do normal interactions
			setNoCast(event.getPlayer());
			return;
		}

		if (isEventCastAction(event)) {
			// Cast
			MagicSpells.scheduleDelayedTask(() -> {
				if (!isNoCast(player)) {
					if (!MagicSpells.isCastingOnAnimate()) castSpell(player);
				}
			}, 1);
			return;
		}

		if (isEventCycleAction(event) && (MagicSpells.isCyclingSpellsOnOffhandAction() || event.getHand() == EquipmentSlot.HAND)) {
			ItemStack inHand = player.getInventory().getItemInMainHand();
			
			if (isBow(inHand.getType())) {
				if (!MagicSpells.canBowCycleSpellsSneaking() && player.isSneaking()) return;
			}
			
			if ((!BlockUtils.isAir(inHand.getType())) || MagicSpells.canCastWithFist()) {
				// Cycle spell
				Spell spell;
				if (!player.isSneaking()) spell = MagicSpells.getSpellbook(player).nextSpell(inHand);
				else spell = MagicSpells.getSpellbook(player).prevSpell(inHand);

				if (spell == null) return;
				// Send message
				MagicSpells.sendMessageAndFormat(player, MagicSpells.getSpellChangeMessage(), "%s", spell.getName());
				// Show spell icon
				if (MagicSpells.getSpellIconSlot() >= 0) showIcon(player, MagicSpells.getSpellIconSlot(), spell.getSpellIcon());

				// Use cool new text thingy
				/*boolean yay = false;
				if (yay) {
					final ItemStack fake = inHand.clone();
					if (fake == null) return;
					ItemMeta meta = fake.getItemMeta();
					meta.setDisplayName("Spell: " + spell.getName());
					fake.setItemMeta(meta);
					MagicSpells.scheduleDelayedTask(() -> MagicSpells.getVolatileCodeHandler().sendFakeSlotUpdate(player, player.getInventory().getHeldItemSlot(), fake), 0);
				} */
			}
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerDamage(EntityDamageByEntityEvent event) {
		if (MagicSpells.isCastingOnAnimate()) return;
		if (event.getCause() == DamageCause.ENTITY_ATTACK && event.getDamager() instanceof Player player) {

			if (player.getUniqueId().equals(event.getEntity().getUniqueId())) return;

			Spellbook spellbook = MagicSpells.getSpellbook(player);
			Spell spell = spellbook.getActiveSpell(player.getInventory().getItemInMainHand());

			if (spell != null) {
				if (!isNoCast(player)) {
					event.setCancelled(castSpell(player));
				}
			}
		}
	}

	@EventHandler
	public void onItemHeldChange(final PlayerItemHeldEvent event) {
		if (MagicSpells.getSpellIconSlot() >= 0 && MagicSpells.getSpellIconSlot() <= 8) {
			Player player = event.getPlayer();
			if (event.getNewSlot() == MagicSpells.getSpellIconSlot()) {
				showIcon(player, MagicSpells.getSpellIconSlot(), null);
				return;
			}
			Spellbook spellbook = MagicSpells.getSpellbook(player);
			Spell spell = spellbook.getActiveSpell(player.getInventory().getItem(event.getNewSlot()));
			if (spell != null) showIcon(player, MagicSpells.getSpellIconSlot(), spell.getSpellIcon());
			else showIcon(player, MagicSpells.getSpellIconSlot(), null);
		}
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerAnimation(PlayerAnimationEvent event) {
		if (!MagicSpells.isCastingOnAnimate() || event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

		Player player = event.getPlayer();

		InventoryType type = player.getOpenInventory().getType();
		if (type != InventoryType.CRAFTING && type != InventoryType.CREATIVE) return;

		if (MagicSpells.areBowCycleButtonsReversed()) {
			ItemStack inHand = player.getInventory().getItemInMainHand();
			if (isBow(inHand.getType())) return;
		}

		MagicSpells.scheduleDelayedTask(() -> {
			if (!isNoCast(player)) {
				castSpell(player);
			}
		}, 1);
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerDrop(PlayerDropItemEvent event) {
		//if (!MagicSpells.isCastingOnAnimate()) return;
		setNoCast(event.getPlayer());
	}

	@EventHandler
	public void onPlayerShootBow(EntityShootBowEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;
		if (event.getHand() == EquipmentSlot.OFF_HAND && !MagicSpells.castBoundBowSpellsFromOffhand()) return;

		ItemStack bow = event.getBow();
		if (bow == null) return;

		Spellbook spellbook = MagicSpells.getSpellbook(player);
		Spell spell = spellbook.getActiveSpell(bow);

		if (spell instanceof BowSpell bowSpell) {
			if (bowSpell.canCastWithItem() && bowSpell.isBindRequired() && checkGlobalCooldown(player, spell)) bowSpell.handleBowCast(event);
		} else castSpell(player, spell, bow);

		event.getProjectile().setMetadata("bow-draw-strength", new FixedMetadataValue(plugin, event.getForce()));
	}

	private Boolean castSpell(Player player) {
		ItemStack inHand = player.getInventory().getItemInMainHand();
		if (!MagicSpells.canCastWithFist() && BlockUtils.isAir(inHand)) return false;

		Spellbook spellbook = MagicSpells.getSpellbook(player);
		Spell spell = spellbook.getActiveSpell(inHand);

		return castSpell(player, spell, inHand);
	}

	private Boolean castSpell(Player player, Spell spell, ItemStack item) {
		if (spell == null || !spell.canCastWithItem()) return false;
		if (!checkGlobalCooldown(player, spell)) return false;

		// Cast spell
		String[] args = null;
		if (item != null && item.hasItemMeta()) {
			PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
			if (container.has(new NamespacedKey(MagicSpells.getInstance(), "creator_name"), PersistentDataType.STRING)) {
				args = new String[] {container.get(new NamespacedKey(MagicSpells.getInstance(), "creator_name"), PersistentDataType.STRING)};
			}
		}
		return spell.cast(player, 1.0F, args).state.equals(SpellCastState.NORMAL);
	}

	private boolean checkGlobalCooldown(Player player, Spell spell) {
		if (MagicSpells.getGlobalCooldown() > 0 && !spell.isIgnoringGlobalCooldown()) {
			if (isNoCast(player)) return false;
			noCastUntil.put(player.getName(), System.currentTimeMillis() + MagicSpells.getGlobalCooldown());
		}

		return true;
	}

	private void showIcon(Player player, int slot, ItemStack icon) {
		if (icon == null) icon = player.getInventory().getItem(MagicSpells.getSpellIconSlot());
		MagicSpells.getVolatileCodeHandler().sendFakeSlotUpdate(player, slot, icon);
	}

	private boolean isBow(Material material) {
		if (material == null) return false;
		return material.name().equalsIgnoreCase("BOW") || material.name().equalsIgnoreCase("CROSSBOW");
	}
	
	private boolean isEventCastAction(PlayerInteractEvent event) {
		if (MagicSpells.areBowCycleButtonsReversed() && event.hasItem() && isBow(event.getItem().getType())) {
			return false;
		}
		
		return event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
	}
	
	private boolean isEventCycleAction(PlayerInteractEvent event) {
		if (MagicSpells.areBowCycleButtonsReversed() && event.hasItem() && isBow(event.getItem().getType())) {
			return event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
		}
		
		return event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
	}

	private boolean isNoCast(Player player) {
		Long noCastTime = noCastUntil.get(player.getName());
		if (noCastTime == null || System.currentTimeMillis() >= noCastTime) {
			return false;
		}
		return true;
	}

	public void setNoCast(Player player) {
		setNoCast(player, 150L);
	}

	public void setNoCast(Player player, Long delay) {
		noCastUntil.put(player.getName(), System.currentTimeMillis() + delay);
	}

}
