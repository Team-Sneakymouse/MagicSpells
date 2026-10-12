package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedEntitySpell;

public class RemoveEntitySpell extends TargetedSpell implements TargetedEntitySpell {

	private final boolean allowPlayers;

	public RemoveEntitySpell(MagicConfig config, String spellName) {
		super(config, spellName);

		allowPlayers = getConfigBoolean("allow-players", false);
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<Entity> info = getTargetedEntityAsEntity(data.caster(), data.power(), data.args());
		if (info.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), info), data);

		Entity target = info.target();
		if (!canRemove(target)) return new CastResult(noTarget(data.caster(), data.args()), data);

		remove(data.caster(), target, info.power(), data.args());
		sendMessages(data.caster(), target, data.args());
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
		Entity target = data.target();
		if (target == null || !canRemove(target))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);

		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), target))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			remove(data.caster(), target, data.power(), data.args());
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}

		if (!validTargetList.canTarget(target))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		remove(null, target, data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	private boolean canRemove(Entity target) {
		if (target instanceof Player) return allowPlayers;
		return true;
	}

	private void remove(Entity caster, Entity target, float power, String[] args) {
		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);
		target.remove();
	}

}
