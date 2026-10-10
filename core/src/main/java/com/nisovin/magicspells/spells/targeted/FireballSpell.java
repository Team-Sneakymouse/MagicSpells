package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Fireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.SmallFireball;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TimeUtil;
import com.nisovin.magicspells.util.CastData;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;
import com.nisovin.magicspells.util.managers.AlteredBlockManager;

public class FireballSpell extends TargetedSpell implements TargetedEntityFromLocationSpell {

	private Map<Fireball, CastData> fireballs;

	private int taskId;

	private ConfigData<Float> explosionSize;

	private ConfigData<Double> damageMultiplier;
	private ConfigData<Double> noExplosionDamage;
	private ConfigData<Double> noExplosionDamageRange;

	private ConfigData<Boolean> noFire;
	private ConfigData<Boolean> noExplosion;
	private ConfigData<Boolean> checkPlugins;
	private ConfigData<Boolean> smallFireball;
	private ConfigData<Boolean> fireballGravity;
	private ConfigData<Boolean> noExplosionEffect;
	private ConfigData<Boolean> requireEntityTarget;
	private ConfigData<Boolean> doOffsetTargetingCorrections;
	private ConfigData<Boolean> powerAffectsDamageMultiplier;
	private ConfigData<Boolean> powerAffectsNoExplosionDamage;
	private ConfigData<Boolean> useRelativeCastLocationOffset;
	private ConfigData<Boolean> useAbsoluteCastLocationOffset;

	private Vector relativeCastLocationOffset;
	private Vector absoluteCastLocationOffset;

	public FireballSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		fireballs = new HashMap<>();

		explosionSize = getConfigDataFloat("explosion-size", 0);
		damageMultiplier = getConfigDataDouble("damage-multiplier", 0);

		noExplosionDamage = getConfigDataDouble("no-explosion-damage", 5);
		noExplosionDamageRange = getConfigDataDouble("no-explosion-damage-range", 3);

		noFire = getConfigDataBoolean("no-fire", true);
		noExplosion = getConfigDataBoolean("no-explosion", true);
		checkPlugins = getConfigDataBoolean("check-plugins", true);
		smallFireball = getConfigDataBoolean("small-fireball", false);
		fireballGravity = getConfigDataBoolean("gravity", false);
		noExplosionEffect = getConfigDataBoolean("no-explosion-effect", true);
		requireEntityTarget = getConfigDataBoolean("require-entity-target", false);
		doOffsetTargetingCorrections = getConfigDataBoolean("do-offset-targeting-corrections", true);
		powerAffectsDamageMultiplier = getConfigDataBoolean("power-affects-damage-multiplier", true);
		powerAffectsNoExplosionDamage = getConfigDataBoolean("power-affects-no-explosion-damage", true);
		useRelativeCastLocationOffset = getConfigDataBoolean("use-relative-cast-location-offset", false);
		useAbsoluteCastLocationOffset = getConfigDataBoolean("use-absolute-cast-location-offset", false);

		relativeCastLocationOffset = getConfigVector("relative-cast-position-offset", "0,0,0");
		absoluteCastLocationOffset = getConfigVector("absolute-cast-position-offset", "0,0,0");

