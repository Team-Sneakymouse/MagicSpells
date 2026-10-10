package com.nisovin.magicspells.util.trackers;

import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.*;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;

import de.slikey.effectlib.Effect;
import de.slikey.effectlib.effect.ModifiedEffect;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.util.ValidTargetList;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.events.TrackerMoveEvent;
import com.nisovin.magicspells.zones.NoMagicZoneManager;
import com.nisovin.magicspells.spelleffects.SpellEffect;
import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.instant.ProjectileSpell;
import com.nisovin.magicspells.util.projectile.ProjectileManager;
import com.nisovin.magicspells.spelleffects.util.EffectlibSpellEffect;

public class ProjectileTracker implements Runnable, Tracker {

	private final Random rand = ThreadLocalRandom.current();

	private Set<EffectlibSpellEffect> effectSet;
	private Map<SpellEffect, Entity> entityMap;

	private ProjectileSpell spell;

	private NoMagicZoneManager zoneManager;

	private ProjectileManager projectileManager;

	private Vector relativeOffset;
	private Vector effectOffset;

	private int tickInterval;
	private int tickSpellInterval;
	private int specialEffectInterval;
	private int intermediateEffects;
	private int intermediateHitboxes;

	private float rotation;
	private float velocity;
	private float hitRadius;
	private float vertSpread;
	private float horizSpread;
	private float verticalHitRadius;

	private boolean visible;
	private boolean gravity;
	private boolean charged;
	private boolean incendiary;
	private boolean callEvents;
	private boolean stopOnModifierFail;

	private double maxDuration;

	private Component projectileName;

	private Subspell hitSpell;
	private Subspell tickSpell;
	private Subspell groundSpell;
	private Subspell modifierSpell;
	private Subspell durationSpell;
	private Subspell entityLocationSpell;

	private ModifierSet projectileModifiers;

	private Projectile projectile;
	private Location previousLocation;
	private Location currentLocation;
	private Location startLocation;
	private LivingEntity caster;
	private Vector currentVelocity;
	private SpellData spellData;
	private String[] args;
	private float power;
	private long startTime;

	private ValidTargetList targetList;

	private int taskId;
	private int counter = 0;

	private boolean stopped = false;

	public ProjectileTracker(LivingEntity caster, Location startLocation, float power, String[] args) {
		this.caster = caster;
		this.power = power;
		this.args = args;
		this.startLocation = startLocation;

		spellData = new SpellData(caster, startLocation, power, args);
	}

	public void start() {
		initialize();
	}

	@Override
	public void initialize() {
		zoneManager = MagicSpells.getNoMagicZoneManager();
		startTime = System.currentTimeMillis();
		taskId = MagicSpells.scheduleRepeatingTask(this, 0, tickInterval);

		Vector startDir = startLocation.clone().getDirection().normalize();
		Vector horizOffset = new Vector(-startDir.getZ(), 0D, startDir.getX()).normalize();
		startLocation.add(horizOffset.multiply(relativeOffset.getZ())).getBlock().getLocation();
		startLocation.add(startLocation.getDirection().multiply(relativeOffset.getX()));
		startLocation.setY(startLocation.getY() + relativeOffset.getY());

		currentLocation = startLocation.clone();

		projectile = startLocation.getWorld().spawn(startLocation, projectileManager.getProjectileClass());
		currentVelocity = startLocation.getDirection();
		currentVelocity.multiply(velocity * power);
		if (rotation != 0)
			Util.rotateVector(currentVelocity, rotation);
		if (horizSpread > 0 || vertSpread > 0) {
			float rx = -1 + rand.nextFloat() * 2;
			float ry = -1 + rand.nextFloat() * 2;
			float rz = -1 + rand.nextFloat() * 2;
			currentVelocity.add(new Vector(rx * horizSpread, ry * vertSpread, rz * horizSpread));
		}

		projectile.setVisibleByDefault(visible);
		projectile.setVelocity(currentVelocity);
		projectile.setGravity(gravity);
		projectile.setShooter(caster);
		if (projectileName != null && !Util.getPlainString(projectileName).isEmpty()) {
			projectile.customName(projectileName);
			projectile.setCustomNameVisible(true);
		}
		if (projectile instanceof WitherSkull witherSkull)
			witherSkull.setCharged(charged);
		if (projectile instanceof Explosive explosive)
			explosive.setIsIncendiary(incendiary);

		if (spell != null) {
			spell.playEffects(EffectPosition.CASTER, startLocation, spellData);
			effectSet = spell.playEffectsProjectile(EffectPosition.PROJECTILE, currentLocation, spellData);
			entityMap = spell.playEntityEffectsProjectile(EffectPosition.PROJECTILE, currentLocation, spellData);
			spell.playTrackingLinePatterns(EffectPosition.DYNAMIC_CASTER_PROJECTILE_LINE, startLocation,
					projectile.getLocation(), caster, projectile, spellData);
		}
		ProjectileSpell.getProjectileTrackers().add(this);
	}

