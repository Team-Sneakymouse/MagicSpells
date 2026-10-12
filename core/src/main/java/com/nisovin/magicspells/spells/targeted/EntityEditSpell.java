package com.nisovin.magicspells.spells.targeted;

import java.util.Set;
import java.util.List;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
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
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof LivingEntity caster))
			return new CastResult(noTarget(data.caster(), data.args()), data);
		TargetInfo<LivingEntity> target = getTargetedEntity(caster, data.power(), data.args());
		if (target.noTarget()) return new CastResult(noTarget(caster, data.args(), target), data);

		applyAttributes(caster, target.target(), target.power(), data.args());
		playSpellEffects(caster, target.target(), target.power(), data.args());
		sendMessages(caster, target.target(), data.args());

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
		LivingEntity caster = data.caster() instanceof LivingEntity livingCaster ? livingCaster : null;
		if (!(data.target() instanceof LivingEntity target))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			playSpellEffects(caster, target, data.power(), data.args());
			applyAttributes(caster, target, data.power(), data.args());
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
		if (!validTargetList.canTarget(data.target())) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		playSpellEffects(EffectPosition.TARGET, target, data.power(), data.args());
		applyAttributes(null, target, data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
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
