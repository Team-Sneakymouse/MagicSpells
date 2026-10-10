package com.nisovin.magicspells.spells.buff;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class WindglideSpell extends BuffSpell {

	private final Map<UUID, SpellData> entities;

	private Subspell glideSpell;
	private Subspell collisionSpell;

	private final String glideSpellName;
	private final String collisionSpellName;

	private boolean cancelOnCollision;
	private boolean blockCollisionDmg;

	private int interval;

	private ConfigData<Double> height;
	private ConfigData<Double> velocity;

	private final GlideMonitor monitor;

	public WindglideSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		glideSpellName = getConfigString("spell", "");
		collisionSpellName = getConfigString("collision-spell", "");

		blockCollisionDmg = getConfigBoolean("block-collision-dmg", true);
		cancelOnCollision = getConfigBoolean("cancel-on-collision", false);

		height = getConfigDataDouble("height", 0F);
		velocity = getConfigDataDouble("velocity", 20F);

		interval = getConfigInt("interval", 4);
		if (interval <= 0) interval = 4;

		entities = new HashMap<>();

		monitor = new GlideMonitor();
	}

	@Override
	public void initialize() {
		super.initialize();

		glideSpell = new Subspell(glideSpellName);
		if (!glideSpell.process()) {
			glideSpell = null;
			if (!glideSpellName.isEmpty())
				MagicSpells.error("WindglideSpell " + internalName + " has an invalid spell defined: " + glideSpellName);
		}

		collisionSpell = new Subspell(collisionSpellName);
		if (!collisionSpell.process()) {
			collisionSpell = null;
			if (!collisionSpellName.isEmpty())
				MagicSpells.error("WindglideSpell " + internalName + " has an invalid collision-spell defined: " + collisionSpellName);
		}
	}

	@Override
	public boolean castBuff(LivingEntity entity, float power, String[] args) {
		entities.put(entity.getUniqueId(), new SpellData(entity, power, args));
		entity.setGliding(true);
		return true;
	}

	@Override
	public boolean isActive(LivingEntity entity) {
		return entities.containsKey(entity.getUniqueId());
	}

	@Override
	public void turnOffBuff(LivingEntity entity) {
		entities.remove(entity.getUniqueId());
		entity.setGliding(false);
	}

	@Override
	protected void turnOff() {
		for (EffectPosition pos : EffectPosition.values()) {
			cancelEffectForAllPlayers(pos);
		}

		for (UUID id : entities.keySet()) {
			Entity entity = Bukkit.getEntity(id);
			if (!(entity instanceof LivingEntity livingEntity)) continue;
			if (!entity.isValid()) continue;

			livingEntity.setGliding(false);
			turnOff(livingEntity);
		}

		entities.clear();
		monitor.stop();
	}

	@EventHandler
	public void onEntityGlide(EntityToggleGlideEvent e) {
		if (!(e.getEntity() instanceof LivingEntity livingEntity)) return;
		if (!isActive(livingEntity)) return;
		if (livingEntity.isGliding()) e.setCancelled(true);
	}

	@EventHandler
	public void onEntityCollision(EntityDamageEvent e) {
		if (e.getCause() != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return;
		if (!(e.getEntity() instanceof LivingEntity entity)) return;

		SpellData data = entities.get(entity.getUniqueId());
		if (data == null) return;

		if (blockCollisionDmg) e.setCancelled(true);
		if (cancelOnCollision) turnOff(entity);
		if (collisionSpell != null) collisionSpell.subcast(data.location(entity.getLocation()));
	}

	public Subspell getGlideSpell() {
		return glideSpell;
	}

	public void setGlideSpell(Subspell glideSpell) {
		this.glideSpell = glideSpell;
	}

	public Subspell getCollisionSpell() {
		return collisionSpell;
	}

	public void setCollisionSpell(Subspell collisionSpell) {
		this.collisionSpell = collisionSpell;
	}

	public boolean shouldCancelOnCollision() {
		return cancelOnCollision;
	}

	public void setCancelOnCollision(boolean cancelOnCollision) {
		this.cancelOnCollision = cancelOnCollision;
	}

	public boolean shouldBlockCollisionDamage() {
		return blockCollisionDmg;
	}

	public void setBlockCollisionDmg(boolean blockCollisionDmg) {
		this.blockCollisionDmg = blockCollisionDmg;
	}

	public int getInterval() {
		return interval;
	}

	public void setInterval(int interval) {
		this.interval = interval;
	}

	private class GlideMonitor implements Runnable {

		private final int taskId;

		private GlideMonitor() {
			taskId = MagicSpells.scheduleRepeatingTask(this, interval, interval);
		}

		@Override
		public void run() {
			Entity entity;
			SpellData data;

			double velocity;
			double height;

			Location eLoc;
			Vector v;

			for (UUID id : entities.keySet()) {
				entity = Bukkit.getEntity(id);
				if (entity == null || !entity.isValid()) continue;
				if (!(entity instanceof LivingEntity caster)) continue;

				data = entities.get(id);

				velocity = WindglideSpell.this.velocity.get(caster, null, data.power(), data.args()) / 10;
				height = WindglideSpell.this.height.get(caster, null, data.power(), data.args());

				eLoc = entity.getLocation();
				v = eLoc.getDirection().normalize().multiply(velocity).add(new Vector(0, height, 0));
				entity.setVelocity(v);

				if (glideSpell != null) glideSpell.subcast(data.location(eLoc));
				playSpellEffects(EffectPosition.SPECIAL, eLoc, data);
				addUseAndChargeCost(caster);
			}
		}

		public void stop() {
			MagicSpells.cancelTask(taskId);
		}

	}

}
