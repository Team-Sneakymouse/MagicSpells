# Compile warnings backlog

Snapshot from `:core:compileJava` on the DvZ fork (Paper / Bukkit APIs).

**Note:** `javac` reports at most **100** warnings per compile. Clearing a large cluster can make previously hidden warnings appear until the true total drops below 100.

Lint is **on** in `core/build.gradle` (`-Xlint:deprecation`, `-Xlint:unchecked`).

How to refresh:

```bash
./gradlew :core:compileJava --rerun-tasks 2>&1 | grep 'warning:' | sed 's/.*warning: //' | sort | uniq -c | sort -rn
```

---

## Done (`[removal]` cleared or intentionally suppressed)

| API | Change | Notes |
|-----|--------|-------|
| WorldEdit `BlockVector` getters | `.x()` / `.y()` / `.z()` | Mechanical |
| `org.bukkit.util.Consumer` | `java.util.function.Consumer` | Mechanical |
| **Bukkit Conversations** | `@SuppressWarnings("removal")` | Keep until Dialog/chat rewrite; unused on LoM |
| **`PotionData`** | `PotionIdentity` + `PotionType` / `setBasePotionType` | Unit-tested grammar; legacy `extended`/`upgraded` still accepted |
| **`Effect.SMOKE` / `STEP_SOUND`** | `SMOKE_SHOOT` / `DESTROY_BLOCK` | Spot-check via `spells-test.yml` smoke fixtures |
| **`AttributeModifier` legacy ctors / `Attribute.values()`** | `NamespacedKey` + `EquipmentSlotGroup`; `Registry.ATTRIBUTE`; `AttributeSpecParser` | Unit-tested parse/UUID |
| **`OldEnum.name()` (Sound / Pattern)** | Registry keys | Serialize with `Registry.*.getKey` |
| **`PatternType` values/valueOf/getKey** | `RegistryKey.BANNER_PATTERN` | |
| **`MaterialData` / `ItemStack.getData` / Enderman carried** | `BlockData` / `setCarriedBlock` / `ItemStack(Material)` | |
| **`Entity.setLastDamageCause`** | Removed (internal API) | Damage events still fired |
| **`AbstractArrow.setKnockbackStrength`** | `setWeapon` + Punch enchant | |
| **`Sound.valueOf`** | `SoundUtil` / `SoundKeyNames` | Unit-tested key shaping |
| **`JukeboxPlayable` show-in-tooltip** | Stopped calling no-op API | Config flag kept for serialize only |
| **`EntityDamageByEntityEvent` legacy ctor** | `DamageSource` builder | |
| **`EntityEffect.HURT`** | `playHurtAnimation` | |
| **`InventoryView.Property`** | `@SuppressWarnings` on stub | Dummy inventory only |
| **`ChatColor` (general)** | `ColorUtil` section-sign + MiniMessage | Config chars unchanged |
| **`Targeted*Spell` no-args** | Args overloads are primary | Live: `MANUAL-TEST-TARGETED-CAST.md` |
| **Unchecked `getAttribute` casts** | Typed `MagicItemData.getAttribute` | |
| **`World.getPVP`** | `getGameRuleValue(GameRules.PVP)` | |
| **`BookMeta` string pages** | Adventure `pages()` | |
| **`JavaPlugin.getDescription`** | `getPluginMeta()` | |
| **WorldEdit `ClipboardFormats.findByFile`** | `ClipboardFormatsCompat` | |
| **Bukkit Metadata (MS-owned)** | `EntityPdc` / ThreadLocal / `getHitEntity()` | Live: `MANUAL-TEST-PDC.md` |
| **`getBedSpawnLocation` / `setBedSpawnLocation`** | `getRespawnLocation` / `setRespawnLocation` | BedCoord* meta + RecallSpell |
| **`PigZapEvent`** | Paper `EntityZapEvent` + `getBolt()` | LightningSpell |
| **Deprecated `playSpellEffects(pos, entity)`** | Pass `SpellData` (ConjureSpell) | |
| **`sendMultiBlockChange(..., boolean)`** | `sendMultiBlockChange(Map)` only | NovaEffect |
| **`PotionEffectType` name/values/getByName** | `Registry.MOB_EFFECT` + `PotionEffectKeyNames` | Handler + potioneffect passive |
| **`DamageModifier` (listeners)** | Still used for ABSORPTION / BLOCKING checks (`@SuppressWarnings`) — Paper has no replacement; `isBlocking()` is false mid-hit | Compat damage event also needs modifier maps (below) |
| **`GlowSpell` ChatColor** | `@SuppressWarnings("deprecation")` | XGlow abandoned |
| **Citizens `IsNPCCondition`** | `@SuppressWarnings` on `hasMetadata("NPC")` | External Citizens convention |
| **Vendored bStats `Metrics`** | Class-level `@SuppressWarnings` | Not worth rewriting |
| **`MagicSpellsEntityDamageByEntityEvent`** | `@SuppressWarnings("deprecation")` for `DamageModifier` maps | Paper's only non-`forRemoval` ctor still requires them |
| **`AttributeModifier.getSlot`** | `getSlotGroup` | MagicItemData serialize / apply |
| **Swim `EntityToggleSwimEvent#setCancelled`** | `@SuppressWarnings` + start-swim sprint clear | Paper documents cancel as no-op |
| **`InventoryView` title (Util mock)** | `@SuppressWarnings` on stub | Dummy inventory only |
| **FallingBlock `setBlockData` after spawn** | `world.spawn(loc, FallingBlock.class, fb -> …)` | MaterializeSpell / EntityData |
| **`World.generateTree` legacy** | `generateTree(…, Random[, Predicate])` | TreeSpell; TreeWatch removed |
| **Suspicious stew effects** | `SuspiciousEffectEntry` | SuspiciousStewHandler |
| **Sign `getLine` / legacy side** | `Side.FRONT` Adventure lines | SummonSpell / SignTextCondition |
| **Cat / frog type enums** | `RegistryKey.CAT_VARIANT` / `FROG_VARIANT` | EntityData |
| **Unchecked / raw types** | Typed loops + local `@SuppressWarnings` | CommandHelpFilter, EntityData, InventoryUtil, MagicEnumSetPrompt |
| **ItemMeta lore / CMD** | Adventure `lore()`; `CustomModelDataValues` | MagicItemMenuSpell, ItemSprayEffect |
| **Recipes** | `SmithingTransformRecipe` + stop stonecutting `setGroup` | Also fixed smithing `addition` reading `base` by mistake |
| **CraftSpell workbench** | `MenuType.CRAFTING.builder()` | |
| **Scoreboard objective** | `Criteria.DUMMY` | VariableManager |
| **Enchantment.getByKey** | `RegistryKey.ENCHANTMENT` via RegistryAccess | EnchantmentHandler |
| **WorldEdit edit session** | `newEditSessionBuilder()` | PasteSpell |
| **JsonReader.setLenient** | `setStrictness(LENIENT)` | MagicItemDataParser |
| **TippedArrow** | `Arrow.class` | ProjectileManagerTippedArrow |
| **URL(String)** | `URI.create(url).toURL()` | Util.downloadFile |

