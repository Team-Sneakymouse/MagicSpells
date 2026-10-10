package com.nisovin.magicspells.spells;

import java.util.List;

import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class PermissionSpell extends InstantSpell {

	private ConfigData<Integer> duration;
	
	private List<String> permissionNodes;
	
	public PermissionSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		duration = getConfigDataInt("duration", 0);
		permissionNodes = getConfigStringList("permission-nodes", null);
	}

	@Override
	public CastResult cast(SpellData data) {
		if (permissionNodes != null) {
			int duration = this.duration.get(data.caster(), null, data.power(), data.args());
			if (duration <= 0) return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

			for (String node : permissionNodes) {
				data.caster().addAttachment(MagicSpells.plugin, node, true, duration);
			}

			playSpellEffects(EffectPosition.CASTER, data.caster(), data.power(), data.args());
		}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

}
