package com.nisovin.magicspells.util.magicitems;

import java.io.File;
import java.util.*;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.configuration.file.YamlConfiguration;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Containers.CMIUser;

import net.sneakymouse.sneakyvaults.SneakyVaults;
import net.sneakymouse.sneakyvaults.utlitiy.ChatUtility;

import net.sneakycharactermanager.paper.SneakyCharacterManager;
import net.sneakycharactermanager.paper.handlers.character.LoadCharacterEvent;

import org.jetbrains.annotations.Nullable;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.util.io.AtomicFiles;
import com.nisovin.magicspells.util.compat.CompatBasics;
import com.nisovin.magicspells.events.MagicSpellsLoadedEvent;
import com.nisovin.magicspells.util.magicitems.MagicItemBehaviors.ExpirationResult;
import com.nisovin.magicspells.util.magicitems.MagicItemData.MagicItemAttribute;

import static com.nisovin.magicspells.MagicSpells.setCheckItemPersistentData;
import static com.nisovin.magicspells.util.magicitems.MagicItems.getMagicItems;

public class MagicItemUpdater {
    // This file is my own person nightmare of not knowing how multi-threading works
    // There is a chance that this may crash if you have too many players,
    // characters, or vaults
    private static int itemUpdateCount = 0;
    private static boolean countItemUpdates = false;

    private static MagicItemUpdater.PersistentDataUpdater persistentDataUpdater = new PersistentDataUpdater();

    private static final Map<String, MagicItem> magicItems = getMagicItems();
    private static final Map<String, MagicItemData> magicItemsCache = new HashMap<>();
    private static final NamespacedKey NAME_KEY = new NamespacedKey(MagicSpells.getInstance(), "magicitem");
    private static final NamespacedKey SOULBOUND_OWNER_KEY = new NamespacedKey(MagicSpells.getInstance(), "soulbound_owner");

    // Active updating of items on login/inventory open
    public static class PersistentDataUpdater implements Listener {

