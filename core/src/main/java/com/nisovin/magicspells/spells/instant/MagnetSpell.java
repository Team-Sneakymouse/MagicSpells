package com.nisovin.magicspells.spells.instant;

import java.util.List;
import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.InventoryUtil;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class MagnetSpell extends InstantSpell implements TargetedLocationSpell {

	private ConfigData<Double> radius;
	private ConfigData<Double> velocity;
	private ConfigData<Integer> maxTargets;

	private boolean teleport;
	private boolean forcePickup;
	private boolean removeItemGravity;
	private ConfigData<Boolean> powerAffectsRadius;
	private ConfigData<Boolean> powerAffectsVelocity;
	private boolean resolveVelocityPerItem;

	public MagnetSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		radius = getConfigDataDouble("radius", 5);
		velocity = getConfigDataDouble("velocity", 1);
		maxTargets = getConfigDataInt("max-targets", 100);

		teleport = getConfigBoolean("teleport-items", false);
		forcePickup = getConfigBoolean("force-pickup", false);
		removeItemGravity = getConfigBoolean("remove-item-gravity", false);
		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", true);
		powerAffectsVelocity = getConfigDataBoolean("power-affects-velocity", true);
		resolveVelocityPerItem = getConfigBoolean("resolve-velocity-per-item", false);
	}

	@Override
	public CastResult cast(SpellData data) {
		Location location = data.caster().getLocation();
		List<Item> items = getNearbyItems(data.caster(), location, data.power(), data.args());
		magnet(data.caster(), location, items, data.power(), data.args());
		playSpellEffects(EffectPosition.CASTER, data.caster(), data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		Collection<Item> targetItems = getNearbyItems(data.caster(), data.location(), data.power(), data.args());
		magnet(data.caster(), data.location(), targetItems, data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action()
				!= PostCastAction.ALREADY_HANDLED;
	}

	private List<Item> getNearbyItems(LivingEntity caster, Location center, float power, String[] args) {
		int maxTargets = this.maxTargets.get(caster, null, power, args);
		if (maxTargets <= 0)
			return List.of();

		double radius = this.radius.get(caster, null, power, args);
		if (Boolean.TRUE.equals(powerAffectsRadius.get(caster, null, power, args)))
			radius *= power;
		radius = Math.min(radius, MagicSpells.getGlobalRadius());

		Collection<Entity> entities = center.getWorld().getNearbyEntities(center, radius, radius, radius);
		MagnetItemBuckets<Item> buckets = new MagnetItemBuckets<>(radius, maxTargets);
		Location itemLocation = center.clone();
		for (Entity e : entities) {
			if (!(e instanceof Item i))
				continue;
			if (i.isDead())
				continue;
			if (!forcePickup && i.getPickupDelay() >= i.getTicksLived())
				continue;
			ItemStack stack = i.getItemStack();
			if (InventoryUtil.isNothing(stack))
				continue;

			i.getLocation(itemLocation);
			buckets.add(i, itemLocation.getX() - center.getX(), itemLocation.getY() - center.getY(),
					itemLocation.getZ() - center.getZ());
		}
		return buckets.select();
	}

	private void magnet(LivingEntity caster, Location location, Collection<Item> items, float power, String[] args) {
		double velocity = 0;
		if (!resolveVelocityPerItem) {
			velocity = this.velocity.get(caster, null, power, args);
			if (Boolean.TRUE.equals(powerAffectsVelocity.get(caster, null, power, args)))
				velocity *= power;
		}

		SpellData data = new SpellData(caster, location, power, args);
		for (Item i : items)
			magnet(caster, location, i, power, args, data, velocity);
	}

	private void magnet(LivingEntity caster, Location origin, Item item, float power, String[] args, SpellData data,
			double velocity) {
		if (forcePickup)
			item.setPickupDelay(0);
		if (removeItemGravity)
			item.setGravity(false);
		if (teleport)
			item.teleportAsync(origin);
		else {
			if (resolveVelocityPerItem) {
				velocity = this.velocity.get(caster, null, power, args);
				if (Boolean.TRUE.equals(powerAffectsVelocity.get(caster, null, power, args)))
					velocity *= power;
			}

			item.setVelocity(origin.toVector().subtract(item.getLocation().toVector()).normalize().multiply(velocity));
		}
		playSpellEffects(EffectPosition.PROJECTILE, item, data);
	}

	public boolean shouldTeleport() {
		return teleport;
	}

	public void setTeleport(boolean teleport) {
		this.teleport = teleport;
	}

	public boolean shouldForcePickup() {
		return forcePickup;
	}

	public void setForcePickup(boolean forcePickup) {
		this.forcePickup = forcePickup;
	}

	public boolean shouldRemoveItemGravity() {
		return removeItemGravity;
	}

	public void setRemoveItemGravity(boolean removeItemGravity) {
		this.removeItemGravity = removeItemGravity;
	}

}