		taskId = MagicSpells.scheduleRepeatingTask(
				() -> fireballs.entrySet().removeIf(fireballFloatEntry -> fireballFloatEntry.getKey().isDead()),
				TimeUtil.TICKS_PER_MINUTE, TimeUtil.TICKS_PER_MINUTE);
	}

	@Override
	public void turnOff() {
		MagicSpells.cancelTask(taskId);
	}

	@Override
	public CastResult cast(SpellData data) {
		boolean selfTarget = false;
					LivingEntity target = null;
					Location targetLoc = null;
		
					if (Boolean.TRUE.equals(requireEntityTarget.get(data.caster(), null, data.power(), data.args()))) {
						TargetInfo<LivingEntity> targetInfo = getTargetedEntity(data.caster(), data.power(), data.args());
						if (targetInfo.noTarget())
							return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);
		
						target = targetInfo.target();
						data = data.power(targetInfo.power());
		
						if (Boolean.TRUE.equals(checkPlugins.get(data.caster(), target, data.power(), data.args()))) {
							// Run a pvp damage check
							MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(data.caster(),
									target, DamageCause.MAGIC, 1D, this);
							EventUtil.call(event);
							if (event.isCancelled())
								return new CastResult(noTarget(data.caster(), data.args()), data);
						}
		
						targetLoc = target.getLocation();
						if (target.equals(data.caster()))
							selfTarget = true;
					}
		
					// Create fireball
					Location loc;
					Location pLoc = data.caster().getLocation();
					if (!selfTarget) {
						loc = data.caster().getEyeLocation().toVector().add(pLoc.getDirection().multiply(2))
								.toLocation(data.caster().getWorld(), pLoc.getYaw(), pLoc.getPitch());
						loc = offsetLocation(loc, data.caster(), target, data.power(), data.args());
						loc = applyOffsetTargetingCorrection(loc, targetLoc, data.caster(), target, data.power(), data.args());
					} else {
						loc = pLoc.toVector().add(pLoc.getDirection().setY(0).multiply(2)).toLocation(data.caster().getWorld(),
								pLoc.getYaw() + 180, 0);
					}
					Fireball fireball;
					if (Boolean.TRUE.equals(smallFireball.get(data.caster(), target, data.power(), data.args())) && data.caster() instanceof Player) {
						fireball = data.caster().launchProjectile(SmallFireball.class);
						data.caster().getWorld().playEffect(data.caster().getLocation(), Effect.GHAST_SHOOT, 0);
					} else {
						fireball = data.caster().getWorld().spawn(loc, Fireball.class);
						data.caster().getWorld().playEffect(data.caster().getLocation(), Effect.GHAST_SHOOT, 0);
						fireballs.put(fireball, new CastData(data.power(), data.args()));
					}
		
					fireball.setShooter(data.caster());
					fireball.setGravity(Boolean.TRUE.equals(fireballGravity.get(data.caster(), target, data.power(), data.args())));
		
					SpellData effectData = new SpellData(data.caster(), loc, data.power(), data.args());
		playSpellEffects(EffectPosition.CASTER, data.caster(), effectData);
		playSpellEffects(EffectPosition.PROJECTILE, fireball, effectData);

		if (target != null) {
			sendMessages(data.caster(), target, data.args());
			return new CastResult(PostCastAction.NO_MESSAGES, data);
		}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	@Override
	public CastResult castAtEntityFromLocation(SpellData data) {
		if (data.caster() == null ? !validTargetList.canTarget(data.target())
				: !validTargetList.canTarget(data.caster(), data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		Location from = offsetLocation(data.location(), data.caster(), data.target(), data.power(), data.args());
		Vector facing = data.target().getLocation().toVector().subtract(from.toVector()).normalize();
		Location loc = from.clone();
		Util.setLocationFacingFromVector(loc, facing);
		loc.add(facing.multiply(2));

		Fireball fireball = from.getWorld().spawn(loc, Fireball.class);
		fireball.setGravity(Boolean.TRUE.equals(fireballGravity.get(data.caster(), data.target(), data.power(), data.args())));
		if (data.caster() != null)
			fireball.setShooter(data.caster());
		fireballs.put(fireball, new CastData(data.power(), data.args()));

		SpellData effectData = new SpellData(data.caster(), data.target(), loc, data.power(), data.args());

		if (data.caster() != null)
			playSpellEffects(EffectPosition.CASTER, data.caster(), effectData);
		else
			playSpellEffects(EffectPosition.CASTER, from, effectData);

		playSpellEffects(EffectPosition.PROJECTILE, fireball, effectData);
		playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE, from, fireball.getLocation(),
				data.caster(), fireball, effectData);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	private Location offsetLocation(Location loc, LivingEntity caster, LivingEntity target, float power, String[] args) {
		Location ret = loc;
		if (Boolean.TRUE.equals(useRelativeCastLocationOffset.get(caster, target, power, args)))
			ret = Util.applyRelativeOffset(ret, relativeCastLocationOffset);
		if (Boolean.TRUE.equals(useAbsoluteCastLocationOffset.get(caster, target, power, args)))
			ret = Util.applyAbsoluteOffset(ret, absoluteCastLocationOffset);
		return ret;
	}

	private Location applyOffsetTargetingCorrection(Location origin, Location targetLoc, LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (Boolean.TRUE.equals(doOffsetTargetingCorrections.get(caster, target, power, args)) && targetLoc != null)
			return Util.faceTarget(origin, targetLoc);
		return origin;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onExplosionPrime(ExplosionPrimeEvent event) {
		Entity entityRaw = event.getEntity();
		if (!(entityRaw instanceof Fireball fireball))
			return;
		if (!fireballs.containsKey(fireball))
			return;

		LivingEntity caster = fireball.getShooter() instanceof LivingEntity le ? le : null;
		CastData data = fireballs.get(fireball);

		playSpellEffects(EffectPosition.TARGET, fireball.getLocation(),
				new SpellData(caster, fireball.getLocation(), data.power(), data.args()));

		boolean noExplosion = Boolean.TRUE.equals(this.noExplosion.get(caster, null, data.power(), data.args()));
		boolean noFire = Boolean.TRUE.equals(this.noFire.get(caster, null, data.power(), data.args()));
		boolean noExplosionEffect = Boolean.TRUE.equals(this.noExplosionEffect.get(caster, null, data.power(), data.args()));

		if (noExplosion) {
			event.setCancelled(true);
			Location loc = fireball.getLocation();
			if (noExplosionEffect)
				loc.getWorld().createExplosion(loc, 0);

			double noExplosionDamageRange = this.noExplosionDamageRange.get(caster, null, data.power(), data.args());
			List<Entity> inRange = fireball.getNearbyEntities(noExplosionDamageRange, noExplosionDamageRange,
					noExplosionDamageRange);
			for (Entity entity : inRange) {
				if (!(entity instanceof LivingEntity target))
					continue;
				if (!validTargetList.canTarget(entity))
					continue;

				double noExplosionDamage = this.noExplosionDamage.get(caster, target, data.power(), data.args());
				if (Boolean.TRUE.equals(powerAffectsNoExplosionDamage.get(caster, target, data.power(), data.args())))
					noExplosionDamage *= data.power();

				target.damage(noExplosionDamage, caster);
			}

			if (!noFire) {
				Set<AlteredBlockManager.Change> fires = new HashSet<>();
				for (int x = loc.getBlockX() - 1; x <= loc.getBlockX() + 1; x++) {
					for (int y = loc.getBlockY() - 1; y <= loc.getBlockY() + 1; y++) {
						for (int z = loc.getBlockZ() - 1; z <= loc.getBlockZ() + 1; z++) {
							if (!BlockUtils.isAir(loc.getWorld().getBlockAt(x, y, z).getType()))
								continue;
							Block b = loc.getWorld().getBlockAt(x, y, z);
							fires.add(MagicSpells.getAlteredBlockManager().apply(internalName, b, Material.FIRE, false));
						}
					}
				}
				fireball.remove();
				if (!fires.isEmpty()) {
					MagicSpells.scheduleDelayedTask(() -> {
						for (AlteredBlockManager.Change change : fires) {
							if (change.block().getType() == Material.FIRE) change.undo(false);
						}
					}, TimeUtil.TICKS_PER_SECOND);
				}
			}
		} else {
			event.setFire(!noFire);

			float explosionSize = this.explosionSize.get(caster, null, data.power(), data.args());
			if (explosionSize > 0)
				event.setRadius(explosionSize);
		}

		if (noExplosion)
			fireballs.remove(fireball);
		else
			MagicSpells.scheduleDelayedTask(() -> fireballs.remove(fireball), 1);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityDamage(EntityDamageEvent event) {
		Entity entity = event.getEntity();
		if (!(entity instanceof LivingEntity target))
			return;
		if (!(event instanceof EntityDamageByEntityEvent evt))
			return;
		if (event.getCause() != DamageCause.ENTITY_EXPLOSION && event.getCause() != DamageCause.PROJECTILE)
			return;

		Entity damager = evt.getDamager();
		if (!(damager instanceof Fireball fireball) || !fireballs.containsKey(fireball))
			return;

		ProjectileSource shooter = fireball.getShooter();
		if (!(shooter instanceof LivingEntity caster))
			return;

		CastData castData = fireballs.get(fireball);
		SpellData data = new SpellData(caster, target, castData.power(), castData.args());

		if (!validTargetList.canTarget(caster, target))
			event.setCancelled(true);
		else {
			double damageMultiplier = this.damageMultiplier.get(data);
			if (damageMultiplier > 0) {
				if (Boolean.TRUE.equals(powerAffectsDamageMultiplier.get(data)))
					damageMultiplier *= castData.power();
				event.setDamage(event.getDamage() * damageMultiplier);
			}
		}
	}

}
