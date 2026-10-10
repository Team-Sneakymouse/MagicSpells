package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scoreboard.Objective;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.variables.variabletypes.GlobalStringVariable;
import com.nisovin.magicspells.variables.variabletypes.PlayerStringVariable;

public class ScoreboardDataSpell extends TargetedSpell implements TargetedEntitySpell {

	private String variableName;
	private String objectiveName;
	private Objective objective;

	public ScoreboardDataSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		variableName = getConfigString("variable-name", "");
		objectiveName = getConfigString("objective-name", "");
	}

	@Override
	public void initialize() {
		if (objectiveName == null) {
			MagicSpells.error("ScoreboardDataSpell '" + internalName + "' has an invalid objective name defined for objective-name!");
			return;
		}

		objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(objectiveName);
		if (objective == null) {
			MagicSpells.error("ScoreboardDataSpell '" + internalName + "' has an objective name defined for objective-name that could not be resolved as an existing objective!");
			objectiveName = null;
		}
	}

	@Override
	public void initializeVariables() {
		super.initializeVariables();

		if (variableName.isEmpty() || MagicSpells.getVariableManager().getVariable(variableName) == null) {
			MagicSpells.error("ScoreboardDataSpell '" + internalName + "' has an invalid variable-name defined!");
		}
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL && caster instanceof Player player) {
			TargetInfo<LivingEntity> info = getTargetedEntity(player, power, args);
			if (info.noTarget()) return noTarget(caster, args, info);

			setScore(player, info.target());
			playSpellEffects(player, info.target(), info.power(), args);
			sendMessages(caster, info.target(), args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(caster instanceof Player player) || !validTargetList.canTarget(caster, target)) return false;

		setScore(player, target);
		playSpellEffects(caster, target, power, args);

		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return false;
	}

	private void setScore(Player caster, LivingEntity target) {
		if (objective == null) return;

		Variable variable = MagicSpells.getVariableManager().getVariable(variableName);
		if (variable == null) return;

		int score = objective.getScoreFor(target).getScore();

		if (variable instanceof GlobalStringVariable || variable instanceof PlayerStringVariable)
			variable.parseAndSet(caster, String.valueOf(score));
		else variable.set(caster, score);
	}

}