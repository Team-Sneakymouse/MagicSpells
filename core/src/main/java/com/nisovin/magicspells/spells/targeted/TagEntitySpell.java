package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.HashSet;

import org.bukkit.entity.LivingEntity;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
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
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> info = getTargetedEntity(caster, power, args);
			if (info.noTarget()) return noTarget(caster, args, info);

			tag(caster, info.target(), args);
			playSpellEffects(caster, info.target(), info.power(), args);
			sendMessages(caster, info.target(), args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		tag(caster, target, args);
		playSpellEffects(caster, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target)) return false;
		tag(null, target, args);
		playSpellEffects(EffectPosition.TARGET, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return castAtEntity(target, power, null);
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
