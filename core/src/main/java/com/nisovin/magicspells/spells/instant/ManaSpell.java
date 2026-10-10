package com.nisovin.magicspells.spells.instant;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.mana.ManaChangeReason;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class ManaSpell extends InstantSpell {

	private ConfigData<Integer> mana;

	private ConfigData<Boolean> powerAffectsMana;

	public ManaSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		mana = getConfigDataInt("mana", 25);
		powerAffectsMana = getConfigDataBoolean("power-affects-mana", true);
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		int amount = mana.get(player, null, data.power(), data.args());
		if (Boolean.TRUE.equals(powerAffectsMana.get(player, null, data.power(), data.args())))
			amount = Math.round(amount * data.power());

		boolean added = MagicSpells.getManaHandler().addMana(player, amount, ManaChangeReason.OTHER);
		if (!added)
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		playSpellEffects(EffectPosition.CASTER, player, data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


}
