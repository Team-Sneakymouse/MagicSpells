package com.nisovin.magicspells.spells;

import java.util.*;

import co.aikar.commands.ACFUtil;

import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.ConfigDataUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import com.nisovin.magicspells.util.*;
import com.nisovin.magicspells.Subspell;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.events.MagicSpellsGenericPlayerEvent;
import com.nisovin.magicspells.util.magicitems.MagicItemData;

public class MenuSpell extends TargetedSpell implements TargetedEntitySpell, TargetedLocationSpell {

	private final Map<String, MenuOption> options = new LinkedHashMap<>();
	private final Map<UUID, MenuData> menuData = new HashMap<>();

	private int rows;

	private final String title;
	private ConfigData<Integer> delay;
	private final ItemStack filler;
	private ConfigData<Boolean> stayOpenNonOption;
	private ConfigData<Boolean> bypassNormalCast;
	private ConfigData<Boolean> requireEntityTarget;
	private ConfigData<Boolean> requireLocationTarget;
	private ConfigData<Boolean> targetOpensMenuInstead;
	private ConfigData<Boolean> autoArrange;

	private final ItemStack previousPageItem;
	private final ItemStack nextPageItem;
	private final ItemStack spacerItem;

	private final ConfigData<ConfigurationSection> spellsOnDropNonOption;

	public MenuSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		title = getConfigString("title", "Window Title " + spellName);
		delay = getConfigDataInt("delay", 1);
		filler = createItem("filler");
		stayOpenNonOption = getConfigDataBoolean("stay-open-non-option", true);
		bypassNormalCast = getConfigDataBoolean("bypass-normal-cast", true);
		requireEntityTarget = getConfigDataBoolean("require-entity-target", false);
		requireLocationTarget = getConfigDataBoolean("require-location-target", false);
		targetOpensMenuInstead = getConfigDataBoolean("target-opens-menu-instead", false);

		autoArrange = getConfigDataBoolean("auto-arrange", false);
		previousPageItem = createItem("previous-page-item", "Previous Page");
		nextPageItem = createItem("next-page-item", "Next Page");
		spacerItem = createItem("spacer-item", null);

		spellsOnDropNonOption = getConfigDataConfigurationSection("spells-on-drop", null);

