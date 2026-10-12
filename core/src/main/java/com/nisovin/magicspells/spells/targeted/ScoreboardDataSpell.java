package com.nisovin.magicspells.spells.targeted;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scoreboard.Objective;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
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
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> targetInfo = getTargetedEntity(data.caster(), data.power(), data.args());
		if (targetInfo.noTarget())
			return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);

		data = data.target(targetInfo.target()).power(targetInfo.power());
		CastResult result = castAtEntity(data);
		if (result.action() == PostCastAction.ALREADY_HANDLED)
			return result;

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
		if (!(data.caster() instanceof Player player) || !validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		
				setScore(player, data.target());
				playSpellEffects(data.caster(), data.target(), data.power(), data.args());
		
				return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


	private void setScore(Player caster, Entity target) {
		if (objective == null) return;

		Variable variable = MagicSpells.getVariableManager().getVariable(variableName);
		if (variable == null) return;

		int score = objective.getScoreFor(target).getScore();

		if (variable instanceof GlobalStringVariable || variable instanceof PlayerStringVariable)
			variable.parseAndSet(caster, String.valueOf(score));
		else variable.set(caster, score);
	}

}