package com.nisovin.magicspells.spells.targeted;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.entity.LightningStrike;
import org.bukkit.event.entity.PigZapEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.event.entity.CreeperPowerEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedLocationSpell;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;

public class LightningSpell extends TargetedSpell implements TargetedLocationSpell {

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
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			Block target;
			LivingEntity entityTarget = null;
			if (requireEntityTarget) {
				TargetInfo<LivingEntity> targetInfo = getTargetedEntity(caster, power, args);
				if (targetInfo.noTarget()) return noTarget(caster, args, targetInfo);

				entityTarget = targetInfo.target();
				power = targetInfo.power();

				double additionalDamage = this.additionalDamage.get(caster, entityTarget, power, args);

				if (checkPlugins) {
					MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(caster, entityTarget, DamageCause.MAGIC, 1 + additionalDamage, this);
					if (!event.callEvent()) return noTarget(caster, args);
				}

				target = entityTarget.getLocation().getBlock();
				if (additionalDamage > 0) entityTarget.damage(additionalDamage * power, caster);
			} else {
				target = getTargetedBlock(caster, power, args);
				if (target == null) return noTarget(caster, args);

				SpellTargetLocationEvent event = new SpellTargetLocationEvent(this, caster, target.getLocation(), power, args);
				if (!event.callEvent()) return noTarget(caster, args);

				target = event.getTargetLocation().getBlock();
			}

			lightning(target.getLocation());
			playSpellEffects(caster, target.getLocation(), power, args);

			if (entityTarget != null) {
				sendMessages(caster, entityTarget, args);
				return PostCastAction.NO_MESSAGES;
			}
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		lightning(target);
		playSpellEffects(caster, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power) {
		lightning(target);
		playSpellEffects(caster, target, power, null);
		return true;
	}

	@Override
	public boolean castAtLocation(Location target, float power, String[] args) {
		lightning(target);
		playSpellEffects(EffectPosition.CASTER, target, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtLocation(Location target, float power) {
		lightning(target);
		playSpellEffects(EffectPosition.CASTER, target, power, null);
		return true;
	}

	private void lightning(Location target) {
		if (noDamage) target.getWorld().strikeLightningEffect(target);
		else {
			LightningStrike strike = target.getWorld().strikeLightning(target);
			strike.setMetadata("MS" + internalName, new FixedMetadataValue(MagicSpells.plugin, new ChargeOption(chargeCreepers, zapPigs)));
		}
	}
	
	@EventHandler
	public void onCreeperCharge(CreeperPowerEvent event) {
		LightningStrike strike = event.getLightning();
		if (strike == null) return;
		List<MetadataValue> data = strike.getMetadata("MS" + internalName);
		if (data.isEmpty()) return;
		for (MetadataValue val : data) {
			ChargeOption option = (ChargeOption) val.value();
			if (option == null) continue;
			if (!option.chargeCreeper) event.setCancelled(true);
			break;
		}
	}
	
	@EventHandler
	public void onPigZap(PigZapEvent event) {
		LightningStrike strike = event.getLightning();
		List<MetadataValue> data = strike.getMetadata("MS" + internalName);
		if (data.isEmpty()) return;
		for (MetadataValue val: data) {
			ChargeOption option = (ChargeOption) val.value();
			if (option == null) continue;
			if (!option.changePig) event.setCancelled(true);
		}
	}

	private record ChargeOption(boolean chargeCreeper, boolean changePig) {}
	
}