	@Override
	public void run() {
		if ((caster != null && !caster.isValid())) {
			stop();
			return;
		}

		if (projectile == null || projectile.isDead()) {
			stop();
			return;
		}

		if (zoneManager.willFizzle(currentLocation, spell)) {
			stop();
			return;
		}

		if (projectileModifiers != null) {
			ModifierResult result = projectileModifiers.apply(spellData);
			spellData = result.data();
			power = spellData.power();
			args = spellData.args();

			if (!result.check()) {
				if (modifierSpell != null)
					modifierSpell.subcast(spellData.location(currentLocation));
				if (stopOnModifierFail)
					stop();
				return;
			}
		}

		if (maxDuration > 0 && startTime + maxDuration < System.currentTimeMillis()) {
			if (durationSpell != null)
				durationSpell.subcast(spellData.location(currentLocation));
			stop();
			return;
		}

		previousLocation = currentLocation.clone();
		currentLocation = projectile.getLocation().clone();
		currentLocation.setDirection(projectile.getVelocity());

		if (callEvents) {
			TrackerMoveEvent trackerMoveEvent = new TrackerMoveEvent(this, previousLocation, currentLocation);
			EventUtil.call(trackerMoveEvent);
			if (stopped)
				return;
		}

		if (counter % tickSpellInterval == 0 && tickSpell != null)
			tickSpell.subcast(spellData.location(currentLocation));

		if (spell != null) {
			if (specialEffectInterval > 0 && counter % specialEffectInterval == 0)
				spell.playEffects(EffectPosition.SPECIAL, currentLocation, spellData);
			if (intermediateEffects > 0)
				playIntermediateEffects(previousLocation, currentVelocity);
		}

		if (effectSet != null) {
			Effect effect;
			Location effectLoc;
			for (EffectlibSpellEffect spellEffect : effectSet) {
				if (spellEffect == null)
					continue;
				effect = spellEffect.getEffect();
				if (effect == null)
					continue;

				effectLoc = spellEffect.getSpellEffect().applyOffsets(currentLocation.clone(), spellData);
				effect.setLocation(effectLoc);

				if (effect instanceof ModifiedEffect mod) {
					Effect modifiedEffect = mod.getInnerEffect();
					if (modifiedEffect != null)
						modifiedEffect.setLocation(effectLoc);
				}
			}
		}

		if (entityMap != null) {
			// Changing the effect location
			Vector dir = currentLocation.getDirection().normalize();
			Vector horizOffset = new Vector(-dir.getZ(), 0.0, dir.getX()).normalize();
			Location effectLoc = currentLocation.clone();
			effectLoc.add(horizOffset.multiply(effectOffset.getZ()));
			effectLoc.add(effectLoc.getDirection().multiply(effectOffset.getX()));
			effectLoc.setY(effectLoc.getY() + effectOffset.getY());

			effectLoc = Util.makeFinite(effectLoc);

			for (var entry : entityMap.entrySet()) {
				entry.getValue().teleportAsync(entry.getKey().applyOffsets(effectLoc.clone()));
			}
		}

		counter++;

		if (intermediateHitboxes > 0)
			checkIntermediateHitboxes(previousLocation, currentVelocity);
		checkHitbox(currentLocation);
	}

	public void playIntermediateEffects(Location old, Vector movement) {
		if (old == null)
			return;
		int divideFactor = intermediateEffects + 1;
		Vector v = movement.clone();

		v.setX(v.getX() / divideFactor);
		v.setY(v.getY() / divideFactor);
		v.setZ(v.getZ() / divideFactor);

		for (int i = 0; i < intermediateEffects; i++) {
			old = old.add(v).setDirection(v);
			if (specialEffectInterval > 0 && counter % specialEffectInterval == 0)
				spell.playEffects(EffectPosition.SPECIAL, old, spellData);
		}
	}

