package com.nisovin.magicspells.util.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.nisovin.magicspells.MagicSpells;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Marker;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AlteredBlockManager {

    private static final GsonComponentSerializer COMPONENT_JSON = GsonComponentSerializer.gson();

    private final Map<Block, AlteredBlock> alteredBlocks = new HashMap<>();
    private final NamespacedKey blockDataKey;
    private final NamespacedKey blockStateKey;
    private final NamespacedKey worldKey;
    private final NamespacedKey xKey;
    private final NamespacedKey yKey;
    private final NamespacedKey zKey;

    public AlteredBlockManager() {
        MagicSpells plugin = MagicSpells.getInstance();
        blockDataKey = new NamespacedKey(plugin, "altered_block_data");
        blockStateKey = new NamespacedKey(plugin, "altered_block_state");
        worldKey = new NamespacedKey(plugin, "altered_block_world");
        xKey = new NamespacedKey(plugin, "altered_block_x");
        yKey = new NamespacedKey(plugin, "altered_block_y");
        zKey = new NamespacedKey(plugin, "altered_block_z");
    }

    public void add(Change change) {
        boolean isNew = !alteredBlocks.containsKey(change.block());
        AlteredBlock alteredBlock = alteredBlocks.computeIfAbsent(change.block(), b -> new AlteredBlock(change));
        alteredBlock.changes.add(change);
        if (isNew) {
            alteredBlock.marker = spawnMarker(change.block(), change.fromData(), change.fromState());
        }
    }

    /**
     * Captures the current block, applies {@code newData}, and registers a reversible {@link Change}.
     */
    public Change apply(String internalName, Block block, BlockData newData, boolean applyPhysics) {
        BlockData fromData = block.getBlockData();
        BlockState fromState = block.getState();
        block.setBlockData(newData, applyPhysics);
        Change change = new Change(internalName, block, fromData, fromState);
        add(change);
        return change;
    }

    /**
     * Captures the current block, applies {@code material}'s default BlockData, and registers a reversible {@link Change}.
     */
    public Change apply(String internalName, Block block, Material material, boolean applyPhysics) {
        return apply(internalName, block, material.createBlockData(), applyPhysics);
    }

    /**
     * Registers a reversible {@link Change} for a block that has already been modified
     * (e.g. by WorldEdit). Does not write to the world.
     */
    public Change register(String internalName, Block block, BlockData fromData, BlockState fromState) {
        Change change = new Change(internalName, block, fromData, fromState);
        add(change);
        return change;
    }

    public boolean isTracked(Block block) {
        return alteredBlocks.containsKey(block);
    }

    public List<Change> getByInternalName(String internalName) {
        List<Change> result = new ArrayList<>();
        for (AlteredBlock alteredBlock : alteredBlocks.values()) {
            for (Change change : alteredBlock.changes) {
                if (change.internalName().equals(internalName)) {
                    result.add(change);
                }
            }
        }
        return result;
    }

    public List<Change> getByBlock(Block block) {
        AlteredBlock alteredBlock = alteredBlocks.get(block);
        if (alteredBlock == null) return Collections.emptyList();
        return new ArrayList<>(alteredBlock.changes);
    }

    public List<Change> getByBlockAndInternalName(Block block, String internalName) {
        AlteredBlock alteredBlock = alteredBlocks.get(block);
        if (alteredBlock == null) return Collections.emptyList();

        List<Change> result = new ArrayList<>();
        for (Change change : alteredBlock.changes) {
            if (change.internalName().equals(internalName)) {
                result.add(change);
            }
        }
        return result;
    }

    /**
     * Undoes every change registered under {@code internalName} (LIFO per block).
     */
    public void undoAll(String internalName, boolean applyPhysics) {
        List<Change> changes = getByInternalName(internalName);
        for (int i = changes.size() - 1; i >= 0; i--) {
            changes.get(i).undo(applyPhysics);
        }
    }

    /**
     * Resolves the block this marker was created for (from PDC), falling back to the entity's current block.
     */
    public Block getMarkedBlock(Entity entity) {
        if (entity == null) return null;
        var pdc = entity.getPersistentDataContainer();
        String worldId = pdc.get(worldKey, PersistentDataType.STRING);
        Integer x = pdc.get(xKey, PersistentDataType.INTEGER);
        Integer y = pdc.get(yKey, PersistentDataType.INTEGER);
        Integer z = pdc.get(zKey, PersistentDataType.INTEGER);
        if (worldId != null && x != null && y != null && z != null) {
            try {
                org.bukkit.World world = Bukkit.getWorld(UUID.fromString(worldId));
                if (world != null) return world.getBlockAt(x, y, z);
                MagicSpells.error("Altered-block marker references missing world " + worldId);
                return null;
            } catch (IllegalArgumentException e) {
                MagicSpells.error("Altered-block marker has invalid world UUID: " + worldId);
                return null;
            }
        }
        return entity.getLocation().getBlock();
    }

    /**
     * Restores a block from an orphan marker (prior session) and removes the entity.
     */
    public void restoreFromMarker(Entity entity) {
        if (entity == null || !entity.isValid()) return;
        if (!entity.getScoreboardTags().contains(MagicSpells.ALTERED_BLOCK_TAG)) return;

        Block block = getMarkedBlock(entity);
        var pdc = entity.getPersistentDataContainer();
        String dataString = pdc.get(blockDataKey, PersistentDataType.STRING);
        if (block != null && dataString != null) {
            try {
                block.setBlockData(Bukkit.createBlockData(dataString), false);
            } catch (IllegalArgumentException e) {
                MagicSpells.error("Invalid altered-block marker BlockData at " + block.getLocation() + ": " + dataString);
            }

            String stateString = pdc.get(blockStateKey, PersistentDataType.STRING);
            if (stateString != null) {
                try {
                    applySerializedTileData(block, stateString, false);
                } catch (RuntimeException e) {
                    MagicSpells.error("Invalid altered-block marker tile data at " + block.getLocation() + ": " + e.getMessage());
                }
            }
        }
        entity.remove();
    }

    private Marker spawnMarker(Block block, BlockData originalData, BlockState originalState) {
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        Marker marker = (Marker) block.getWorld().spawnEntity(loc, EntityType.MARKER);
        marker.setPersistent(true);
        marker.setGravity(false);
        marker.setSilent(true);
        marker.setInvulnerable(true);
        marker.addScoreboardTag(MagicSpells.ALTERED_BLOCK_TAG);
        var pdc = marker.getPersistentDataContainer();
        pdc.set(blockDataKey, PersistentDataType.STRING, originalData.getAsString());
        String tileData = serializeTileData(originalState);
        if (tileData != null)
            pdc.set(blockStateKey, PersistentDataType.STRING, tileData);
        pdc.set(worldKey, PersistentDataType.STRING, block.getWorld().getUID().toString());
        pdc.set(xKey, PersistentDataType.INTEGER, block.getX());
        pdc.set(yKey, PersistentDataType.INTEGER, block.getY());
        pdc.set(zKey, PersistentDataType.INTEGER, block.getZ());
        return marker;
    }

    private void removeMarker(AlteredBlock alteredBlock) {
        if (alteredBlock.marker != null && alteredBlock.marker.isValid()) {
            alteredBlock.marker.remove();
        }
        alteredBlock.marker = null;
    }

    /**
     * Serializes tile-entity state for cross-session marker restore (signs, containers, etc.).
     * Returns null when there is nothing beyond BlockData to store.
     */
    static String serializeTileData(BlockState state) {
        if (state == null) return null;

        JsonObject root = new JsonObject();
        root.addProperty("v", 1);

        switch (state) {
            case Sign sign -> {
                root.addProperty("kind", "sign");
                root.add("front", serializeSignSide(sign.getSide(Side.FRONT)));
                root.add("back", serializeSignSide(sign.getSide(Side.BACK)));
                root.addProperty("waxed", sign.isWaxed());
            }
            case Container container -> {
                root.addProperty("kind", "container");
                root.addProperty("items", encodeItems(container.getInventory().getContents()));
            }
            case Lectern lectern -> {
                root.addProperty("kind", "lectern");
                root.addProperty("items", encodeItems(lectern.getInventory().getContents()));
                root.addProperty("page", lectern.getPage());
            }
            case CreatureSpawner spawner -> {
                root.addProperty("kind", "spawner");
                EntityType spawned = spawner.getSpawnedType();
                if (spawned != null)
                    root.addProperty("spawnedType", spawned.name());
                root.addProperty("delay", spawner.getDelay());
                root.addProperty("minSpawnDelay", spawner.getMinSpawnDelay());
                root.addProperty("maxSpawnDelay", spawner.getMaxSpawnDelay());
                root.addProperty("spawnCount", spawner.getSpawnCount());
                root.addProperty("maxNearbyEntities", spawner.getMaxNearbyEntities());
                root.addProperty("requiredPlayerRange", spawner.getRequiredPlayerRange());
                root.addProperty("spawnRange", spawner.getSpawnRange());
            }
            case Banner banner -> {
                root.addProperty("kind", "banner");
                JsonArray patterns = new JsonArray();
                for (Pattern pattern : banner.getPatterns()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("color", pattern.getColor().name());
                    NamespacedKey key = RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.BANNER_PATTERN).getKey(pattern.getPattern());
                    entry.addProperty("pattern", key != null ? key.toString() : pattern.getPattern().toString());
                    patterns.add(entry);
                }
                root.add("patterns", patterns);
            }
            default -> {
                return null;
            }
        }

        return root.toString();
    }

    private static JsonObject serializeSignSide(org.bukkit.block.sign.SignSide side) {
        JsonObject obj = new JsonObject();
        JsonArray lines = new JsonArray();
        for (int i = 0; i < 4; i++) {
            lines.add(COMPONENT_JSON.serialize(side.line(i)));
        }
        obj.add("lines", lines);
        obj.addProperty("glowing", side.isGlowingText());
        DyeColor color = side.getColor();
        if (color != null)
            obj.addProperty("color", color.name());
        return obj;
    }

    private static String encodeItems(ItemStack[] contents) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents));
    }

    private static ItemStack[] decodeItems(String encoded) {
        return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
    }

    static void applySerializedTileData(Block block, String json, boolean applyPhysics) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("malformed tile JSON", e);
        }

        String kind = root.has("kind") ? root.get("kind").getAsString() : "";
        BlockState state = block.getState();

        switch (kind) {
            case "sign" -> {
                if (!(state instanceof Sign sign)) return;
                applySignSide(sign.getSide(Side.FRONT), root.getAsJsonObject("front"));
                applySignSide(sign.getSide(Side.BACK), root.getAsJsonObject("back"));
                if (root.has("waxed"))
                    sign.setWaxed(root.get("waxed").getAsBoolean());
                sign.update(true, applyPhysics);
            }
            case "container" -> {
                if (!(state instanceof Container container)) return;
                container.getInventory().setContents(decodeItems(root.get("items").getAsString()));
                container.update(true, applyPhysics);
            }
            case "lectern" -> {
                if (!(state instanceof Lectern lectern)) return;
                lectern.getInventory().setContents(decodeItems(root.get("items").getAsString()));
                if (root.has("page"))
                    lectern.setPage(root.get("page").getAsInt());
                lectern.update(true, applyPhysics);
            }
            case "spawner" -> {
                if (!(state instanceof CreatureSpawner spawner)) return;
                if (root.has("spawnedType")) {
                    try {
                        spawner.setSpawnedType(EntityType.valueOf(root.get("spawnedType").getAsString()));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                if (root.has("delay"))
                    spawner.setDelay(root.get("delay").getAsInt());
                if (root.has("minSpawnDelay"))
                    spawner.setMinSpawnDelay(root.get("minSpawnDelay").getAsInt());
                if (root.has("maxSpawnDelay"))
                    spawner.setMaxSpawnDelay(root.get("maxSpawnDelay").getAsInt());
                if (root.has("spawnCount"))
                    spawner.setSpawnCount(root.get("spawnCount").getAsInt());
                if (root.has("maxNearbyEntities"))
                    spawner.setMaxNearbyEntities(root.get("maxNearbyEntities").getAsInt());
                if (root.has("requiredPlayerRange"))
                    spawner.setRequiredPlayerRange(root.get("requiredPlayerRange").getAsInt());
                if (root.has("spawnRange"))
                    spawner.setSpawnRange(root.get("spawnRange").getAsInt());
                spawner.update(true, applyPhysics);
            }
            case "banner" -> {
                if (!(state instanceof Banner banner)) return;
                List<Pattern> patterns = new ArrayList<>();
                JsonArray array = root.getAsJsonArray("patterns");
                if (array != null) {
                    for (JsonElement element : array) {
                        JsonObject entry = element.getAsJsonObject();
                        DyeColor color = DyeColor.valueOf(entry.get("color").getAsString());
                        String patternId = entry.get("pattern").getAsString();
                        PatternType type = resolvePatternType(patternId);
                        if (type != null)
                            patterns.add(new Pattern(color, type));
                    }
                }
                banner.setPatterns(patterns);
                banner.update(true, applyPhysics);
            }
            default -> {
            }
        }
    }

    private static void applySignSide(org.bukkit.block.sign.SignSide side, JsonObject obj) {
        if (obj == null) return;
        JsonArray lines = obj.getAsJsonArray("lines");
        if (lines != null) {
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                Component line = COMPONENT_JSON.deserialize(lines.get(i).getAsString());
                side.line(i, line);
            }
        }
        if (obj.has("glowing"))
            side.setGlowingText(obj.get("glowing").getAsBoolean());
        if (obj.has("color")) {
            try {
                side.setColor(DyeColor.valueOf(obj.get("color").getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static PatternType resolvePatternType(String id) {
        if (id == null || id.isBlank()) return null;
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BANNER_PATTERN);
        String trimmed = id.trim();
        NamespacedKey key = NamespacedKey.fromString(trimmed.contains(":") ? trimmed : "minecraft:" + trimmed.toLowerCase());
        if (key != null) {
            PatternType typed = registry.get(key);
            if (typed != null) return typed;
        }
        // Legacy enum-style ids (e.g. STRIPE_BOTTOM → minecraft:stripe_bottom)
        return registry.get(NamespacedKey.minecraft(trimmed.toLowerCase()));
    }

    public class AlteredBlock {
        final BlockData originalBlockData;
        final BlockState originalBlockState;
        final List<Change> changes = new ArrayList<>();
        Entity marker;

        public AlteredBlock(Change change) {
            this.originalBlockData = change.fromData();
            this.originalBlockState = change.fromState();
        }
    }

    public record Change(String internalName, Block block, BlockData fromData, BlockState fromState) {
        public void undo(boolean applyPhysics) {
            AlteredBlockManager manager = MagicSpells.getAlteredBlockManager();
            AlteredBlock alteredBlock = manager.alteredBlocks.get(block);
            if (alteredBlock != null && alteredBlock.changes.contains(this)) {
                if (alteredBlock.changes.size() == 1) {
                    // Restore to original state
                    restoreBlockState(alteredBlock.originalBlockData, alteredBlock.originalBlockState, applyPhysics);
                    manager.removeMarker(alteredBlock);
                    manager.alteredBlocks.remove(block);
                } else {
                    // Restore to previous change state
                    restoreBlockState(fromData, fromState, applyPhysics);
                    alteredBlock.changes.remove(this);
                }
            }
        }

        private void restoreBlockState(BlockData blockData, BlockState blockState, boolean applyPhysics) {
            block.setBlockData(blockData, applyPhysics);

            String tileData = serializeTileData(blockState);
            if (tileData != null)
                applySerializedTileData(block, tileData, applyPhysics);
        }
    }
}
