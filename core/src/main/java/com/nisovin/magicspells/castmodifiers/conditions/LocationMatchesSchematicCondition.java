package com.nisovin.magicspells.castmodifiers.conditions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.castmodifiers.Condition;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;

import com.nisovin.magicspells.util.compat.ClipboardFormatsCompat;

public class LocationMatchesSchematicCondition extends Condition {

	private File file;
	private Clipboard clipboard;
	private boolean matchAir = false;

	@Override
	public boolean initialize(String var) {
		if (var == null || var.isBlank())
			return false;

		matchAir = var.endsWith(";true");

		File folder = new File(MagicSpells.plugin.getDataFolder(), "schematics");
		if (!folder.exists())
			folder.mkdir();

		String name = var.replace(";true", "").replace(";false", "").trim();
		if (name.isEmpty())
			return false;

		file = new File(folder, name);
		if (!file.isFile()) {
			MagicSpells.error("locationmatchesschematic: schematic file not found: " + file.getPath());
			return false;
		}

		ClipboardFormat format = ClipboardFormatsCompat.find(file);
		if (format == null) {
			MagicSpells.error("locationmatchesschematic: unknown clipboard format: " + file.getPath());
			return false;
		}

		try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
			clipboard = reader.read();
		} catch (IOException e) {
			MagicSpells.handleException(e);
			return false;
		}

		return clipboard != null;
	}

	@Override
	public boolean check(LivingEntity caster) {
		return matchesSchematic(caster.getLocation());
	}

	@Override
	public boolean check(LivingEntity caster, LivingEntity target) {
		return matchesSchematic(target.getLocation());
	}

	@Override
	public boolean check(LivingEntity caster, Location location) {
		return matchesSchematic(location);
	}

	private boolean matchesSchematic(Location location) {
		BlockVector3 origin = this.clipboard.getOrigin();

		for (BlockVector3 pos : this.clipboard.getRegion()) {
			BlockVector3 pos_ = BlockVector3.at(pos.x(), pos.y(), pos.z());

			BlockData data = BukkitAdapter.adapt(this.clipboard.getBlock(pos_));

			Block bl = location.getBlock().getRelative(pos_.x() - origin.x(), pos_.y() - origin.y(),
					pos_.z() - origin.z());

			if (!matchAir && data.getMaterial().isAir())
				continue;

			if (!data.matches(bl.getBlockData())) {
				return false;
			}
		}

		return true;
	}

}
