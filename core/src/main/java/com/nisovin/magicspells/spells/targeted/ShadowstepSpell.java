package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;

public class ShadowstepSpell extends TargetedSpell implements TargetedEntitySpell {

	private ConfigData<Float> yaw;
	private ConfigData<Float> pitch;

	private ConfigData<Double> distance;

	private Vector relativeOffset;

	private String strNoLandingSpot;

	public ShadowstepSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		yaw = getConfigDataFloat("yaw", 0);
		pitch = getConfigDataFloat("pitch", 0);

		distance = getConfigDataDouble("distance", -1);

		relativeOffset = getConfigVector("relative-offset", "-1,0,0");

		strNoLandingSpot = getConfigString("str-no-landing-spot", "Cannot shadowstep there.");
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> target = getTargetedEntity(data.caster(), data.power(), data.args());
		if (target.noTarget())
			return new CastResult(noTarget(data.caster(), data.args(), target), data);

		data = data.target(target.target()).power(target.power());
		CastResult result = castAtEntity(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return new CastResult(noTarget(data.caster(), strNoLandingSpot, data.args()), data);

		sendMessages(data.caster(), data.target(), data.args());
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
		if (!validTargetList.canTarget(data.caster(), data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!shadowstep(data))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


	private boolean shadowstep(SpellData data) {
		Entity caster = data.caster();
		Entity target = data.target();
		float power = data.power();
		String[] args = data.args();
		Location targetLoc = target.getLocation().clone();
		targetLoc.setPitch(0);

		Vector startDir = targetLoc.getDirection().setY(0).normalize();
		Vector horizOffset = new Vector(-startDir.getZ(), 0, startDir.getX()).normalize();

		double distance = this.distance.get(caster, target, power, args);
		Vector relativeOffset = distance != -1 ? this.relativeOffset.clone().setX(distance) : this.relativeOffset;

		targetLoc.add(horizOffset.multiply(relativeOffset.getZ())).getBlock().getLocation();
		targetLoc.add(targetLoc.getDirection().setY(0).multiply(relativeOffset.getX()));
		targetLoc.setY(targetLoc.getY() + relativeOffset.getY());

		targetLoc.setPitch(pitch.get(caster, target, power, args));
		targetLoc.setYaw(targetLoc.getYaw() + yaw.get(caster, target, power, args));

		Block b = targetLoc.getBlock();
		if (!BlockUtils.isPathable(b.getType()) || !BlockUtils.isPathable(b.getRelative(BlockFace.UP)))
			return false;

		playSpellEffects(caster.getLocation(), targetLoc, data.location(targetLoc));
		caster.teleportAsync(targetLoc);

		return true;
	}

}
