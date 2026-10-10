package com.nisovin.magicspells.spells.targeted.ext;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import me.clip.placeholderapi.PlaceholderAPI;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;

// NOTE: PLACEHOLDERAPI IS REQUIRED FOR THIS
public class PlaceholderAPIDataSpell extends TargetedSpell implements TargetedEntitySpell {

	private final String variableName;
	private final String placeholderAPITemplate;
	private final boolean useTargetVariables;
	private final boolean setTargetVariable;
	private final boolean setTargetPlaceholders;

	public PlaceholderAPIDataSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		variableName = getConfigString("variable-name", null);
		placeholderAPITemplate = getConfigString("placeholderapi-template", "An admin forgot to set placeholderapi-template");
		useTargetVariables = getConfigBoolean("use-target-variables", true);
		setTargetVariable = getConfigBoolean("set-target-variable", false);
		setTargetPlaceholders = getConfigBoolean("set-target-placeholders", true);
	}

	@Override
	public void initializeVariables() {
		super.initializeVariables();

		if (variableName == null) {
			MagicSpells.error("variable-name is null for '" + internalName + "'");
			MagicSpells.error("In most cases, this should be set to the name of a string variable, but non string variables may work depending on values.");
			return;
		}

		if (MagicSpells.getVariableManager().getVariable(variableName) == null) {
			MagicSpells.error("Invalid variable-name on '" + internalName + "'");
			return;
		}

		// You have to REALLY screw up for this to happen.
		if (placeholderAPITemplate == null) {
			MagicSpells.error("placeholderapi-template is null (you made it worse than the default) in '" + internalName + "'");
			MagicSpells.error("This was probably because you put something similar to \"placeholderapi-template\" and did not specify a value.");
		}
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL && caster instanceof Player player) {
			TargetInfo<Player> targetInfo = getTargetedPlayer(player, power, args);
			if (targetInfo.noTarget()) return noTarget(player, args, null);
			Player target = targetInfo.target();

			setPlaceholders(player, target, targetInfo.power(), args);
			sendMessages(caster, target, args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(caster instanceof Player casterPlayer) || !(target instanceof Player targetPlayer)) return false;
		if (!validTargetList.canTarget(caster, target)) return false;
		setPlaceholders(casterPlayer, targetPlayer, power, args);
		return true;
	}

	private void setPlaceholders(Player caster, Player target, float power, String[] args) {
		String value = MagicSpells.doArgumentSubstitution(placeholderAPITemplate, args);

		Player variableCaster, variableTarget;
		if (useTargetVariables) {
			variableCaster = target;
			variableTarget = caster;
		} else {
			variableCaster = caster;
			variableTarget = target;
		}

		value = MagicSpells.doVariableReplacements(value, variableCaster, variableTarget);
		value = PlaceholderAPI.setBracketPlaceholders(setTargetPlaceholders ? target : caster, value);
		value = PlaceholderAPI.setPlaceholders(setTargetPlaceholders ? target : caster, value);

		MagicSpells.getVariableManager().set(variableName, setTargetVariable ? target : caster, value);
		playSpellEffects(caster, target, power, args);
	}

}