        @EventHandler(ignoreCancelled = true)
        public void onMagicSpellsLoaded(MagicSpellsLoadedEvent event) {
            if (!MagicSpells.enableUpdateItemData())
                return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                joinOrLoadCharacter(player);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void onJoin(PlayerJoinEvent event) {
            joinOrLoadCharacter(event.getPlayer());
        }

        public void joinOrLoadCharacter(Player player) {
            if (!shouldProcessItems())
                return;
            processInventory(player.getInventory(), player);
            PlayerInventory inv = player.getInventory();
            ItemStack[] armor = inv.getArmorContents();
            processItemStacks(armor, player);
            inv.setArmorContents(armor);
            processInventory(player.getEnderChest(), player);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onInvOpen(InventoryOpenEvent event) {
            if (!shouldProcessItems())
                return;
            if (isBagOfHoldingGui(event.getInventory()))
                return;
            Player player = event.getPlayer() instanceof Player p ? p : null;
            processInventory(event.getInventory(), player);
        }
    }

    public static class ChunkMagicItemListener implements Listener {

        @EventHandler(ignoreCancelled = true)
        public void onMagicSpellsLoaded(MagicSpellsLoadedEvent event) {
            if (!shouldProcessItems())
                return;

            final ArrayDeque<Chunk> chunks = new ArrayDeque<>();
            for (World world : Bukkit.getWorlds()) {
                Collections.addAll(chunks, world.getLoadedChunks());
            }

            if (chunks.isEmpty())
                return;

            final int chunksPerTick = 5;
            new BukkitRunnable() {
                @Override
                public void run() {
                    int processed = 0;
                    while (processed++ < chunksPerTick && !chunks.isEmpty()) {
                        Chunk chunk = chunks.poll();
                        if (chunk == null || !chunk.isLoaded())
                            continue;

                        for (Entity entity : chunk.getEntities()) {
                            processGroundEntityIfNeeded(entity);
                        }
                    }

                    if (chunks.isEmpty())
                        cancel();
                }
            }.runTaskTimer(MagicSpells.getInstance(), 1L, 1L);
        }

        @EventHandler(ignoreCancelled = true)
        public void onChunkLoad(ChunkLoadEvent event) {
            if (!shouldProcessItems())
                return;

            for (Entity entity : event.getChunk().getEntities()) {
                processGroundEntityIfNeeded(entity);
            }
        }

        /**
         * Tags then updates magic items that appear in the world (chest spills, explosions, etc.).
         * Skips player inventory drops: those already ran through inventory paths,
         * and Item.getThrower() is set for Q/death drops from a player.
         */
        @EventHandler(ignoreCancelled = true)
        public void onItemSpawn(ItemSpawnEvent event) {
            if (!shouldProcessItems())
                return;

            Item item = event.getEntity();
            if (item.getThrower() != null)
                return;

            processItemEntityIfNeeded(item);
        }

        private static void processGroundEntityIfNeeded(Entity entity) {
            if (entity instanceof Item item) {
                processItemEntityIfNeeded(item);
            } else if (entity instanceof ItemDisplay itemDisplay) {
                ItemStack stack = itemDisplay.getItemStack();
                ItemStack updated = processItemStack(stack, null);
                if (updated != stack)
                    itemDisplay.setItemStack(updated);
            }
        }

        private static void processItemEntityIfNeeded(Item item) {
            ItemStack stack = item.getItemStack();
            ItemStack updated = processItemStack(stack, null);
            if (updated != stack)
                item.setItemStack(updated);
        }
    }

    public static class CharacterPersistentDataUpdater implements Listener {

        @EventHandler(priority = EventPriority.LOWEST)
        private void onCharacterLoad(LoadCharacterEvent event) {
            if (!event.isCancelled()) {
                MagicSpells.scheduleDelayedTask(() -> {
                    persistentDataUpdater.joinOrLoadCharacter(event.getPlayer());
                }, 1);
            }
        }
    }

    private static boolean shouldProcessItems() {
        return MagicSpells.enableUpdateItemData() || MagicItemTagging.hasMappings();
    }

    private static boolean isBagOfHoldingGui(Inventory inventory) {
        if (inventory == null)
            return false;
        var holder = inventory.getHolder();
        if (holder == null)
            return false;
        return holder.getClass().getName().startsWith("com.sneakybagofholding.gui.BagInventoryHolder");
    }

    /** Tag (if mappings exist) then update a player's inventories. Used by ItemTagSpell cast. */
    public static void processPlayerInventories(Player player) {
        if (player == null)
            return;
        processInventory(player.getInventory(), player);
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        processItemStacks(armor, player);
        inv.setArmorContents(armor);
        processInventory(player.getEnderChest(), player);
    }

    public static void processInventory(Inventory inv, @Nullable Player player) {
        if (inv == null || isBagOfHoldingGui(inv))
            return;
        ItemStack[] contents = inv.getContents();
        processItemStacks(contents, player);
        inv.setContents(contents);
    }

    public static void processItemStacks(ItemStack[] items, @Nullable Player player) {
        if (items == null)
            return;
        for (int i = 0; i < items.length; i++) {
            ItemStack itemStack = items[i];
            if (itemStack == null)
                continue;
            items[i] = processItemStack(itemStack, player);
        }
    }

    /**
     * Applies ItemTagSpell mappings first (PDC name), then rebuilds from the magic-item definition
     * when updates are enabled or a tag was just applied.
     */
    public static ItemStack processItemStack(ItemStack itemStack, @Nullable Player player) {
        if (itemStack == null)
            return null;

        MagicItemTagging.TagResult tagResult = MagicItemTagging.applyTagIfNeeded(itemStack);
        ItemStack stack = tagResult.stack();
        boolean tagged = tagResult.tagged();

        if (tagged || MagicSpells.enableUpdateItemData()) {
            ItemStack updated = updateMagicItemItemStackIfNeeded(stack);
            if (updated != stack)
                stack = updated;
        }

        if (tagged) {
            String internalName = tagResult.internalName();
            MagicItem updateSource = MagicItems.getMagicItemByInternalName(internalName);
            if (updateSource == null)
                updateSource = tagResult.magicItem();

            ItemMeta meta = stack.getItemMeta();
            if (meta != null && internalName != null) {
                meta.getPersistentDataContainer().set(NAME_KEY, PersistentDataType.STRING, internalName);
                stack.setItemMeta(meta);
            }

            if (updateSource != null) {
                MagicItemBehaviors.applyFromData(stack, updateSource.getMagicItemData(), player);
                if (player != null)
                    MagicItemExpirationScheduler.scheduleFromItem(player, stack);
            }
        } else if (player != null && MagicSpells.enableUpdateItemData()) {
            ExpirationResult result = MagicItemBehaviors.updateExpiresLineIfNeeded(stack, player);
            if (result == ExpirationResult.EXPIRED)
                return null;
        }

        return stack;
    }

    /**
     * If the stack is a magic item whose stored data differs from the current definition, returns an updated
     * stack from {@link #updateItem}; otherwise returns the same reference.
     */
    private static ItemStack updateMagicItemItemStackIfNeeded(ItemStack itemStack) {
        if (itemStack == null)
            return null;

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null)
            return itemStack;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        if (container.has(new NamespacedKey("sneakybagofholding", "gui_display"), PersistentDataType.BYTE)) {
            return itemStack;
        }

        if (container.has(new NamespacedKey(MagicSpells.getInstance(), "transmogrifyoption"),
                PersistentDataType.STRING)) {
            return itemStack;
        }

        String magicitemName = null;

        if (container.has(NAME_KEY, PersistentDataType.STRING)) {
            magicitemName = container.get(NAME_KEY, PersistentDataType.STRING);
        }

        if (magicitemName == null || !magicItems.containsKey(magicitemName))
            return itemStack;

        // Ignore transient soulbound ownership when determining whether a magic item needs updating.
        ItemStack compareStack = itemStack;
        if (container.has(SOULBOUND_OWNER_KEY, PersistentDataType.STRING)) {
            compareStack = itemStack.clone();
            ItemMeta compareMeta = compareStack.getItemMeta();
            if (compareMeta != null) {
                compareMeta.getPersistentDataContainer().remove(SOULBOUND_OWNER_KEY);
                compareStack.setItemMeta(compareMeta);
            }
        }

        MagicItemData stackData = MagicItems.getMagicItemDataFromItemStack(compareStack);
        MagicItemData magicItemData = MagicItems.getMagicItemDataByInternalName(magicitemName);

        if (magicItemData == null || stackData == null)
            return itemStack;
        if (MagicItems.matches(magicItemData, compareStack, false))
            return itemStack;

        return updateItem(itemStack, magicItems.get(magicitemName));
    }

