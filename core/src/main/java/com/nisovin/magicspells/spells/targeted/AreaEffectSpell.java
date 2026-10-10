package com.nisovin.magicspells.spells.targeted;

import java.util.*;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.NumberConversions;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;

import org.apache.commons.math4.core.jdkmath.AccurateMath;

public class AreaEffectSpell extends TargetedSpell implements TargetedLocationSpell {

	private List<Subspell> spells;
	private List<String> spellNames;

	private ConfigData<Integer> maxTargets;

	private ConfigData<Double> cone;
	private ConfigData<Double> horizontalCone;
	private ConfigData<Double> vRadius;
	private ConfigData<Double> hRadius;

	private ConfigData<Boolean> pointBlank;
	private ConfigData<Boolean> circleShape;
	private ConfigData<Boolean> useProximity;
	private ConfigData<Boolean> passTargeting;
	private ConfigData<Boolean> failIfNoTargets;
	private ConfigData<Boolean> reverseProximity;
	private ConfigData<Boolean> spellSourceInCenter;

	public AreaEffectSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		spellNames = getConfigStringList("spells", null);

		maxTargets = getConfigDataInt("max-targets", 0);

		cone = getConfigDataDouble("cone", 0);
		horizontalCone = getConfigDataDouble("horizontal-cone", 0);

		vRadius = getConfigDataDouble("vertical-radius", 5);
		hRadius = getConfigDataDouble("horizontal-radius", 10);

