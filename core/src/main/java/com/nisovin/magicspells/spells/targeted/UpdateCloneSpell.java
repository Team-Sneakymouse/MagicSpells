package com.nisovin.magicspells.spells.targeted;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.MagicConfig;
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
    public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {

        Block block = getTargetedBlock(caster, power, args);

        Collection<Entity> nearbyEntities = block.getLocation().getNearbyEntities(
                maxX.get(caster, null, power, args),
                maxY.get(caster, null, power, args),
                maxZ.get(caster, null, power, args));

        Bukkit.getLogger().info("I am checking for nearby block entities! " + nearbyEntities.size());
        Bukkit.getLogger().info("Position: " + block.getX() + " | " + block.getY() + " | " + block.getZ());

        for(Entity entity : nearbyEntities){
            if(entity instanceof Display
                    && MagicSpells.getVolatileCodeHandler().isRelatedToFalsePlayer((Display)entity)){
                MagicSpells.getVolatileCodeHandler().updateFalsePlayer((Display)entity);
            }
        }
        return PostCastAction.HANDLE_NORMALLY;
    }


    @Override
    public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
        updateDisplays(caster, target, power);
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean castAtLocation(LivingEntity caster, Location target, float power) {
        return castAtLocation(caster, target, power, null);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean castAtLocation(Location target, float power) {return false;}


    private void updateDisplays(LivingEntity caster, Location location, float power){
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
