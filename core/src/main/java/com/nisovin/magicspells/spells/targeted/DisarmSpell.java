package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.event.entity.EntityPickupItemEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.util.magicitems.MagicItemData;

public class DisarmSpell extends TargetedSpell implements TargetedEntitySpell {

	private Set<MagicItemData> disarmable;
	private Map<Item, UUID> disarmedItems;

	private boolean dontDrop;
	private boolean preventTheft;

	private ConfigData<Integer> disarmDuration;

	private String strInvalidItem;
	
	public DisarmSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		List<String> disarmableItems = getConfigStringList("disarmable-items", null);
		if (disarmableItems != null && !disarmableItems.isEmpty()) {
			disarmable = new HashSet<>();

			for (String itemName : disarmableItems) {
				MagicItemData data = MagicItems.getMagicItemDataFromString(itemName);
				if (data != null) disarmable.add(data);
			}
		}

		dontDrop = getConfigBoolean("dont-drop", false);
		preventTheft = getConfigBoolean("prevent-theft", true);

		disarmDuration = getConfigDataInt("disarm-duration", 100);

		strInvalidItem = getConfigString("str-invalid-item", "Your target could not be disarmed.");
		
		if (dontDrop) preventTheft = false;
		if (preventTheft) disarmedItems = new HashMap<>();
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> info = getTargetedEntity(data.caster(), data.power(), data.args());
					if (info.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), info), data);
		
					LivingEntity target = info.target();
					data = data.power(info.power());
		
					boolean disarmed = disarm(data.caster(), target, data.power(), data.args());
					if (!disarmed) return new CastResult(noTarget(data.caster(), strInvalidItem, data.args()), data);
		
					playSpellEffects(data.caster(), target, data.power(), data.args());
					sendMessages(data.caster(), target, data.args());
		
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (!(data.target() instanceof LivingEntity target))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							boolean disarmed =  disarm(data.caster(), target, data.power(), data.args());
							if (disarmed) playSpellEffects(data.caster(), data.target(), data.power(), data.args());
			if (!(disarmed)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							boolean disarmed = disarm(null, target, data.power(), data.args());
							if (disarmed) playSpellEffects(EffectPosition.TARGET, data.target(), data.power(), data.args());
			if (!(disarmed)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



	private boolean disarm(Entity caster, LivingEntity target, float power, String[] args) {
		final ItemStack inHand = getItemInHand(target);
		if (inHand == null) return false;

		if (disarmable != null) {
			if (!contains(inHand)) return false;
		}

		int disarmDuration = this.disarmDuration.get(caster, target, power, args);
		if (!dontDrop) {
			setItemInHand(target, null);
			Item item = target.getWorld().dropItemNaturally(target.getLocation(), inHand.clone());
			item.setPickupDelay(disarmDuration);
			if (preventTheft && target instanceof Player) disarmedItems.put(item, target.getUniqueId());
			return true;
		}

		setItemInHand(target, null);
		MagicSpells.scheduleDelayedTask(() -> {
			ItemStack inHand2 = getItemInHand(target);
			if (inHand2 == null || inHand2.getType() == Material.AIR) {
				setItemInHand(target, inHand);
			} else if (target instanceof Player) {
				int slot = ((Player) target).getInventory().firstEmpty();
				if (slot >= 0) ((Player) target).getInventory().setItem(slot, inHand);
				else {
					Item item = target.getWorld().dropItem(target.getLocation(), inHand);
					item.setPickupDelay(0);
				}
			}
		}, disarmDuration);

		return true;
	}

	private boolean contains(ItemStack item) {
		for (MagicItemData data : disarmable) {
			if (MagicItems.matches(data, item)) return true;
		}
		return false;
	}
	
	private ItemStack getItemInHand(LivingEntity entity) {
		EntityEquipment equip = entity.getEquipment();
		if (equip == null) return null;
		return equip.getItemInMainHand();
	}
	
	private void setItemInHand(LivingEntity entity, ItemStack item) {
		EntityEquipment equip = entity.getEquipment();
		if (equip == null) return;
		equip.setItemInMainHand(item);
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onItemPickup(EntityPickupItemEvent event) {
		if (!preventTheft) return;
		
		Item item = event.getItem();
		if (!disarmedItems.containsKey(item)) return;
		if (disarmedItems.get(item).equals(event.getEntity().getUniqueId())) disarmedItems.remove(item);
		else event.setCancelled(true);
	}

}
