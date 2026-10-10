package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellPreImpactEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;

@SuppressWarnings("deprecation") // Bukkit Metadata API; migrate to PDC later
public class VolleySpell extends TargetedSpell implements TargetedLocationSpell, TargetedEntityFromLocationSpell {

	private static final String METADATA_KEY = "MagicSpellsSource";

	private ConfigData<Integer> fire;
	private ConfigData<Integer> arrows;
	private ConfigData<Integer> removeDelay;
	private ConfigData<Integer> shootInterval;
	private ConfigData<Integer> knockbackStrength;

	private ConfigData<Float> speed;
	private ConfigData<Float> spread;

	private ConfigData<Double> damage;
	private ConfigData<Double> yOffset;

	private boolean gravity;
	private boolean critical;
	private boolean noTarget;
	private ConfigData<Boolean> powerAffectsSpeed;
	private ConfigData<Boolean> powerAffectsArrowCount;
	private float addPitch;

	public VolleySpell(MagicConfig config, String spellName) {
		super(config, spellName);

		fire = getConfigDataInt("fire", 0);
		arrows = getConfigDataInt("arrows", 10);
		removeDelay = getConfigDataInt("remove-delay", 0);
		shootInterval = getConfigDataInt("shoot-interval", 0);
		knockbackStrength = getConfigDataInt("knockback-strength", 0);

		speed = getConfigDataFloat("speed", 20);
		spread = getConfigDataFloat("spread", 150);

		damage = getConfigDataDouble("damage", 4);
		yOffset = getConfigDataDouble("y-offset", 3);

		gravity = getConfigBoolean("gravity", true);
		critical = getConfigBoolean("critical", false);
		noTarget = getConfigBoolean("no-target", false);
		powerAffectsSpeed = getConfigDataBoolean("power-affects-speed", false);
		powerAffectsArrowCount = getConfigDataBoolean("power-affects-arrow-count", true);

		addPitch = getConfigFloat("add-pitch", 0);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			if (noTarget) {
				volley(caster, null, caster.getLocation(), null, power, args);
				return PostCastAction.HANDLE_NORMALLY;
			}

			Block target;
			try {
				target = getTargetedBlock(caster, power, args);
			} catch (IllegalStateException e) {
				target = null;
			}
			if (target == null || BlockUtils.isAir(target.getType()))
				return noTarget(caster, args);
			volley(caster, null, caster.getLocation(), target.getLocation(), power, args);
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		if (noTarget)
			return false;
		volley(caster, null, caster.getLocation(), target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power,
			String[] args) {
		if (noTarget || !validTargetList.canTarget(caster, target))
			return false;
		volley(caster, target, from, target.getLocation(), power, args);
		return true;
	}

	@Override
	public boolean castAtEntityFromLocation(Location from, LivingEntity target, float power, String[] args) {
		if (noTarget || !validTargetList.canTarget(target))
			return false;
		volley(null, target, from, target.getLocation(), power, args);
		return true;
	}

	private void volley(LivingEntity caster, LivingEntity target, Location from, Location targetLoc, float power,
			String[] args) {
		Location spawn = from.clone().add(0, yOffset.get(caster, target, power, args), 0);
		Vector v;

		if (noTarget || targetLoc == null)
			v = from.getDirection();
		else
			v = targetLoc.toVector().subtract(spawn.toVector()).normalize();

		if (addPitch != 0) {
			v = v.add(new Vector(0, addPitch / 90, 0));
		}

		SpellData data = new SpellData(caster, target, from, power, args);
		int shootInterval = this.shootInterval.get(caster, target, power, args);
		if (shootInterval <= 0) {
			List<Arrow> arrowList = new ArrayList<>();

			int arrows = this.arrows.get(caster, target, power, args);
			int removeDelay = this.removeDelay.get(caster, target, power, args);
			int castingArrows = Boolean.TRUE.equals(powerAffectsArrowCount.get(caster, target, power, args)) ? Math.round(arrows * power) : arrows;
			for (int i = 0; i < castingArrows; i++) {
				float speed = this.speed.get(caster, target, power, args) / 10f;
				if (Boolean.TRUE.equals(powerAffectsSpeed.get(caster, target, power, args)))
					speed *= power;

				float spread = this.spread.get(caster, target, power, args) / 10f;

				Arrow arrow = from.getWorld().spawnArrow(spawn, v, speed, spread);
				applyArrowKnockback(arrow, knockbackStrength.get(caster, target, power, args));
				arrow.setCritical(critical);
				arrow.setGravity(gravity);

				double damage = this.damage.get(caster, target, power, args);
				arrow.setDamage(damage);
				arrow.setMetadata(METADATA_KEY, new FixedMetadataValue(MagicSpells.plugin,
						new VolleyData("VolleySpell" + internalName, damage)));

				int fire = this.fire.get(caster, target, power, args);
				if (fire > 0)
					arrow.setFireTicks(fire);

				if (caster != null)
					arrow.setShooter(caster);

				if (removeDelay > 0)
					arrowList.add(arrow);

				playSpellEffects(EffectPosition.PROJECTILE, arrow, data);
				playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE, spawn, arrow.getLocation(),
						caster, arrow, data);
			}

			if (removeDelay > 0) {
				MagicSpells.scheduleDelayedTask(() -> {
					for (Arrow a : arrowList)
						a.remove();
					arrowList.clear();
				}, removeDelay);
			}
		} else
			new ArrowShooter(caster, target, spawn, v, power, args);

		if (caster != null) {
			if (targetLoc != null)
				playSpellEffects(caster, targetLoc, data);
			else
				playSpellEffects(EffectPosition.CASTER, caster, data);
		} else {
			playSpellEffects(EffectPosition.CASTER, from, data);
			if (targetLoc != null)
				playSpellEffects(EffectPosition.TARGET, targetLoc, data);
		}
	}

