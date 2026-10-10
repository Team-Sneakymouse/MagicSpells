package com.nisovin.magicspells.spells.command;

import java.util.Set;
import java.util.List;
import java.util.HashSet;

import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.command.CommandSender;

import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.CastItem;
import com.nisovin.magicspells.util.BlockUtils;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.CommandSpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;

public class BindSpell extends CommandSpell {
	
	private Set<CastItem> bindableItems;

	private Set<Spell> allowedSpells;

	private boolean allowBindToFist;

	private String strUsage;
	private String strNoSpell;
	private String strCantBindItem;
	private String strCantBindSpell;
	private String strSpellCantBind;

	public BindSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		List<String> bindables = getConfigStringList("bindable-items", null);
		if (bindables != null) {
			bindableItems = new HashSet<>();
			for (String s : bindables) {
				bindableItems.add(new CastItem(s));
			}
		}

		List<String> allowedSpellNames = getConfigStringList("allowed-spells", null);
		if (allowedSpellNames != null && !allowedSpellNames.isEmpty()) {
			allowedSpells = new HashSet<>();
			for (String name: allowedSpellNames) {
				Spell s = MagicSpells.getSpellByInternalName(name);
				if (s != null) allowedSpells.add(s);
				else MagicSpells.plugin.getLogger().warning("Invalid spell listed: " + name);
			}
		}

		allowBindToFist = getConfigBoolean("allow-bind-to-fist", false);

		strUsage = getConfigString("str-usage", "You must specify a spell name and hold an item in your hand.");
		strNoSpell = getConfigString("str-no-spell", "You do not know a spell by that name.");
		strCantBindItem = getConfigString("str-cant-bind-item", "That spell cannot be bound to that item.");
		strCantBindSpell = getConfigString("str-cant-bind-spell", "That spell cannot be bound to an item.");
		strSpellCantBind = getConfigString("str-spell-cant-bind", "That spell cannot be bound like this.");
	}
	
	// DEBUG INFO: level 3, trying to bind spell internalName to cast item castItemString
	// DEBUG INFO: level 3, performing bind
	// DEBUG INFO: level 3, bind successful
	@Override
	public CastResult cast(SpellData data) {
		if (!(data.caster() instanceof Player player))
			return new CastResult(PostCastAction.HANDLE_NORMALLY, data);

		if (data.args() == null || data.args().length == 0) {
						sendMessage(strUsage, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					Spell spell = MagicSpells.getSpellByInGameName(Util.arrayJoin(data.args(), ' '));
					Spellbook spellbook = MagicSpells.getSpellbook(player);
		
					if (spell == null) {
						sendMessage(strNoSpell, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					if (spell.isHelperSpell()) {
						sendMessage(strNoSpell, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					if (!spellbook.hasSpell(spell)) {
						sendMessage(strNoSpell, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					if (!spell.canCastWithItem()) {
						sendMessage(strCantBindSpell, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					if (allowedSpells != null && !allowedSpells.contains(spell)) {
						sendMessage(strSpellCantBind, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					CastItem castItem = new CastItem(player.getEquipment().getItemInMainHand());
					MagicSpells.debug(3, "Trying to bind spell '" + spell.getInternalName() + "' to cast item " + castItem + "...");
		
					if (BlockUtils.isAir(castItem.getType()) && !allowBindToFist) {
						sendMessage(strCantBindItem, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					if (bindableItems != null && !bindableItems.contains(castItem)) {
						sendMessage(strCantBindItem, player, data.args());
						return new CastResult(PostCastAction.ALREADY_HANDLED, data);
					}
		
					if (!spell.canBind(castItem)) {
						String msg = spell.getCantBindError();
						if (msg == null) msg = strCantBindItem;
						sendMessage(msg, player, data.args());
						return new CastResult(PostCastAction.NO_MESSAGES, data);
					}
		
					MagicSpells.debug(3, "    Performing bind...");
					spellbook.addCastItem(spell, castItem);
					spellbook.save();
					MagicSpells.debug(3, "    Bind successful.");
					sendMessage(strCastSelf, player, data.args(), "%s", spell.getName());
					playSpellEffects(EffectPosition.CASTER, player, data.power(), data.args());
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
		return false;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String partial) {
		if (sender instanceof Player && !partial.contains(" ")) return tabCompleteSpellName(sender, partial);
		return null;
	}

	public Set<CastItem> getBindableItems() {
		return bindableItems;
	}

	public Set<Spell> getAllowedSpells() {
		return allowedSpells;
	}

	public boolean shouldAllowBindToFist() {
		return allowBindToFist;
	}

	public void setAllowBindToFist(boolean allowBindToFist) {
		this.allowBindToFist = allowBindToFist;
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

	public String getStrCantBindItem() {
		return strCantBindItem;
	}

	public void setStrCantBindItem(String strCantBindItem) {
		this.strCantBindItem = strCantBindItem;
	}

	public String getStrCantBindSpell() {
		return strCantBindSpell;
	}

	public void setStrCantBindSpell(String strCantBindSpell) {
		this.strCantBindSpell = strCantBindSpell;
	}

	public String getStrSpellCantBind() {
		return strSpellCantBind;
	}

	public void setStrSpellCantBind(String strSpellCantBind) {
		this.strSpellCantBind = strSpellCantBind;
	}

}
