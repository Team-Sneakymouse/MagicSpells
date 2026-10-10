package com.nisovin.magicspells.spells;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.Spell;
import com.nisovin.magicspells.Spellbook;
import com.nisovin.magicspells.castmodifiers.ModifierSet;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.magicitems.MagicItemData;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.variables.Variable;
import com.nisovin.magicspells.variables.variabletypes.GlobalVariable;
import com.nisovin.magicspells.variables.variabletypes.PlayerVariable;

import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * AutoPickupSpell automatically converts picked up items into variable values.
 * When a player picks up configured items, they are converted to variable points
 * based on the configured multiplier, respecting the variable's maximum capacity.
 * 
 * Configuration example:
 * options:
 *   coins:
 *     item: "gold_ingot"
 *     variable: "coins"
 *     multiplier: 10
 *     modifiers: ["chance 50 denied"]
 */
public class AutoPickupSpell extends Spell {

    private List<AutoPickupOption> options = new ArrayList<>();
    private ConfigData<Boolean> supressPickupSound;

    /**
     * Creates a new AutoPickupSpell and loads configuration options.
     * 
     * @param config the spell configuration
     * @param spellName the name of this spell
     */
    public AutoPickupSpell(MagicConfig config, String spellName) {
        super(config, spellName);
        supressPickupSound = getConfigDataBoolean("supress-pickup-sound", false);
		Set<String> optionKeys = getConfigKeys("options");
		if (optionKeys == null || optionKeys.isEmpty()) {
			MagicSpells.error("AutoPickupSpell '" + spellName + "' has no options!");
			return;
		}
		for (String optionName : optionKeys) {
			String path = "options." + optionName + ".";
            AutoPickupOption option = new AutoPickupOption();
            option.item = getConfigDataString(path + "item", null);
            option.variableName = getConfigDataString(path + "variable", null);
            option.modifierList = getConfigStringList(path + "modifiers", null);
            option.multiplier = getConfigDataFloat(path + "multiplier", 1);
            options.add(option);
		}
    }

	@Override
	public void initializeModifiers() {
		super.initializeModifiers();

		// Initialize modifier sets for each pickup option
		for (AutoPickupOption option : options) {
			if (option.modifierList != null) option.modifiers = new ModifierSet(option.modifierList, this);
		}
	}

    @Override
    public void initialize() {
        super.initialize();
        // Register the event listener to handle item pickup events
        MagicSpells.registerEvents(new AutoPickupListener());
    }

    /**
     * This spell cannot be cast directly - it only responds to pickup events.
     */
    @Override
    public CastResult cast(SpellData data) {
        return new CastResult(PostCastAction.ALREADY_HANDLED, data);
    }

