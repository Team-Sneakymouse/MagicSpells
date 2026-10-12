package com.nisovin.magicspells.spells.targeted;

import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
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
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		TargetInfo<LivingEntity> targetInfo = getTargetedEntity(player, data.power(), data.args());
					if (targetInfo.noTarget()) return new CastResult(noTarget(player, data.args(), targetInfo), data);
					LivingEntity target = targetInfo.target();
		
					playSpellEffects(player, target, targetInfo.power(), data.args());
					String value = dataElement.apply(target);
					MagicSpells.getVariableManager().set(variableName, player, value);
		
					sendMessages(player, target, data.args());
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
		if (!(data.caster() instanceof Player) || !validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
				playSpellEffects(data.caster(), data.target(), data.power(), data.args());
				if (!(data.target() instanceof LivingEntity livingTarget)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
				String value = dataElement.apply(livingTarget);
				MagicSpells.getVariableManager().set(variableName, (Player) data.caster(), value);
				return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}


}
