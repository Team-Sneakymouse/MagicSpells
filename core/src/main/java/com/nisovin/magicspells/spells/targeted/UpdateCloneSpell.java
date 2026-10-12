package com.nisovin.magicspells.spells.targeted;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.config.ConfigData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;
import java.util.List;

public class UpdateCloneSpell extends TargetedSpell implements TargetedLocationSpell {

    private ConfigData<Double> maxX;
    private ConfigData<Double> maxY;
    private ConfigData<Double> maxZ;
    public UpdateCloneSpell(MagicConfig config, String spellName) {
        super(config, spellName);

        maxX = getConfigDataDouble("distance-x", 1);
        maxY = getConfigDataDouble("distance-y", 1);
        maxZ = getConfigDataDouble("distance-z", 1);

    }

    @Override
    public CastResult cast(SpellData data) {
        Block block = getTargetedBlock(data.caster(), data.power(), data.args());

        Collection<Entity> nearbyEntities = block.getLocation().getNearbyEntities(
                maxX.get(data.caster(), null, data.power(), data.args()),
                maxY.get(data.caster(), null, data.power(), data.args()),
                maxZ.get(data.caster(), null, data.power(), data.args()));

        Bukkit.getLogger().info("I am checking for nearby block entities! " + nearbyEntities.size());
        Bukkit.getLogger().info("Position: " + block.getX() + " | " + block.getY() + " | " + block.getZ());

        for (Entity entity : nearbyEntities) {
            if (entity instanceof Display
                    && MagicSpells.getVolatileCodeHandler().isRelatedToFalsePlayer((Display) entity)) {
                MagicSpells.getVolatileCodeHandler().updateFalsePlayer((Display) entity);
            }
        }
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
		updateDisplays(data.caster(), data.location(), data.power());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    
	}
@Override
	public boolean castAtLocation(Entity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}



    private void updateDisplays(Entity caster, Location location, float power){
        Bukkit.getScheduler().scheduleSyncDelayedTask(MagicSpells.getInstance(), () ->{
            Collection<Entity> nearbyEntities = location.getNearbyEntities(
                    maxX.get(caster, null, power, null),
                    maxY.get(caster, null, power, null),
                    maxZ.get(caster, null, power, null));

            for(Entity entity : nearbyEntities){
                if(entity instanceof Display
                        && MagicSpells.getVolatileCodeHandler().isRelatedToFalsePlayer((Display)entity)){
                    MagicSpells.getVolatileCodeHandler().updateFalsePlayer((Display)entity);
                }
            }
        }, 5);  //Needed to add a slight delay because of the way EntityTeleportEvent works.
    }
}
