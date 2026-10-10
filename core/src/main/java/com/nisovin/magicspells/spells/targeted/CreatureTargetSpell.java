package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.InstantSpell;

public class CreatureTargetSpell extends InstantSpell {

	private String targetSpellName;
	private Subspell targetSpell;

	public CreatureTargetSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		targetSpellName = getConfigString("spell", "");
	}

	@Override
	public void initialize() {
		super.initialize();

		targetSpell = new Subspell(targetSpellName);
		if (!targetSpell.process()) {
			targetSpell = null;
			if (!targetSpellName.isEmpty()) MagicSpells.error("CreatureTargetSpell '" + internalName + "' has an invalid spell defined!");
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Creature creature))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		LivingEntity target = creature.getTarget();
		if (target == null || !target.isValid())
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		playSpellEffects(data.caster(), target, data);

		if (targetSpell != null)
			targetSpell.subcast(data.target(target).location(data.caster().getLocation()));

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

}