    public static ItemStack updateItem(ItemStack itemStack, MagicItem magicItem) {
        Material material = itemStack.getType();

        if (material.equals(Material.WRITTEN_BOOK)) {
            return itemStack;
        }

        Integer durability = null;
        Long expiresAt = null;
        String creatorName = null;
        String soulboundOwner = null;
        int amount = itemStack.getAmount();
        BookMeta bookMeta = null;
        Inventory blockInventory = null;

        ItemMeta sourceMeta = itemStack.getItemMeta();

        PersistentDataContainer sourceContainer = sourceMeta.getPersistentDataContainer();

        if (sourceContainer.has(new NamespacedKey(MagicSpells.getInstance(), "menuoption"),
                PersistentDataType.STRING)) {
            return itemStack;
        }

        if (sourceContainer.has(new NamespacedKey(MagicSpells.getInstance(), "transmogrifyoption"),
                PersistentDataType.STRING)) {
            return itemStack;
        }

        if (sourceContainer.has(new NamespacedKey(MagicSpells.getInstance(), "expires_at"), PersistentDataType.LONG)) {
            expiresAt = sourceContainer.get(new NamespacedKey(MagicSpells.getInstance(), "expires_at"),
                    PersistentDataType.LONG);
        }

        if (sourceContainer.has(new NamespacedKey(MagicSpells.getInstance(), "creator_name"),
                PersistentDataType.STRING)) {
            creatorName = sourceContainer.get(new NamespacedKey(MagicSpells.getInstance(), "creator_name"),
                    PersistentDataType.STRING);
        }

        // Magic item soulbound ownership should not be lost during magic item updates.
        if (sourceContainer.has(SOULBOUND_OWNER_KEY, PersistentDataType.STRING)) {
            soulboundOwner = sourceContainer.get(SOULBOUND_OWNER_KEY, PersistentDataType.STRING);
        }

        if (sourceMeta instanceof Damageable damageable) {
            durability = damageable.getDamage();
        }

        if (sourceMeta instanceof BookMeta sourceBookMeta) {
            bookMeta = sourceBookMeta.clone();
        }

        if (sourceMeta instanceof BlockStateMeta blockStateMeta
                && blockStateMeta.getBlockState() instanceof Container container) {
            blockInventory = container.getInventory();
        }

        ItemStack updatedItem = magicItem.getItemStack().clone();
        updatedItem.setAmount(amount);

        ItemMeta meta = updatedItem.getItemMeta();
        MagicItemData definition = magicItem.getMagicItemData();
        EnumSet<MagicItemAttribute> effectiveIgnored = MagicItemIgnoredAttributes.getEffectiveIgnored(definition, itemStack);
        boolean durabilityIgnored = effectiveIgnored.contains(MagicItemAttribute.DURABILITY);

        if (durability != null && durability != 0 && meta instanceof Damageable updatedDamageable && !durabilityIgnored) {
            updatedDamageable.setDamage(durability);
        }
        if (expiresAt != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(MagicSpells.getInstance(), "expires_at"),
                    PersistentDataType.LONG, expiresAt);
        }