		pointBlank = getConfigDataBoolean("point-blank", true);
		circleShape = getConfigDataBoolean("circle-shape", false);
		useProximity = getConfigDataBoolean("use-proximity", false);
		passTargeting = getConfigDataBoolean("pass-targeting", true);
		failIfNoTargets = getConfigDataBoolean("fail-if-no-targets", true);
		reverseProximity = getConfigDataBoolean("reverse-proximity", false);
		spellSourceInCenter = getConfigDataBoolean("spell-source-in-center", false);
	}

	@Override
	public void initialize() {
		super.initialize();

		spells = new ArrayList<>();

		if (spellNames == null || spellNames.isEmpty()) {
			MagicSpells.error("AreaEffectSpell '" + internalName + "' has no spells defined!");
			return;
		}

		for (String spellName : spellNames) {
			Subspell spell = new Subspell(spellName);

			if (!spell.process()) {
				MagicSpells.error(
						"AreaEffectSpell '" + internalName + "' attempted to use invalid spell '" + spellName + '\'');
				continue;
			}

			spells.add(spell);
		}

		spellNames.clear();
		spellNames = null;
	}

	@Override
	public CastResult cast(SpellData data) {
		Location loc = null;
		if (Boolean.TRUE.equals(pointBlank.get(data)))
			loc = data.caster().getLocation();
		else {
			try {
				Block block = getTargetedBlock(data.caster(), data.power(), data.args());
				if (block != null && !BlockUtils.isAir(block.getType()))
					loc = block.getLocation().add(0.5, 0, 0.5);
			} catch (IllegalStateException ignored) {
			}
		}

		if (loc == null)
			return new CastResult(noTarget(data.caster(), data.args()), data);

		SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, data.location(loc));
		EventUtil.call(event);
		if (event.isCancelled())
			return new CastResult(noTarget(data.caster(), data.args()), data);

		data = event.getSpellData();
		if (!doAoe(data))
			return new CastResult(noTarget(data.caster(), data.args()), data);

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
		if (!doAoe(data))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		return castAtLocation(new SpellData(null, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	private boolean doAoe(SpellData data) {
		LivingEntity caster = data.caster();
		Location location = data.location();
		float basePower = data.power();
		String[] args = data.args();
		int count = 0;

		location = Util.makeFinite(location);

		int maxTargets = this.maxTargets.get(caster, null, basePower, args);

		double cone = this.cone.get(caster, null, basePower, args);
		double horizontalCone = this.horizontalCone.get(caster, null, basePower, args);

		double vRadius = Math.min(this.vRadius.get(caster, null, basePower, args), MagicSpells.getGlobalRadius());
		double hRadius = Math.min(this.hRadius.get(caster, null, basePower, args), MagicSpells.getGlobalRadius());

		double vRadiusSquared = vRadius * vRadius;
		double hRadiusSquared = hRadius * hRadius;

		boolean circleShape = Boolean.TRUE.equals(this.circleShape.get(caster, null, basePower, args));
		boolean useProximity = Boolean.TRUE.equals(this.useProximity.get(caster, null, basePower, args));
		boolean passTargeting = Boolean.TRUE.equals(this.passTargeting.get(caster, null, basePower, args));
		boolean failIfNoTargets = Boolean.TRUE.equals(this.failIfNoTargets.get(caster, null, basePower, args));
		boolean reverseProximity = Boolean.TRUE.equals(this.reverseProximity.get(caster, null, basePower, args));
		boolean spellSourceInCenter = Boolean.TRUE.equals(this.spellSourceInCenter.get(caster, null, basePower, args));

		SpellTargetEvent event;
		SpellData effectData;

		float power;

		double hDistance;
		double vDistance;

		if (validTargetList.canTargetOnlyCaster()) {
			if (caster == null)
				return false;

			LivingEntity target = caster;
			power = basePower;

			if (!target.getWorld().equals(location.getWorld()))
				return false;

			hDistance = NumberConversions.square(target.getLocation().getX() - location.getX())
					+ NumberConversions.square(target.getLocation().getZ() - location.getZ());
			if (hDistance > hRadiusSquared)
				return false;
			vDistance = NumberConversions.square(target.getLocation().getY() - location.getY());
			if (vDistance > vRadiusSquared)
				return false;

			event = new SpellTargetEvent(this, new SpellData(caster, target, power, args));
			EventUtil.call(event);
			if (event.isCancelled())
				return false;

			target = event.getTarget();
			power = event.getPower();

			castSpells(caster, location, target, power, args, spellSourceInCenter, passTargeting);

			effectData = new SpellData(caster, target, location, power, args);

			playSpellEffects(EffectPosition.TARGET, target, effectData);
			playSpellEffects(EffectPosition.SPECIAL, location, effectData);
			if (spellSourceInCenter)
				playSpellEffects(caster, location, target, effectData);
			else
				playSpellEffectsTrail(caster.getLocation(), target.getLocation(), effectData);

			return true;
		}

		List<LivingEntity> entities = new ArrayList<>(
				location.getWorld().getNearbyLivingEntities(location, hRadius, vRadius, hRadius));

		if (useProximity) {
			// check world before distance
			for (LivingEntity entity : new ArrayList<>(entities)) {
				if (entity.getWorld().equals(location.getWorld()))
					continue;
				entities.remove(entity);
			}
			Location finalLocation = location;
			Comparator<LivingEntity> comparator = Comparator
					.comparingDouble(entity -> entity.getLocation().distanceSquared(finalLocation));
			if (reverseProximity)
				comparator = comparator.reversed();
			entities.sort(comparator);
		}

		for (LivingEntity target : entities) {
			if (target.isDead())
				continue;
			if (!validTargetList.canTarget(caster, target))
				continue;

			if (circleShape) {
				hDistance = NumberConversions.square(target.getLocation().getX() - location.getX())
						+ NumberConversions.square(target.getLocation().getZ() - location.getZ());
				if (hDistance > hRadiusSquared)
					continue;
				vDistance = NumberConversions.square(target.getLocation().getY() - location.getY());
				if (vDistance > vRadiusSquared)
					continue;
			}

			if (horizontalCone > 0 && horizontalAngle(location, target.getLocation()) > horizontalCone)
				continue;

			if (cone > 0) {
				Vector dir = target.getLocation().toVector().subtract(location.toVector());
				if (AccurateMath.toDegrees(AccurateMath.abs(dir.angle(location.getDirection()))) > cone)
					continue;
			}

			power = basePower;

			event = new SpellTargetEvent(this, new SpellData(caster, target, power, args));
			EventUtil.call(event);
			if (event.isCancelled())
				continue;

			target = event.getTarget();
			power = event.getPower();

			castSpells(caster, location, target, power, args, spellSourceInCenter, passTargeting);

			effectData = new SpellData(caster, target, location, power, args);
			playSpellEffects(EffectPosition.TARGET, target, effectData);

			if (spellSourceInCenter)
				playSpellEffects(caster, location, target, effectData);
			else if (caster != null)
				playSpellEffectsTrail(caster.getLocation(), target.getLocation(), effectData);

			count++;

			if (maxTargets > 0 && count >= maxTargets)
				break;
		}

		boolean success = count > 0 || !failIfNoTargets;
		if (success) {
			effectData = new SpellData(caster, location, basePower, args);
			playSpellEffects(EffectPosition.SPECIAL, location, effectData);
			if (caster != null)
				playSpellEffects(EffectPosition.CASTER, caster, effectData);
		}

		return success;
	}

	private void castSpells(LivingEntity caster, Location location, LivingEntity target, float power, String[] args,
			boolean spellSourceInCenter, boolean passTargeting) {
		Location source = spellSourceInCenter ? location : (caster == null ? null : caster.getLocation());
		for (Subspell spell : spells) {
			if (source != null)
				spell.subcast(new SpellData(caster, target, source, power, args), passTargeting);
			else
				spell.subcast(new SpellData(caster, target, power, args), passTargeting);
		}
	}

	private double horizontalAngle(Location from, Location to) {
		Location startLoc = from.clone();
		Location endLoc = to.clone();

		startLoc.setY(0.0D);
		startLoc.setPitch(0.0F);

		endLoc.setY(0.0D);
		endLoc.setPitch(0.0F);

		Vector direction = endLoc.toVector().subtract(startLoc.toVector()).normalize();

		return AccurateMath.toDegrees(direction.angle(startLoc.getDirection()));
	}

}
