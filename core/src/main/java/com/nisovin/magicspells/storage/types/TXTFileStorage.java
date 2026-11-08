package com.nisovin.magicspells.storage.types;

import java.io.File;

import java.util.Set;
import java.util.List;
import java.util.Scanner;
import java.util.ArrayList;

import java.nio.charset.StandardCharsets;

import org.bukkit.entity.Player;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastItem;
import com.nisovin.magicspells.util.io.AtomicFiles;
import com.nisovin.magicspells.handlers.DebugHandler;
import com.nisovin.magicspells.storage.StorageHandler;
import com.sneakyitems.util.magicitems.MagicItemDataParser;

public class TXTFileStorage extends StorageHandler {

	public TXTFileStorage(MagicSpells plugin) {
		super(plugin);
	}

	@Override
	public void initialize() {

	}

	@Override
	public void load(Spellbook spellbook) {
		Player pl = spellbook.getPlayer();
		String worldName = pl.getWorld().getName();
		String id = Util.getUniqueId(pl);
		try {
			MagicSpells.debug("  ...retrieving files...");
			File file;
			String path = "spellbooks" + File.separator;
			if (MagicSpells.arePlayerSpellsSeparatedPerWorld()) {
				File folder = new File(plugin.getDataFolder(), path + worldName);
				if (!folder.exists()) folder.mkdir();

				file = new File(plugin.getDataFolder(), path + worldName + File.separator + id + ".txt");
				if (!file.exists()) {
					File file2 = new File(plugin.getDataFolder(), path + worldName + File.separator + pl.getName().toLowerCase() + ".txt");
					if (file2.exists()) file2.renameTo(file);
				}
			} else {
				file = new File(plugin.getDataFolder(), path + id + ".txt");
				if (!file.exists()) {
					File file2 = new File(plugin.getDataFolder(), path + pl.getName().toLowerCase() + ".txt");
					if (file2.exists()) file2.renameTo(file);
				}
			}

			if (!file.exists()) return;

			Scanner scanner = new Scanner(file, StandardCharsets.UTF_8);
			while (scanner.hasNext()) {
				String line = scanner.nextLine();
				if (line.isEmpty()) continue;

				if (!line.contains(":")) {
					Spell spell = MagicSpells.getSpellByInternalName(line);
					if (spell == null) continue;
					spellbook.addSpell(spell);
				}

				String[] data = line.split(":", 2);
				Spell spell = MagicSpells.getSpellByInternalName(data[0]);

				if (spell == null) continue;
				if (data.length <= 1) continue;

				List<CastItem> items = new ArrayList<>();
				String[] s = data[1].split(MagicItemDataParser.DATA_REGEX);
				for (String value : s) {
					try {
						CastItem castItem = new CastItem(value);
						items.add(castItem);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				spellbook.addSpell(spell, items.toArray(new CastItem[0]));
			}
			scanner.close();

		} catch (Exception e) {
			DebugHandler.debugGeneral(e);
		}
	}

	@Override
	public void save(Spellbook spellbook) {
		Player pl = spellbook.getPlayer();
		String worldName = pl.getWorld().getName();
		String id = Util.getUniqueId(pl);
		try {
			File file;
			File oldFile;
			String path = "spellbooks" + File.separator;
			if (MagicSpells.arePlayerSpellsSeparatedPerWorld()) {
				File folder = new File(plugin.getDataFolder(), path + worldName);
				if (!folder.exists()) folder.mkdirs();
				oldFile = new File(plugin.getDataFolder(), path + worldName + File.separator + pl.getName() + ".txt");
				file = new File(plugin.getDataFolder(), path + worldName + File.separator + id + ".txt");
			} else {
				oldFile = new File(plugin.getDataFolder(), path + pl.getName() + ".txt");
				file = new File(plugin.getDataFolder(), path + id + ".txt");
			}

			StringBuilder content = new StringBuilder();
			for (Spell spell : spellbook.getSpells()) {
				if (spellbook.isTemporary(spell)) continue;
				content.append(spell.getInternalName());

				if (spellbook.getCustomBindings().containsKey(spell)) {
					Set<CastItem> items = spellbook.getCustomBindings().get(spell);
					StringBuilder s = new StringBuilder();
					for (CastItem item : items) {
						s.append((s.length() == 0) ? "" : "|").append(item);
					}

					// When you unbind an item with no binds left, restore the original cast item.
					CastItem castItem = (CastItem) items.toArray()[0];
					if (items.size() == 1 && castItem.getType() == null) {
						content.append('\n');
						continue;
					}
					content.append(':').append(s);
				}
				content.append('\n');
			}

			AtomicFiles.writeUtf8(file.toPath(), content.toString());
			if (!oldFile.equals(file) && oldFile.exists()) oldFile.delete();
			MagicSpells.debug("Saved spellbook for player '" + pl.getName() + "'.");
		} catch (Exception e) {
			plugin.getServer().getLogger().severe("Error saving spellbook for player '" + pl.getName() + "'.");
			e.printStackTrace();
		}
	}

	@Override
	public void disable() {

	}

}
