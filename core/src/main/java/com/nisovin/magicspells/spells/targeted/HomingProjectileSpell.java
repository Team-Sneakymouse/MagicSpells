package com.nisovin.magicspells.spells.targeted;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import com.nisovin.magicspells.util.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import net.kyori.adventure.text.Component;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.zones.NoMagicZoneManager;
import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.util.projectile.ProjectileManager;
import com.nisovin.magicspells.util.projectile.ProjectileManagers;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;

public class HomingProjectileSpell extends TargetedSpell
		implements TargetedEntitySpell, TargetedEntityFromLocationSpell {

	private HomingProjectileSpell thisSpell;

	private NoMagicZoneManager zoneManager;

	private List<HomingProjectileMonitor> monitors;

	private ProjectileManager projectileManager;

	private Vector relativeOffset;
	private Vector targetRelativeOffset;

	private ConfigData<Integer> tickInterval;
	private ConfigData<Integer> airSpellInterval;
	private ConfigData<Integer> specialEffectInterval;
	private ConfigData<Integer> intermediateSpecialEffects;

	private ConfigData<Float> velocity;
	private ConfigData<Float> hitRadius;
	private ConfigData<Float> verticalHitRadius;

	private boolean stopOnModifierFail;
	private ConfigData<Boolean> powerAffectsVelocity;

	private ConfigData<Double> maxDuration;

	private String hitSpellName;
	private String airSpellName;
	private String groundSpellName;
	private String modifierSpellName;
	private String durationSpellName;

	private Component projectileName;

	private Subspell hitSpell;
	private Subspell airSpell;
	private Subspell groundSpell;
	private Subspell modifierSpell;
	private Subspell durationSpell;

	private ModifierSet homingModifiers;
	private List<String> homingModifiersStrings;

	public HomingProjectileSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		thisSpell = this;

		monitors = new ArrayList<>();

		projectileManager = ProjectileManagers.getManager(getConfigString("projectile-type", "arrow"));

		relativeOffset = getConfigVector("relative-offset", "0.5,0.5,0");
		targetRelativeOffset = getConfigVector("target-relative-offset", "0,0.5,0");

		tickInterval = getConfigDataInt("tick-interval", 1);
		airSpellInterval = getConfigDataInt("spell-interval", 20);
		specialEffectInterval = getConfigDataInt("special-effect-interval", 0);
		intermediateSpecialEffects = getConfigDataInt("intermediate-special-effect-locations", 0);

		velocity = getConfigDataFloat("velocity", 1F);
		hitRadius = getConfigDataFloat("hit-radius", 2F);
		verticalHitRadius = getConfigDataFloat("vertical-hit-radius", 2F);

		stopOnModifierFail = getConfigBoolean("stop-on-modifier-fail", true);
		powerAffectsVelocity = getConfigDataBoolean("power-affects-velocity", true);

		maxDuration = getConfigDataDouble("max-duration", 10);

		hitSpellName = getConfigString("spell", "");
		airSpellName = getConfigString("spell-on-hit-air", "");
		projectileName = Util.getMiniMessage(getConfigString("projectile-name", ""));
		groundSpellName = getConfigString("spell-on-hit-ground", "");
		modifierSpellName = getConfigString("spell-on-modifier-fail", "");
		durationSpellName = getConfigString("spell-after-duration", "");

		homingModifiersStrings = getConfigStringList("homing-modifiers", null);
	}

	@Override
	public void initializeModifiers() {
		super.initializeModifiers();

		if (homingModifiersStrings != null && !homingModifiersStrings.isEmpty()) {
			homingModifiers = new ModifierSet(homingModifiersStrings, this);
			homingModifiersStrings = null;
		}
	}

	@Override
	public void initialize() {
		super.initialize();

		hitSpell = new Subspell(hitSpellName);
		if (!hitSpell.process()) {
			hitSpell = null;
			if (!hitSpellName.isEmpty())
				MagicSpells.error("HomingMissileSpell '" + internalName + "' has an invalid spell defined!");
		}

		groundSpell = new Subspell(groundSpellName);
		if (!groundSpell.process()) {
			groundSpell = null;
			if (!groundSpellName.isEmpty())
				MagicSpells
						.error("HomingMissileSpell '" + internalName + "' has an invalid spell-on-hit-ground defined!");
		}

		airSpell = new Subspell(airSpellName);
		if (!airSpell.process()) {
			airSpell = null;
			if (!airSpellName.isEmpty())
				MagicSpells.error("HomingMissileSpell '" + internalName + "' has an invalid spell-on-hit-air defined!");
		}

		durationSpell = new Subspell(durationSpellName);
		if (!durationSpell.process()) {
			durationSpell = null;
			if (!durationSpellName.isEmpty())
				MagicSpells.error(
						"HomingMissileSpell '" + internalName + "' has an invalid spell-after-duration defined!");
		}

		modifierSpell = new Subspell(modifierSpellName);
		if (!modifierSpell.process()) {
			if (!modifierSpellName.isEmpty())
				MagicSpells.error(
						"HomingMissileSpell '" + internalName + "' has an invalid spell-on-modifier-fail defined!");
			modifierSpell = null;
		}

		zoneManager = MagicSpells.getNoMagicZoneManager();
	}

	@Override
	public void turnOff() {
		for (HomingProjectileMonitor monitor : monitors) {
			monitor.stop();
		}

		monitors.clear();
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> targetInfo = getTargetedEntity(data.caster(), data.power(), data.args());
		if (targetInfo.noTarget())
			return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);

		data = data.target(targetInfo.target()).power(targetInfo.power());
		CastResult result = castAtEntity(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return result;

		sendMessages(data.caster(), data.target(), data.args());
		return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

	@Override
	public CastResult castAtEntity(SpellData data) {
		if (!validTargetList.canTarget(data.caster(), data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		new HomingProjectileMonitor(data.caster(), data.target(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public CastResult castAtEntityFromLocation(SpellData data) {
		if (!validTargetList.canTarget(data.caster(), data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		new HomingProjectileMonitor(data.caster(), data.location(), data.target(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power,
			String[] args) {
		return castAtEntityFromLocation(new SpellData(caster, target, from, power, args)).action()
				!= PostCastAction.ALREADY_HANDLED;
	}

	@EventHandler
	public void onProjectileHit(EntityDamageByEntityEvent event) {
		if (event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE)
			return;
		if (!(event.getEntity() instanceof LivingEntity entity))
			return;
		Entity damagerEntity = event.getDamager();
		if (!(damagerEntity instanceof Projectile projectile))
			return;

		for (HomingProjectileMonitor monitor : monitors) {
			if (monitor.projectile == null)
				continue;
			if (!monitor.projectile.equals(projectile))
				continue;
			if (monitor.target == null)
				continue;
			if (!monitor.target.equals(entity))
				continue;

			if (hitSpell != null)
				hitSpell.subcast(monitor.data.target(entity));
			playSpellEffects(EffectPosition.TARGET, entity, monitor.data);
			event.setCancelled(true);

			monitor.stop();
			break;
		}
	}

	@EventHandler
	public void onProjectileBlockHit(ProjectileHitEvent e) {
		Projectile projectile = e.getEntity();
		Block block = e.getHitBlock();
		if (block == null)
			return;
		for (HomingProjectileMonitor monitor : monitors) {
			if (monitor.projectile == null)
				continue;
			if (!monitor.projectile.equals(projectile))
				continue;
			if (monitor.caster == null)
				continue;
			if (groundSpell != null)
				groundSpell.subcast(monitor.data.location(projectile.getLocation()));
			monitor.stop();
		}

	}

	private class HomingProjectileMonitor implements Runnable {

		private Projectile projectile;
		private Location currentLocation;
		private Location previousLocation;
		private Location startLocation;
		private LivingEntity caster;
		private LivingEntity target;
		private BoundingBox hitBox;
		private Vector currentVelocity;
		private SpellData data;
		private String[] args;
		private float power;
		private long startTime;

		private int airSpellInterval;
		private int specialEffectInterval;
		private int intermediateSpecialEffects;

		private float velocity;

		private double maxDuration;

		private int taskId;
		private int counter = 0;

		private HomingProjectileMonitor(LivingEntity caster, LivingEntity target, float power, String[] args) {
			this.caster = caster;
			this.target = target;
			this.power = power;
			startLocation = caster.getLocation();

			initialize(caster, target, power, args);
		}

		private HomingProjectileMonitor(LivingEntity caster, Location startLocation, LivingEntity target, float power,
				String[] args) {
			this.caster = caster;
			this.target = target;
			this.power = power;
			this.args = args;
			this.startLocation = startLocation;

			initialize(caster, target, power, args);
		}

		private void initialize(LivingEntity caster, LivingEntity target, float power, String[] args) {
			startTime = System.currentTimeMillis();

			data = new SpellData(caster, target, power, args);

			Vector startDir = startLocation.clone().getDirection().normalize();
			Vector horizOffset = new Vector(-startDir.getZ(), 0.0, startDir.getX()).normalize();
			startLocation.add(horizOffset.multiply(relativeOffset.getZ())).getBlock().getLocation();
			startLocation.add(startLocation.getDirection().multiply(relativeOffset.getX()));
			startLocation.setY(startLocation.getY() + relativeOffset.getY());

			airSpellInterval = HomingProjectileSpell.this.airSpellInterval.get(caster, target, power, args);
			specialEffectInterval = HomingProjectileSpell.this.specialEffectInterval.get(caster, target, power, args);

			intermediateSpecialEffects = HomingProjectileSpell.this.intermediateSpecialEffects.get(caster, target,
					power, args);
			if (intermediateSpecialEffects < 0)
				intermediateSpecialEffects = 0;

			velocity = HomingProjectileSpell.this.velocity.get(caster, target, power, args);
			if (Boolean.TRUE.equals(powerAffectsVelocity.get(caster, target, power, args)))
				velocity *= power;

			maxDuration = HomingProjectileSpell.this.maxDuration.get(caster, target, power, args)
					* TimeUtil.MILLISECONDS_PER_SECOND;

			float hitRadius = HomingProjectileSpell.this.hitRadius.get(caster, target, power, args);
			float verticalHitRadius = HomingProjectileSpell.this.verticalHitRadius.get(caster, target, power, args);
			hitBox = new BoundingBox(startLocation, hitRadius, verticalHitRadius);

			playSpellEffects(EffectPosition.CASTER, startLocation, data);

			projectile = startLocation.getWorld().spawn(startLocation, projectileManager.getProjectileClass());

			currentLocation = startLocation.clone();

			if (projectileName != null) {
				projectile.customName(projectileName);
				projectile.setCustomNameVisible(true);
			}

			currentVelocity = target.getLocation().add(0, 0.75, 0).toVector()
					.subtract(projectile.getLocation().toVector()).normalize();
			currentVelocity.multiply(velocity);
			currentVelocity.setY(currentVelocity.getY() + 0.15);
			projectile.setVelocity(currentVelocity);

			playSpellEffects(EffectPosition.PROJECTILE, projectile, data);
			playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE, startLocation,
					projectile.getLocation(), caster, projectile, data);
			monitors.add(this);

			int tickInterval = HomingProjectileSpell.this.tickInterval.get(caster, target, power, args);
			taskId = MagicSpells.scheduleRepeatingTask(this, 0, tickInterval);
		}

		@Override
		public void run() {
			if ((caster != null && !caster.isValid()) || !target.isValid()) {
				stop();
				return;
			}

			if (projectile == null || projectile.isDead()) {
				stop();
				return;
			}

			if (!projectile.getLocation().getWorld().equals(target.getWorld())) {
				stop();
				return;
			}

			if (zoneManager.willFizzle(currentLocation, thisSpell)) {
				stop();
				return;
			}

			if (homingModifiers != null) {
				ModifierResult result = homingModifiers.apply(data);
				data = result.data();
				power = data.power();
				args = data.args();

				if (!result.check()) {
					if (modifierSpell != null)
						modifierSpell.subcast(data.location(currentLocation));

					if (stopOnModifierFail)
						stop();
					return;
				}
			}

			if (maxDuration > 0 && startTime + maxDuration < System.currentTimeMillis()) {
				if (durationSpell != null)
					durationSpell.subcast(data.location(currentLocation));
				stop();
				return;
			}

			previousLocation = projectile.getLocation();

			Vector oldVelocity = new Vector(currentVelocity.getX(), currentVelocity.getY(), currentVelocity.getZ());

			Location targetLoc = target.getLocation().clone();
			Vector startDir = targetLoc.clone().getDirection().normalize();
			Vector horizOffset = new Vector(-startDir.getZ(), 0.0, startDir.getX()).normalize();
			targetLoc.add(horizOffset.multiply(targetRelativeOffset.getZ())).getBlock().getLocation();
			targetLoc.add(targetLoc.getDirection().multiply(targetRelativeOffset.getX()));
			targetLoc.setY(target.getLocation().getY() + targetRelativeOffset.getY());

			currentVelocity = targetLoc.toVector().subtract(projectile.getLocation().toVector()).normalize();
			currentVelocity.multiply(velocity);
			currentVelocity.setY(currentVelocity.getY() + 0.15);
			projectile.setVelocity(currentVelocity);
			currentLocation = projectile.getLocation();

			if (counter % airSpellInterval == 0 && airSpell != null)
				airSpell.subcast(data.location(currentLocation));

			if (intermediateSpecialEffects > 0)
				playIntermediateEffectLocations(previousLocation, oldVelocity);

			if (specialEffectInterval > 0 && counter % specialEffectInterval == 0)
				playSpellEffects(EffectPosition.SPECIAL, currentLocation, data);

			counter++;

			hitBox.setCenter(currentLocation);
			if (hitBox.contains(targetLoc)) {
				SpellTargetEvent targetEvent = new SpellTargetEvent(thisSpell, data);
				if (!targetEvent.callEvent())
					return;

				LivingEntity subTarget = targetEvent.getTarget();
				float subPower = targetEvent.getPower();

				SpellData hitData = data.target(subTarget).power(subPower).location(targetLoc);
				playSpellEffects(EffectPosition.TARGET, subTarget, hitData);
				if (hitSpell != null)
					hitSpell.subcast(hitData);
				stop();
			}
		}

		private void playIntermediateEffectLocations(Location old, Vector movement) {
			int divideFactor = intermediateSpecialEffects + 1;
			movement.setX(movement.getX() / divideFactor);
			movement.setY(movement.getY() / divideFactor);
			movement.setZ(movement.getZ() / divideFactor);
			for (int i = 0; i < intermediateSpecialEffects; i++) {
				old = old.add(movement).setDirection(movement);
				playSpellEffects(EffectPosition.SPECIAL, old, data);
			}
		}

		private void stop() {
			playSpellEffects(EffectPosition.DELAYED, currentLocation, data);
			MagicSpells.cancelTask(taskId);
			caster = null;
			target = null;
			currentLocation = null;
			if (projectile != null)
				projectile.remove();
			projectile = null;
		}

	}

}
