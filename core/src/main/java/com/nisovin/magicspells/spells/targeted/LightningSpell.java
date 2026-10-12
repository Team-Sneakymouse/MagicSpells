package com.nisovin.magicspells.spells.targeted;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Pig;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.destroystokyo.paper.event.entity.EntityZapEvent;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;

/**
 * Charge/zap flags must be available during {@code strikeLightning()} itself —
 * {@link CreeperPowerEvent} / {@link EntityZapEvent} often fire before the returned
 * entity can be tagged. ThreadLocal covers the sync case; UUID map covers later ticks.
 */
public class LightningSpell extends TargetedSpell implements TargetedLocationSpell {

	/** bit0 = charge creepers, bit1 = zap pigs */
	private static final ThreadLocal<Byte> PENDING_FLAGS = new ThreadLocal<>();
	private static final Map<UUID, Byte> STRIKE_FLAGS = new ConcurrentHashMap<>();

	private ConfigData<Double> additionalDamage;

	private boolean zapPigs;
	private boolean noDamage;
	private boolean checkPlugins;
	private boolean chargeCreepers;
	private boolean requireEntityTarget;

	public LightningSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		additionalDamage = getConfigDataDouble("additional-damage", 0F);

		zapPigs = getConfigBoolean("zap-pigs", true);
		noDamage = getConfigBoolean("no-damage", false);
		checkPlugins = getConfigBoolean("check-plugins", true);
		chargeCreepers = getConfigBoolean("charge-creepers", true);
		requireEntityTarget = getConfigBoolean("require-entity-target", false);
	}

	@Override
	public CastResult cast(SpellData data) {
		Block target;
		LivingEntity entityTarget = null;
		if (requireEntityTarget) {
			TargetInfo<LivingEntity> targetInfo = getTargetedEntity(data.caster(), data.power(), data.args());
			if (targetInfo.noTarget())
				return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);

			entityTarget = targetInfo.target();
			data = data.target(entityTarget).power(targetInfo.power());

			double additionalDamage = this.additionalDamage.get(data.caster(), entityTarget, data.power(), data.args());

			if (checkPlugins) {
				MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(data.caster(),
						entityTarget, DamageCause.MAGIC, 1 + additionalDamage, this);
				if (!event.callEvent())
					return new CastResult(noTarget(data.caster(), data.args()), data);
			}

			target = entityTarget.getLocation().getBlock();
			if (additionalDamage > 0)
				entityTarget.damage(additionalDamage * data.power(), data.caster());
		} else {
			target = getTargetedBlock(data.caster(), data.power(), data.args());
			if (target == null)
				return new CastResult(noTarget(data.caster(), data.args()), data);

			SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, data.location(target.getLocation()));
			if (!event.callEvent())
				return new CastResult(noTarget(data.caster(), data.args()), data);

			data = event.getSpellData();
			target = data.location().getBlock();
		}

		castAtLocation(data.location(target.getLocation()));

		if (entityTarget != null) {
			sendMessages(data.caster(), entityTarget, data.args());
			return new CastResult(PostCastAction.NO_MESSAGES, data);
		}

		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}

	@Override
	public CastResult castAtLocation(SpellData data) {
		lightning(data.location());
		if (data.hasCaster())
			playSpellEffects(data.caster(), data.location(), data);
		else
			playSpellEffects(EffectPosition.CASTER, data.location(), data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}



	private void lightning(Location target) {
		if (noDamage) target.getWorld().strikeLightningEffect(target);
		else {
			byte packed = (byte) ((chargeCreepers ? 1 : 0) | (zapPigs ? 2 : 0));
			PENDING_FLAGS.set(packed);
			try {
				LightningStrike strike = target.getWorld().strikeLightning(target);
				UUID id = strike.getUniqueId();
				STRIKE_FLAGS.put(id, packed);
				MagicSpells.scheduleDelayedTask(() -> STRIKE_FLAGS.remove(id), 40);
			} finally {
				PENDING_FLAGS.remove();
			}
		}
	}

	private static Byte flagsFor(LightningStrike strike) {
		Byte pending = PENDING_FLAGS.get();
		if (pending != null) return pending;
		if (strike == null) return null;
		return STRIKE_FLAGS.get(strike.getUniqueId());
	}

	@EventHandler
	public void onCreeperCharge(CreeperPowerEvent event) {
		Byte packed = flagsFor(event.getLightning());
		if (packed == null) return;
		if ((packed & 1) == 0) event.setCancelled(true);
	}

	@EventHandler
	public void onEntityZap(EntityZapEvent event) {
		if (!(event.getEntity() instanceof Pig)) return;
		Byte packed = flagsFor(event.getBolt());
		if (packed == null) return;
		if ((packed & 2) == 0) event.setCancelled(true);
	}

}
