package com.nisovin.magicspells.spells.command;

import java.io.File;
import java.util.List;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.io.FileNotFoundException;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventPriority;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.nisovin.magicspells.Perm;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.RegexUtil;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.io.AtomicFiles;
import com.nisovin.magicspells.util.MagicLocation;
import com.nisovin.magicspells.spells.CommandSpell;
import com.nisovin.magicspells.util.compat.EventUtil;
import com.nisovin.magicspells.events.SpellLearnEvent;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.SpellLearnEvent.LearnSource;

// Advanced perm is for being able to destroy spellbooks
// Op is currently required for using the reload

public class SpellbookSpell extends CommandSpell {

	private static final Pattern PATTERN_CAST_ARG_USAGE = Pattern.compile("^[0-9]+$");

	private List<String> bookSpells;
	private List<Integer> bookUses;
	private List<MagicLocation> bookLocations;

	private Material spellbookBlock;

	private int defaultUses;

	private boolean destroySpellbook;

	private String strUsage;
	private String strNoSpell;
	private String strLearned;
	private String strNoTarget;
	private String strCantTeach;
	private String strCantLearn;
	private String strLearnError;
	private String strCantDestroy;
	private String strHasSpellbook;
	private String strAlreadyKnown;

	public SpellbookSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		bookSpells = new ArrayList<>();
		bookUses = new ArrayList<>();
		bookLocations = new ArrayList<>();

		String spellbookBlockName = getConfigString("spellbook-block", "bookshelf");
		spellbookBlock = Util.getMaterial(spellbookBlockName);
		if (spellbookBlock == null || !spellbookBlock.isBlock()) {
			MagicSpells.error("SpellbookSpell '" + internalName + "' has an invalid spellbook-block defined!");
			spellbookBlock = null;
		}

		defaultUses = getConfigInt("default-uses", -1);

		destroySpellbook = getConfigBoolean("destroy-when-used-up", false);

		strUsage = getConfigString("str-usage", "Usage: /cast spellbook <spell> [uses]");
		strNoSpell = getConfigString("str-no-spell", "You do not know a spell by that name.");
		strLearned = getConfigString("str-learned", "You have learned the %s spell!");
		strNoTarget = getConfigString("str-no-target", "You must target a bookcase to create a spellbook.");
		strCantTeach = getConfigString("str-cant-teach", "You can't create a spellbook with that spell.");
		strCantLearn = getConfigString("str-cant-learn", "You cannot learn the spell in this spellbook.");
		strLearnError = getConfigString("str-learn-error", "");
		strCantDestroy = getConfigString("str-cant-destroy", "You cannot destroy a bookcase with a spellbook.");
		strHasSpellbook = getConfigString("str-has-spellbook", "That bookcase already has a spellbook.");
		strAlreadyKnown = getConfigString("str-already-known", "You already know the %s spell.");

