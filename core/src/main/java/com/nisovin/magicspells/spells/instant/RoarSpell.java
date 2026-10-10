package com.nisovin.magicspells.spells.instant;

import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MobUtil;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class RoarSpell extends InstantSpell {

	private ConfigData<Double> radius;

	private String strNoTarget;

	private boolean cancelIfNoTargets;

	public RoarSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		radius = getConfigDataDouble("radius", 8F);

		strNoTarget = getConfigString("str-no-target", "No targets found.");

		cancelIfNoTargets = getConfigBoolean("cancel-if-no-targets", true);
	}

	@Override
	public CastResult cast(SpellData data) {
		double radius = Math.min(this.radius.get(data.caster(), null, data.power(), data.args()), MagicSpells.getGlobalRadius());
		List<Entity> entities = data.caster().getNearbyEntities(radius, radius, radius);

		int count = 0;
		for (Entity entity : entities) {
			if (!(entity instanceof LivingEntity livingEntity)) continue;
			if (entity instanceof Player) continue;
			if (!validTargetList.canTarget(data.caster(), entity)) continue;

			MobUtil.setTarget(livingEntity, data.caster());
			count++;

			SpellData effectData = new SpellData(data.caster(), livingEntity, data.power(), data.args());
			playSpellEffectsTrail(data.caster().getLocation(), entity.getLocation(), effectData);
			playSpellEffects(EffectPosition.TARGET, entity, effectData);
		}

		if (cancelIfNoTargets && count == 0) {
			sendMessage(strNoTarget, data.caster(), data.args());
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		}

		playSpellEffects(EffectPosition.CASTER, data.caster(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	public String getStrNoTarget() {
		return strNoTarget;
	}

	public void setStrNoTarget(String strNoTarget) {
		this.strNoTarget = strNoTarget;
	}

	public boolean shouldCancelIfNoTargets() {
		return cancelIfNoTargets;
	}

	public void setCancelIfNoTargets(boolean cancelIfNoTargets) {
		this.cancelIfNoTargets = cancelIfNoTargets;
	}

}
