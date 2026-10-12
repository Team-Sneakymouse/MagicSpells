package com.nisovin.magicspells.spells;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.RegexUtil;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public final class TargetedMultiSpell extends TargetedSpell implements TargetedEntitySpell, TargetedLocationSpell, TargetedEntityFromLocationSpell {

	private static final Pattern DELAY_PATTERN = Pattern.compile("DELAY [0-9]+");

	private List<Action> actions;
	private List<String> spellList;

	private ConfigData<Float> yOffset;

	private ConfigData<Boolean> pointBlank;
	private ConfigData<Boolean> stopOnFail;
	private ConfigData<Boolean> passTargeting;
	private ConfigData<Boolean> requireEntityTarget;
	private ConfigData<Boolean> castRandomSpellInstead;

	public TargetedMultiSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		actions = new ArrayList<>();
		spellList = getConfigStringList("spells", null);

		yOffset = getConfigDataFloat("y-offset", 0F);

		pointBlank = getConfigDataBoolean("point-blank", false);
		stopOnFail = getConfigDataBoolean("stop-on-fail", true);
		passTargeting = getConfigDataBoolean("pass-targeting", true);
		requireEntityTarget = getConfigDataBoolean("require-entity-target", false);
		castRandomSpellInstead = getConfigDataBoolean("cast-random-spell-instead", false);
	}

	@Override
	public void initialize() {
		super.initialize();

		if (spellList == null) return;

		for (String s : spellList) {
			if (RegexUtil.matches(DELAY_PATTERN, s)) {
				int delay = Integer.parseInt(s.split(" ")[1]);
				actions.add(new Action(delay));
				continue;
			}

			Subspell spell = new Subspell(s);
			if (spell.process()) actions.add(new Action(spell));
			else MagicSpells.error("TargetedMultiSpell '" + internalName + "' has an invalid spell '" + s + "' defined!");
		}

		spellList = null;
	}

	@Override
	public CastResult cast(SpellData data) {
		Location locTarget = null;
		LivingEntity entTarget = null;

		boolean requireEntityTarget = Boolean.TRUE.equals(this.requireEntityTarget.get(data));
		boolean pointBlank = Boolean.TRUE.equals(this.pointBlank.get(data));

		if (requireEntityTarget) {
			TargetInfo<LivingEntity> info = getTargetedEntity(data.caster(), data.power(), data.args());
			if (info.noTarget())
				return new CastResult(noTarget(data.caster(), data.args(), info), data);

			entTarget = info.target();
			data = data.target(entTarget).power(info.power());
		} else if (pointBlank) {
			locTarget = data.caster().getLocation();
		} else {
			Block b;
			try {
				b = getTargetedBlock(data.caster(), data.power(), data.args());
				if (b != null && !BlockUtils.isAir(b.getType())) {
					locTarget = b.getLocation();
					locTarget.add(0.5, 0, 0.5);
				}
			} catch (IllegalStateException e) {
				DebugHandler.debugIllegalState(e);
			}
		}
		if (locTarget == null && entTarget == null)
			return new CastResult(noTarget(data.caster(), data.args()), data);
		if (locTarget != null) {
			locTarget.setY(locTarget.getY() + yOffset.get(data));
			locTarget.setDirection(data.caster().getLocation().getDirection());
			data = data.location(locTarget);
		}

		if (!runSpells(data, null))
			return new CastResult(noTarget(data.caster(), data.args()), data);

		if (entTarget != null) {
			sendMessages(data.caster(), entTarget, data.args());
			return new CastResult(PostCastAction.NO_MESSAGES, data);
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
		Location loc = data.location().clone().add(0, yOffset.get(data), 0);
		if (!runSpells(data.location(loc), null))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		} else if (!validTargetList.canTarget(data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		if (!runSpells(data, null))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	private boolean runSpells(SpellData data, Location center) {
		Entity caster = data.caster();
		Entity targetEnt = data.target();
		Location targetLoc = targetEnt != null ? null : data.location();
		float power = data.power();
		String[] args = data.args();

		if (targetEnt != null && (caster == null ? !validTargetList.canTarget(targetEnt) : !validTargetList.canTarget(caster, targetEnt)))
			return false;

		boolean castRandomSpellInstead = Boolean.TRUE.equals(this.castRandomSpellInstead.get(caster, targetEnt, power, args));
		boolean stopOnFail = Boolean.TRUE.equals(this.stopOnFail.get(caster, targetEnt, power, args));
		boolean passTargeting = Boolean.TRUE.equals(this.passTargeting.get(caster, targetEnt, power, args));

		boolean somethingWasDone = false;
		if (!castRandomSpellInstead) {
			int delay = 0;
			Subspell spell;
			List<DelayedSpell> delayedSpells = new ArrayList<>();
			for (Action action : actions) {
				if (action.isDelay()) {
					delay += action.getDelay();
					continue;
				}

				if (action.isSpell()) {
					spell = action.getSpell();
					if (delay == 0) {
						boolean ok = castTargetedSpells(spell, caster, center, targetEnt, targetLoc, power, args, passTargeting);
						if (ok) somethingWasDone = true;
						else if (stopOnFail) break;
						continue;
					}

					DelayedSpell ds = new DelayedSpell(spell, caster, center, targetEnt, targetLoc, power, args, delayedSpells, passTargeting, stopOnFail);
					delayedSpells.add(ds);
					MagicSpells.scheduleDelayedTask(ds, delay);
					somethingWasDone = true;
				}
			}
		} else {
			Action action = actions.get(random.nextInt(actions.size()));
			if (action.isSpell()) somethingWasDone = castTargetedSpells(action.getSpell(), caster, center, targetEnt, targetLoc, power, args, passTargeting);
		}
		if (somethingWasDone) {
			if (caster != null) {
				if (targetEnt != null)
					playSpellEffects(caster, targetEnt, data);
				else if (targetLoc != null)
					playSpellEffects(caster, targetLoc, data);
			} else {
				if (targetEnt != null)
					playSpellEffects(EffectPosition.TARGET, targetEnt, data);
				else if (targetLoc != null)
					playSpellEffects(EffectPosition.TARGET, targetLoc, data);
			}
		}
		return somethingWasDone;
	}

	private boolean castTargetedSpells(Subspell spell, Entity caster, Location center, Entity targetEnt, Location targetLoc, float power, String[] args, boolean passTargeting) {
		if (targetEnt != null && center != null) return spell.subcast(new SpellData(caster, targetEnt, center, power, args), passTargeting);
		if (targetEnt != null) return spell.subcast(new SpellData(caster, targetEnt, power, args), passTargeting);
		if (targetLoc != null) return spell.subcast(new SpellData(caster, targetLoc, power, args));
		return spell.subcast(new SpellData(caster, power, args));
	}

	@Override
	public CastResult castAtEntityFromLocation(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		} else if (!validTargetList.canTarget(data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		if (!runSpells(data, data.location()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtEntityFromLocation(Entity caster, Location from, Entity target, float power, String[] args) {
		return castAtEntityFromLocation(new SpellData(caster, target, from, power, args)).action()
				!= PostCastAction.ALREADY_HANDLED;
	}


	private static class Action {

		private final Subspell spell;
		private final int delay;

		private Action(Subspell spell) {
			this.spell = spell;
			delay = 0;
		}

		private Action(int delay) {
			this.delay = delay;
			spell = null;
		}

		public boolean isSpell() {
			return spell != null;
		}

		public Subspell getSpell() {
			return spell;
		}

		public boolean isDelay() {
			return delay > 0;
		}

		public int getDelay() {
			return delay;
		}

	}

	private class DelayedSpell implements Runnable {

		private final Subspell spell;
		private final Entity caster;
		private final Location center;
		private final Entity targetEnt;
		private final Location targetLoc;
		private final String[] args;
		private final float power;
		private final boolean passTargeting;
		private final boolean stopOnFail;

		private List<DelayedSpell> delayedSpells;
		private boolean cancelled;

		private DelayedSpell(Subspell spell, Entity caster, Location center, Entity targetEnt, Location targetLoc, float power, String[] args, List<DelayedSpell> delayedSpells, boolean passTargeting, boolean stopOnFail) {
			this.spell = spell;
			this.caster = caster;
			this.center = center;
			this.targetEnt = targetEnt;
			this.targetLoc = targetLoc;
			this.args = args;
			this.power = power;
			this.delayedSpells = delayedSpells;
			this.passTargeting = passTargeting;
			this.stopOnFail = stopOnFail;

			cancelled = false;
		}

		public void cancel() {
			cancelled = true;
			delayedSpells = null;
		}

		public void cancelAll() {
			for (DelayedSpell ds : delayedSpells) {
				if (ds == this) continue;
				ds.cancel();
			}
			delayedSpells.clear();
			cancel();
		}

		@Override
		public void run() {
			if (cancelled) {
				delayedSpells = null;
				return;
			}

			if (caster == null || caster.isValid()) {
				boolean ok = castTargetedSpells(spell, caster, center, targetEnt, targetLoc, power, args, passTargeting);
				delayedSpells.remove(this);
				if (!ok && stopOnFail) cancelAll();
			} else cancelAll();

			delayedSpells = null;
		}

	}

}