	public void checkIntermediateHitboxes(Location old, Vector movement) {
		if (old == null)
			return;
		int divideFactor = intermediateHitboxes + 1;
		Vector v = movement.clone();

		v.setX(v.getX() / divideFactor);
		v.setY(v.getY() / divideFactor);
		v.setZ(v.getZ() / divideFactor);

		for (int i = 0; i < intermediateHitboxes; i++) {
			old = old.add(v).setDirection(v);
			checkHitbox(old);
		}
	}

	public void checkHitbox(Location location) {
		if (location == null)
			return;
		if (caster == null)
			return;
		for (LivingEntity entity : projectile.getLocation().getNearbyLivingEntities(hitRadius, verticalHitRadius,
				hitRadius)) {
			if (!targetList.canTarget(caster, entity))
				continue;

			SpellTargetEvent event = new SpellTargetEvent(spell, spellData.target(entity));
			if (!event.callEvent())
				continue;

			SpellData hitData = spellData.target(event.getTarget()).power(event.getPower());
			if (hitSpell != null)
				hitSpell.subcast(hitData);
			if (entityLocationSpell != null)
				entityLocationSpell.subcast(hitData.location(currentLocation));

			stop();
			return;
		}
	}

	@Override
	public void stop() {
		stop(true);
	}

	public void stop(boolean removeTracker) {
		if (spell != null) {
			spell.playEffects(EffectPosition.DELAYED, currentLocation, spellData);
			if (removeTracker)
				ProjectileSpell.getProjectileTrackers().remove(this);
		}
		MagicSpells.cancelTask(taskId);
		if (effectSet != null) {
			for (EffectlibSpellEffect spellEffect : effectSet) {
				if (spellEffect == null)
					continue;
				if (spellEffect.getEffect() == null)
					continue;
				spellEffect.getEffect().cancel();
			}
			effectSet.clear();
		}
		if (entityMap != null) {
			for (Entity entity : entityMap.values()) {
				entity.remove();
			}
			entityMap.clear();
		}
		caster = null;
		currentLocation = null;
		if (projectile != null)
			projectile.remove();
		projectile = null;
		stopped = true;
	}

	public ProjectileSpell getSpell() {
		return spell;
	}

	public void setSpell(ProjectileSpell spell) {
		this.spell = spell;
	}

	public NoMagicZoneManager getZoneManager() {
		return zoneManager;
	}

	public void setZoneManager(NoMagicZoneManager zoneManager) {
		this.zoneManager = zoneManager;
	}

	public ProjectileManager getProjectileManager() {
		return projectileManager;
	}

	public void setProjectileManager(ProjectileManager projectileManager) {
		this.projectileManager = projectileManager;
	}

	public Vector getRelativeOffset() {
		return relativeOffset;
	}

	public void setRelativeOffset(Vector relativeOffset) {
		this.relativeOffset = relativeOffset;
	}

	public Vector getEffectOffset() {
		return effectOffset;
	}

	public void setEffectOffset(Vector effectOffset) {
		this.effectOffset = effectOffset;
	}

	public int getTickInterval() {
		return tickInterval;
	}

	public void setTickInterval(int tickInterval) {
		this.tickInterval = tickInterval;
	}

	public int getTickSpellInterval() {
		return tickSpellInterval;
	}

	public void setTickSpellInterval(int tickSpellInterval) {
		this.tickSpellInterval = tickSpellInterval;
	}

	public int getSpecialEffectInterval() {
		return specialEffectInterval;
	}

	public void setSpecialEffectInterval(int specialEffectInterval) {
		this.specialEffectInterval = specialEffectInterval;
	}

	public void setIntermediateEffects(int intermediateEffects) {
		this.intermediateEffects = intermediateEffects;
	}

	public int getIntermediateEffects() {
		return intermediateEffects;
	}

	public void setIntermediateHitboxes(int intermediateHitboxes) {
		this.intermediateHitboxes = intermediateHitboxes;
	}

	public int getIntermediateHitboxes() {
		return intermediateHitboxes;
	}

	public float getRotation() {
		return rotation;
	}

	public void setRotation(float rotation) {
		this.rotation = rotation;
	}

	public float getVelocity() {
		return velocity;
	}

	public void setVelocity(float velocity) {
		this.velocity = velocity;
	}

	public float getHitRadius() {
		return hitRadius;
	}

	public void setHitRadius(float hitRadius) {
		this.hitRadius = hitRadius;
	}

