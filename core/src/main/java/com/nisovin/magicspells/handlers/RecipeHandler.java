package com.nisovin.magicspells.handlers;

import java.util.*;

import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.event.Listener;
import org.bukkit.configuration.ConfigurationSection;

import com.nisovin.magicspells.util.Util;
import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.ItemUtil;
import com.nisovin.magicspells.util.magicitems.MagicItem;
import com.nisovin.magicspells.util.magicitems.MagicItems;

public class RecipeHandler implements Listener {

    private static final Map<String, Recipe> recipes = new HashMap<>();

    public static void create(ConfigurationSection config) {
        String type = config.getString("type", "shaped").toLowerCase();

        String itemString = config.getString("result");
        if (itemString == null || itemString.isEmpty()) {
            MagicSpells.error("Recipe '" + config.getName() + "' has an invalid 'result' item defined.");
            return;
        }
        MagicItem magicItem = MagicItems.getMagicItemFromString(itemString);
        if (magicItem == null) {
            MagicSpells.error("Recipe '" + config.getName() + "' has an invalid 'result' item defined.");
            return;
        }
        ItemStack result = magicItem.getItemStack();

        int quantity = config.getInt("quantity", 1);
        if (quantity > 1) result.setAmount(quantity);

        String group = config.getString("group");
        if (group == null) group = "";

        String namespaceKeyString = config.getString("namespace-key");
        if (namespaceKeyString == null || namespaceKeyString.isEmpty()) {
            MagicSpells.error("Recipe '" + config.getName() + "' has an invalid 'namespace-key' defined.");
            return;
        }
        NamespacedKey namespaceKey;
        try {
            namespaceKey = new NamespacedKey(MagicSpells.plugin, namespaceKeyString.toLowerCase());
        }
        catch (IllegalArgumentException e) {
            MagicSpells.error("Recipe '" + config.getName() + "' has an invalid 'namespace-key' defined: " + namespaceKeyString);
            MagicSpells.handleException(e);
            return;
        }

        Recipe recipe = null;
        switch (type) {
            case "shaped" -> recipe = createShapedRecipe(config, namespaceKey, result, group);
            case "shapeless" -> recipe = createShapelessRecipe(config, namespaceKey, result, group);
            case "smoking", "campfire", "blasting", "furnace" -> recipe = createCookingRecipe(config, type, namespaceKey, result, group);
            case "stonecutting" -> recipe = createStoneCuttingRecipe(config, namespaceKey, result, group);
            case "smithing" -> recipe = createSmithingRecipe(config, namespaceKey, result);
            default -> MagicSpells.error("Recipe '" + config.getName() + "' has an invalid 'type' defined.");
        }
        if (recipe == null) return;

        Bukkit.addRecipe(recipe);
        recipes.put(config.getName(), recipe);
    }

    public static Map<String, Recipe> getRecipes() {
        return recipes;
    }

    private static Recipe createShapedRecipe(ConfigurationSection config, NamespacedKey namespaceKey, ItemStack result, String group) {
        ShapedRecipe recipe = new ShapedRecipe(namespaceKey, result);
        recipe.setGroup(group);

        List<String> shape = config.getStringList("shape");
        if (shape.isEmpty()) {
            MagicSpells.error("Recipe '" + config.getName() + "' has an invalid 'shape' defined.");
            return null;
        }
        try {
            recipe.shape(shape.toArray(new String[0]));
        }
        catch (IllegalArgumentException e) {
            MagicSpells.handleException(e);
            return null;
        }

        // Validate ingredients.
        ConfigurationSection shapedIngredients = config.getConfigurationSection("ingredients");
        if (shapedIngredients == null) {
            MagicSpells.error("Recipe '" + config.getName() + "' has no 'ingredients' defined.");
            return null;
        }
        for (String ingredientKey : shapedIngredients.getKeys(false)) {
            if (ingredientKey.length() != 1) {
                MagicSpells.error("Recipe '" + config.getName() + "' has an invalid ingredient key defined: " + ingredientKey);
                return null;
            }
            Material ingredient = getMaterial(shapedIngredients.getString(ingredientKey), "Recipe '" + config.getName() + "' has an invalid 'ingredient' defined under key '" + ingredientKey + "'.");
            if (ingredient == null) return null;
            recipe.setIngredient(ingredientKey.charAt(0), ingredient);
        }

        return recipe;
    }

