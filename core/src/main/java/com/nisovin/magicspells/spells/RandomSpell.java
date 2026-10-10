package com.nisovin.magicspells.spells;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.castmodifiers.ModifierSet;

public class RandomSpell extends InstantSpell {

	private List<String> rawOptions;

	private RandomOptionSet options;

	private boolean pseudoRandom;
	private boolean checkIndividualCooldowns;
	private boolean checkIndividualModifiers;
	
	public RandomSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		rawOptions = getConfigStringList("spells", null);

		pseudoRandom = getConfigBoolean("pseudo-random", true);
		checkIndividualCooldowns = getConfigBoolean("check-individual-cooldowns", true);
		checkIndividualModifiers = getConfigBoolean("check-individual-modifiers", true);
	}
	
	@Override
	public void initialize() {
		super.initialize();
		
		options = new RandomOptionSet();
		for (String s : rawOptions) {
			String[] split = s.split(" ");
			Subspell spell = new Subspell(split[0]);
			int weight = 0;
			try {
				weight = Integer.parseInt(split[1]);
			} catch (NumberFormatException e) {
				// No op
			}

			if (spell.process() && weight > 0) options.add(new SpellOption(spell, weight));
			else MagicSpells.error("Invalid spell option on RandomSpell '" + internalName + "': " + s);
		}
		
		rawOptions.clear();
		rawOptions = null;
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			RandomOptionSet set = options;
			if (checkIndividualCooldowns || checkIndividualModifiers) {
				set = new RandomOptionSet();
				for (SpellOption o : options.randomOptionSetOptions) {
					if (checkIndividualCooldowns && o.spell.getSpell().onCooldown(caster)) continue;
					if (checkIndividualModifiers) {
						ModifierSet modifiers = o.spell.getSpell().getModifiers();
						if (modifiers != null && caster instanceof Player && !modifiers.check(caster)) continue;
					}
					set.add(o);
				}
			}
			if (!set.randomOptionSetOptions.isEmpty()) {
				Subspell spell = set.choose();
				return spell != null && spell.subcast(new SpellData(caster, power, args)) ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED;
			}
			return PostCastAction.ALREADY_HANDLED;
		}
		return PostCastAction.HANDLE_NORMALLY;
	}

	private static class SpellOption {

		private Subspell spell;
		private int weight;
		private int adjustedWeight;

		private SpellOption(Subspell spell, int weight) {
			this.spell = spell;
			this.weight = weight;
			adjustedWeight = weight;
		}
		
	}

	private class RandomOptionSet {

		private List<SpellOption> randomOptionSetOptions = new ArrayList<>();
		private int total = 0;

		private void add(SpellOption option) {
			randomOptionSetOptions.add(option);
			total += option.adjustedWeight;
		}

		private Subspell choose() {
			int r = random.nextInt(total);
			int x = 0;
			Subspell spell = null;
			for (SpellOption o : randomOptionSetOptions) {
				if (r < o.adjustedWeight + x && spell == null) {
					spell = o.spell;
					if (pseudoRandom) o.adjustedWeight = 0;
					else break;
				} else {
					x += o.adjustedWeight;
					if (pseudoRandom) o.adjustedWeight += o.weight;
				}
			}
			return spell;
		}
		
	}
	
}
