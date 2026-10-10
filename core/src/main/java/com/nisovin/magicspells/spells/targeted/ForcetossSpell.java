package com.nisovin.magicspells.spells.targeted;

import org.bukkit.util.Vector;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.events.MagicSpellsEntityDamageByEntityEvent;

public class ForcetossSpell extends TargetedSpell implements TargetedEntitySpell {

	private ConfigData<Integer> damage;

	private ConfigData<Double> vForce;
	private ConfigData<Double> hForce;

	private ConfigData<Float> rotation;

	private ConfigData<Boolean> checkPlugins;
	private ConfigData<Boolean> powerAffectsForce;
	private boolean addVelocityInstead;
	private ConfigData<Boolean> avoidDamageModification;

	public ForcetossSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		damage = getConfigDataInt("damage", 0);

		vForce = getConfigDataDouble("vertical-force", 10);
		hForce = getConfigDataDouble("horizontal-force", 20);

		rotation = getConfigDataFloat("rotation", 0);

		checkPlugins = getConfigDataBoolean("check-plugins", true);
		powerAffectsForce = getConfigDataBoolean("power-affects-force", true);
		addVelocityInstead = getConfigBoolean("add-velocity-instead", false);
		avoidDamageModification = getConfigDataBoolean("avoid-damage-modification", true);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL) {
			TargetInfo<LivingEntity> targetInfo = getTargetedEntity(caster, power, args);
			if (targetInfo.noTarget()) return noTarget(caster, args, targetInfo);

			toss(caster, targetInfo.target(), targetInfo.power(), args);
			sendMessages(caster, targetInfo.target(), args);

			return PostCastAction.NO_MESSAGES;
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!validTargetList.canTarget(caster, target)) return false;
		toss(caster, target, power, args);
		return true;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return false;
	}

	private void toss(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (target == null) return;
		if (caster == null) return;
		if (!caster.getLocation().getWorld().equals(target.getLocation().getWorld())) return;

		int damage = this.damage.get(caster, target, power, args);
		if (damage > 0) {
			double dmg = damage * power;
			if (Boolean.TRUE.equals(checkPlugins.get(caster, target, power, args))) {
				MagicSpellsEntityDamageByEntityEvent event = new MagicSpellsEntityDamageByEntityEvent(caster, target, DamageCause.MAGIC, damage, this);
				EventUtil.call(event);
				if (!Boolean.TRUE.equals(avoidDamageModification.get(caster, target, power, args))) dmg = event.getDamage();
			}
			target.damage(dmg);
		}

		Vector v;
		if (caster.equals(target)) v = caster.getLocation().getDirection();
		else v = target.getLocation().toVector().subtract(caster.getLocation().toVector());

		double hForce = this.hForce.get(caster, target, power, args) / 10;
		double vForce = this.vForce.get(caster, target, power, args) / 10;
		if (Boolean.TRUE.equals(powerAffectsForce.get(caster, target, power, args))) {
			hForce *= power;
			vForce *= power;
		}
		v.setY(0).normalize().multiply(hForce).setY(vForce);

		float rotation = this.rotation.get(caster, target, power, args);
		if (rotation != 0) Util.rotateVector(v, rotation);

		v = Util.makeFinite(v);

		if (addVelocityInstead) target.setVelocity(target.getVelocity().add(v));
		else target.setVelocity(v);

		playSpellEffects(caster, target, power, args);
	}

}
