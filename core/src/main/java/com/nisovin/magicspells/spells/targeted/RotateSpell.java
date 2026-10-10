package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class RotateSpell extends TargetedSpell implements TargetedEntitySpell, TargetedLocationSpell {

	private ConfigData<Integer> rotationYaw;
	private ConfigData<Integer> rotationPitch;

	private boolean random;
	private boolean affectPitch;
	private boolean mimicDirection;

	private String face;

	public RotateSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		rotationYaw = getConfigDataInt("rotation-yaw", 10);
		rotationPitch = getConfigDataInt("rotation-pitch", 0);

		random = getConfigBoolean("random", false);
		affectPitch = getConfigBoolean("affect-pitch", false);
		mimicDirection = getConfigBoolean("mimic-direction", false);

		face = getConfigString("face", "");
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> info = getTargetedEntity(data.caster(), data.power(), data.args());
					if (info.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), info), data);
		
					spinFace(data.caster(), info.target(), info.power(), data.args());
					playSpellEffects(data.caster(), info.target(), info.power(), data.args());
					sendMessages(data.caster(), info.target(), data.args());
		
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
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							spinFace(data.caster(), data.target(), data.power(), data.args());
							playSpellEffects(data.caster(), data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							spinTarget(null, data.target(), data.power(), data.args());
							playSpellEffects(EffectPosition.TARGET, data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		spin(caster, target);
		playSpellEffects(caster, target, power, args);
		return true;
	}

	private void spinTarget(LivingEntity caster, LivingEntity target, float power, String[] args) {
		Location loc = target.getLocation();
		if (random) {
			loc.setYaw(Util.getRandomInt(360));
			if (affectPitch) loc.setPitch(Util.getRandomInt(181) - 90);
		} else {
			loc.setYaw(loc.getYaw() + rotationYaw.get(caster, target, power, args));
			if (affectPitch) loc.setPitch(loc.getPitch() + rotationPitch.get(caster, target, power, args));
		}
		target.teleportAsync(loc);
	}

	private void spinFace(LivingEntity caster, LivingEntity target, float power, String[] args) {
		Location targetLoc = target.getLocation();
		Location casterLoc = caster.getLocation();

		if (face.isEmpty()) {
			spinTarget(caster, target, power, args);
			return;
		}

		Location loc;
		switch (face) {
			case "target" -> caster.teleportAsync(changeDirection(casterLoc, targetLoc));
			case "caster" -> target.teleportAsync(changeDirection(targetLoc, casterLoc));
			case "away-from-caster" -> {
				loc = changeDirection(targetLoc, casterLoc);
				loc.setYaw(loc.getYaw() + 180);
				target.teleportAsync(loc);
			}
			case "away-from-target" -> {
				loc = changeDirection(casterLoc, targetLoc);
				loc.setYaw(loc.getYaw() + 180);
				caster.teleportAsync(loc);
			}
		}

	}

	private void spin(LivingEntity entity, Location target) {
		entity.teleportAsync(changeDirection(entity.getLocation(), target));
	}

	private Location changeDirection(Location pos1, Location pos2) {
		Location loc = pos1.clone();
		if (mimicDirection) {
			if (affectPitch) loc.setPitch(pos2.getPitch());
			loc.setYaw(pos2.getYaw());
		} else loc.setDirection(getVectorDir(pos1, pos2));

		return loc;
	}

	private Vector getVectorDir(Location caster, Location target) {
		return target.clone().subtract(caster.toVector()).toVector();
	}

}
