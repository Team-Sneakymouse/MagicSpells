package com.nisovin.magicspells.spells.targeted;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
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
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> info = getTargetedEntity(caster, power, args);
			if (info.noTarget())
				return noTarget(caster, args, info);
			LivingEntity target = info.target();

			chain(caster, caster.getLocation(), target, info.power(), args);
			sendMessages(caster, target, args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		chain(caster, caster.getLocation(), target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target))
			return false;
		chain(null, null, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power,
			String[] args) {
		if (!validTargetList.canTarget(caster, target))
			return false;
		chain(caster, from, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntityFromLocation(Location from, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target))
			return false;
		chain(null, from, target, power, args);
		return true;
	}

	private void chain(LivingEntity caster, Location start, LivingEntity target, float power, String[] args) {
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
					SpellTargetEvent event = new SpellTargetEvent(this, caster, livingEntity, subPower, args);
					if (!event.callEvent())
						continue;

					livingEntity = event.getTarget();
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

	private void castSpellAt(LivingEntity caster, Location from, LivingEntity target, float power, String[] args) {
		if (from != null)
			spellToCast.subcast(caster, from, target, power, args);
		else
			spellToCast.subcast(caster, target, power, args);
	}

	private class ChainBouncer implements Runnable {

		private final LivingEntity caster;
		private final Location start;
		private final String[] args;
		private final int taskId;

		private final List<LivingEntity> targets;
		private final List<Float> targetPowers;

		private int current = 0;

		private ChainBouncer(LivingEntity caster, Location start, List<LivingEntity> targets, List<Float> targetPowers,
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
