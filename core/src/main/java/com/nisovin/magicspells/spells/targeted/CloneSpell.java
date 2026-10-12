package com.nisovin.magicspells.spells.targeted;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.config.ConfigData;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class CloneSpell extends TargetedSpell implements TargetedLocationSpell {

	private static Map<Integer, Location> cloneMap = new HashMap<>();
	private Map<Integer, Location> temporaryCloneMap;

	private ConfigData<Boolean> permanent;
	private ConfigData<Boolean> cloneEquipment;
	private boolean pointBlank;

    private ConfigData<String> pose;

	private int duration;

    public CloneSpell(MagicConfig config, String spellName) {
        super(config, spellName);

        temporaryCloneMap = new HashMap<>();

        permanent = getConfigDataBoolean("permanent", false);
        cloneEquipment = getConfigDataBoolean("clone-equipment", true);
		pointBlank = getConfigBoolean("point-blank", true);

        pose = getConfigDataString("pose", "");

		duration = getConfigInt("duration", 0);
    }

	@Override
	public void turnOff() {
		for (int cloneID : temporaryCloneMap.keySet()) {
			CloneSpell.this.removeTemporaryClone(cloneID);
		}
		temporaryCloneMap.clear();
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player))
			return new CastResult(PostCastAction.NO_MESSAGES, data);

		Location loc = null;
		if (pointBlank)
			loc = data.caster().getLocation();
		else {
			try {
				Block block = getTargetedBlock(data.caster(), data.power(), data.args());
				if (block != null && !BlockUtils.isAir(block.getType()))
					loc = block.getLocation().add(0.5, 1, 0.5);
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
		CastResult result = castAtLocation(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return new CastResult(noTarget(data.caster(), data.args()), data);

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		Location loc = data.location().clone();
		loc.setY(data.location().getY() + 1);
		if (!createFalsePlayer(data.caster(), loc, data.power(), data.args()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


    private boolean createFalsePlayer(Entity caster, Location loc, float basePower, String[] args) {
        final int cloneID = MagicSpells.getVolatileCodeHandler().createFalsePlayer((Player) caster, loc, this.pose.get(caster, null, basePower, args).toUpperCase(), this.cloneEquipment.get(caster, null, basePower, args));

    	cloneMap.put(cloneID, loc);

        if (!this.permanent.get(caster, null, basePower, args)) {
        	this.temporaryCloneMap.put(cloneID, loc);
        	if (duration > 0) {
    			MagicSpells.scheduleDelayedTask(() -> {
    				CloneSpell.this.removeTemporaryClone(cloneID);
    			}, duration);
        	}
        }

        return true;
    }

	public static Map<Integer, Location> getCloneMap() {
		return cloneMap;
	}

	public Map<Integer, Location> getTemporaryCloneMap() {
		return this.temporaryCloneMap;
	}

	private void removeTemporaryClone(int cloneID) {
		MagicSpells.getVolatileCodeHandler().removeFalsePlayer(cloneID);
		cloneMap.remove(cloneID);
		this.temporaryCloneMap.remove(cloneID);
	}
}