		loadSpellbooks();
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		if (data.args() == null || data.args().length < 1 || data.args().length > 2
							|| (data.args().length == 2 && !RegexUtil.matches(PATTERN_CAST_ARG_USAGE, data.args()[1]))) {
						sendMessage(strUsage, player, data.args());
						return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
					}
					if (player.isOp() && data.args()[0].equalsIgnoreCase("reload")) {
						bookLocations = new ArrayList<>();
						bookSpells = new ArrayList<>();
						bookUses = new ArrayList<>();
						loadSpellbooks();
						player.sendMessage("Spellbook file reloaded.");
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					Spellbook spellbook = MagicSpells.getSpellbook(player);
					Spell spell = MagicSpells.getSpellByInGameName(data.args()[0]);
					if (spell == null || !spellbook.hasSpell(spell)) {
						sendMessage(strNoSpell, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					if (!MagicSpells.getSpellbook(player).canTeach(spell)) {
						sendMessage(strCantTeach, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					Block target = getTargetedBlock(player, 10, data.args());
					if (target == null || !spellbookBlock.equals(target.getType())) {
						sendMessage(strNoTarget, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					if (bookLocations.contains(new MagicLocation(target.getLocation()))) {
						sendMessage(strHasSpellbook, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
					bookLocations.add(new MagicLocation(target.getLocation()));
					bookSpells.add(spell.getInternalName());
					if (data.args().length == 1)
						bookUses.add(defaultUses);
					else
						bookUses.add(Integer.parseInt(data.args()[1]));
		
					saveSpellbooks();
					sendMessage(strCastSelf, player, data.args(), "%s", spell.getName());
					playSpellEffects(player, target.getLocation(), data.power(), data.args());
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		if (sender.isOp() && args != null && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
			bookLocations = new ArrayList<>();
			bookSpells = new ArrayList<>();
			bookUses = new ArrayList<>();
			loadSpellbooks();
			sender.sendMessage("Spellbook file reloaded.");
			return true;
		}
		return false;
	}

	private void removeSpellbook(int index) {
		bookLocations.remove(index);
		bookSpells.remove(index);
		bookUses.remove(index);
		saveSpellbooks();
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (spellbookBlock == null)
			return;
		Block clickedBlock = event.getClickedBlock();
		if (clickedBlock == null)
			return;
		EquipmentSlot slot = event.getHand();
		if (slot == null)
			return;
		if (!event.hasBlock() || !spellbookBlock.equals(clickedBlock.getType())
				|| event.getAction() != Action.RIGHT_CLICK_BLOCK)
			return;
		if (slot == EquipmentSlot.OFF_HAND)
			return;
		Location location = event.getClickedBlock().getLocation();
		MagicLocation loc = new MagicLocation(location);
		if (!bookLocations.contains(loc))
			return;

		event.setCancelled(true);
		Player player = event.getPlayer();
		int i = bookLocations.indexOf(loc);
		Spellbook spellbook = MagicSpells.getSpellbook(player);
		Spell spell = MagicSpells.getSpellByInternalName(bookSpells.get(i));
		if (spell == null) {
			sendMessage(strLearnError, player, MagicSpells.NULL_ARGS);
			return;
		}
		if (!spellbook.canLearn(spell)) {
			sendMessage(strCantLearn, player, MagicSpells.NULL_ARGS, "%s", spell.getName());
			return;
		}
		if (spellbook.hasSpell(spell)) {
			sendMessage(strAlreadyKnown, player, MagicSpells.NULL_ARGS, "%s", spell.getName());
			return;
		}
		SpellLearnEvent learnEvent = new SpellLearnEvent(spell, player, LearnSource.SPELLBOOK, event.getClickedBlock());
		EventUtil.call(learnEvent);
		if (learnEvent.isCancelled()) {
			sendMessage(strCantLearn, player, MagicSpells.NULL_ARGS, "%s", spell.getName());
			return;
		}
		spellbook.addSpell(spell);
		spellbook.save();
		sendMessage(strLearned, player, MagicSpells.NULL_ARGS, "%s", spell.getName());
		playSpellEffects(EffectPosition.DELAYED, player, new SpellData(player, location));

		int uses = bookUses.get(i);
		if (uses <= 0)
			return;

		uses--;
		if (uses == 0) {
			if (destroySpellbook)
				bookLocations.get(i).getLocation().getBlock().setType(Material.AIR);
			removeSpellbook(i);
		} else
			bookUses.set(i, uses);
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		if (spellbookBlock == null || !spellbookBlock.equals(event.getBlock().getType()))
			return;
		MagicLocation loc = new MagicLocation(event.getBlock().getLocation());
		if (!bookLocations.contains(loc))
			return;
		Player pl = event.getPlayer();
		if (pl.isOp() || Perm.ADVANCED_SPELLBOOK.has(pl)) {
			int i = bookLocations.indexOf(loc);
			removeSpellbook(i);
			return;
		}

		event.setCancelled(true);
		sendMessage(strCantDestroy, pl, MagicSpells.NULL_ARGS);
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String partial) {
		if (sender instanceof Player && !partial.contains(" "))
			return tabCompleteSpellName(sender, partial);
		return null;
	}

	private void loadSpellbooks() {
		try {
			Scanner scanner = new Scanner(new File(MagicSpells.plugin.getDataFolder(), "books.txt"));
			while (scanner.hasNext()) {
				String line = scanner.nextLine();
				if (line.isEmpty())
					continue;
				try {
					String[] data = line.split(":");
					MagicLocation loc = new MagicLocation(data[0], Integer.parseInt(data[1]), Integer.parseInt(data[2]),
							Integer.parseInt(data[3]));
					int uses = Integer.parseInt(data[5]);
					bookLocations.add(loc);
					bookSpells.add(data[4]);
					bookUses.add(uses);
				} catch (Exception e) {
					MagicSpells.plugin.getServer().getLogger().severe("MagicSpells: Failed to load spellbook: " + line);
				}
			}
		} catch (FileNotFoundException e) {
			// DebugHandler.debugFileNotFoundException(e);
		}
	}

	private void saveSpellbooks() {
		try {
			StringBuilder content = new StringBuilder();
			MagicLocation loc;
			for (int i = 0; i < bookLocations.size(); i++) {
				loc = bookLocations.get(i);
				content.append(loc.getWorld()).append(':')
						.append((int) loc.getX()).append(':')
						.append((int) loc.getY()).append(':')
						.append((int) loc.getZ()).append(':')
						.append(bookSpells.get(i)).append(':')
						.append(bookUses.get(i)).append(System.lineSeparator());
			}
			File file = new File(MagicSpells.plugin.getDataFolder(), "books.txt");
			AtomicFiles.writeUtf8(file.toPath(), content.toString());
		} catch (Exception e) {
			MagicSpells.plugin.getServer().getLogger().severe("MagicSpells: Error saving spellbooks");
		}
	}

	public static Pattern getPatternCastArgUsage() {
		return PATTERN_CAST_ARG_USAGE;
	}

	public List<String> getBookSpells() {
		return bookSpells;
	}

	public List<Integer> getBookUses() {
		return bookUses;
	}

	public List<MagicLocation> getBookLocations() {
		return bookLocations;
	}

	public Material getSpellbookBlock() {
		return spellbookBlock;
	}

	public void setSpellbookBlock(Material spellbookBlock) {
		this.spellbookBlock = spellbookBlock;
	}

	public int getDefaultUses() {
		return defaultUses;
	}

	public void setDefaultUses(int defaultUses) {
		this.defaultUses = defaultUses;
	}

	public boolean shouldDestroySpellbook() {
		return destroySpellbook;
	}

	public void setDestroySpellbook(boolean destroySpellbook) {
		this.destroySpellbook = destroySpellbook;
	}

	public String getStrUsage() {
		return strUsage;
	}

	public void setStrUsage(String strUsage) {
		this.strUsage = strUsage;
	}

	public String getStrNoSpell() {
		return strNoSpell;
	}

	public void setStrNoSpell(String strNoSpell) {
		this.strNoSpell = strNoSpell;
	}

	public String getStrLearned() {
		return strLearned;
	}

	public void setStrLearned(String strLearned) {
		this.strLearned = strLearned;
	}

	public String getStrNoTarget() {
		return strNoTarget;
	}

	public void setStrNoTarget(String strNoTarget) {
		this.strNoTarget = strNoTarget;
	}

	public String getStrCantTeach() {
		return strCantTeach;
	}

	public void setStrCantTeach(String strCantTeach) {
		this.strCantTeach = strCantTeach;
	}

	public String getStrCantLearn() {
		return strCantLearn;
	}

	public void setStrCantLearn(String strCantLearn) {
		this.strCantLearn = strCantLearn;
	}

	public String getStrLearnError() {
		return strLearnError;
	}

	public void setStrLearnError(String strLearnError) {
		this.strLearnError = strLearnError;
	}

	public String getStrCantDestroy() {
		return strCantDestroy;
	}

	public void setStrCantDestroy(String strCantDestroy) {
		this.strCantDestroy = strCantDestroy;
	}

	public String getStrHasSpellbook() {
		return strHasSpellbook;
	}

	public void setStrHasSpellbook(String strHasSpellbook) {
		this.strHasSpellbook = strHasSpellbook;
	}

	public String getStrAlreadyKnown() {
		return strAlreadyKnown;
	}

	public void setStrAlreadyKnown(String strAlreadyKnown) {
		this.strAlreadyKnown = strAlreadyKnown;
	}

}
