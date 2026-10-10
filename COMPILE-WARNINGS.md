# Compile warnings backlog

Snapshot from `:core:compileJava` on the DvZ fork (Paper / Bukkit for-removal APIs).

**Note:** `javac` reports at most **100** warnings per compile. Clearing a large cluster can make previously hidden warnings appear until the true total drops below 100.

How to refresh:

```bash
./gradlew :core:compileJava --rerun-tasks 2>&1 | grep 'warning:' | sed 's/.*warning: //' | sort | uniq -c | sort -rn
```

For ordinary (non-removal) deprecations and unchecked casts, enable lint (currently `-Xlint:none` in `core/build.gradle`):

```bash
# temporarily: options.compilerArgs << "-Xlint:deprecation" << "-Xlint:unchecked"
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
| **`ChatColor`** | `ColorUtil` section-sign + MiniMessage; `getTextColor()` is now a `String` (`§` code) | Config chars unchanged; `GlowSpell` still uses ChatColor for XGlow |
| **`Targeted*Spell` no-args** | Args overloads are primary; no-args are deprecated interface defaults (`null` args) | Removed per-spell `@SuppressWarnings` + no-args overrides; live smoke: `MANUAL-TEST-TARGETED-CAST.md` |
| **Unchecked `getAttribute` casts** | Typed `MagicItemData.getAttribute` (single suppress) | Call sites assign without casts |
| **`World.getPVP`** | `getGameRuleValue(GameRules.PVP)` | Not `GameRule.PVP` (for-removal) |
| **`BookMeta` string pages** | Adventure `pages()` / `pages(List<Component>)` | |
| **`JavaPlugin.getDescription`** | `getPluginMeta()` | |
| **WorldEdit `ClipboardFormats.findByFile`** | `ClipboardFormatsCompat` — `findByPath` when present, else `findByFile` | Runtime WE may be older than compile 7.4 |
| **Bukkit Metadata (`FixedMetadataValue`)** | `EntityPdc` flags/primitives + `ObjectStore` for BowSpell; LightningSpell uses ThreadLocal+UUID map (charge events fire inside `strikeLightning`); missarrow uses `getHitEntity()` | Live: `MANUAL-TEST-PDC.md`. Left alone: Citizens `IsNPCCondition` (`hasMetadata("NPC")`) |

**Current `[removal]` count on `:core:compileJava`:** **0** (as of this migration pass).

## Manual / live verification

See **[MANUAL-TEST-WARNING-MIGRATION.md](MANUAL-TEST-WARNING-MIGRATION.md)** for the human checklist.  
Targeted cast smoke: **[MANUAL-TEST-TARGETED-CAST.md](MANUAL-TEST-TARGETED-CAST.md)** (already signed off).  
ChatColor smoke: **[MANUAL-TEST-CHATCOLOR.md](MANUAL-TEST-CHATCOLOR.md)** (already signed off).  
Inversion / misc: **[MANUAL-TEST-INVERSION.md](MANUAL-TEST-INVERSION.md)** (already signed off).  
PDC / metadata: **[MANUAL-TEST-PDC.md](MANUAL-TEST-PDC.md)**.  
Tracked fixtures under `dev-fixtures/` (copy into `core/run/plugins/MagicSpells/` for `runServer`).

---

## Remaining when `-Xlint:deprecation` / `unchecked` is enabled

| Cluster | Notes |
|---------|--------|
| **`GlowSpell` ChatColor** | XGlow API still takes Bukkit `ChatColor`. |
| **Citizens `IsNPCCondition`** | Still reads external `hasMetadata("NPC")` — not MagicSpells-owned. |
| **Misc leftovers** | Other `-Xlint:deprecation` / `unchecked` noise outside the clusters above (spot-check when enabling lint). |

---

## Unit tests added with this work

- `PotionIdentityTest`
- `AttributeSpecParserTest`
- `SoundKeyNamesTest`
- `ColorUtilTest`
- `EntityPdcTest` (lightning charge bit packing; live cover in MANUAL-TEST-PDC)
- Existing `SpellSnippetValidatorTest` unchanged

Regenerate spell option index for snippet tests: `python3 scripts/wiki/export-test-spell-index.py`