	public float getVertSpread() {
		return vertSpread;
	}

	public void setVertSpread(float vertSpread) {
		this.vertSpread = vertSpread;
	}

	public float getHorizSpread() {
		return horizSpread;
	}

	public void setHorizSpread(float horizSpread) {
		this.horizSpread = horizSpread;
	}

	public float getVerticalHitRadius() {
		return verticalHitRadius;
	}

	public void setVerticalHitRadius(float verticalHitRadius) {
		this.verticalHitRadius = verticalHitRadius;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public boolean hasGravity() {
		return gravity;
	}

	public void setGravity(boolean gravity) {
		this.gravity = gravity;
	}

	public boolean isCharged() {
		return charged;
	}

	public void setCharged(boolean charged) {
		this.charged = charged;
	}

	public boolean isIncendiary() {
		return incendiary;
	}

	public void setIncendiary(boolean incendiary) {
		this.incendiary = incendiary;
	}

	public boolean shouldStopOnModifierFail() {
		return stopOnModifierFail;
	}

	public void setStopOnModifierFail(boolean stopOnModifierFail) {
		this.stopOnModifierFail = stopOnModifierFail;
	}

	public double getMaxDuration() {
		return maxDuration;
	}

	public void setMaxDuration(double maxDuration) {
		this.maxDuration = maxDuration;
	}

	public Component getProjectileName() {
		return projectileName;
	}

	public void setProjectileName(Component projectileName) {
		this.projectileName = projectileName;
	}

	public Subspell getHitSpell() {
		return hitSpell;
	}

	public void setHitSpell(Subspell hitSpell) {
		this.hitSpell = hitSpell;
	}

	public Subspell getTickSpell() {
		return tickSpell;
	}

	public void setTickSpell(Subspell tickSpell) {
		this.tickSpell = tickSpell;
	}

	public Subspell getGroundSpell() {
		return groundSpell;
	}

	public void setGroundSpell(Subspell groundSpell) {
		this.groundSpell = groundSpell;
	}

	public Subspell getModifierSpell() {
		return modifierSpell;
	}

	public void setModifierSpell(Subspell modifierSpell) {
		this.modifierSpell = modifierSpell;
	}

	public Subspell getDurationSpell() {
		return durationSpell;
	}

	public void setDurationSpell(Subspell durationSpell) {
		this.durationSpell = durationSpell;
	}

	public Subspell getEntityLocationSpell() {
		return entityLocationSpell;
	}

	public void setEntityLocationSpell(Subspell entityLocationSpell) {
		this.entityLocationSpell = entityLocationSpell;
	}

	public ModifierSet getProjectileModifiers() {
		return projectileModifiers;
	}

	public void setProjectileModifiers(ModifierSet projectileModifiers) {
		this.projectileModifiers = projectileModifiers;
	}

	public Projectile getProjectile() {
		return projectile;
	}

	public void setProjectile(Projectile projectile) {
		this.projectile = projectile;
	}

	public Location getPreviousLocation() {
		return previousLocation;
	}

	public void setPreviousLocation(Location previousLocation) {
		this.previousLocation = previousLocation;
	}

	public Location getCurrentLocation() {
		return currentLocation;
	}

	public void setCurrentLocation(Location currentLocation) {
		this.currentLocation = currentLocation;
	}

	public Location getStartLocation() {
		return startLocation;
	}

	public void setStartLocation(Location startLocation) {
		this.startLocation = startLocation;
	}

	public LivingEntity getCaster() {
		return caster;
	}

	public void setCaster(LivingEntity caster) {
		this.caster = caster;
	}

	public Vector getCurrentVelocity() {
		return currentVelocity;
	}

	public void setCurrentVelocity(Vector currentVelocity) {
		this.currentVelocity = currentVelocity;
	}

	public float getPower() {
		return power;
	}

	public void setPower(float power) {
		this.power = power;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public ValidTargetList getTargetList() {
		return targetList;
	}

	public void setTargetList(ValidTargetList targetList) {
		this.targetList = targetList;
	}

	public boolean shouldCallEvents() {
		return callEvents;
	}

	public void setCallEvents(boolean callEvents) {
		this.callEvents = callEvents;
	}

	public String[] getArgs() {
		return args;
	}

	public void setArgs(String[] args) {
		this.args = args;
	}

	public SpellData getSpellData() {
		return spellData;
	}

	public void setSpellData(SpellData spellData) {
		this.spellData = spellData;
	}

}
