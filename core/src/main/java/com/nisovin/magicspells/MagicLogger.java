package com.nisovin.magicspells;

import java.io.File;
import java.util.Date;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.events.SpellLearnEvent;
import com.nisovin.magicspells.events.SpellCastedEvent;
import com.nisovin.magicspells.events.SpellForgetEvent;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;

public class MagicLogger implements Listener {

	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

	private FileWriter writer;
	
	MagicLogger(MagicSpells plugin) {
		File file = new File(plugin.getDataFolder(), "log-" + System.currentTimeMillis() + ".txt");
		try {
			writer = new FileWriter(file, true);
			MagicSpells.registerEvents(this);
		} catch (IOException e) {
			MagicSpells.handleException(e);
		}
	}
	
	void disable() {
		if (writer != null) {
			try {
				writer.flush();
				writer.close();
			} catch (IOException e) {
				MagicSpells.handleException(e);
			}
		}
		writer = null;
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onSpellLearn(SpellLearnEvent event) {
		log("LEARN" + 
				"; spell=" + event.getSpell().getInternalName() + 
				"; player=" + event.getLearner().getName() + 
				"; loc=" + formatLoc(event.getLearner().getLocation()) +
				"; source=" + event.getSource().name() +
				"; teacher=" + getTeacherName(event.getTeacher()) +
				"; cancelled=" + event.isCancelled());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onSpellForget(SpellForgetEvent event) {
		log("FORGET" + 
				"; spell=" + event.getSpell().getInternalName() + 
				"; player=" + event.getForgetter().getName() + 
				"; loc=" + formatLoc(event.getForgetter().getLocation()) +
				"; cancelled=" + event.isCancelled());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onSpellCast(SpellCastEvent event) {
		log("BEGIN CAST" + 
				"; spell=" + event.getSpell().getInternalName() + 
				"; caster=" + event.getCaster().getName() + 
				"; loc=" + formatLoc(event.getCaster().getLocation()) +
				"; state=" + event.getSpellCastState().name() +
				"; power=" + event.getPower() +
				"; cancelled=" + event.isCancelled());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onSpellTarget(SpellTargetEvent event) {
		Entity caster = event.getCaster();
		log("TARGET ENTITY" +
				"; spell=" + event.getSpell().getInternalName() + 
				"; caster=" + (caster != null ? caster.getName() : "null") + 
				"; casterLoc=" + (caster != null ? formatLoc(caster.getLocation()) : "null") +
				": target=" + getTargetName(event.getTarget()) + 
				"; targetLoc=" + formatLoc(event.getTarget().getLocation()) +
				"; cancelled=" + event.isCancelled());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onSpellTargetLocation(SpellTargetLocationEvent event) {
		log("TARGET LOCATION" +
				"; spell=" + event.getSpell().getInternalName() + 
				"; caster=" + event.getCaster().getName() + 
				"; casterLoc=" + formatLoc(event.getCaster().getLocation()) +
				"; targetLoc=" + formatLoc(event.getTargetLocation()) +
				"; cancelled=" + event.isCancelled());
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onSpellCasted(SpellCastedEvent event) {
		log("END CAST" +
				"; spell=" + event.getSpell().getInternalName() + 
				"; caster=" + event.getCaster().getName() + 
				"; loc=" + formatLoc(event.getCaster().getLocation()) +
				"; state=" + event.getSpellCastState().name() +
				"; power=" + event.getPower() +
				"; result=" + event.getPostCastAction().name());
	}
	
	private String formatLoc(Location location) {
		return location.getWorld().getName() + ',' + location.getBlockX() + ',' + location.getBlockY() + ',' + location.getBlockZ();
	}
	
	private String getTargetName(Entity target) {
		if (target instanceof Player) return target.getName();
		return target.getType().name();
	}
	
	private String getTeacherName(Object o) {
		if (o == null) return "none";
		if (o instanceof Player player) return "player-" + player.getName();
		if (o instanceof Spell spell) return "spell-" + spell.getInternalName();
		if (o instanceof Block block) return "block-" + formatLoc(block.getLocation());
		return o.toString();
	}
	
	private void log(String string) {
		if (writer == null) return;
		try {
			writer.write('[' + dateFormat.format(new Date()) + "] " + string + '\n');
		} catch (IOException e) {
			DebugHandler.debugIOException(e);
		}
	}
	
}