	/**
	 * Arrow knockback is derived from the firing weapon's Punch enchant on modern Paper.
	 */
	private static void applyArrowKnockback(Arrow arrow, int strength) {
		if (strength <= 0) return;
		ItemStack weapon = new ItemStack(Material.BOW);
		weapon.addUnsafeEnchantment(Enchantment.PUNCH, strength);
		arrow.setWeapon(weapon);
	}

	@EventHandler
	public void onArrowHit(EntityDamageByEntityEvent event) {
		if (event.getCause() != DamageCause.PROJECTILE || !(event.getEntity() instanceof LivingEntity target))
			return;

		Entity damagerEntity = event.getDamager();
		if (!(damagerEntity instanceof Arrow arrow) || !damagerEntity.hasMetadata(METADATA_KEY))
			return;

		MetadataValue meta = damagerEntity.getMetadata(METADATA_KEY).iterator().next();
		if (meta == null)
			return;

		VolleyData data = (VolleyData) meta.value();
		if (data == null || !data.identifier.equals("VolleySpell" + internalName))
			return;

		event.setDamage(data.damage);

		SpellPreImpactEvent preImpactEvent = new SpellPreImpactEvent(this, this, (LivingEntity) arrow.getShooter(),
				target, 1);
		EventUtil.call(preImpactEvent);
		if (!preImpactEvent.getRedirected())
			return;

		event.setCancelled(true);
		arrow.setVelocity(arrow.getVelocity().multiply(-1));
		arrow.teleportAsync(arrow.getLocation().add(arrow.getVelocity()));
	}

	private class ArrowShooter implements Runnable {

		private final Map<Integer, Arrow> arrowMap;

		private final LivingEntity caster;
		private final LivingEntity target;
		private final SpellData data;
		private final Location spawn;
		private final String[] args;
		private final float power;
		private final Vector dir;
		private final int taskId;

		private final int castingArrows;
		private final int removeDelay;

		private int count;

		private ArrowShooter(LivingEntity caster, LivingEntity target, Location spawn, Vector dir, float power,
				String[] args) {
			this.caster = caster;
			this.target = target;
			this.spawn = spawn;
			this.power = power;
			this.args = args;
			this.dir = dir;

			data = new SpellData(caster, target, spawn, power, args);

			removeDelay = VolleySpell.this.removeDelay.get(caster, target, power, args);

			int arrows = VolleySpell.this.arrows.get(caster, target, power, args);
			if (Boolean.TRUE.equals(powerAffectsArrowCount.get(caster, target, power, args)))
				arrows = Math.round(arrows * power);
			castingArrows = arrows;

			this.count = 0;

			if (removeDelay > 0)
				this.arrowMap = new HashMap<>();
			else
				arrowMap = null;

			this.taskId = MagicSpells.scheduleRepeatingTask(this, 0, shootInterval.get(caster, target, power, args));
		}

		@Override
		public void run() {
			if (count < castingArrows) {
				float speed = VolleySpell.this.speed.get(caster, target, power, args) / 10f;
				if (Boolean.TRUE.equals(powerAffectsSpeed.get(caster, target, power, args)))
					speed *= power;

				float spread = VolleySpell.this.spread.get(caster, target, power, args) / 10f;

				Arrow arrow = spawn.getWorld().spawnArrow(spawn, dir, speed, spread);
				applyArrowKnockback(arrow, knockbackStrength.get(caster, target, power, args));
				arrow.setCritical(critical);
				arrow.setGravity(gravity);

				double damage = VolleySpell.this.damage.get(caster, target, power, args);
				arrow.setDamage(damage);
				arrow.setMetadata(METADATA_KEY, new FixedMetadataValue(MagicSpells.plugin,
						new VolleyData("VolleySpell" + internalName, damage)));

				int fire = VolleySpell.this.fire.get(caster, target, power, args);
				if (fire > 0)
					arrow.setFireTicks(fire);

				if (caster != null)
					arrow.setShooter(caster);

				if (removeDelay > 0)
					arrowMap.put(count, arrow);

				playSpellEffects(EffectPosition.PROJECTILE, arrow, data);
				playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE,
						caster == null ? spawn : caster.getLocation(), arrow.getLocation(), caster, arrow, data);
			}

			if (removeDelay > 0) {
				int old = count - removeDelay;
				if (old >= 0) {
					Arrow a = arrowMap.remove(old);
					if (a != null)
						a.remove();
				}
			}

			if (count >= castingArrows + removeDelay)
				MagicSpells.cancelTask(taskId);

			count++;
		}

	}

	private record VolleyData(String identifier, double damage) {
	}

}
