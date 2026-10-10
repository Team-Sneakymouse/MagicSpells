package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class SkinSpell extends TargetedSpell implements TargetedEntitySpell {
	
	private String texture;
	private String signature;
	
	public SkinSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		texture = getConfigString("texture", null);
		signature = getConfigString("signature", null);
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<Player> info = getTargetedPlayer(data.caster(), data.power(), data.args());
					if (info.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), info), data);
		
					Util.setSkin(info.target(), texture, signature);
					playSpellEffects(data.caster(), info.target(), info.power(), data.args());
					sendMessages(data.caster(), info.target(), data.args());
		
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!(data.target() instanceof Player player) || !validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							Util.setSkin(player, texture, signature);
							playSpellEffects(data.caster(), data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!(data.target() instanceof Player player) || !validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							Util.setSkin(player, texture, signature);
							playSpellEffects(EffectPosition.TARGET, data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



}
