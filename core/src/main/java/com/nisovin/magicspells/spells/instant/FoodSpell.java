package com.nisovin.magicspells.spells.instant;

import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class FoodSpell extends InstantSpell {

	private ConfigData<Integer> food;

	private ConfigData<Float> saturation;
	private ConfigData<Float> maxSaturation;
	
	public FoodSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		food = getConfigDataInt("food", 4);
		saturation = getConfigDataFloat("saturation", 2.5F);
		maxSaturation = getConfigDataFloat("max-saturation", 0F);
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		int f = Math.min(player.getFoodLevel() + food.get(player, null, data.power(), data.args()), 20);
					player.setFoodLevel(f);
		
					float saturation = this.saturation.get(player, null, data.power(), data.args());
					float maxSaturation = this.maxSaturation.get(player, null, data.power(), data.args());
		
					float s = Math.min(player.getSaturation() + saturation, maxSaturation);
					player.setSaturation(s);
		
					playSpellEffects(EffectPosition.CASTER, player, data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


}
