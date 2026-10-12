package com.nisovin.magicspells.util.reagent;

import java.util.List;
import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.util.magicitems.MagicItems;
import com.nisovin.magicspells.util.magicitems.MagicItemData;

public class SpellReagents {

    private List<Reagent> reagents;
    private boolean parseSucceeded = true;

    public SpellReagents() {
        reagents = new ArrayList<>();
    }

    public SpellReagents(SpellReagents other) {
        reagents = new ArrayList<>();
        for (Reagent reagent : other.reagents) {
            reagents.add(reagent.clone());
        }
    }

    public List<Reagent> getReagents() {
        return reagents;
    }

    public void setReagents(List<Reagent> newReagents) {
        if (newReagents == null || newReagents.isEmpty()) reagents = new ArrayList<>();
        else reagents = new ArrayList<>(newReagents);
    }

    public void addReagent(Reagent reagent) {
        reagents.add(reagent);
    }

    public static SpellReagents fromList(List<String> costList, String internalName, Entity livingEntity) {
        return fromList(costList, internalName, livingEntity, false);
    }

    public static SpellReagents fromList(List<String> costList, String internalName, Entity livingEntity,
            boolean consumeFromBagOfHolding) {
        SpellReagents spellReagents = new SpellReagents();
        if (costList == null || costList.isEmpty()) return spellReagents;

        for (String costEntry : costList) {
            try {
                // Split by pipe to get alternative reagents
                String[] alternatives = costEntry.split("\\|");
                Reagent selectedReagent = null;

                // Try each alternative until we find one that the player has or reach the end
                for (int i = 0; i < alternatives.length; i++) {
                    String costVal = alternatives[i].trim(); // Remove any whitespace
                    String[] data = costVal.split(" ");
                    Reagent reagent = switch (data[0].toLowerCase()) {
                        case "mana" -> new ManaReagent(Integer.parseInt(data[1]));
                        case "health" -> new HealthReagent(Double.parseDouble(data[1]));
                        case "hunger" -> new HungerReagent(Integer.parseInt(data[1]));
                        case "experience" -> new ExperienceReagent(Integer.parseInt(data[1]));
                        case "levels" -> new LevelReagent(Integer.parseInt(data[1]));
                        case "durability" -> {
                            int durabilityAmount = Integer.parseInt(data[1]);
                            if (data.length > 2) {
                                // MagicItem specified in data[2]
                                MagicItemData magicItemData = MagicItems.getMagicItemDataFromString(data[2]);
                                if (magicItemData == null) {
                                    MagicSpells.error("Failed to process durability reagent MagicItem for " + internalName + " spell: " + data[2]);
                                    spellReagents.parseSucceeded = false;
                                    yield null;
                                }
                                yield new DurabilityReagent(durabilityAmount, magicItemData);
                            } else {
                                // No MagicItem specified, use original behavior
                                yield new DurabilityReagent(durabilityAmount);
                            }
                        }
                        case "money" -> new MoneyReagent(Float.parseFloat(data[1]));
                        case "itementity" -> {
                            if (data.length < 2) {
                                MagicSpells.error("Failed to process itementity reagent for " + internalName + " spell: " + costVal);
                                spellReagents.parseSucceeded = false;
                                yield null;
                            }

                            MagicItemData magicItemData = MagicItems.getMagicItemDataFromString(data[1]);
                            if (magicItemData == null) {
                                MagicSpells.error("Failed to process itementity reagent MagicItem for " + internalName + " spell: " + data[1]);
                                spellReagents.parseSucceeded = false;
                                yield null;
                            }

                            int amount = 1;
                            if (data.length > 2) {
                                amount = (int) Float.parseFloat(data[2]);
                            }

                            double radius = 3D;
                            if (data.length > 3) {
                                radius = Double.parseDouble(data[3]);
                            }

                            Material blockMaterial = null;
                            int blockDistance = 0;

                            if (data.length > 4) {
                                blockMaterial = Util.getMaterial(data[4]);
                                if (blockMaterial == null) {
                                    MagicSpells.error("Failed to process itementity reagent block material for " + internalName + " spell: " + data[4]);
                                    spellReagents.parseSucceeded = false;
                                    yield null;
                                }
                            }

                            if (data.length > 5) {
                                blockDistance = Integer.parseInt(data[5]);
                            }

                            yield new ItemEntityReagent(magicItemData, amount, radius, blockMaterial, blockDistance);
                        }
                        case "variable" -> {
                            VariableReagent varReagent = new VariableReagent();
                            varReagent.add(data[1], Double.parseDouble(data[2]));
                            yield varReagent;
                        }
                        default -> {
                            int quantity = 1;
                            if (data.length > 1) quantity = (int) Float.parseFloat(data[1]);

                            MagicItemData itemData = MagicItems.getMagicItemDataFromString(data[0]);
                            if (itemData == null) {
                                MagicSpells.error("Failed to process cost value for " + internalName + " spell: " + costVal);
                                spellReagents.parseSucceeded = false;
                                yield null;
                            }
                            ItemReagent itemReagent = new ItemReagent();
                            itemReagent.setConsumeFromBagOfHolding(consumeFromBagOfHolding);
                            String bagItemId = MagicItems.getMagicItemByInternalName(data[0]) != null ? data[0] : null;
                            itemReagent.add(itemData, quantity, bagItemId);
                            yield itemReagent;
                        }
                    };

                    if (reagent != null && reagent.get().doubleValue() != 0) {
                        if (livingEntity == null
                                || (livingEntity instanceof LivingEntity living && reagent.has(living))
                                || i == alternatives.length - 1) {
                            selectedReagent = reagent;
                            break;
                        }
                    } else {
                        MagicSpells.error("Failed to process cost value for " + internalName + " spell: " + costVal);
                        spellReagents.parseSucceeded = false;
                    }
                }

                // Add the selected reagent if one was found
                if (selectedReagent != null) {
                    spellReagents.addReagent(selectedReagent);
                }
            }
            catch (Exception e) {
                MagicSpells.error("Failed to process cost value for " + internalName + " spell: " + costEntry);
                spellReagents.parseSucceeded = false;
            }
        }

        return spellReagents;
    }

    public boolean hasAll(Entity livingEntity) {
        if (!parseSucceeded) return false;
        if (!(livingEntity instanceof LivingEntity living)) return livingEntity == null;
        if (reagents != null && !reagents.isEmpty()) {
            for (Reagent reagent : reagents) {
                if (!reagent.has(living)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void removeAll(Entity livingEntity) {
        if (!(livingEntity instanceof LivingEntity living)) return;
        if (reagents != null && !reagents.isEmpty()) {
            for (Reagent reagent : reagents) {
                reagent.remove(living);
            }
        }
    }

    public SpellReagents multiply(float x) {
        SpellReagents other = new SpellReagents();
        if (reagents != null && !reagents.isEmpty()) {
            for (Reagent reagent : reagents) {
                Reagent multipliedReagent = reagent.clone();
                multipliedReagent.multiply(x);
                other.addReagent(multipliedReagent);
            }
        }
        return other;
    }

    @Override
    public SpellReagents clone() {
        return new SpellReagents(this);
    }


    @Override
    public String toString() {
        return "SpellReagents{" +
                "reagents=" + reagents +
                '}';
    }
}

