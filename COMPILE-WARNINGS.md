# Compile warnings backlog

Snapshot from `:core:compileJava` on the DvZ fork (Paper / Bukkit for-removal APIs).

**Note:** `javac` reports at most **100** warnings per compile. Clearing a large cluster can make previously hidden warnings appear until the true total drops below 100.

How to refresh:

```bash
./gradlew :core:compileJava --rerun-tasks 2>&1 | grep 'warning:' | sed 's/.*warning: //' | sort | uniq -c | sort -rn
```

For ordinary (non-removal) deprecations and unchecked casts, enable lint (currently commented in `core/build.gradle`):

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

**Current `[removal]` count on `:core:compileJava`:** **0** (as of this migration pass).

## Manual / live verification

See **[MANUAL-TEST-WARNING-MIGRATION.md](MANUAL-TEST-WARNING-MIGRATION.md)** for the human checklist.  
Tracked fixtures: `dev-fixtures/spells-warning-migration.yml` (copy into `core/run/plugins/MagicSpells/` for `runServer`).

---

## Remaining when `-Xlint:deprecation` / `unchecked` is enabled

These do not appear in the default for-removal-only output:

| Cluster | Notes |
|---------|--------|
| **Deprecated `Targeted*Spell` no-args overloads** | **Suppressed on overrides** (`@SuppressWarnings("deprecation")`). Debt: later invert interfaces so args methods are primary and no-args become defaults/`removed`. Live smoke: `MANUAL-TEST-TARGETED-CAST.md`. |
| **`ChatColor`** | Many call sites. Migrate to Adventure; spot-check legacy codes. **Next real migration.** |
| **Unchecked casts** | `CastItem`, `MagicItemData`, `MagicItems` — generics cleanup or suppress. |
| **Misc** | `World.getPVP`, `BookMeta` string pages, `FixedMetadataValue`, `JavaPlugin.getDescription`, WorldEdit `ClipboardFormats.findByFile`, etc. |

---

## Unit tests added with this work

- `PotionIdentityTest`
- `AttributeSpecParserTest`
- `SoundKeyNamesTest`
- Existing `SpellSnippetValidatorTest` unchanged

Regenerate spell option index for snippet tests: `python3 scripts/wiki/export-test-spell-index.py`
