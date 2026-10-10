package com.nisovin.magicspells.spelleffects.trackers;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.spelleffects.SpellEffect;
import com.nisovin.magicspells.spelleffects.SpellEffect.SpellEffectActiveChecker;

public class BuffTracker extends EffectTracker implements Runnable {

	private ModifierResult result;

	public BuffTracker(Entity entity, SpellEffectActiveChecker checker, SpellEffect effect, SpellData data) {
		super(entity, checker, effect, data);
	}

	@Override
	public void run() {
		if (!entity.isValid() || !checker.isActive(entity) || effect == null) {
			stop();
			return;
		}

		if (entity instanceof LivingEntity livingEntity && effect.getModifiers() != null) {
			result = effect.getModifiers().apply(data.caster(livingEntity));
			data = result.data();

			if (!result.check()) return;
		}

		playEffects(entity, data);
	}

	private void playEffects(Entity entity, SpellData data) {
		if (!isEntityEffect) {
			effect.playEffect(entity, data);
			return;
		}

		if (!effect.isDraggingEntity().get(data)) {
			effectEntity = effect.playEntityEffect(entity.getLocation(), data);
			return;
		}

		if (effectEntity == null) {
			effectEntity = effect.playEntityEffect(entity.getLocation(), data);
			return;
		}

		effectEntity.teleport(effect.applyOffsets(entity.getLocation()));
	}

	@Override
	public void stop() {
		super.stop();
	}

}
