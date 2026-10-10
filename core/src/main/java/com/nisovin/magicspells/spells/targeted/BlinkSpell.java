package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.BlockIterator;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;

public class BlinkSpell extends TargetedSpell implements TargetedLocationSpell {

	private String strCantBlink;

	private boolean passThroughCeiling;
	
	public BlinkSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		strCantBlink = getConfigString("str-cant-blink", "You can't blink there.");

		passThroughCeiling = getConfigBoolean("pass-through-ceiling", false);
	}
	
	@Override
	public CastResult cast(SpellData data) {
		int range = getRange(data.power());
		if (range <= 0)
			range = 25;
		if (range > 125)
			range = 125;
		BlockIterator iter;
		try {
			iter = new BlockIterator(data.caster(), range);
		} catch (IllegalStateException e) {
			iter = null;
		}

		Block b;
		Block prev = null;
		Block found = null;

		if (iter != null) {
			while (iter.hasNext()) {
				b = iter.next();
				if (BlockUtils.isTransparent(this, b))
					prev = b;
				else {
					found = b;
					break;
				}
			}
		}

		if (found == null)
			return new CastResult(noTarget(data.caster(), strCantBlink, data.args()), data);

		Location loc = null;
		if (!passThroughCeiling && found.getRelative(0, -1, 0).equals(prev)) {
			if (BlockUtils.isPathable(prev) && BlockUtils.isPathable(prev.getRelative(0, -1, 0)))
				loc = prev.getRelative(0, -1, 0).getLocation();
		} else if (BlockUtils.isPathable(found.getRelative(0, 1, 0))
				&& BlockUtils.isPathable(found.getRelative(0, 2, 0))) {
			loc = found.getLocation();
			loc.setY(loc.getY() + 1);
		} else if (prev != null && BlockUtils.isPathable(prev) && BlockUtils.isPathable(prev.getRelative(0, 1, 0)))
			loc = prev.getLocation();

		if (loc != null) {
			SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, data.location(loc));
			EventUtil.call(event);
			if (event.isCancelled())
				loc = null;
			else {
				data = event.getSpellData();
				loc = data.location();
			}
		}

		if (loc == null)
			return new CastResult(noTarget(data.caster(), strCantBlink, data.args()), data);

		loc.setX(loc.getX() + 0.5);
		loc.setZ(loc.getZ() + 0.5);
		loc.setPitch(data.caster().getLocation().getPitch());
		loc.setYaw(data.caster().getLocation().getYaw());

		return castAtLocation(data.location(loc));
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		Location location = data.location().clone();
		location.setYaw(data.caster().getLocation().getYaw());
		location.setPitch(data.caster().getLocation().getPitch());

		playSpellEffects(data.caster(), location, data);
		data.caster().teleportAsync(location);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


}