        if (creatorName != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(MagicSpells.getInstance(), "creator_name"),
                    PersistentDataType.STRING, creatorName);
        }

        if (soulboundOwner != null) {
            meta.getPersistentDataContainer().set(SOULBOUND_OWNER_KEY, PersistentDataType.STRING, soulboundOwner);
        }

        if (meta instanceof BookMeta updatedBookMeta && bookMeta != null) {
            updatedBookMeta.setTitle(bookMeta.getTitle());
            updatedBookMeta.setAuthor(bookMeta.getAuthor());
            updatedBookMeta.setPages(bookMeta.getPages());
            updatedBookMeta.setGeneration(bookMeta.getGeneration());
        }

        if (meta instanceof BlockStateMeta updatedBlockStateMeta && blockInventory != null) {
            ItemStack[] contents = blockInventory.getContents();
            processItemStacks(contents, null);
            Container updatedContainer = (Container) updatedBlockStateMeta.getBlockState();
            updatedContainer.getInventory().setContents(contents);
            updatedBlockStateMeta.setBlockState(updatedContainer);
        }

        String regularPrefix = "magicspelldata_";
        String namespace = MagicSpells.getInstance().getName().toLowerCase();
        for (NamespacedKey key : sourceContainer.getKeys()) {
            if (key.getNamespace().equals(namespace) && key.getKey().startsWith(regularPrefix)) {
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING,
                        sourceContainer.get(key, PersistentDataType.STRING));
            }
        }

        MagicItemIgnoredAttributes.copyPdc(sourceMeta, meta);

        MagicItemData stackData = MagicItems.getMagicItemDataFromItemStack(itemStack);
        EnumSet<MagicItemAttribute> ignored = effectiveIgnored;
        EnumSet<MagicItemAttribute> blacklisted = definition.getBlacklistedAttributes();

        for (MagicItemAttribute attr : ignored) {
            if (blacklisted.contains(attr)) continue;
            if (attr == MagicItemAttribute.AMOUNT || attr == MagicItemAttribute.TYPE
                    || attr == MagicItemAttribute.MAGIC_ITEM_NAME || attr == MagicItemAttribute.PERSISTENT_DATA) {
                continue;
            }
            boolean has = stackData != null && stackData.hasAttribute(attr);
            MagicItems.applyMagicItemAttribute(updatedItem, meta, stackData, attr, !has);
        }

        for (MagicItemAttribute attr : blacklisted) {
            if (attr == MagicItemAttribute.AMOUNT || attr == MagicItemAttribute.TYPE
                    || attr == MagicItemAttribute.MAGIC_ITEM_NAME || attr == MagicItemAttribute.PERSISTENT_DATA) {
                continue;
            }
            MagicItems.applyMagicItemAttribute(updatedItem, meta, null, attr, true);
        }

        updatedItem.setItemMeta(meta);

        if (countItemUpdates)
            itemUpdateCount += amount;

        return updatedItem;
    }

    // Add PDC to all items for worlds used prior to PDC
    public static void addMagicItemPDC(boolean updatePlayers, boolean updateWorlds,
            boolean updateVaults, boolean updateCharacters) {

        MagicSpells.log("Adding PDC to Magic Items.");
        cacheItemData();
        setCheckItemPersistentData(false);
        countItemUpdates = true;

        // Update Player Inventories
        if (updatePlayers)
            updatePlayers();

        // Update Characters
        if (updateCharacters && CompatBasics.pluginEnabled("SneakyCharacterManager"))
            updateCharacters();

        // Update Vaults
        if (updateVaults && CompatBasics.pluginEnabled("SneakyVaults"))
            updateVaults();

        // Update Chunks
        if (updateWorlds)
            scanWorlds();
    }

    private static void cacheItemData() {
        // Cache data for performance purposes when adding PDC
        for (Map.Entry<String, MagicItem> entry : magicItems.entrySet()) {
            String key = entry.getKey();
            MagicItem value = entry.getValue();
            magicItemsCache.put(key, value.getMagicItemData());
        }
    }

    private static void addItemNames(ItemStack[] items) {
        if (items == null)
            return;

        for (int i = 0; i < items.length; i++) {
            ItemStack itemStack = items[i];
            if (itemStack == null)
                continue;

            MagicItemData item = MagicItems.getMagicItemDataFromItemStack(itemStack);
            if (item == null)
                continue;

            for (Map.Entry<String, MagicItemData> entry : magicItemsCache.entrySet()) {
                if (MagicItems.matches(entry.getValue(), itemStack, false)) {

                    items[i] = updateItem(itemStack, magicItems.get(entry.getKey()));
                    break;
                }
            }
        }
    }

    private static void updatePlayers() {
        CMI cmi = CMI.getInstance();
        List<CMIUser> users = new ArrayList<>(cmi.getPlayerManager().getAllUsers().values());
        int batchSize = 5;
        Iterator<CMIUser> userIterator = users.iterator();

        MagicSpells.log("Starting player update task...");

        Bukkit.getScheduler().runTaskTimer(MagicSpells.getInstance(), new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < batchSize && userIterator.hasNext(); i++) {
                    CMIUser user = userIterator.next();
                    Player player = user.getPlayer();
                    MagicSpells.log("Updating user " + user.getName() + ".");

                    if (player == null) {
                        MagicSpells.error("User " + user.getName() + " is null!");
                        continue;
                    }

                    Inventory inventory = player.getInventory();
                    ItemStack[] items = inventory.getContents();
                    Inventory enderChest = player.getEnderChest();
                    ItemStack[] enderChestContents = enderChest.getContents();

                    addItemNames(items);
                    addItemNames(enderChestContents);

                    inventory.setContents(items);
                    enderChest.setContents(enderChestContents);

                    player.updateInventory();
                    cmi.save(player);
                }

                if (!userIterator.hasNext()) {
                    MagicSpells.log("Finished updating all players.");
                    Bukkit.getScheduler().cancelTasks(MagicSpells.getInstance());
                }
            }
        }, 0L, 1L);
    }

    private static void updateCharacters() {
        // Adapted from SneakyCharacterManager by MomoPewPew & ItzBungo
        if (!SneakyCharacterManager.getInstance().getConfig().getBoolean("manageInventories", true)) {
            MagicSpells.error(
                    "manageInventories is currently set to false in the config, so you probably don't want this command.");
            return;
        }
        File dir = SneakyCharacterManager.getCharacterDataFolder();

        File[] playerData = dir.listFiles();
        if (playerData == null || playerData.length == 0)
            return;

        for (File playerDir : playerData) {
            if (!playerDir.exists() || !playerDir.isDirectory())
                continue;
            File[] characterData = playerDir.listFiles();
            if (characterData == null)
                continue;

            for (File characterFile : characterData) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(characterFile);
                String inventoryB64 = config.getString("inventory");
                if (inventoryB64 == null)
                    continue;

                ItemStack[] items = net.sneakycharactermanager.paper.util.InventoryUtility
                        .getSavedInventory(inventoryB64);
                if (items == null || items.length == 0)
                    continue;

                addItemNames(items);

                inventoryB64 = net.sneakycharactermanager.paper.util.InventoryUtility.inventoryToBase64(41, items);
                config.set("inventory", inventoryB64);

                try {
                    AtomicFiles.writeUtf8(characterFile.toPath(), config.saveToString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        MagicSpells.log("Updated items in SneakyCharacterManager.");
    }

    private static void updateVaults() {
        // Adapted from SneakyVaults written by ItzBungo
        File dir = SneakyVaults.getInstance().playerDataFolder;
        if (!dir.exists() || !dir.isDirectory()) {
            MagicSpells.error("SneakyVaults folder not found.");
            return;
        }
        File[] playerDataFiles = dir.listFiles();

        if (playerDataFiles == null || playerDataFiles.length == 0) {
            MagicSpells.error("No SneakyVaults data files found.");
            return;
        }

        for (File playerDataFile : playerDataFiles) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(playerDataFile);
            ConfigurationSection vaults = configuration.getConfigurationSection("player_vaults");
            if (vaults == null)
                continue;

            for (String vault : vaults.getKeys(false)) {
                if (!vaults.getBoolean(vault + ".paperConverted")) {
                    MagicSpells.error("Vault " + vault + " not found.");
                    continue;
                }

                String vaultInventory = vaults.getString(vault);
                if (vaultInventory == null)
                    continue;
                List<String> vaultItems = vaults.getStringList(vault + ".items");

                Inventory inventory = Bukkit.createInventory(null, 9);

                ItemStack[] items = net.sneakymouse.sneakyvaults.utlitiy.InventoryUtility
                        .inventoryPaperFromBase64(vaultItems).toArray(new ItemStack[0]);
                if (items.length > inventory.getSize()) {
                    if (items.length % 9 != 0) {
                        MagicSpells.error(
                                "Error: Saved inventory size is not a multiple of 9. This should be impossible!");
                        continue;
                    }
                    inventory = Bukkit.createInventory(null, items.length,
                            ChatUtility.convertToComponent("&ePlayer Vault"));
                }

                addItemNames(items);
                inventory.setContents(items);

                List<String> itemEncoded = net.sneakymouse.sneakyvaults.utlitiy.InventoryUtility
                        .inventoryPaperToBase64(inventory);
                vaults.set(vault + ".items", itemEncoded);

                try {
                    AtomicFiles.writeUtf8(playerDataFile.toPath(), configuration.saveToString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        MagicSpells.log("Updated items in SneakyVaults.");
    }

    private static void scanWorlds() {
        Bukkit.getScheduler().runTaskAsynchronously(MagicSpells.getInstance(), () -> {
            CountDownLatch latch = new CountDownLatch(Bukkit.getWorlds().size());

            for (World world : Bukkit.getWorlds()) {
                Bukkit.getScheduler().runTaskAsynchronously(MagicSpells.getInstance(), () -> {
                    MagicSpells.error("Scanning world: " + world.getName());

                    File worldFolder = world.getWorldFolder();
                    File regionFolder;
                    switch (world.getEnvironment()) {
                        case NORMAL:
                            regionFolder = new File(worldFolder, "region");
                            break;
                        case NETHER:
                            regionFolder = new File(worldFolder, "DIM-1/region");
                            break;
                        case THE_END:
                            regionFolder = new File(worldFolder, "DIM1/region");
                            break;
                        default:
                            MagicSpells.error("Unknown world environment for world: " + world.getName());
                            latch.countDown();
                            return;
                    }

                    if (!regionFolder.exists() || !regionFolder.isDirectory()) {
                        MagicSpells.error("Region folder not found for world: " + world.getName());
                        latch.countDown();
                        return;
                    }

                    File[] regionFiles = regionFolder.listFiles((dir, name) -> name.endsWith(".mca"));

                    if (regionFiles == null || regionFiles.length == 0) {
                        MagicSpells.error("No region files found for world: " + world.getName());
                        latch.countDown();
                        return;
                    }

                    for (File regionFile : regionFiles) {
                        String fileName = regionFile.getName();
                        String[] parts = fileName.split("\\.");
                        if (parts.length != 4) {
                            MagicSpells.error("Invalid region file name: " + fileName);
                            continue;
                        }

                        try {
                            int regionX = Integer.parseInt(parts[1]);
                            int regionZ = Integer.parseInt(parts[2]);

                            MagicSpells.error("Scanning region: r." + regionX + "." + regionZ + ".mca in world "
                                    + world.getName());

                            for (int chunkX = regionX * 32; chunkX < (regionX + 1) * 32; chunkX++) {
                                for (int chunkZ = regionZ * 32; chunkZ < (regionZ + 1) * 32; chunkZ++) {
                                    if (world.isChunkGenerated(chunkX, chunkZ)) {
                                        final int finalChunkX = chunkX;
                                        final int finalChunkZ = chunkZ;

                                        world.getChunkAtAsync(finalChunkX, finalChunkZ).thenAccept(chunk -> {
                                            Bukkit.getScheduler().runTask(MagicSpells.getInstance(), () -> {
                                                if (chunk.isLoaded() && chunk.getTileEntities() != null) {
                                                    processChunk(chunk);
                                                } else {
                                                    MagicSpells.error("Chunk not fully loaded: " + finalChunkX + ", "
                                                            + finalChunkZ);
                                                }
                                            });
                                        }).exceptionally(ex -> {
                                            MagicSpells
                                                    .error("Error loading chunk: " + finalChunkX + ", " + finalChunkZ);
                                            return null;
                                        });
                                    }
                                }
                            }
                        } catch (NumberFormatException e) {
                            MagicSpells.error("Error parsing region coordinates from file name: " + fileName);
                        }
                    }
                    latch.countDown();
                });
            }

            Bukkit.getScheduler().runTaskAsynchronously(MagicSpells.getInstance(), () -> {
                try {
                    latch.await();
                    Bukkit.getScheduler().runTaskLater(MagicSpells.getInstance(),
                            () -> MagicSpells.log("All chunk data has been updated."),
                            1L);
                    Bukkit.getScheduler().runTaskLater(MagicSpells.getInstance(),
                            () -> MagicSpells.log("All tasks complete. " + itemUpdateCount + " items have been updated."
                                    + " Please restart to proceed."),
                            1L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    MagicSpells.error("Task interrupted while waiting for world scans to complete.");
                }
            });
        });
    }

    private static void processChunk(Chunk chunk) {
        synchronized (chunk) {
            for (BlockState blockState : chunk.getTileEntities()) {
                if (blockState instanceof Container container) {
                    Inventory inventory = container.getInventory();
                    ItemStack[] items = inventory.getContents();
                    addItemNames(items);

                    inventory.setContents(items);
                }
            }
        }
    }
}
