package com.nisovin.magicspells.spells.targeted;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.block.Block;
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
    public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
        if(!(caster instanceof Player)) return PostCastAction.NO_MESSAGES;

        if(state == SpellCastState.NORMAL) {
			Location loc = null;
			if (pointBlank) loc = caster.getLocation();
			else {
				try {
					Block block = getTargetedBlock(caster, power, args);
					if (block != null && !BlockUtils.isAir(block.getType())) loc = block.getLocation().add(0.5, 1, 0.5);
				}
				catch (IllegalStateException ignored) {}
			}

			if (loc == null) return noTarget(caster, args);

			SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, caster, loc, power, args);
			EventUtil.call(event);
			if (event.isCancelled()) loc = null;
			else {
				loc = event.getTargetLocation();
				power = event.getPower();
			}

			if (loc == null) return noTarget(caster, args);

			boolean done = createFalsePlayer(caster, loc, power, args);
			if (!done) return noTarget(caster, args);
        }
        return PostCastAction.HANDLE_NORMALLY;
    }

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		Location loc = target.clone();
		loc.setY(target.getY() + 1);
		return createFalsePlayer(caster, loc, power, args);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		Location loc = target.clone();
		loc.setY(target.getY() + 1);
		return createFalsePlayer(caster, loc, power, null);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtLocation(Location target, float power) {
		return false;
	}

    private boolean createFalsePlayer(LivingEntity caster, Location loc, float basePower, String[] args) {
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