**Current `[removal]` count on `:core:compileJava`:** **0**.  
**Current `-Xlint` warning count on `:core:compileJava`:** **0**.

## Manual / live verification

See **[MANUAL-TEST-WARNING-MIGRATION.md](MANUAL-TEST-WARNING-MIGRATION.md)** for the human checklist.  
Targeted cast smoke: **[MANUAL-TEST-TARGETED-CAST.md](MANUAL-TEST-TARGETED-CAST.md)** (signed off).  
ChatColor smoke: **[MANUAL-TEST-CHATCOLOR.md](MANUAL-TEST-CHATCOLOR.md)** (signed off).  
Inversion / misc: **[MANUAL-TEST-INVERSION.md](MANUAL-TEST-INVERSION.md)** (signed off).  
PDC / metadata: **[MANUAL-TEST-PDC.md](MANUAL-TEST-PDC.md)** (signed off).  
Xlint clusters: **[MANUAL-TEST-XLINT.md](MANUAL-TEST-XLINT.md)** (`spells-xlint-smoke.yml`).  
Tracked fixtures under `dev-fixtures/` (copy into `core/run/plugins/MagicSpells/` for `runServer`).

`GlowSpell` is **not** live-testable here (XGlow abandoned / not on the test server).

---

## Remaining `-Xlint` clusters (next passes)

**Current total: 0**. Refresh with the command above. Intentional `@SuppressWarnings` remain for Conversations, GlowSpell, Citizens NPC metadata, bStats Metrics, swim cancel no-op, InventoryView stub, and DamageModifier compat ctor/maps.

---

## Unit tests added with this work

- `PotionIdentityTest`
- `AttributeSpecParserTest`
- `SoundKeyNamesTest`
- `ColorUtilTest`
- `EntityPdcTest` (lightning charge bit packing; live cover in MANUAL-TEST-PDC)
- `PotionEffectKeyNamesTest`
- Existing `SpellSnippetValidatorTest` unchanged

Regenerate spell option index for snippet tests: `python3 scripts/wiki/export-test-spell-index.py`
