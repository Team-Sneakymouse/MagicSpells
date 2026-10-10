package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.HashSet;

import org.bukkit.entity.LivingEntity;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class TagEntitySpell extends TargetedSpell implements TargetedEntitySpell {

	private final String operation;
	private final String tag;

	private final boolean doReplacements;

	public TagEntitySpell(MagicConfig config, String spellName) {
		super(config, spellName);

		tag = getConfigString("tag", null);
		operation = getConfigString("operation", "add");

		doReplacements = MagicSpells.requireReplacement(tag);
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<LivingEntity> info = getTargetedEntity(data.caster(), data.power(), data.args());
					if (info.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), info), data);
		
					tag(data.caster(), info.target(), data.args());
					playSpellEffects(data.caster(), info.target(), info.power(), data.args());
					sendMessages(data.caster(), info.target(), data.args());
		
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							tag(data.caster(), data.target(), data.args());
							playSpellEffects(data.caster(), data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							tag(null, data.target(), data.args());
							playSpellEffects(EffectPosition.TARGET, data.target(), data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	private void tag(LivingEntity caster, LivingEntity target, String[] args) {
		String varTag = doReplacements ? MagicSpells.doReplacements(tag, caster, target, args) : tag;

		switch (operation) {
			case "add", "insert" -> target.addScoreboardTag(varTag);
			case "remove", "take" -> target.removeScoreboardTag(varTag);
			case "clear" -> {
				Set<String> tags = new HashSet<>(target.getScoreboardTags());
				tags.forEach(target::removeScoreboardTag);
			}
			default -> MagicSpells.error("TagEntitySpell '" + internalName + "' has an invalid operation defined!");
		}
	}

}
