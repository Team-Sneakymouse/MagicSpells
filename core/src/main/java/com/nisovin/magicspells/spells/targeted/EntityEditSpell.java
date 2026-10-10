package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.List;

import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.managers.AttributeManager;

public class EntityEditSpell extends TargetedSpell implements TargetedEntitySpell {

	private ConfigData<Set<AttributeManager.AttributeInfo>> attributes;

	private boolean toggle;

	public EntityEditSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		// Attributes
		// - [AttributeName] [Number] [Operation]
		List<String> attributeList = getConfigStringList("attributes", null);
		if (attributeList != null && !attributeList.isEmpty()) {
			attributes = MagicSpells.getAttributeManager().getAttributesConfigData(attributeList, internalName + ".attributes");
		}

		toggle = getConfigBoolean("toggle", false);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> target = getTargetedEntity(caster, power, args);
			if (target.noTarget()) return noTarget(caster, args, target);

			applyAttributes(caster, target.target(), target.power(), args);
			playSpellEffects(caster, target.target(), target.power(), args);
			sendMessages(caster, target.target(), args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		playSpellEffects(caster, target, power, args);
		applyAttributes(caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(target)) return false;
		playSpellEffects(EffectPosition.TARGET, target, power, args);
		applyAttributes(null, target, power, args);
		return true;
	}

	private void applyAttributes(LivingEntity caster, LivingEntity entity, float power, String[] args) {
		if (attributes == null) return;
		Set<AttributeManager.AttributeInfo> resolved = attributes.get(new SpellData(caster, entity, power, args));
		if (resolved == null || resolved.isEmpty()) return;
		if (toggle) {
			boolean apply = false;
			for (AttributeManager.AttributeInfo info : resolved) {
				apply |= MagicSpells.getAttributeManager().hasEntityAttribute(entity, info);
			}
			if (!apply) MagicSpells.getAttributeManager().addEntityAttributes(entity, resolved);
			else MagicSpells.getAttributeManager().clearEntityAttributeModifiers(entity, resolved);
			return;
		}

		for (AttributeManager.AttributeInfo info : resolved) {
			if (MagicSpells.getAttributeManager().hasEntityAttribute(entity, info)) continue;
			MagicSpells.getAttributeManager().addEntityAttribute(entity, info);
		}
	}

}
