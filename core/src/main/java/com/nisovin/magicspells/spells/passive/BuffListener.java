package com.nisovin.magicspells.spells.passive;

import java.util.EnumSet;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.spells.BuffSpell;
import com.nisovin.magicspells.util.OverridePriority;
import com.nisovin.magicspells.events.SpellLearnEvent;
import com.nisovin.magicspells.events.SpellForgetEvent;
import com.nisovin.magicspells.spells.passive.util.PassiveListener;

// No trigger variable currently used
public class BuffListener extends PassiveListener {

	private final EnumSet<GameMode> invalidGameModes = EnumSet.of(GameMode.CREATIVE, GameMode.SPECTATOR);

	@Override
	public void initialize(String var) {
		for (Subspell s : passiveSpell.getActivatedSpells()) {
			if (!(s.getSpell() instanceof BuffSpell buff)) continue;
			buff.setAsEverlasting();
		}

		for (Player player : Bukkit.getOnlinePlayers()) {
			on(player);
		}

		Util.forEachLivingInLoadedChunks(livingEntity -> {
			if (livingEntity instanceof Player) return;
			if (!canTrigger(livingEntity)) return;
			on(livingEntity);
		});
	}

	@OverridePriority
	@EventHandler
	public void onEntitySpawn(EntitySpawnEvent event) {
		Entity entity = event.getEntity();
		if (entity instanceof Player) return;
		if (!(entity instanceof LivingEntity)) return;
		on((LivingEntity) entity);
	}

	@OverridePriority
	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		for (Entity entity : event.getChunk().getEntities()) {
			if (!(entity instanceof LivingEntity)) continue;
			on((LivingEntity) entity);
		}
	}

	@OverridePriority
	@EventHandler
	public void onChunkUnload(ChunkUnloadEvent event) {
		for (Entity entity : event.getChunk().getEntities()) {
			if (!(entity instanceof LivingEntity)) continue;
			off((LivingEntity) entity);
		}
	}

	@OverridePriority
	@EventHandler
	public void onGameModeChange(PlayerGameModeChangeEvent event) {
		if (invalidGameModes.contains(event.getNewGameMode())) off(event.getPlayer());
		else MagicSpells.scheduleDelayedTask(() -> on(event.getPlayer()), 1);
	}

	@OverridePriority
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		on(event.getPlayer());
	}

	@OverridePriority
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		off(event.getPlayer());
	}

	@OverridePriority
	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		off(event.getEntity());
	}

	@OverridePriority
	@EventHandler
	public void onPlayerRespawn(final PlayerRespawnEvent event) {
		MagicSpells.scheduleDelayedTask(() -> on(event.getPlayer()), 1);
	}

	@OverridePriority
	@EventHandler
	public void onSpellLearn(final SpellLearnEvent event) {
		if (!passiveSpell.equals(event.getSpell())) return;
		MagicSpells.scheduleDelayedTask(() -> on(event.getLearner()), 1);
	}

	@OverridePriority
	@EventHandler
	public void onSpellForget(SpellForgetEvent event) {
		Spell spell = event.getSpell();
		if (spell != null && !passiveSpell.equals(spell)) return;
		off(event.getForgetter());
	}

	private void on(LivingEntity entity) {
		if (!canTrigger(entity, false)) return;
		if (!hasSpell(entity)) return;

		for (Subspell s : passiveSpell.getActivatedSpells()) {
			if (s.getSpell() instanceof BuffSpell buff && buff.isActive(entity)) continue;
			s.subcast(new SpellData(entity, 1F, null));
		}
	}

	private void off(LivingEntity entity) {
		if (!canTrigger(entity, false)) return;
		if (!hasSpell(entity)) return;

		for (Subspell s : passiveSpell.getActivatedSpells()) {
			if (!(s.getSpell() instanceof BuffSpell buff)) continue;
			if (!buff.isActive(entity)) continue;
			buff.turnOff(entity);
		}
	}

}
