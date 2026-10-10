package com.nisovin.magicspells.spells.targeted;

import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.util.data.DataLivingEntity;

public class DataSpell extends TargetedSpell implements TargetedEntitySpell {

	private String variableName;
	private Function<? super LivingEntity, String> dataElement;
	
	public DataSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		variableName = getConfigString("variable-name", "");

		dataElement = DataLivingEntity.getDataFunction(getConfigString("data-element", "uuid"));
	}
	
	@Override
	public void initialize() {
		if (dataElement == null) MagicSpells.error("DataSpell '" + internalName + "' has an invalid option defined for data-element!");
	}

	@Override
	public void initializeVariables() {
		super.initializeVariables();

		if (variableName.isEmpty() || MagicSpells.getVariableManager().getVariable(variableName) == null) {
			MagicSpells.error("DataSpell '" + internalName + "' has an invalid variable-name defined!");
		}
	}
	
	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL && caster instanceof Player player) {
			TargetInfo<LivingEntity> targetInfo = getTargetedEntity(player, power, args);
			if (targetInfo.noTarget()) return noTarget(player, args, targetInfo);
			LivingEntity target = targetInfo.target();

			playSpellEffects(player, target, targetInfo.power(), args);
			String value = dataElement.apply(target);
			MagicSpells.getVariableManager().set(variableName, player, value);

			sendMessages(caster, target, args);
			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(caster instanceof Player) || !validTargetList.canTarget(caster, target)) return false;
		playSpellEffects(caster, target, power, args);
		String value = dataElement.apply(target);
		MagicSpells.getVariableManager().set(variableName, (Player) caster, value);
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

}
