package com.nisovin.magicspells.spells;

import static com.nisovin.magicspells.util.magicitems.MagicItemData.MagicItemAttribute.NAME;

import java.util.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

import com.nisovin.magicspells.Subspell;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.MenuData;
import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class PlayerMenuSpell extends TargetedSpell implements TargetedEntitySpell {

	private Map<UUID, MenuData> menuData;

	private final int delay;
	private final String title;
	private final double radius;
	private final boolean stayOpen;
	private final boolean addOpener;
	private final String skullName;
	private final String skullNameOffline;
	private final String skullNameRadius;
	private final List<String> skullLore;
	private final String spellRangeName;
	private final String spellOfflineName;
	private final boolean castSpellsOnTarget;
	private final String spellOnLeftName;
	private final String spellOnRightName;
	private final String spellOnMiddleName;
	private final String spellOnSneakLeftName;
	private final String spellOnSneakRightName;
	private final List<String> playerModifiersStrings;
	private final String variableTarget;

	private Subspell spellOffline;
	private Subspell spellRange;
	private Subspell spellOnLeft;
	private Subspell spellOnRight;
	private Subspell spellOnMiddle;
	private Subspell spellOnSneakLeft;
	private Subspell spellOnSneakRight;
	private ModifierSet playerModifiers;

	private final ItemStack previousPageItem;
	private final ItemStack nextPageItem;

	public PlayerMenuSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		delay = getConfigInt("delay", 0);
		title = getConfigString("title", "PlayerMenuSpell '" + internalName + "'");
		radius = getConfigDouble("radius", 0);
		stayOpen = getConfigBoolean("stay-open", false);
		addOpener = getConfigBoolean("add-opener", false);
		skullName = getConfigString("skull-name", "&6%t");
		skullNameOffline = getConfigString("skull-name-offline", "&4%t");
		skullNameRadius = getConfigString("skull-name-radius", "&4%t &3out of radius.");
		skullLore = getConfigStringList("skull-lore", null);
		spellOfflineName = getConfigString("spell-offline", "");
		spellRangeName = getConfigString("spell-range", "");
		castSpellsOnTarget = getConfigBoolean("cast-spells-on-target", true);
		spellOnLeftName = getConfigString("spell-on-left", "");
		spellOnRightName = getConfigString("spell-on-right", "");
		spellOnMiddleName = getConfigString("spell-on-middle", "");
		spellOnSneakLeftName = getConfigString("spell-on-sneak-left", "");
		spellOnSneakRightName = getConfigString("spell-on-sneak-right", "");
		playerModifiersStrings = getConfigStringList("player-modifiers", null);
		variableTarget = getConfigString("variable-target", null);

		previousPageItem = createItem("previous-page-item", "Previous Page");
		nextPageItem = createItem("next-page-item", "Next Page");
	}

	@Override
	public void initializeModifiers() {
		super.initializeModifiers();

		if (playerModifiersStrings == null || playerModifiersStrings.isEmpty()) return;
		playerModifiers = new ModifierSet(playerModifiersStrings, this);
	}

	@Override
	public void initialize() {
		super.initialize();

		String error = "PlayerMenuSpell '" + internalName + "' has an invalid ";
		spellOffline = initSubspell(spellOfflineName, error + "spell-offline defined!");
		spellRange = initSubspell(spellRangeName, error + "spell-range defined!");
		spellOnLeft = initSubspell(spellOnLeftName, error + "spell-on-left defined!");
		spellOnRight = initSubspell(spellOnRightName, error + "spell-on-right defined!");
		spellOnMiddle = initSubspell(spellOnMiddleName, error + "spell-on-middle defined!");
		spellOnSneakLeft = initSubspell(spellOnSneakLeftName, error + "spell-on-sneak-left defined!");
		spellOnSneakRight = initSubspell(spellOnSneakRightName, error + "spell-on-sneak-right defined!");

		menuData = new HashMap<>();
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state == SpellCastState.NORMAL && caster instanceof Player) {
			TargetInfo<Player> targetInfo = getTargetedPlayer(caster, power, args);
			if (targetInfo.noTarget()) return noTarget(caster, args, targetInfo);
			Player target = targetInfo.target();

			openDelay(caster, target, power, args);
		}

		return PostCastAction.HANDLE_NORMALLY;
	}

	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {
		if (!(target instanceof Player player) || !validTargetList.canTarget(caster, target)) return false;
		openDelay(caster, player, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity caster, LivingEntity target, float power) {
		return castAtEntity(caster, target, power, null);
	}

	@Override
	public boolean castAtEntity(LivingEntity target, float power, String[] args) {
		if (!(target instanceof Player player) || !validTargetList.canTarget(target)) return false;
		openDelay(null, player, power, args);
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean castAtEntity(LivingEntity target, float power) {
		return castAtEntity(target, power, null);
	}

	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		if (args.length < 1) return false;
		Player player = Bukkit.getPlayer(args[0]);
		if (player == null) return false;
		openDelay(null, player, 1, null);
		return true;
	}

	private ItemStack createItem(String path, String defaultName) {
		MagicItem magicItem = MagicItems.getMagicItemFromString(getConfigString(path, null));
		if (magicItem != null) return magicItem.getItemStack().clone();

		ItemStack item = new ItemStack(Material.GREEN_WOOL);
		ItemMeta meta = item.getItemMeta();

		meta.displayName(Component.text(defaultName).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
		item.setItemMeta(meta);

		return item;
	}

	private void openDelay(LivingEntity caster, Player opener, float power, String[] args) {
		MenuData data = new MenuData(new SpellData(caster, opener, power, args), 0);
		menuData.put(opener.getUniqueId(), data);

		if (delay > 0) MagicSpells.scheduleDelayedTask(() -> open(opener, data), delay);
		else open(opener, data);
	}

	private Component translate(Player opener, Player target, String string, String[] args) {
		return Util.getMiniMessage(MagicSpells.doReplacements(string, opener, target, args,
			"%a", opener.getName(),
			"%t", target == null ? null : target.getName()
		));
	}

	private void processClickSpell(Subspell subspell, Player caster, Player target, float power, String[] args) {
		if (subspell == null) return;
		if (castSpellsOnTarget) subspell.subcast(caster, target, power, args);
		else subspell.subcast(caster, power, args);
	}

	private void open(Player opener, MenuData data) {
		List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
		if (!addOpener) players.remove(opener);
		if (playerModifiers != null) players.removeIf(player -> !playerModifiers.check(player));
		if (radius > 0) players.removeIf(player -> opener.getLocation().distance(player.getLocation()) > radius);

		int size = Math.max((int) Math.ceil(Math.min(players.size(), 54) / 9.0) * 9, 9);
		Inventory inv = Bukkit.createInventory(opener, size, Component.text(internalName));
		SpellData spellData = data.spellData;
		String[] args = spellData.args();

		for (int i = (data.page * 52); i < Math.min(players.size(), (data.page + 1) * 52); i++) {
			ItemStack head = new ItemStack(Material.PLAYER_HEAD);
			ItemMeta itemMeta = head.getItemMeta();

			SkullMeta skullMeta = (SkullMeta) itemMeta;
			if (skullMeta == null) continue;

			skullMeta.setOwningPlayer(players.get(i));
			itemMeta.displayName(translate(opener, players.get(i), skullName, args));

			if (skullLore != null) {
				List<Component> lore = new ArrayList<>();
				for (String loreLine : skullLore) lore.add(translate(opener, players.get(i), loreLine, args));

				itemMeta.lore(lore);
			}

			head.setItemMeta(skullMeta);
			inv.setItem(i % 52, head);
		}

		if (data.page > 0) inv.setItem(52, previousPageItem);
		if (players.size() > (data.page + 1) * 52) inv.setItem(53, nextPageItem);

		opener.openInventory(inv);
		Util.setInventoryTitle(opener, title);

		if (spellData.caster() != null) playSpellEffects(spellData.caster(), spellData.target(), spellData);
		else playSpellEffects(EffectPosition.TARGET, spellData.target(), spellData);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		menuData.remove(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onItemClick(InventoryClickEvent event) {
		if (!Util.getStringFromComponent(event.getView().title()).equals(internalName)) return;

		event.setCancelled(true);
		if (!(event.getWhoClicked() instanceof Player opener)) return;

		ItemStack item = event.getCurrentItem();
		if (item == null) return;

		ItemMeta meta = item.getItemMeta();
		if (meta == null) return;

		MenuData data = menuData.get(opener.getUniqueId());
		SpellData spellData = data.spellData();
		String[] args = spellData.args();
		float power = spellData.power();

		if (event.getRawSlot() == 52) {
			data = new MenuData(spellData, data.page - 1);
			menuData.put(opener.getUniqueId(), data);
			open(opener, data);

			return;
		} else if (event.getRawSlot() == 53) {
			data = new MenuData(spellData, data.page + 1);
			menuData.put(opener.getUniqueId(), data);
			open(opener, data);

			return;
		}

		if (!(meta instanceof SkullMeta skullMeta)) return;

		OfflinePlayer target = skullMeta.getOwningPlayer();
		if (target == null || !target.isOnline()) {
			meta.displayName(translate(opener, null, skullNameOffline, args));
			if (spellOffline != null) spellOffline.subcast(opener, power, args);

			if (stayOpen) item.setItemMeta(meta);
			else {
				opener.closeInventory();
				menuData.remove(opener.getUniqueId());
				playSpellEffects(EffectPosition.DISABLED, opener, spellData);
			}

			return;
		}

		Player targetPlayer = target.getPlayer();
		if (targetPlayer == null) return;

		meta.displayName(translate(opener, targetPlayer, skullName, args));
		item.setItemMeta(meta);

		if (radius > 0 && targetPlayer.getLocation().distance(opener.getLocation()) > radius) {
			meta.displayName(translate(opener, targetPlayer, skullNameRadius, args));
			if (spellRange != null) spellRange.subcast(opener, power, args);

			if (stayOpen) item.setItemMeta(meta);
			else {
				opener.closeInventory();
				menuData.remove(opener.getUniqueId());
				playSpellEffects(EffectPosition.DISABLED, opener, spellData);
			}

			return;
		}

		switch (event.getClick()) {
			case LEFT -> processClickSpell(spellOnLeft, opener, targetPlayer, power, args);
			case RIGHT -> processClickSpell(spellOnRight, opener, targetPlayer, power, args);
			case MIDDLE -> processClickSpell(spellOnMiddle, opener, targetPlayer, power, args);
			case SHIFT_LEFT -> processClickSpell(spellOnSneakLeft, opener, targetPlayer, power, args);
			case SHIFT_RIGHT -> processClickSpell(spellOnSneakRight, opener, targetPlayer, power, args);
		}

		if (variableTarget != null && !variableTarget.isEmpty() && MagicSpells.getVariableManager().getVariable(variableTarget) != null)
			MagicSpells.getVariableManager().set(variableTarget, opener, target.getName());

		if (stayOpen) open(opener, data);
		else {
			opener.closeInventory();
			menuData.remove(opener.getUniqueId());
			playSpellEffects(EffectPosition.DISABLED, opener, spellData);
		}
	}

	public record MenuData(SpellData spellData, int page) {}

}
