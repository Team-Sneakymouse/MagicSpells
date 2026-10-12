package com.nisovin.magicspells.spells;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.util.ValidTargetChecker;

public abstract class TargetedSpell extends InstantSpell {

	protected boolean targetSelf;
	protected boolean alwaysActivate;
	protected boolean playFizzleSound;

	protected String spellNameOnFail;
	protected Subspell spellOnFail;

	protected String strNoTarget;
	protected String strCastTarget;

	public TargetedSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		targetSelf = getConfigBoolean("target-self", false);
		alwaysActivate = getConfigBoolean("always-activate", false);
		playFizzleSound = getConfigBoolean("play-fizzle-sound", false);

		spellNameOnFail = getConfigString("spell-on-fail", "");

		strNoTarget = getConfigString("str-no-target", "");
		strCastTarget = getConfigString("str-cast-target", "");

	}

	@Override
	public void initialize() {
		super.initialize();

		if (spellNameOnFail.isEmpty())
			return;

		spellOnFail = new Subspell(spellNameOnFail);
		if (!spellOnFail.process()) {
			spellOnFail = null;
			MagicSpells.error("Spell '" + internalName + "' has an invalid spell-on-fail defined!");
		}
	}

	public void sendMessages(Entity caster, Entity target) {
		sendMessages(caster, target, null, null);
	}

	public void sendMessages(Entity caster, Entity target, Location location) {
		sendMessages(caster, target, location, null);
	}

	public void sendMessages(Entity caster, Entity target, String[] args) {
		sendMessages(caster, target, null, args);
	}

	public void sendMessages(Entity caster, Location location, String[] args) {
		sendMessages(caster, null, location, args);
	}

	public void sendMessages(Entity caster, Entity target, Location location, String[] args) {
		String casterName = getTargetName(caster);
		String targetName = getTargetName(target);

		sendMessage(strCastSelf, caster, caster, target, location, args, "%a", casterName, "%t", targetName);
		sendMessage(strCastTarget, target, caster, target, location, args, "%a", casterName, "%t", targetName);
		sendMessageNear(caster, target, strCastOthers, args, "%a", casterName, "%t", targetName);
	}

	protected String getTargetName(Entity target) {
		if (target == null)
			return "none";
		if (target instanceof Player)
			return target.getName();
		String name = MagicSpells.getEntityNames().get(target.getType());
		if (name != null)
			return name;
		return "unknown";
	}

	/**
	 * Checks whether two locations are within a certain distance from each other.
	 * 
	 * @param loc1  The first location
	 * @param loc2  The second location
	 * @param range The maximum distance
	 * @return true if the distance is less than the range, false otherwise
	 */
	protected boolean inRange(Location loc1, Location loc2, int range) {
		return loc1.distanceSquared(loc2) < range * range;
	}

	/**
	 * Plays the fizzle sound if it is enabled for this spell.
	 */
	protected void fizzle(Entity livingEntity) {
		if (!playFizzleSound || !(livingEntity instanceof Player player))
			return;
		player.playEffect(livingEntity.getLocation(), Effect.EXTINGUISH, null);
	}

	@Override
	protected TargetInfo<LivingEntity> getTargetedEntity(Entity caster, float power, boolean forceTargetPlayers,
			ValidTargetChecker checker) {
		return getTargetedEntity(caster, power, forceTargetPlayers, checker, null);
	}

	@Override
	protected TargetInfo<LivingEntity> getTargetedEntity(Entity caster, float power, boolean forceTargetPlayers,
			ValidTargetChecker checker, String[] args) {
		TargetInfo<Entity> info = getTargetedEntityAsEntity(caster, power, forceTargetPlayers, checker, args);
		Entity target = info.target();
		if (target == null) return new TargetInfo<>(null, info.power(), info.cancelled());
		if (!(target instanceof LivingEntity living)) return new TargetInfo<>(null, info.power(), false);
		return new TargetInfo<>(living, info.power(), info.cancelled());
	}

	@Override
	protected TargetInfo<Entity> getTargetedEntityAsEntity(Entity caster, float power, boolean forceTargetPlayers,
			ValidTargetChecker checker, String[] args) {
		if (targetSelf || validTargetList.canTargetSelf()) {
			SpellTargetEvent event = new SpellTargetEvent(this, caster, caster, power, args);
			Entity target = event.callEvent() ? event.getTarget() : null;
			return new TargetInfo<>(target, event.getPower(), event.isCastCancelled());
		}

		return super.getTargetedEntityAsEntity(caster, power, forceTargetPlayers, checker, args);
	}

	/**
	 * This should be called if a target should not be found. It sends the no target
	 * message
	 * and returns the appropriate return value.
	 * 
	 * @param livingEntity the casting living entity
	 * @return the appropriate PostCastAction value
	 */
	protected PostCastAction noTarget(Entity livingEntity) {
		return noTarget(livingEntity, strNoTarget, null, null);
	}

	/**
	 * This should be called if a target should not be found. It sends the no target
	 * message
	 * and returns the appropriate return value.
	 * 
	 * @param livingEntity the casting living entity
	 * @param args         arguments of spell
	 * @return the appropriate PostCastAction value
	 */
	protected PostCastAction noTarget(Entity livingEntity, String[] args) {
		return noTarget(livingEntity, strNoTarget, args, null);
	}

	/**
	 * This should be called if a target should not be found. It sends the no target
	 * message
	 * and returns the appropriate return value.
	 * 
	 * @param livingEntity the casting living entity
	 * @param info         targeting info
	 * @return the appropriate PostCastAction value
	 */
	protected PostCastAction noTarget(Entity livingEntity, TargetInfo<?> info) {
		return noTarget(livingEntity, strNoTarget, null, info);
	}

	/**
	 * This should be called if a target should not be found. It sends the no target
	 * message
	 * and returns the appropriate return value.
	 * 
	 * @param livingEntity the casting living entity
	 * @param args         arguments of spell
	 * @param info         targeting info
	 * @return the appropriate PostCastAction value
	 */
	protected PostCastAction noTarget(Entity livingEntity, String[] args, TargetInfo<?> info) {
		return noTarget(livingEntity, strNoTarget, args, info);
	}

	/**
	 * This should be called if a target should not be found. It sends the provided
	 * message
	 * and returns the appropriate return value.
	 * 
	 * @param livingEntity the casting living entity
	 * @param message      the message to send
	 * @return the appropriate PostCastAction value
	 */
	protected PostCastAction noTarget(Entity livingEntity, String message) {
		return noTarget(livingEntity, message, null, null);
	}

	/**
	 * This should be called if a target should not be found. It sends the provided
	 * message
	 * and returns the appropriate return value.
	 * 
	 * @param livingEntity the casting living entity
	 * @param message      the message to send
	 * @param args         arguments of spell
	 * @return the appropriate PostCastAction value
	 */
	protected PostCastAction noTarget(Entity livingEntity, String message, String[] args) {
		return noTarget(livingEntity, message, args, null);
	}

	/**
	 * This should be called if a target should not be found. It sends the provided
	 * message
	 * and returns the appropriate return value.
	 * 
	 * @param livingEntity the casting living entity
	 * @param message      the message to send
	 * @param info         targeting info
	 * @return the appropriate PostCastAction value
	 */
	protected PostCastAction noTarget(Entity livingEntity, String message, TargetInfo<?> info) {
		return noTarget(livingEntity, message, null, info);
	}

	/**
	 * This should be called if a target should not be found. It sends the provided
	 * message
	 * and returns the appropriate return value.
	 * 
	 * @param livingEntity the casting living entity
	 * @param message      the message to send
	 * @param args         arguments of spell
	 * @param info         targeting info
	 * @return the appropriate PostCastAction value
	 */
	protected PostCastAction noTarget(Entity livingEntity, String message, String[] args, TargetInfo<?> info) {
		if (info != null && info.cancelled())
			return PostCastAction.ALREADY_HANDLED;
		fizzle(livingEntity);
		sendMessage(message, livingEntity, args);
		if (spellOnFail != null)
			spellOnFail.subcast(new SpellData(livingEntity, info == null ? 1f : info.power(), args));
		return alwaysActivate ? PostCastAction.NO_MESSAGES : PostCastAction.ALREADY_HANDLED;
	}

}
