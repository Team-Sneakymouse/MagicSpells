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
| **`GlowSpell` ChatColor** | `@SuppressWarnings("deprecation")` | XGlow abandoned; `GlowBuilder#color(ChatColor)` only API |
| **Citizens `IsNPCCondition`** | `@SuppressWarnings` on `hasMetadata("NPC")` | External Citizens convention |
| **Vendored bStats `Metrics`** | Class-level `@SuppressWarnings` | Not worth rewriting |

**Current `[removal]` count on `:core:compileJava`:** **0**.

## Manual / live verification

See **[MANUAL-TEST-WARNING-MIGRATION.md](MANUAL-TEST-WARNING-MIGRATION.md)** for the human checklist.  
Targeted cast smoke: **[MANUAL-TEST-TARGETED-CAST.md](MANUAL-TEST-TARGETED-CAST.md)** (signed off).  
ChatColor smoke: **[MANUAL-TEST-CHATCOLOR.md](MANUAL-TEST-CHATCOLOR.md)** (signed off).  
Inversion / misc: **[MANUAL-TEST-INVERSION.md](MANUAL-TEST-INVERSION.md)** (signed off).  
PDC / metadata: **[MANUAL-TEST-PDC.md](MANUAL-TEST-PDC.md)** (signed off).  
Tracked fixtures under `dev-fixtures/` (copy into `core/run/plugins/MagicSpells/` for `runServer`).

`GlowSpell` is **not** live-testable here (XGlow abandoned / not on the test server).

---

## Remaining `-Xlint` clusters (next passes)

**Current total: 50** (after Metrics/GlowSpell/IsNPC suppress + bed/pigzap/Conjure fixes). Refresh with the command above.

| Cluster | Count | Notes |
|---------|-------|-------|
| **`DamageModifier`** | 6 | `EntityDamageEvent` legacy modifiers |
| **`sendMultiBlockChange`** | 5 | Player multi-block packet overload |
| **`PotionEffectType` name/values/getByName** | 5 | Prefer registry keys (Sound-style) |
| **Unchecked casts / raw types** | 5 | Outside Metrics |
| **InventoryView title** | 3 | Adventure title APIs |
| **Swim toggle `setCancelled`** | 2 | `EntityToggleSwimEvent` |
| **`AttributeModifier.getSlot`** | 2 | Prefer `getSlotGroup` |
| **Falling block spawn** | 2 | |
| **SuspiciousStew `addCustomEffect`** | 2 | |
| **Tree gen / tipped arrow / Sign / ItemMeta / WE / recipes / …** | rest | Odds and ends |

No new fixtures for this pass. Re-check PDC lightning on Easy+ (now `EntityZapEvent`). Bed/recall: `getRespawnLocation` — use any existing recall-with-bed config if you want a spot check.

---

## Unit tests added with this work

- `PotionIdentityTest`
- `AttributeSpecParserTest`
- `SoundKeyNamesTest`
- `ColorUtilTest`
- `EntityPdcTest` (lightning charge bit packing; live cover in MANUAL-TEST-PDC)
- Existing `SpellSnippetValidatorTest` unchanged

Regenerate spell option index for snippet tests: `python3 scripts/wiki/export-test-spell-index.py`
