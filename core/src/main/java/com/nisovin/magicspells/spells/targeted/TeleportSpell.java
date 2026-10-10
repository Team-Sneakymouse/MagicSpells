package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class TeleportSpell extends TargetedSpell implements TargetedEntitySpell {

	private ConfigData<Float> yaw;
	private ConfigData<Float> pitch;

	private Vector relativeOffset;

	private String strCantTeleport;

	public TeleportSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		yaw = getConfigDataFloat("yaw", 0);
		pitch = getConfigDataFloat("pitch", 0);

		relativeOffset = getConfigVector("relative-offset", "0,0.1,0");

		strCantTeleport = getConfigString("str-cant-teleport", "");
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> target = getTargetedEntity(data.caster(), data.power(), data.args());
		if (target.noTarget())
			return new CastResult(noTarget(data.caster(), data.args(), target), data);

		data = data.target(target.target()).power(target.power());
		CastResult result = castAtEntity(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return new CastResult(noTarget(data.caster(), strCantTeleport, data.args()), data);

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
		if (!teleport(data))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	private boolean teleport(SpellData data) {
		LivingEntity caster = data.caster();
		LivingEntity target = data.target();
		float power = data.power();
		String[] args = data.args();
		Location targetLoc = target.getLocation();
		Location startLoc = caster.getLocation();

		Vector startDir = startLoc.clone().getDirection().normalize();
		Vector horizOffset = new Vector(-startDir.getZ(), 0.0, startDir.getX()).normalize();
		targetLoc.add(horizOffset.multiply(relativeOffset.getZ())).getBlock().getLocation();
		targetLoc.add(startLoc.getDirection().multiply(relativeOffset.getX()));
		targetLoc.setY(targetLoc.getY() + relativeOffset.getY());

		targetLoc.setPitch(startLoc.getPitch() - pitch.get(caster, target, power, args));
		targetLoc.setYaw(startLoc.getYaw() + yaw.get(caster, target, power, args));

		if (!BlockUtils.isPathable(targetLoc.getBlock()))
			return false;

		SpellData effectData = data.location(targetLoc);
		playSpellEffects(EffectPosition.CASTER, caster, effectData);
		playSpellEffects(EffectPosition.TARGET, target, effectData);
		playSpellEffectsTrail(startLoc, targetLoc, effectData);

		caster.teleportAsync(targetLoc);
		return true;
	}

}