    private static Recipe createShapelessRecipe(ConfigurationSection config, NamespacedKey namespaceKey, ItemStack result, String group) {
        ShapelessRecipe recipe = new ShapelessRecipe(namespaceKey, result);
        recipe.setGroup(group);

        // Validate ingredients.
        List<String> shapelessIngredients = config.getStringList("ingredients");
        if (shapelessIngredients.isEmpty()) {
            MagicSpells.error("Recipe '" + config.getName() + "' has no 'ingredients' defined.");
            return null;
        }
        for (String ingredientString : shapelessIngredients) {
            Material ingredient = getMaterial(ingredientString, "Recipe '" + config.getName() + "' has an invalid ingredient defined: " + ingredientString);
            if (ingredient == null) return null;
            recipe.addIngredient(ingredient);
        }

        return recipe;
    }

    private static Recipe createCookingRecipe(ConfigurationSection config, String type, NamespacedKey namespaceKey, ItemStack result, String group) {
        Material ingredient = getMaterial(config.getString("ingredient"), "Recipe '" + config.getName() + "' has an invalid 'ingredient' defined.");
        if (ingredient == null) return null;

        float experience = (float) config.getDouble("experience", 0);
        int cookingTime = config.getInt("cooking-time", 0);

        if (type.equals("furnace")) {
            FurnaceRecipe recipe = new FurnaceRecipe(namespaceKey, result, ingredient, experience, cookingTime);
            recipe.setGroup(group);
            return recipe;
        }
        // Check volatile types.
        Recipe recipe = ItemUtil.createCookingRecipe(type, namespaceKey, group, result, ingredient, experience, cookingTime);
        if (recipe == null) MagicSpells.error("Recipe type '" + type + "' on recipe '" + config.getName() + "' is unsupported on this version of spigot.");
        return recipe;
    }

    private static Recipe createStoneCuttingRecipe(ConfigurationSection config, NamespacedKey namespaceKey, ItemStack result, String group) {
        Material ingredient = getMaterial(config.getString("ingredient"), "Recipe '" + config.getName() + "' has an invalid 'ingredient' defined.");
        if (ingredient == null) return null;
        return ItemUtil.createStonecutterRecipe(namespaceKey, group, result, ingredient);
    }

    private static Recipe createSmithingRecipe(ConfigurationSection config, NamespacedKey namespaceKey, ItemStack result) {
        Material base = getMaterial(config.getString("base"), "Recipe '" + config.getName() + "' has an invalid 'base' defined.");
        if (base == null) return null;
        Material addition = getMaterial(config.getString("addition"), "Recipe '" + config.getName() + "' has an invalid 'addition' defined.");
        if (addition == null) return null;

        RecipeChoice templateChoice = RecipeChoice.empty();
        String templateName = config.getString("template");
        if (templateName != null && !templateName.isEmpty()) {
            Material template = getMaterial(templateName, "Recipe '" + config.getName() + "' has an invalid 'template' defined.");
            if (template == null) return null;
            templateChoice = new RecipeChoice.MaterialChoice(template);
        }

        return new SmithingTransformRecipe(
                namespaceKey,
                result,
                templateChoice,
                new RecipeChoice.MaterialChoice(base),
                new RecipeChoice.MaterialChoice(addition)
        );
    }

    public static void clearRecipes() {
        List<Recipe> recipeBackup = new ArrayList<>();

        // Collect recipes.
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            recipeBackup.add(iterator.next());
        }

        Bukkit.clearRecipes();

        // Add back old recipes.
        for (Recipe recipe : recipeBackup) {
            // Filter out MS recipes.
            if (recipe instanceof Keyed && ((Keyed) recipe).getKey().getNamespace().equals("magicspells")) continue;
            Bukkit.addRecipe(recipe);
        }
    }

    private static Material getMaterial(String materialName, String errorMsg) {
        if (materialName == null || materialName.isEmpty()) {
            MagicSpells.error(errorMsg);
            return null;
        }
        Material material = null;
        try {
            material = Util.getMaterial(materialName);
        } catch (IllegalArgumentException ignored) {

        }

        if (material == null) {
            MagicSpells.error(errorMsg);
            return null;
        }
        return material;
    }

}