		Set<String> optionKeys = getConfigKeys("options");
		if (optionKeys == null) {
			MagicSpells.error("MenuSpell '" + spellName + "' has no menu options!");
			return;
		}
		for (String optionName : optionKeys) {
			String path = "options." + optionName + ".";

			ConfigData<List<Integer>> slots;
			if (getConfigStringList(path + "slots", new ArrayList<>()).isEmpty())
				slots = ConfigDataUtil.getIntList(config.getMainConfig(),
						"spells." + internalName + '.' + path + "slot");
			else
				slots = ConfigDataUtil.getIntList(config.getMainConfig(),
						"spells." + internalName + '.' + path + "slots");

			ConfigData<ConfigurationSection> itemSection = null;
			ConfigData<String> itemString = null;
			if (isConfigSection(path + "item")) {
				itemSection = getConfigDataConfigurationSection(path + "item", null);
			} else {
				itemString = getConfigDataString(path + "item", null);
			}

			List<String> itemList = getConfigStringList(path + "items", null);
			List<ItemStack> items = new ArrayList<>();
			if (itemString == null && itemSection == null) {
				// If no items are defined, exit.
				if (itemList == null) {
					MagicSpells.error("MenuSpell '" + internalName + "' has no items defined for: " + optionName);
					continue;
				}
				// Otherwise process item list.
				for (String itemName : itemList) {
					MagicItem magicItem = MagicItems.getMagicItemFromString(itemName);
					if (magicItem == null) {
						MagicSpells.error("MenuSpell '" + internalName + "' has an invalid item listed in '"
								+ optionName + "': " + itemName);
						continue;
					}
					ItemStack itemStack = magicItem.getItemStack();
					if (itemStack == null) {
						MagicSpells.error("MenuSpell '" + internalName + "' has an invalid item listed in '"
								+ optionName + "': " + itemName);
						continue;
					}
					items.add(itemStack);
				}
				// Skip if list was invalid.
				if (items.isEmpty()) {
					MagicSpells.error("MenuSpell '" + internalName + "' has no items defined for: " + optionName);
					continue;
				}
			}

			MenuOption option = new MenuOption();
			option.menuOptionName = optionName;
			option.slots = slots;
			option.itemSection = itemSection;
			option.itemString = itemString;
			option.items = items;
			option.quantity = getConfigDataInt(path + "quantity", 1);
			option.spellName = getConfigString(path + "spell", "");
			option.spellRightName = getConfigString(path + "spell-right", "");
			option.spellMiddleName = getConfigString(path + "spell-middle", "");
			option.spellSneakLeftName = getConfigString(path + "spell-sneak-left", "");
			option.spellSneakRightName = getConfigString(path + "spell-sneak-right", "");
			option.spellSwapName = getConfigString(path + "spell-swap", "");
			option.spellDropName = getConfigString(path + "spell-drop", "");
			option.power = getConfigFloat(path + "power", 1);
			option.modifierList = getConfigStringList(path + "modifiers", null);
			option.stayOpen = getConfigBoolean(path + "stay-open", false);
			option.varModsClick = getConfigStringList(path + "variable-mods-click", null);
			option.varModsClicked = getConfigStringList(path + "variable-mods-clicked", null);
			option.spellsOnDrop = getConfigDataConfigurationSection(path + "spells-on-drop", null);

			options.put(optionName, option);
		}
		rows = Boolean.TRUE.equals(autoArrange.get(null, null, null, 1f, null)) ? 54 : getConfigInt("min-rows", 1);
		if (options.isEmpty())
			MagicSpells.error("MenuSpell '" + spellName + "' has no menu options!");
	}

	@Override
	public void initializeModifiers() {
		super.initializeModifiers();

		for (MenuOption option : options.values()) {
			if (option.modifierList != null)
				option.menuOptionModifiers = new ModifierSet(option.modifierList, this);
		}
	}

	@Override
	public void initializeVariables() {
		super.initializeVariables();

		for (MenuOption option : options.values()) {
			if (option.varModsClick != null && !option.varModsClick.isEmpty()) {
				option.variableModsClick = LinkedListMultimap.create();
				for (String s : option.varModsClick) {
					try {
						String[] data = s.split(" ", 2);
						String var = data[0];
						VariableMod varMod = new VariableMod(data[1]);
						option.variableModsClick.put(var, varMod);
					} catch (Exception e) {
						MagicSpells
								.error("Invalid variable-mods-click option for MenuSpell '" + internalName + "': " + s);
					}
				}
			}

			if (option.varModsClicked != null && !option.varModsClicked.isEmpty()) {
				option.variableModsClicked = LinkedListMultimap.create();
				for (String s : option.varModsClicked) {
					try {
						String[] data = s.split(" ", 2);
						String var = data[0];
						VariableMod varMod = new VariableMod(data[1]);
						option.variableModsClicked.put(var, varMod);
					} catch (Exception e) {
						MagicSpells.error(
								"Invalid variable-mods-clicked option for MenuSpell '" + internalName + "': " + s);
					}
				}
			}
		}
	}

	@Override
	public void initialize() {
		super.initialize();

		for (MenuOption option : options.values()) {
			option.spell = initSubspell(option.spellName,
					"MenuSpell '" + internalName + "' has an invalid 'spell' defined for: " + option.menuOptionName);
			option.spellRight = initSubspell(option.spellRightName, "MenuSpell '" + internalName
					+ "' has an invalid 'spell-right' defined for: " + option.menuOptionName);
			option.spellMiddle = initSubspell(option.spellMiddleName, "MenuSpell '" + internalName
					+ "' has an invalid 'spell-middle' defined for: " + option.menuOptionName);
			option.spellSneakLeft = initSubspell(option.spellSneakLeftName, "MenuSpell '" + internalName
					+ "' has an invalid 'spell-sneak-left' defined for: " + option.menuOptionName);
			option.spellSneakRight = initSubspell(option.spellSneakRightName, "MenuSpell '" + internalName
					+ "' has an invalid 'spell-sneak-right' defined for: " + option.menuOptionName);
			option.spellSwap = initSubspell(option.spellSwapName, "MenuSpell '" + internalName
					+ "' has an invalid 'spell-swap' defined for: " + option.menuOptionName);
			option.spellDrop = initSubspell(option.spellDropName, "MenuSpell '" + internalName
					+ "' has an invalid 'spell-drop' defined for: " + option.menuOptionName);
		}
	}

	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		LivingEntity target = null;
		Location locTarget = null;
		Player opener = player;
		float power = data.power();
		String[] args = data.args();
		LivingEntity caster = data.caster();

		boolean requireEntity = Boolean.TRUE.equals(requireEntityTarget.get(caster, power, args));
		boolean requireLocation = Boolean.TRUE.equals(requireLocationTarget.get(caster, power, args));
		boolean targetOpens = Boolean.TRUE.equals(targetOpensMenuInstead.get(caster, power, args));

		if (requireEntity) {
			if (targetOpens) {
				TargetInfo<Player> info = getTargetedPlayer(player, power, args);
				if (info.noTarget())
					return new CastResult(noTarget(caster, args, info), data);

				opener = info.target();
				power = info.power();
			} else {
				TargetInfo<LivingEntity> info = getTargetedEntity(player, power, args);
				if (info.noTarget())
					return new CastResult(noTarget(caster, args, info), data);

				target = info.target();
				power = info.power();
			}
		} else if (requireLocation) {
			Block block = getTargetedBlock(player, power, args);
			if (block == null || BlockUtils.isAir(block.getType()))
				return new CastResult(noTarget(caster, args), data);

			locTarget = block.getLocation();
		}

		open(player, opener, target, locTarget, power, args);

		if (requireEntity) {
			sendMessages(caster, targetOpens ? opener : target, args);
			return new CastResult(PostCastAction.NO_MESSAGES, data);
		}
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
	}

	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target()))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			if (!(data.caster() instanceof Player casterPlayer))
				return new CastResult(PostCastAction.ALREADY_HANDLED, data);
			Player opener = casterPlayer;
			LivingEntity target = data.target();
			if (Boolean.TRUE.equals(targetOpensMenuInstead.get(data.caster(), data.target(), data.power(),
					data.args()))) {
				if (!(data.target() instanceof Player player))
					return new CastResult(PostCastAction.ALREADY_HANDLED, data);
				opener = player;
				target = null;
			}
			open(casterPlayer, opener, target, null, data.power(), data.args());
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
		if (!Boolean.TRUE.equals(targetOpensMenuInstead.get(null, data.target(), data.power(), data.args())))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!validTargetList.canTarget(data.target()))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		if (!(data.target() instanceof Player player))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		open(null, player, null, null, data.power(), data.args());
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

	@Override
	public CastResult castAtLocation(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.ALREADY_HANDLED, data);
		open(player, player, null, data.location(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action()
				!= PostCastAction.ALREADY_HANDLED;
	}

	@Override
	public boolean castFromConsole(CommandSender sender, String[] args) {
		if (args.length < 1)
			return false;
		Player player = PlayerNameUtils.getPlayer(args[0]);
		String[] spellArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : null;
		if (player != null) {
			open(null, player, null, null, 1, spellArgs);
			return true;
		}
		return false;
	}

	private ItemStack createItem(String path, String defaultName) {
		ItemStack magicItem = createItem(path);
		if (magicItem != null)
			return magicItem.clone();

		if (defaultName == null)
			return null;

		ItemStack item = new ItemStack(Material.GREEN_WOOL);
		ItemMeta meta = item.getItemMeta();

		meta.displayName(
				Component.text(defaultName).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
		item.setItemMeta(meta);

		return item;
	}

	private ItemStack createItem(String path) {
		ItemStack item = null;
		if (isConfigSection(path)) {
			MagicItem magicItem = MagicItems.getMagicItemFromSection(getConfigSection(path));
			if (magicItem != null)
				item = magicItem.getItemStack();
		} else {
			MagicItem magicItem = MagicItems.getMagicItemFromString(getConfigString(path, ""));
			if (magicItem != null)
				item = magicItem.getItemStack();
		}
		return item;
	}

	private void open(Player caster, Player opener, LivingEntity entityTarget, Location locTarget, float power,
			String[] args) {
		LivingEntity resolveCaster = caster != null ? caster : opener;
		int delayVal = delay.get(resolveCaster, entityTarget, power, args);
		if (delayVal < 0) {
			openMenu(caster, opener, entityTarget, locTarget, power, args);
			return;
		}
		MagicSpells.scheduleDelayedTask(() -> openMenu(caster, opener, entityTarget, locTarget, power, args), delayVal);
	}

	private void openMenu(Player caster, Player opener, LivingEntity entityTarget, Location locTarget, float power,
			String[] args) {
		LivingEntity resolveCaster = caster != null ? caster : opener;
		boolean requireEntity = Boolean.TRUE.equals(requireEntityTarget.get(resolveCaster, entityTarget, power, args));
		boolean requireLocation = Boolean.TRUE.equals(requireLocationTarget.get(resolveCaster, entityTarget, locTarget, power, args));
		MenuData mData = new MenuData(requireEntity ? entityTarget : null,
				requireLocation ? locTarget : null, power, args, 0);
		menuData.put(opener.getUniqueId(), mData);

		Map<Integer, ItemStack> itemStacks = buildInventory(opener, args, mData);
		int rows_ = (int) Math.ceil((itemStacks.keySet().stream().max(Integer::compareTo).orElse(0) + 1) / 9.0);
		Inventory inv = Bukkit.createInventory(opener, Math.min(6, Math.max(1, (rows_ > rows ? rows_ : rows))) * 9,
				Component.text(internalName));

		applyOptionsToInventory(opener, inv, args, mData, itemStacks);

		opener.openInventory(inv);
		Util.setInventoryTitle(opener, title);

		SpellData data = new SpellData(caster, entityTarget, locTarget, power, args);
		if (entityTarget != null && caster != null) {
			playSpellEffects(caster, entityTarget, data);
			return;
		}
		playSpellEffects(EffectPosition.SPECIAL, opener, data);
		if (caster != null)
			playSpellEffects(EffectPosition.CASTER, caster, data);
		if (locTarget != null)
			playSpellEffects(EffectPosition.TARGET, locTarget, data);
	}

	private Map<Integer, ItemStack> buildInventory(Player opener, String[] args, MenuData mData) {
		Map<Integer, ItemStack> itemStacks = new HashMap<>();
		boolean arrange = Boolean.TRUE.equals(autoArrange.get(opener, mData.power(), args));

		// Setup option items.
		for (MenuOption option : options.values()) {
			// Check modifiers.
			if (option.menuOptionModifiers != null) {
				MagicSpellsGenericPlayerEvent event = new MagicSpellsGenericPlayerEvent(opener);
				option.menuOptionModifiers.apply(event);
				if (event.isCancelled())
					continue;
			}
			// Select and finalise item to display.
			SpellData spellData = new SpellData(opener, 0f, args);
			ItemStack itemData = null;
			if (option.itemSection != null) {
				MagicItem magicItem = MagicItems.getMagicItemFromSection(option.itemSection.get(spellData));
				if (magicItem != null)
					itemData = magicItem.getItemStack();
			} else if (option.itemString != null) {
				MagicItem magicItem = MagicItems.getMagicItemFromString(option.itemString.get(spellData));
				if (magicItem != null)
					itemData = magicItem.getItemStack();
			}

			option.item = itemData;

			if (option.item == null && option.items.isEmpty()) {
				MagicSpells.error(
						"MenuSpell '" + internalName + "' has invalid items defined for: " + option.menuOptionName);
				continue;
			}

			ItemStack item = (option.item != null ? option.item
					: option.items.get(Util.getRandomInt(option.items.size()))).clone();
			DataUtil.setString(item, "menuOption", option.menuOptionName);
			item = translateItem(opener, args, item);

			int quantity = option.quantity.get(spellData);
			item.setAmount(quantity);

			// Set item for all defined slots.
			if (arrange)
				itemStacks.put(itemStacks.keySet().size(), item);
			else {
				List<Integer> slots = option.slots.get(spellData);
				if (slots != null) {
					for (int slot : slots) {
						if (slot < 0 || slot >= 54)
							continue;
						itemStacks.put(slot, item);
					}
				}
			}
		}

		return itemStacks;
	}

	public void applyOptionsToInventory(Player opener, Inventory inv, String[] args, MenuData mData,
			Map<Integer, ItemStack> itemStacks) {
		boolean arrange = Boolean.TRUE.equals(autoArrange.get(opener, mData.power(), args));
		// Fill inventory.
		ItemStack fillerItem = (filler == null) ? null : translateItem(opener, args, filler);
		for (int i = 0; i < inv.getSize(); i++) {
			if (inv.getItem(i) != null)
				continue;

			if (arrange && i >= 50) {
				ItemStack item = (i == 51) ? spacerItem
						: (i == 52 && mData.page() > 0) ? previousPageItem
								: (i == 53 && itemStacks.size() > 50 * (mData.page() + 1)) ? nextPageItem : null;
				if (item != null)
					inv.setItem(i, item);
			} else {
				ItemStack item = itemStacks.get(i + mData.page() * 50);
				if (item != null)
					inv.setItem(i, item);
				else if (fillerItem != null)
					inv.setItem(i, fillerItem);
			}
		}
	}

	private Component translateRawComponent(Component component, Player player, String[] args) {
		String text = Util.getStringFromComponent(component);
		text = MagicSpells.doReplacements(text, player, args);
		return Util.getMiniMessage(text);
	}

	private ItemStack translateItem(Player opener, String[] args, ItemStack item) {
		ItemStack newItem = item.clone();
		ItemMeta meta = newItem.getItemMeta();
		if (meta == null)
			return newItem;
		meta.displayName(translateRawComponent(meta.displayName(), opener, args));
		List<Component> lore = meta.lore();
		if (lore != null) {
			for (int i = 0; i < lore.size(); i++) {
				lore.set(i, translateRawComponent(lore.get(i), opener, args));
			}
			meta.lore(lore);
		}
		newItem.setItemMeta(meta);
		return newItem;
	}

	@EventHandler
	public void onInvClick(InventoryClickEvent event) {
		Player player = (Player) event.getWhoClicked();
		if (!Util.getStringFromComponent(event.getView().title()).equals(internalName))
			return;

		// Handle shift-clicks from player inventory to menu
		if (event.getClickedInventory() != event.getView().getTopInventory()) {
			// Player clicked in their own inventory
			if ((event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) &&
					event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {

				// Always cancel shift-clicks to prevent unintentional item transfers
				event.setCancelled(true);

				// Check if this item has a spell configured for empty slot drops
				if (spellsOnDropNonOption != null) {
					ItemStack clickedItem = event.getCurrentItem();
					MagicItemData magicItem = MagicItems.getMagicItemDataFromItemStack(clickedItem);
					if (magicItem != null) {
						String itemName = (String) magicItem
								.getAttribute(MagicItemData.MagicItemAttribute.MAGIC_ITEM_NAME);
						if (itemName != null) {
							// Get menu data for spell evaluation context
							UUID id = player.getUniqueId();
							MenuData data = menuData.get(id);
							String[] args = data != null ? data.args() : null;

							SpellData spellData = new SpellData(player, 0f, args);
							ConfigurationSection dropSection = spellsOnDropNonOption.get(spellData);
							if (dropSection != null && dropSection.getString(itemName) != null) {
								// Handle as empty slot spell
								String result = handleShiftClickFromInventory(player, clickedItem);
								if (result.equals("close")) {
									menuData.remove(id);
									MagicSpells.scheduleDelayedTask(player::closeInventory, 0);
									return;
								} else if (result.equals("reopen")) {
									// Reopen the menu
									MenuData mData = menuData.get(id);
									Map<Integer, ItemStack> itemStacks = buildInventory(player, MagicSpells.NULL_ARGS,
											mData);
									Inventory newInv = Bukkit.createInventory(player,
											event.getView().getTopInventory().getSize(), Component.text(internalName));
									applyOptionsToInventory(player, newInv, MagicSpells.NULL_ARGS, mData, itemStacks);
									player.openInventory(newInv);
									Util.setInventoryTitle(player, title);
								}
								return;
							}
						}
					}
				}
				// If no spell was triggered, the shift-click is still cancelled but nothing
				// else happens
				return;
			}
			// Allow normal clicks in player inventory
			return;
		}

		// Cancel clicks in the top inventory (menu)
		event.setCancelled(true);

		String closeState = "reopen";

		UUID id = player.getUniqueId();
		MenuData mData = menuData.get(id);
		float menuPower = mData != null ? mData.power() : 1f;
		String[] menuArgs = mData != null ? mData.args() : null;
		boolean arrange = Boolean.TRUE.equals(autoArrange.get(player, menuPower, menuArgs));

		if (arrange && event.getSlot() == 52) {
			if (event.getCurrentItem() != null && event.getCurrentItem().equals(previousPageItem))
				mData = new MenuData(mData.targetEntity(), mData.targetLocation(), mData.power(), mData.args(),
						mData.page() - 1);
		} else if (arrange && event.getSlot() == 53) {
			if (event.getCurrentItem() != null && event.getCurrentItem().equals(nextPageItem))
				mData = new MenuData(mData.targetEntity(), mData.targetLocation(), mData.power(), mData.args(),
						mData.page() + 1);
		} else {
			closeState = castSpells(player, event.getCurrentItem(), event.getClick());
		}

		if (closeState.equals("ignore"))
			return;
		if (closeState.equals("close")) {
			menuData.remove(id);
			MagicSpells.scheduleDelayedTask(player::closeInventory, 0);
			return;
		}
		// Reopen.
		menuData.put(id, mData);
		Map<Integer, ItemStack> itemStacks = buildInventory(player, MagicSpells.NULL_ARGS, mData);
		Inventory newInv = Bukkit.createInventory(player, event.getView().getTopInventory().getSize(),
				Component.text(internalName));
		applyOptionsToInventory(player, newInv, MagicSpells.NULL_ARGS, mData, itemStacks);
		player.openInventory(newInv);
		Util.setInventoryTitle(player, title);
	}

	private String castSpells(Player player, ItemStack item, ClickType click) {
		// Check if this is any kind of drag/drop or shift-click attempt
		boolean isDragAndDrop = click == ClickType.LEFT && player.getItemOnCursor() != null
				&& !player.getItemOnCursor().getType().isAir();
		boolean isShiftClick = (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT);
		boolean isItemTransferAttempt = isDragAndDrop || isShiftClick;

		// Handle empty slots
		String key = item != null ? DataUtil.getString(item, "menuOption") : null;
		boolean isEmptySlot = (item == null) || (key == null || key.isEmpty() || !options.containsKey(key));

		if (isEmptySlot) {
			// If this is an item transfer attempt on an empty slot
			if (isItemTransferAttempt) {
				// Try to handle empty slot spells if configured
				if (spellsOnDropNonOption != null) {
					return handleShiftClickFromInventory(player, player.getItemOnCursor());
				}
				// If no empty slot spells configured, just ignore the drag/drop and keep menu
				// open
				return "ignore";
			}
			// Empty-handed click on empty slot - respect stay-open-non-option setting
			UUID emptyId = player.getUniqueId();
			MenuData emptyData = menuData.get(emptyId);
			float emptyPower = emptyData != null ? emptyData.power() : 1f;
			String[] emptyArgs = emptyData != null ? emptyData.args() : null;
			boolean stayOpenEmpty = Boolean.TRUE.equals(stayOpenNonOption.get(player, emptyPower, emptyArgs));
			return stayOpenEmpty ? "ignore" : "close";
		}
		MenuOption option = options.get(key);
		if (option == null)
			return "close";

		// Handle drag and drop
		if (click == ClickType.LEFT && player.getItemOnCursor() != null
				&& !player.getItemOnCursor().getType().isAir()) {
			if (option.spellsOnDrop != null) {
				String result = handleDragAndDrop(player, option.spellsOnDrop, option.power,
						option.variableModsClick, option.variableModsClicked, option.stayOpen,
						"MenuSpell '" + internalName + "' has an invalid 'spell-on-drop' spell defined for item '");

				if (result != null)
					return result;
			}
		}

		return switch (click) {
			case LEFT -> processClickSpell(player, option.spell, option);
			case RIGHT -> processClickSpell(player, option.spellRight, option);
			case MIDDLE -> processClickSpell(player, option.spellMiddle, option);
			case SHIFT_LEFT -> processClickSpell(player, option.spellSneakLeft, option);
			case SHIFT_RIGHT -> processClickSpell(player, option.spellSneakRight, option);
			case SWAP_OFFHAND -> processClickSpell(player, option.spellSwap, option);
			case DROP -> processClickSpell(player, option.spellDrop, option);
			default -> option.stayOpen ? "ignore" : "close";
		};
	}

	private String processClickSpell(Player player, Subspell spell, MenuOption option) {
		LivingEntity entityTarget = null;
		Location locationTarget = null;
		float power = option.power;
		String[] args = null;

		UUID id = player.getUniqueId();
		MenuData data = menuData.get(id);
		if (data != null) {
			locationTarget = data.targetLocation;
			entityTarget = data.targetEntity;
			power *= data.power;
			args = data.args;
		}

		processVariables(option.variableModsClick, player, data);

		if (spell == null)
			return option.stayOpen ? "ignore" : "close";

		boolean bypass = Boolean.TRUE.equals(bypassNormalCast.get(player, entityTarget, power, args));
		boolean success;

		if (entityTarget != null)
			success = spell.subcast(new SpellData(player, entityTarget, power, args));
		else if (locationTarget != null)
			success = spell.subcast(new SpellData(player, locationTarget, power, args));
		else if (bypass)
			success = spell.subcast(new SpellData(player, power, args));
		else {
			SpellCastResult result = spell.getSpell().cast(player, power, MagicSpells.NULL_ARGS);
			success = result.state.equals(SpellCastState.NORMAL)
					&& !result.action.equals(PostCastAction.ALREADY_HANDLED);
		}

		if (success)
			processVariables(option.variableModsClicked, player, data);

		return option.stayOpen ? "reopen" : "close";
	}

	private String handleDragAndDrop(Player player, ConfigData<ConfigurationSection> spellsOnDropConfig,
			float basePower,
			Multimap<String, VariableMod> variableModsClick, Multimap<String, VariableMod> variableModsClicked,
			boolean stayOpen, String errorContext) {
		// Get menu data for spell evaluation context
		UUID id = player.getUniqueId();
		MenuData data = menuData.get(id);
		float power = basePower;
		String[] args = null;

		if (data != null) {
			power *= data.power();
			args = data.args();
		}

		// Get the dragged item
		ItemStack draggedItem = player.getItemOnCursor();

		// Find matching spell for the dragged item
		MagicItemData draggedMagicItem = MagicItems.getMagicItemDataFromItemStack(draggedItem);
		if (draggedMagicItem != null) {
			String itemName = (String) draggedMagicItem.getAttribute(MagicItemData.MagicItemAttribute.MAGIC_ITEM_NAME);
			if (itemName != null) {
				// Get the spell configuration for this menu session
				SpellData spellData = new SpellData(player, 0f, args);
				ConfigurationSection dropSection = spellsOnDropConfig.get(spellData);
				if (dropSection != null) {
					// Get and initialize the spell for the dragged item
					String spellName = dropSection.getString(itemName);
					if (spellName != null) {
						Subspell spell = initSubspell(spellName, errorContext + itemName + "'");
						if (spell != null) {
							processVariables(variableModsClick, player, data);

							// Cast the spell
							boolean success;
							boolean bypass = Boolean.TRUE.equals(bypassNormalCast.get(player, data != null ? data.targetEntity() : null, power, args));
							if (data != null && data.targetEntity() != null) {
								success = spell.subcast(new SpellData(player, data.targetEntity(), power, args));
							} else if (data != null && data.targetLocation() != null) {
								success = spell.subcast(new SpellData(player, data.targetLocation(), power, args));
							} else if (bypass) {
								success = spell.subcast(new SpellData(player, power, args));
							} else {
								SpellCastResult result = spell.getSpell().cast(player, power, MagicSpells.NULL_ARGS);
								success = result.state.equals(SpellCastState.NORMAL)
										&& !result.action.equals(PostCastAction.ALREADY_HANDLED);
							}

							if (success)
								processVariables(variableModsClicked, player, data);

							return stayOpen ? "reopen" : "close";
						}
					}
				}
			}
		}

		return null; // Indicates no spell was found/executed
	}

	private String handleShiftClickFromInventory(Player player, ItemStack clickedItem) {
		// Get menu data for spell evaluation context
		UUID id = player.getUniqueId();
		MenuData data = menuData.get(id);
		float power = clickedItem.getAmount();
		String[] args = null;

		if (data != null) {
			power *= data.power();
			args = data.args();
		}

		// Find matching spell for the clicked item
		MagicItemData magicItem = MagicItems.getMagicItemDataFromItemStack(clickedItem);
		if (magicItem != null) {
			String itemName = (String) magicItem.getAttribute(MagicItemData.MagicItemAttribute.MAGIC_ITEM_NAME);
			if (itemName != null) {
				// Get the spell configuration for this menu session
				SpellData spellData = new SpellData(player, 0f, args);
				ConfigurationSection dropSection = spellsOnDropNonOption.get(spellData);
				if (dropSection != null) {
					// Get and initialize the spell for the clicked item
					String spellName = dropSection.getString(itemName);
					if (spellName != null) {
						Subspell spell = initSubspell(spellName,
								"MenuSpell '" + internalName
										+ "' has an invalid 'empty-slot-spells-on-drop' spell defined for item '"
										+ itemName + "'");
						if (spell != null) {
							// Cast the spell
							boolean success;
							boolean bypass = Boolean.TRUE.equals(bypassNormalCast.get(player, data != null ? data.targetEntity() : null, power, args));
							if (data != null && data.targetEntity() != null) {
								success = spell.subcast(new SpellData(player, data.targetEntity(), power, args));
							} else if (data != null && data.targetLocation() != null) {
								success = spell.subcast(new SpellData(player, data.targetLocation(), power, args));
							} else if (bypass) {
								success = spell.subcast(new SpellData(player, power, args));
							} else {
								SpellCastResult result = spell.getSpell().cast(player, power, MagicSpells.NULL_ARGS);
								success = result.state.equals(SpellCastState.NORMAL)
										&& !result.action.equals(PostCastAction.ALREADY_HANDLED);
							}

							return success ? "reopen" : "close";
						}
					}
				}
			}
		}

		// For item transfer attempts, always keep menu open even if no spell was
		// executed
		return "ignore";
	}

	private void processVariables(Multimap<String, VariableMod> varMods, Player player, MenuData data) {
		if (varMods == null || varMods.isEmpty())
			return;

		for (Map.Entry<String, VariableMod> entry : varMods.entries()) {
			VariableMod mod = entry.getValue();
			if (mod == null)
				continue;

			Variable variable = MagicSpells.getVariableManager().getVariable(entry.getKey());

			String amount = MagicSpells.getVariableManager().processVariableMods(variable, mod, player, player, null,
					data.power(), data.args());
			MagicSpells.debug(3, "Variable '" + entry.getKey() + "' for player '" + player.getName() + "' modified by "
					+ amount + " as a result of spell cast '" + internalName + "'");
		}
	}

	@EventHandler
	public void onInvDrag(InventoryDragEvent event) {
		if (Util.getStringFromComponent(event.getView().title()).equals(internalName)) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		menuData.remove(id);
	}

	/** Only meaningful when {@code delay} is a constant expression. */
	public int getDelay() {
		return delay.get(null, null, null, 1f, null);
	}

	public void setDelay(int delay) {
		this.delay = (c, t, l, p, a) -> delay;
	}

	/** Only meaningful when {@code stay-open-non-option} is a constant expression. */
	public boolean isStayOpenNonOption() {
		return Boolean.TRUE.equals(stayOpenNonOption.get(null, null, null, 1f, null));
	}

	public void setStayOpenNonOption(boolean stayOpenNonOption) {
		this.stayOpenNonOption = (c, t, l, p, a) -> stayOpenNonOption;
	}

	/** Only meaningful when {@code bypass-normal-cast} is a constant expression. */
	public boolean isBypassNormalCast() {
		return Boolean.TRUE.equals(bypassNormalCast.get(null, null, null, 1f, null));
	}

	public void setBypassNormalCast(boolean bypassNormalCast) {
		this.bypassNormalCast = (c, t, l, p, a) -> bypassNormalCast;
	}

	/** Only meaningful when {@code require-entity-target} is a constant expression. */
	public boolean isRequireEntityTarget() {
		return Boolean.TRUE.equals(requireEntityTarget.get(null, null, null, 1f, null));
	}

	public void setRequireEntityTarget(boolean requireEntityTarget) {
		this.requireEntityTarget = (c, t, l, p, a) -> requireEntityTarget;
	}

	/** Only meaningful when {@code require-location-target} is a constant expression. */
	public boolean isRequireLocationTarget() {
		return Boolean.TRUE.equals(requireLocationTarget.get(null, null, null, 1f, null));
	}

	public void setRequireLocationTarget(boolean requireLocationTarget) {
		this.requireLocationTarget = (c, t, l, p, a) -> requireLocationTarget;
	}

	/** Only meaningful when {@code target-opens-menu-instead} is a constant expression. */
	public boolean isTargetOpensMenuInstead() {
		return Boolean.TRUE.equals(targetOpensMenuInstead.get(null, null, null, 1f, null));
	}

	public void setTargetOpensMenuInstead(boolean targetOpensMenuInstead) {
		this.targetOpensMenuInstead = (c, t, l, p, a) -> targetOpensMenuInstead;
	}

	/** Only meaningful when {@code auto-arrange} is a constant expression. */
	public boolean isAutoArrange() {
		return Boolean.TRUE.equals(autoArrange.get(null, null, null, 1f, null));
	}

	public void setAutoArrange(boolean autoArrange) {
		this.autoArrange = (c, t, l, p, a) -> autoArrange;
	}

	private record MenuData(LivingEntity targetEntity, Location targetLocation, float power, String[] args, int page) {
	}

	private static class MenuOption {
		private String menuOptionName;
		private ConfigData<List<Integer>> slots;
		private ConfigData<ConfigurationSection> itemSection;
		private ConfigData<String> itemString;
		private ItemStack item;
		private List<ItemStack> items;
		private ConfigData<Integer> quantity;
		private String spellName;
		private String spellRightName;
		private String spellMiddleName;
		private String spellSneakLeftName;
		private String spellSneakRightName;
		private String spellSwapName;
		private String spellDropName;
		private Subspell spell;
		private Subspell spellRight;
		private Subspell spellMiddle;
		private Subspell spellSneakLeft;
		private Subspell spellSneakRight;
		private Subspell spellSwap;
		private Subspell spellDrop;
		private float power;
		private List<String> modifierList;
		private ModifierSet menuOptionModifiers;
		private boolean stayOpen;
		private List<String> varModsClick;
		private List<String> varModsClicked;
		protected Multimap<String, VariableMod> variableModsClick;
		protected Multimap<String, VariableMod> variableModsClicked;
		private ConfigData<ConfigurationSection> spellsOnDrop;
	}

}
