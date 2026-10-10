package com.nisovin.magicspells.spells;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.RegexUtil;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public final class MultiSpell extends InstantSpell {
	
	private static final Pattern RANGED_DELAY_PATTERN = Pattern.compile("DELAY [0-9]+ [0-9]+");
	private static final Pattern BASIC_DELAY_PATTERN = Pattern.compile("DELAY [0-9]+");

	private List<String> spellList;
	private List<ActionChance> actions;

	private boolean castWithItem;
	private boolean castByCommand;
	private ConfigData<Boolean> customSpellCastChance;
	private ConfigData<Boolean> castRandomSpellInstead;
	private ConfigData<Boolean> enableIndividualChances;

	public MultiSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		actions = new ArrayList<>();
		spellList = getConfigStringList("spells", null);

		castWithItem = getConfigBoolean("can-cast-with-item", true);
		castByCommand = getConfigBoolean("can-cast-by-command", true);
		customSpellCastChance = getConfigDataBoolean("enable-custom-spell-cast-chance", false);
		castRandomSpellInstead = getConfigDataBoolean("cast-random-spell-instead", false);
		enableIndividualChances = getConfigDataBoolean("enable-individual-chances", false);
	}

	@Override
	public void initialize() {
		super.initialize();
		if (spellList != null) {
			for (String s : spellList) {
				String[] parts = s.split(":");
				double chance = parts.length == 2 ? Double.parseDouble(parts[1]) : 0.0D;
				s = parts[0];
				if (RegexUtil.matches(RANGED_DELAY_PATTERN, s)) {
					String[] splits = s.split(" ");
					int minDelay = Integer.parseInt(splits[1]);
					int maxDelay = Integer.parseInt(splits[2]);
					actions.add(new ActionChance(new Action(minDelay, maxDelay), chance));
				} else if (RegexUtil.matches(BASIC_DELAY_PATTERN, s)) {
					int delay = Integer.parseInt(s.split(" ")[1]);
					actions.add(new ActionChance(new Action(delay), chance));
				} else {
					Subspell spell = new Subspell(s);
					if (spell.process()) actions.add(new ActionChance(new Action(spell), chance));
					else MagicSpells.error("MultiSpell '" + internalName + "' has an invalid spell '" + s + "' defined!");
				}
			}
		}
		spellList = null;
	}

	@Override
	public CastResult cast(SpellData data) {
		boolean castRandom = Boolean.TRUE.equals(castRandomSpellInstead.get(data));
		boolean customChance = Boolean.TRUE.equals(customSpellCastChance.get(data));
		boolean individualChances = Boolean.TRUE.equals(enableIndividualChances.get(data));

		if (!castRandom) {
			int delay = 0;
			for (ActionChance actionChance : actions) {
				Action action = actionChance.action();
				if (action.isDelay()) {
					delay += action.getDelay();
				} else if (action.isSpell()) {
					Subspell spell = action.getSpell();
					if (delay == 0)
						spell.subcast(data);
					else
						MagicSpells.scheduleDelayedTask(new DelayedSpell(spell, data.caster(), data.power(), data.args()),
								delay);
				}
			}
		} else {
			int index;
			if (customChance) {
				int total = 0;
				for (ActionChance actionChance : actions) {
					total = (int) Math.round(total + actionChance.chance());
				}
				index = random.nextInt(total);
				int s = 0;
				int i = 0;
				while (s < index) {
					s = (int) Math.round(s + actions.get(i++).chance());
				}
				Action action = actions.get(Math.max(0, i - 1)).action();
				if (action.isSpell())
					action.getSpell().subcast(data);
			} else if (individualChances) {
				for (ActionChance actionChance : actions) {
					double chance = Math.random();
					if ((actionChance.chance() / 100.0D > chance) && actionChance.action().isSpell()) {
						Action action = actionChance.action();
						action.getSpell().subcast(data);
					}
				}
			} else {
				Action action = actions.get(random.nextInt(actions.size())).action();
				action.getSpell().subcast(data);
			}
		}
		playSpellEffects(EffectPosition.CASTER, data.caster(), data);
		return new CastResult(Spell.PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public Spell.PostCastAction castSpell(LivingEntity caster, Spell.SpellCastState state, float power, String[] args) {
		if (state != Spell.SpellCastState.NORMAL)
			return Spell.PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	@Override
	public boolean castFromConsole(final CommandSender sender, final String[] args) {
		boolean castRandom = Boolean.TRUE.equals(castRandomSpellInstead.get(null, 1f, args));
		boolean customChance = Boolean.TRUE.equals(customSpellCastChance.get(null, 1f, args));
		boolean individualChances = Boolean.TRUE.equals(enableIndividualChances.get(null, 1f, args));

		if (!castRandom) {
			int delay = 0;
			for (ActionChance actionChance : actions) {
				Action action = actionChance.action();
				if (action.isSpell()) {
					if (delay == 0) action.getSpell().getSpell().castFromConsole(sender, args);
					else {
						final Spell spell = action.getSpell().getSpell();
						MagicSpells.scheduleDelayedTask(() -> spell.castFromConsole(sender, args), delay);
					}
				} else if (action.isDelay()) delay += action.getDelay();
			}
		} else {
			int index;
			if (customChance) {
				int total = 0;
				for (ActionChance actionChance : actions) {
					total = (int) Math.round(total + actionChance.chance());
				}
				index = random.nextInt(total);
				int s = 0;
				int i = 0;
				while (s < index) {
					s = (int) Math.round(s + actions.get(i++).chance());
				}
				Action action = actions.get(Math.max(0, i - 1)).action();
				if (action.isSpell()) action.getSpell().getSpell().castFromConsole(sender, args);
			} else if (individualChances) {
				for (ActionChance actionChance : actions) {
					double chance = Math.random();
					if ((actionChance.chance() / 100.0D > chance) && actionChance.action().isSpell()) {
						actionChance.action().getSpell().getSpell().castFromConsole(sender, args);
					}
				}
			} else {
				Action action = actions.get(random.nextInt(actions.size())).action();
				if (action.isSpell()) action.getSpell().getSpell().castFromConsole(sender, args);
			}
		}
		return true;
	}

	@Override
	public boolean canCastWithItem() {
		return castWithItem;
	}

	@Override
	public boolean canCastByCommand() {
		return castByCommand;
	}

	private class Action {
		
		private Subspell spell;
		private int delay; // Also going to serve as minimum delay
		private boolean isRangedDelay = false;
		private int maxDelay;

		Action(Subspell spell) {
			this.spell = spell;
			delay = 0;
		}

		Action(int delay) {
			this.delay = delay;
			spell = null;
		}
		
		Action(int minDelay, int maxDelay) {
			this.delay = minDelay;
			this.maxDelay = maxDelay;
			spell = null;
			isRangedDelay = true;
		}

		public boolean isSpell() {
			return spell != null;
		}

		public Subspell getSpell() {
			return spell;
		}

		public boolean isDelay() {
			return delay > 0 || isRangedDelay;
		}

		private int getRandomDelay() {
			return random.nextInt(maxDelay - delay) + delay;
		}
		
		public int getDelay() {
			return isRangedDelay ? getRandomDelay() : delay;
		}
		
	}

	private static class DelayedSpell implements Runnable {
		
		private final Subspell spell;
		private final String[] args;
		private final float power;
		private final UUID casterUUID;

		DelayedSpell(Subspell spell, LivingEntity caster, float power, String[] args) {
			this.casterUUID = caster.getUniqueId();
			this.spell = spell;
			this.power = power;
			this.args = args;
		}

		@Override
		public void run() {
			Entity entity = Bukkit.getEntity(casterUUID);
			if (entity == null || !entity.isValid() || !(entity instanceof LivingEntity livingEntity)) return;
			spell.subcast(new SpellData(livingEntity, power, args));
		}
		
	}

	private record ActionChance(Action action, double chance) {


	}
	
}
