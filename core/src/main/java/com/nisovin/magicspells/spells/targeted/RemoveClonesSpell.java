package com.nisovin.magicspells.spells.targeted;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.config.ConfigData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class RemoveClonesSpell extends TargetedSpell implements TargetedLocationSpell {

	private ConfigData<Float> radius;

	private boolean pointBlank;
	private ConfigData<Boolean> powerAffectsRadius;

	private List<String> cloneSpellNames;
	private List<CloneSpell> cloneSpells;

    public RemoveClonesSpell(MagicConfig config, String spellName) {
        super(config, spellName);

        radius = getConfigDataFloat("radius", 10F);

		pointBlank = getConfigBoolean("point-blank", true);
		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", false);

		cloneSpellNames = getConfigStringList("clone-spells", null);
		cloneSpells = new ArrayList<CloneSpell>();
    }

	@Override
	public void initialize() {
		super.initialize();

		if (cloneSpellNames != null) {
			for (String cloneSpellName : cloneSpellNames) {
				Spell spell = MagicSpells.getSpellByInternalName(cloneSpellName);
				if (spell instanceof CloneSpell) {
					cloneSpells.add((CloneSpell) spell);
				} else {
					MagicSpells.error("RemoveClonesSpell '" + internalName + "' has an invalid spell defined in clone-spells!");
					return;
				}
			}
		}
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
		CastResult result = castAtLocation(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
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
		if (!removeClones(data.caster(), data.location(), data.power(), data.args()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

    private boolean removeClones(LivingEntity caster, Location loc, float power, String[] args) {
		float radSq = radius.get(caster, null, power, args);
		if (Boolean.TRUE.equals(powerAffectsRadius.get(caster, null, power, args))) radSq *= power;

		radSq *= radSq;

		boolean succes = false;
		World locWorld = loc.getWorld();

		if (!this.cloneSpells.isEmpty()) {
		    for (CloneSpell cloneSpell : cloneSpells) {
		        Iterator<Integer> iterator = cloneSpell.getTemporaryCloneMap().keySet().iterator();
		        while (iterator.hasNext()) {
		            Integer cloneID = iterator.next();
		            Location location = cloneSpell.getTemporaryCloneMap().get(cloneID);

					if (!location.getWorld().getName().equals(locWorld.getName())) continue;
			        if (location.distanceSquared(loc) < radSq) {
		        		MagicSpells.getVolatileCodeHandler().removeFalsePlayer(cloneID);
	    				CloneSpell.getCloneMap().remove(cloneID);
	    				iterator.remove();
		                succes = true;
		            }
		        }
		    }
		} else {
		    List<CloneSpell> cloneSpellsTemp = new ArrayList<CloneSpell>();

		    for (Spell spell : MagicSpells.spells()) {
		        if (spell instanceof CloneSpell) {
		            cloneSpellsTemp.add((CloneSpell) spell);
		        }
		    }

		    Iterator<Integer> iterator = CloneSpell.getCloneMap().keySet().iterator();
		    while (iterator.hasNext()) {
		        Integer cloneID = iterator.next();
		        Location location = CloneSpell.getCloneMap().get(cloneID);

				if (!location.getWorld().getName().equals(locWorld.getName())) continue;
		        if (location.distanceSquared(loc) < radSq) {
		            for (CloneSpell cloneSpell : cloneSpellsTemp) {
		                if (cloneSpell.getTemporaryCloneMap().containsKey(cloneID)) {
		                    cloneSpell.getTemporaryCloneMap().remove(cloneID);
		                    break;
		                }
		            }

	        		MagicSpells.getVolatileCodeHandler().removeFalsePlayer(cloneID);
	                iterator.remove();
                    succes = true;
		        }
		    }
		}

        return succes;
    }
}
