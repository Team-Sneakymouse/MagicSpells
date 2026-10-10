package com.nisovin.magicspells.spells.instant;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MobUtil;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class PurgeSpell extends InstantSpell implements TargetedLocationSpell {

	private List<EntityType> entities;

	private ConfigData<Double> radius;

	private ConfigData<Boolean> powerAffectsRadius;

	public PurgeSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		radius = getConfigDataDouble("radius", 15);

		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", true);

		List<String> list = getConfigStringList("entities", null);
		if (list != null && !list.isEmpty()) {
			entities = new ArrayList<>();
			for (String s : list) {
				EntityType t = MobUtil.getEntityType(s);
				if (t != null)
					entities.add(t);
				else
					MagicSpells.error("PurgeSpell '" + internalName + "' has an invalid entity defined: " + s);
			}

			if (entities.isEmpty())
				entities = null;
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!purge(data.caster(), data.caster().getLocation(), data.power(), data.args()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		boolean killed = purge(data.caster(), data.location(), data.power(), data.args());
		if (killed && data.hasCaster())
			playSpellEffects(EffectPosition.CASTER, data.caster(), data);
		return new CastResult(killed ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);
	}


	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		return castAtLocation(new SpellData(null, null, target, power, args)).action()
				!= PostCastAction.ALREADY_HANDLED;
	}

	private boolean purge(LivingEntity caster, Location loc, float power, String[] args) {
		double castingRange = radius.get(caster, null, power, args);
		if (Boolean.TRUE.equals(powerAffectsRadius.get(caster, null, power, args)))
			castingRange *= power;
		castingRange = Math.min(castingRange, MagicSpells.getGlobalRadius());

		Collection<Entity> entitiesNearby = loc.getWorld().getNearbyEntities(loc, castingRange, castingRange,
				castingRange);
		boolean killed = false;
		for (Entity entity : entitiesNearby) {
			if (!(entity instanceof LivingEntity livingEntity))
				continue;
			if (entity instanceof Player)
				continue;
			if (entities != null && !entities.contains(entity.getType()))
				continue;

			SpellData data = new SpellData(caster, livingEntity, loc, power, args);
			playSpellEffectsTrail(loc, entity.getLocation(), data);
			playSpellEffects(EffectPosition.TARGET, entity, data);

			livingEntity.setHealth(0);
			killed = true;
		}

		return killed;
	}

	public List<EntityType> getEntities() {
		return entities;
	}

}
