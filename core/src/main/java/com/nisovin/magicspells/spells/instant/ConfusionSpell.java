package com.nisovin.magicspells.spells.instant;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
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

public class ConfusionSpell extends InstantSpell implements TargetedLocationSpell {

	private final ConfigData<Double> radius;
	private final ConfigData<Boolean> powerAffectsRadius;

	public ConfusionSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		radius = getConfigDataDouble("radius", 10);
		powerAffectsRadius = getConfigDataBoolean("power-affects-radius", true);
	}

	@Override
	public CastResult cast(SpellData data) {
		confuse(data.caster(), data.caster().getLocation(), data.power(), data.args());
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
		confuse(data.caster(), data.location(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


	private void confuse(Entity caster, Location location, float power, String[] args) {
		double castingRange = radius.get(caster, null, power, args);
		if (Boolean.TRUE.equals(powerAffectsRadius.get(caster, null, power, args)))
			castingRange = castingRange * power;

		castingRange = Math.min(castingRange, MagicSpells.getGlobalRadius());

		Collection<Entity> entities = location.getWorld().getNearbyEntities(location, castingRange, castingRange,
				castingRange);
		List<LivingEntity> monsters = new ArrayList<>();

		for (Entity e : entities) {
			if (!(e instanceof LivingEntity livingEntity))
				continue;
			if (!validTargetList.canTarget(caster, e))
				continue;
			monsters.add(livingEntity);
		}

		for (int i = 0; i < monsters.size(); i++) {
			int next = i + 1;
			if (next >= monsters.size())
				next = 0;
			MobUtil.setTarget(monsters.get(i), monsters.get(next));

			SpellData data = new SpellData(caster, monsters.get(i), location, power, args);

			playSpellEffects(EffectPosition.TARGET, monsters.get(i), data);
			playSpellEffectsTrail(caster.getLocation(), monsters.get(i).getLocation(), data);
		}
		playSpellEffects(EffectPosition.CASTER, caster, power, args);
	}

}
