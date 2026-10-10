package com.nisovin.magicspells.spells.instant;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.InstantSpell;

public class VariableCastSpell extends InstantSpell {

	private String variableName;
	private String strDoesntContainSpell;

	public VariableCastSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		variableName = getConfigString("variable-name", null);
		strDoesntContainSpell = getConfigString("str-doesnt-contain-spell", "You do not have a valid spell in memory");
	}

	@Override
	public void initializeVariables() {
		super.initializeVariables();

		if (MagicSpells.getVariableManager().getVariable(variableName) == null) {
			MagicSpells.error("VariableCastSpell '" + internalName + "' has an invalid variable-name defined!");
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		if (variableName == null) return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
					String value = MagicSpells.getVariableManager().getVariable(variableName).getStringValue(player);
		
					Spell toCast = MagicSpells.getSpellByInternalName(value);
					if (toCast == null) {
						sendMessage(player, strDoesntContainSpell);
						return new CastResult(PostCastAction.NO_MESSAGES, data);
					}
					toCast.cast(player, data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	public String getVariableName() {
		return variableName;
	}

	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	public String getStrDoesntContainSpell() {
		return strDoesntContainSpell;
	}

	public void setStrDoesntContainSpell(String strDoesntContainSpell) {
		this.strDoesntContainSpell = strDoesntContainSpell;
	}

}
