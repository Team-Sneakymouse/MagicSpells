package com.nisovin.magicspells.spells.command;

import java.io.File;
import java.util.List;
import java.io.IOException;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.InventoryUtil;
import com.nisovin.magicspells.util.io.AtomicFiles;
import com.nisovin.magicspells.spells.CommandSpell;
import com.nisovin.magicspells.util.itemreader.alternative.AlternativeReaderManager;

// TODO find a good way of configuring which items to serialize
// TODO make the serialization and io processing async
// TODO reconsider the way of determining file name
// TODO produce a better way of naming the items
// TODO allow a configurable yaml header with variable substitution
// TODO allow configurable messages
// WARNING: THIS SPELL IS SUBJECT TO BREAKING CHANGES
// DO NOT USE CURRENTLY IF EXPECTING LONG TERM UNCHANGING BEHAVIOR
public class ItemSerializeSpell extends CommandSpell {
	
	private File dataFolder;

	private String serializerKey;

	private int indentation;
	
	public ItemSerializeSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		serializerKey = getConfigString("serializer-key", "external::spigot");

		indentation = getConfigInt("indentation", 4);
	}
	
	@Override
	protected void initialize() {
		// Setup data folder
		dataFolder = new File(MagicSpells.getInstance().getDataFolder(), "items");
		if (!dataFolder.exists()) dataFolder.mkdirs();
	}
	
	@Override
	protected void turnOff() {
		super.turnOff();
		
		// This is where any resources should be closed if they aren't already
	}
	
	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		ItemStack heldItem = player.getInventory().getItemInMainHand();
					if (InventoryUtil.isNothing(heldItem)) {
						player.sendMessage("You must be holding an item in your hand");
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					processItem(heldItem);
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		return false;
	}
	
	private File makeFile() {
		return new File(dataFolder, System.currentTimeMillis() + ".yml");
	}
	
	private void processItem(ItemStack itemStack) {
		ConfigurationSection section = AlternativeReaderManager.serialize(serializerKey, itemStack);
		File file = makeFile();
		YamlConfiguration outputYaml = new YamlConfiguration();
		outputYaml.set("magic-items." + System.currentTimeMillis(), section);
		outputYaml.options().indent(indentation);
		try {
			AtomicFiles.writeUtf8(file.toPath(), outputYaml.saveToString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public List<String> tabComplete(CommandSender sender, String partial) {
		return null;
	}

	public File getDataFolder() {
		return dataFolder;
	}

	public void setDataFolder(File dataFolder) {
		this.dataFolder = dataFolder;
	}

	public String getSerializerKey() {
		return serializerKey;
	}

	public void setSerializerKey(String serializerKey) {
		this.serializerKey = serializerKey;
	}

	public int getIndentation() {
		return indentation;
	}

	public void setIndentation(int indentation) {
		this.indentation = indentation;
	}
	
}