    @Override
    public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
        return cast(new SpellData(caster, power, args)).action();
    }

    @Override
    public boolean canCastWithItem() {
        return false;
    }

    @Override
    public boolean canCastByCommand() {
        return false;
    }

    /**
     * Event listener that handles automatic item pickup and conversion to variables.
     */
    private class AutoPickupListener implements Listener {
        
        /**
         * Handles inventory pickup events to convert items to variable values.
         * 
         * @param event the inventory pickup event
         */
        @EventHandler
        public void onInventoryPickup(EntityPickupItemEvent event) {
            if (event.isCancelled()) {
                MagicSpells.debug(3, "AutoPickupSpell: Event cancelled, skipping");
                return;
            }
            
            // Only process pickups by players
            if (event.getEntity() instanceof Player caster) {
                MagicSpells.debug(3, "AutoPickupSpell: Player " + caster.getName() + " picked up item");
                
                Spellbook spellbook = MagicSpells.getSpellbook(caster);
                boolean hasSpell = spellbook.hasSpell(AutoPickupSpell.this);
                boolean canLearn = spellbook.canLearn(AutoPickupSpell.this);
                MagicSpells.debug(3, "AutoPickupSpell: hasSpell=" + hasSpell + ", canLearn=" + canLearn);
                
                if (!hasSpell && canLearn) {
                    MagicSpells.debug(3, "AutoPickupSpell: Player doesn't have spell and can learn it, skipping");
                    return;
                }
                MagicSpells.debug(3, "AutoPickupSpell: Processing " + options.size() + " pickup options");
                
                // Check each configured pickup option
                for (AutoPickupOption option : options) {
                    SpellData data = new SpellData(caster, caster, 1, new String[0]);
                    ItemStack item = event.getItem().getItemStack();
                    
                    // Check if the picked up item matches this option's configured item
                    MagicItemData itemData = MagicItems.getMagicItemDataFromItemStack(item);
                    String itemString = option.item.get(data);
                    MagicSpells.debug(3, "AutoPickupSpell: Checking item " + item.getType() + " against config " + itemString);
                    
                    if (itemData == null || itemString == null) {
                        MagicSpells.debug(3, "AutoPickupSpell: ItemData or config string is null, continuing");
                        continue;
                    }
                    
                    MagicItemData configItemData = MagicItems.getMagicItemDataFromString(itemString);
                    if (configItemData == null || !MagicItems.matches(configItemData, item)) {
                        MagicSpells.debug(3, "AutoPickupSpell: Item doesn't match (configItemData=" + configItemData + "), continuing");
                        continue;
                    }
                    // Get the target variable and validate it exists and is the right type
                    Variable variable = MagicSpells.getVariableManager().getVariable(option.variableName.get(data));
                    MagicSpells.debug(3, "AutoPickupSpell: Variable " + option.variableName.get(data) + " found: " + (variable != null));
                    
                    if (variable == null || !((variable instanceof GlobalVariable) || (variable instanceof PlayerVariable))) {
                        MagicSpells.debug(3, "AutoPickupSpell: Variable is null or wrong type, continuing");
                        continue;
                    }
                    
                    // Check if player passes the required modifiers for this option
                    ModifierSet modifiers = option.modifiers;
                    if (modifiers != null && !modifiers.check(caster)) {
                        MagicSpells.debug(3, "AutoPickupSpell: Modifiers failed, continuing");
                        continue;
                    }
                    // Calculate how much can be picked up based on variable capacity
                    int amount = item.getAmount();
                    Float multiplier = option.multiplier.get(data);
                    double variableMax = variable.getMaxValue(caster);
                    double currentVariableValue = variable.getValue(caster);
                    
                    // Calculate maximum items that can be converted without exceeding variable limit
                    int capacity = (int) Math.floor((variableMax - currentVariableValue) / multiplier);
                    MagicSpells.debug(3, "AutoPickupSpell: Capacity=" + capacity + ", current=" + currentVariableValue + ", max=" + variableMax);
                    
                    if (capacity <= 0) {
                        MagicSpells.debug(3, "AutoPickupSpell: Variable at max capacity, continuing");
                        continue; // Variable is at max capacity
                    }
                    // Pick up as much as possible without exceeding capacity
                    int pickupAmount = Math.min(capacity, amount);
                    
                    // Update the item stack or remove it entirely if fully consumed
                    if (amount - pickupAmount <= 0) {
                        event.getItem().remove(); // All items converted, remove from world
                        if (!supressPickupSound.get(data)) {
                            caster.playSound(caster.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.3F, 2.0F);
                        }
                        event.setCancelled(true);
                    } else {
                        // Reduce the item stack by the amount picked up
                        event.getItem().setItemStack(new ItemStack(item.getType(), amount - pickupAmount));
                    }

                    //Play effects
                    playSpellEffects(caster, event.getItem().getLocation(), data);
                    
                    // Add the converted value to the player's variable
                    double newValue = currentVariableValue + pickupAmount * multiplier;
                    variable.set(caster, newValue);
                    MagicSpells.debug(3, "AutoPickupSpell: Converted " + pickupAmount + " items to " + (pickupAmount * multiplier) + " variable points. New value: " + newValue);
                    break;
                }
            } else {
                MagicSpells.debug(3, "AutoPickupSpell: Not a player pickup, skipping");
            }
        }
    }

    /**
     * Represents a single auto-pickup configuration option.
     * Each option defines an item type, target variable, and conversion rules.
     */
    private static class AutoPickupOption {
        private ConfigData<String> item;
        private ConfigData<String> variableName;
		private List<String> modifierList;
		private ModifierSet modifiers;
        private ConfigData<Float> multiplier;
    }
}