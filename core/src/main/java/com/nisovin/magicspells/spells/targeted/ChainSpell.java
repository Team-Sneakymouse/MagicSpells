package com.nisovin.magicspells.spells.targeted;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;

public class ChainSpell extends TargetedSpell implements TargetedEntitySpell, TargetedEntityFromLocationSpell {

	private ConfigData<Integer> bounces;
	private ConfigData<Integer> interval;

	private ConfigData<Double> bounceRange;

	private String spellToCastName;
	private Subspell spellToCast;

	public ChainSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		bounces = getConfigDataInt("bounces", 3);
		interval = getConfigDataInt("interval", 10);

		bounceRange = getConfigDataDouble("bounce-range", 8);

		spellToCastName = getConfigString("spell", "");
	}

	@Override
	public void initialize() {
		super.initialize();

		spellToCast = new Subspell(spellToCastName);
		if (!spellToCast.process()) {
			spellToCast = null;
			MagicSpells.error("ChainSpell '" + internalName + "' has an invalid spell defined!");
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> info = getTargetedEntity(data.caster(), data.power(), data.args());
		if (info.noTarget())
			return new CastResult(noTarget(data.caster(), data.args(), info), data);

		data = data.target(info.target()).power(info.power());
		chain(data.caster(), data.caster().getLocation(), data.target(), data.power(), data.args());
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
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			chain(data.caster(), data.caster().getLocation(), data.target(), data.power(), data.args());
		} else {
			if (!validTargetList.canTarget(data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			chain(null, null, data.target(), data.power(), data.args());
		}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	@Override
	public CastResult castAtEntityFromLocation(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			chain(data.caster(), data.location(), data.target(), data.power(), data.args());
		} else {
			if (!validTargetList.canTarget(data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			chain(null, data.location(), data.target(), data.power(), data.args());
		}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	private void chain(Entity caster, Location start, Entity targetEntity, float power, String[] args) {
		if (!(targetEntity instanceof LivingEntity target)) return;
		List<LivingEntity> targets = new ArrayList<>();
		List<Float> targetPowers = new ArrayList<>();
		targets.add(target);
		targetPowers.add(power);

		int bounces = this.bounces.get(caster, target, power, args);
		int interval = this.interval.get(caster, target, power, args);
		double bounceRange = Math.min(this.bounceRange.get(caster, target, power, args), MagicSpells.getGlobalRadius());

		// Get targets
		LivingEntity current = target;
		int attempts = 0;
		while (targets.size() < bounces && attempts++ < bounces << 1) {
			List<Entity> entities = current.getNearbyEntities(bounceRange, bounceRange, bounceRange);
			for (Entity entity : entities) {
				if (!(entity instanceof LivingEntity livingEntity))
					continue;
				if (targets.contains(livingEntity))
					continue;

				if (!validTargetList.canTarget(caster, livingEntity))
					continue;

				float subPower = power;
				if (caster != null) {
					SpellTargetEvent event = new SpellTargetEvent(this, new SpellData(caster, livingEntity, subPower, args));
					if (!event.callEvent())
						continue;

					if (!(event.getTarget() instanceof LivingEntity eventTarget))
						continue;
					livingEntity = eventTarget;
					subPower = event.getPower();
				}

				targets.add(livingEntity);
				targetPowers.add(subPower);
				current = livingEntity;

				break;
			}
		}

		SpellData data = new SpellData(caster, target, power, args);

		// Cast spell at targets
		if (caster != null)
			playSpellEffects(EffectPosition.CASTER, caster, data);
		else if (start != null)
			playSpellEffects(EffectPosition.CASTER, start, data);

		if (interval <= 0) {
			for (int i = 0; i < targets.size(); i++) {
				Location from;
				if (i == 0)
					from = start;
				else
					from = targets.get(i - 1).getLocation();

				castSpellAt(caster, from, targets.get(i), targetPowers.get(i), args);

				data = new SpellData(caster, targets.get(i), from, targetPowers.get(i), args);
				if (i > 0)
					playSpellEffectsTrail(targets.get(i - 1).getLocation(), targets.get(i).getLocation(), data);
				else if (caster != null)
					playSpellEffectsTrail(caster.getLocation(), targets.get(i).getLocation(), data);
				playSpellEffects(EffectPosition.TARGET, targets.get(i), data);
			}
		} else
			new ChainBouncer(caster, start, targets, targetPowers, interval, args);
	}

	private void castSpellAt(Entity caster, Location from, Entity target, float power, String[] args) {
		if (from != null)
			spellToCast.subcast(new SpellData(caster, target, from, power, args));
		else
			spellToCast.subcast(new SpellData(caster, target, power, args));
	}

	private class ChainBouncer implements Runnable {

		private final Entity caster;
		private final Location start;
		private final String[] args;
		private final int taskId;

		private final List<LivingEntity> targets;
		private final List<Float> targetPowers;

		private int current = 0;

		private ChainBouncer(Entity caster, Location start, List<LivingEntity> targets, List<Float> targetPowers,
				int interval, String[] args) {
			this.caster = caster;
			this.start = start;
			this.args = args;

			this.targetPowers = targetPowers;
			this.targets = targets;

			taskId = MagicSpells.scheduleRepeatingTask(this, 0, interval);
		}

		@Override
		public void run() {
			Location from;
			if (current == 0)
				from = start;
			else
				from = targets.get(current - 1).getLocation();

			SpellData data = new SpellData(caster, targets.get(current), from, targetPowers.get(current), args);

			castSpellAt(caster, from, targets.get(current), targetPowers.get(current), args);
			if (current > 0) {
				playSpellEffectsTrail(targets.get(current - 1).getLocation().add(0, 0.5, 0),
						targets.get(current).getLocation().add(0, 0.5, 0), data);
			} else if (current == 0 && caster != null) {
				playSpellEffectsTrail(caster.getLocation().add(0, 0.5, 0),
						targets.get(current).getLocation().add(0, 0.5, 0), data);
			}

			playSpellEffects(EffectPosition.TARGET, targets.get(current), data);
			current++;
			if (current >= targets.size())
				MagicSpells.cancelTask(taskId);
		}

	}

}
