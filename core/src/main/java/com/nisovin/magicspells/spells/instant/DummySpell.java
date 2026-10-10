package com.nisovin.magicspells.spells.instant;

import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;

import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class DummySpell extends InstantSpell {

	public DummySpell(MagicConfig config, String spellName) {
		super(config, spellName);
	}

	@Override
	public CastResult cast(SpellData data) {
		playSpellEffects(EffectPosition.CASTER, data.caster(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		return true;
	}

}
