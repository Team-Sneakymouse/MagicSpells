package com.nisovin.magicspells.spells.instant;

import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.spells.TargetedEntityFromLocationSpell;

public class VelocitySpell extends InstantSpell implements TargetedEntitySpell, TargetedEntityFromLocationSpell {

	private final Set<UUID> jumping;

	private final ConfigData<Double> speed;

	private boolean cancelDamage;
	private ConfigData<Boolean> powerAffectsSpeed;
	private boolean addVelocityInstead;

	public VelocitySpell(MagicConfig config, String spellName) {
		super(config, spellName);

		jumping = new HashSet<>();

		speed = getConfigDataDouble("speed", 40);

		cancelDamage = getConfigBoolean("cancel-damage", true);
		addVelocityInstead = getConfigBoolean("add-velocity-instead", false);
		powerAffectsSpeed = getConfigDataBoolean("power-affects-speed", true);
	}

	public boolean isJumping(LivingEntity livingEntity) {
		return jumping.contains(livingEntity.getUniqueId());
	}

	@Override
	public CastResult cast(SpellData data) {
		LivingEntity caster = data.caster();
		launch(data.target(caster));
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

	@Override
	public CastResult castAtEntityFromLocation(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		} else if (!validTargetList.canTarget(data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!launch(data))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtEntityFromLocation(LivingEntity caster, Location from, LivingEntity target, float power,
			String[] args) {
		return castAtEntityFromLocation(new SpellData(caster, target, from, power, args)).action()
				!= PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtEntityFromLocation(Location from, LivingEntity target, float power, String[] args) {
		return castAtEntityFromLocation(new SpellData(null, target, from, power, args)).action()
				!= PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		} else if (!validTargetList.canTarget(data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!launch(data.location(null)))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		return castAtEntity(new SpellData(null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}

	private boolean launch(SpellData data) {
		LivingEntity target = data.target();
		if (target == null)
			return false;

		LivingEntity caster = data.caster();
		float power = data.power();
		String[] args = data.args();

		Location from = data.location();
		if (from == null)
			from = target.getLocation();

		double speed = this.speed.get(caster, caster, power, args) / 10;
		if (Boolean.TRUE.equals(powerAffectsSpeed.get(caster, target, power, args)))
			speed *= power;

		Vector velocity = from.getDirection().normalize().multiply(speed);

		if (addVelocityInstead)
			target.setVelocity(target.getVelocity().add(velocity));
		else
			target.setVelocity(velocity);
		jumping.add(target.getUniqueId());

		if (caster != null)
			playSpellEffects(caster, target, data);
		else
			playSpellEffects(EffectPosition.TARGET, target, data);

		return true;
	}

	@EventHandler
	public void onEntityDamage(EntityDamageEvent event) {
		if (event.getCause() != EntityDamageEvent.DamageCause.FALL)
			return;
		LivingEntity livingEntity = (LivingEntity) event.getEntity();
		if (!jumping.remove(livingEntity.getUniqueId()))
			return;
		playSpellEffects(EffectPosition.TARGET, livingEntity.getLocation(), new SpellData(livingEntity));
		if (cancelDamage)
			event.setCancelled(true);
	}

	public Set<UUID> getJumping() {
		return jumping;
	}

	public boolean shouldCancelDamage() {
		return cancelDamage;
	}

	public void setCancelDamage(boolean cancelDamage) {
		this.cancelDamage = cancelDamage;
	}

	public boolean shouldAddVelocityInstead() {
		return addVelocityInstead;
	}

	public void setAddVelocityInstead(boolean addVelocityInstead) {
		this.addVelocityInstead = addVelocityInstead;
	}

}
