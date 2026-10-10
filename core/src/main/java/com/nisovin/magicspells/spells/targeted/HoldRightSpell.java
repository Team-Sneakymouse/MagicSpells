package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TimeUtil;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

import org.checkerframework.checker.units.qual.C;

public class HoldRightSpell extends TargetedSpell implements TargetedEntitySpell, TargetedLocationSpell {

	private ConfigData<Integer> resetTime;

	private ConfigData<Float> maxDuration;
	private ConfigData<Float> maxDistance;

	private boolean targetEntity;
	private boolean targetLocation;

	private Subspell spellToCast;
	private String spellToCastName;

	private Map<UUID, CastData> casting;

	public HoldRightSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		resetTime = getConfigDataInt("reset-time", 250);

		maxDuration = getConfigDataFloat("max-duration", 0F);
		maxDistance = getConfigDataFloat("max-distance", 0F);

		targetEntity = getConfigBoolean("target-entity", true);
		targetLocation = getConfigBoolean("target-location", false);

		spellToCastName = getConfigString("spell", "");

		casting = new HashMap<>();
	}

	@Override
	public void initialize() {
		super.initialize();

		spellToCast = new Subspell(spellToCastName);
		if (!spellToCast.process()) {
			spellToCast = null;
			MagicSpells.error("HoldRightSpell '" + internalName + "' has an invalid spell defined!");
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		LivingEntity caster = data.caster();
		CastData castData = casting.get(caster.getUniqueId());
		if (castData != null && castData.isValid(caster)) {
			castData.cast(caster);
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		}

		if (targetEntity) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, data.power(), data.args());
			if (target.noTarget())
				return new CastResult(noTarget(caster, data.args(), target), data);

			castData = new CastData(caster, target.target(), target.power(), data.args());
		} else if (targetLocation) {
			Block block = getTargetedBlock(caster, data.power(), data.args());
			if (block == null || block.getType().isAir())
				return new CastResult(noTarget(caster, data.args()), data);

			castData = new CastData(caster, block.getLocation().add(0.5, 0.5, 0.5), data.power(), data.args());
		} else
			castData = new CastData(caster, data.power(), data.args());

		castData.cast(caster);
		casting.put(caster.getUniqueId(), castData);
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
		if (!targetLocation)
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		LivingEntity caster = data.caster();
		CastData castData = casting.get(caster.getUniqueId());
		if (castData != null && castData.isValid(caster)) {
			castData.cast(caster);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}

		castData = new CastData(caster, data.location(), data.power(), data.args());
		castData.cast(caster);
		casting.put(caster.getUniqueId(), castData);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public CastResult castAtEntity(SpellData data) {
		if (!targetEntity || !validTargetList.canTarget(data.caster(), data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		LivingEntity caster = data.caster();
		CastData castData = casting.get(caster.getUniqueId());
		if (castData != null && castData.isValid(caster)) {
			castData.cast(caster);
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}

		castData = new CastData(caster, data.target(), data.power(), data.args());
		castData.cast(caster);
		casting.put(caster.getUniqueId(), castData);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	private class CastData {

		private Location targetLocation = null;
		private LivingEntity targetEntity = null;

		private final float maxDistance;
		private final float maxDuration;
		private final int resetTime;
		private final String[] args;
		private final float power;

		private long start = System.currentTimeMillis();
		private long lastCast = 0;

		private CastData(LivingEntity caster, LivingEntity target, float power, String[] args) {
			targetEntity = target;
			this.power = power;
			this.args = args;

			maxDistance = HoldRightSpell.this.maxDistance.get(caster, target, power, args);
			maxDuration = HoldRightSpell.this.maxDuration.get(caster, target, power, args);
			resetTime = HoldRightSpell.this.resetTime.get(caster, target, power, args);
		}

		private CastData(LivingEntity caster, Location target, float power, String[] args) {
			targetLocation = target;
			this.power = power;
			this.args = args;

			maxDistance = HoldRightSpell.this.maxDistance.get(caster, null, power, args);
			maxDuration = HoldRightSpell.this.maxDuration.get(caster, null, power, args);
			resetTime = HoldRightSpell.this.resetTime.get(caster, null, power, args);
		}

		private CastData(LivingEntity caster, float power, String[] args) {
			this.power = power;
			this.args = args;

			maxDistance = HoldRightSpell.this.maxDistance.get(caster, null, power, args);
			maxDuration = HoldRightSpell.this.maxDuration.get(caster, null, power, args);
			resetTime = HoldRightSpell.this.resetTime.get(caster, null, power, args);
		}

		private boolean isValid(LivingEntity livingEntity) {
			if (lastCast < System.currentTimeMillis() - resetTime) return false;
			if (maxDuration > 0 && System.currentTimeMillis() - start > maxDuration * TimeUtil.MILLISECONDS_PER_SECOND)
				return false;
			if (maxDistance > 0) {
				Location l = targetLocation;
				if (targetEntity != null) l = targetEntity.getLocation();
				if (l == null) return false;
				if (!l.getWorld().equals(livingEntity.getWorld())) return false;
				if (l.distanceSquared(livingEntity.getLocation()) > maxDistance * maxDistance) return false;
			}
			return true;
		}

		private void cast(LivingEntity caster) {
			lastCast = System.currentTimeMillis();
			if (targetEntity != null) spellToCast.subcast(new SpellData(caster, targetEntity, power, args));
			else if (targetLocation != null) spellToCast.subcast(new SpellData(caster, targetLocation, power, args));
			else spellToCast.subcast(new SpellData(caster, power, args));
		}

	}

}
