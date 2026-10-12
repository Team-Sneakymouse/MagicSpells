package com.nisovin.magicspells.spells.targeted.ext;

import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import me.clip.placeholderapi.PlaceholderAPI;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
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
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		TargetInfo<Player> targetInfo = getTargetedPlayer(player, data.power(), data.args());
		if (targetInfo.noTarget())
			return new CastResult(noTarget(player, data.args(), targetInfo), data);

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
		if (!(data.caster() instanceof Player casterPlayer) || !(data.target() instanceof Player targetPlayer))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!validTargetList.canTarget(data.caster(), data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		setPlaceholders(casterPlayer, targetPlayer, data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
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
