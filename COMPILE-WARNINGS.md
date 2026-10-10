# Compile warnings backlog

Snapshot from `:core:compileJava` on the DvZ fork (Paper / Bukkit for-removal APIs, plus a few WorldEdit leftovers that were already cleared).

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

## Done (mechanical, no behavior change expected)

| API | Change | Files |
|-----|--------|-------|
| WorldEdit `BlockVector3` / `BlockVector2` `.getX/Y/Z()` | → `.x()` / `.y()` / `.z()` | `PasteSpell`, `FillSpell`, `LocationMatchesSchematicCondition` |
| `org.bukkit.util.Consumer` | → `java.util.function.Consumer` | `EntityData` |

---

## Remaining `[removal]` warnings (need real migration / testing)

| Cluster | Approx. share | Main files | Why not a blind rename | Suggested direction |
|---------|---------------|------------|------------------------|---------------------|
| **Bukkit Conversations** (`Conversation`, `ConversationFactory`, `Prompt`, `Conversable`, …) | ~46 of the current 100 | `ConversationSpell`, `ExternalCommandSpell`, `util/prompt/*`, `ConfigReaderUtil` | Entire API is for-removal | Rewrite prompts (Adventure / custom UI) or `@SuppressWarnings("removal")` until replaced |
| **`PotionData` / `PotionMeta.setBasePotionData` / `getBasePotionData`** | ~15 | `MagicItems`, `MagicItemData`, `CastItem`, `PotionHandler` | Potion identity & magic-item matching | `PotionType` / `setBasePotionType`; fixture-test item round-trips |
| **`Effect.SMOKE` / `Effect.STEP_SOUND`** | ~11 | `CloudEffect`, `SmokeSwirl*`, `BuildSpell`, `MaterializeSpell`, `ReachSpell`, `ZapSpell` | Visuals can differ | Map to `Particle` (or current Effect constants); spot-check in-game |
| **`Attribute` / `AttributeModifier` constructors / `Attribute.values()`** | ~5 | `AttributeManager`, `AttributeUtil`, `ConfigDataUtil`, `MagicItems`, `VariableManager` | Slot / key / builder semantics | Attribute modifier builder + registry |
| **Legacy `MaterialData` / `ItemStack.getData` / Enderman carried** | ~5 | `ReachSpell`, `SpawnEntitySpell`, `ZapSpell` | BlockData rewrite | `BlockData` / modern entity APIs |
| **`OldEnum.name()`** | ~4 | `MagicItemData`, `AttributeUtil`, `VariableManager` | Serialized string form may change | Prefer `key().asString()` / registry names; check configs |
| **`PatternType` values / valueOf / getKey** | ~4 | `AlteredBlockManager` | Banner pattern registry | Registry lookup by namespaced key |
| **`Entity.setLastDamageCause`** | 3 | `PainSpell`, `DotSpell`, `DrainlifeSpell` | Affects damage attribution / other plugins | Confirm Paper replacement or drop if redundant with modern damage API |
| **`JukeboxPlayableComponent` show-in-tooltip** | ~3 | `DataComponentsHandler` | Component API churn | Follow current Paper item component docs |
| **`AbstractArrow.setKnockbackStrength`** | 2 | `VolleySpell` | Knockback semantics | Use current arrow knockback / weapon API |
| **`Sound.valueOf`** | 1 | `DataComponentsHandler` | Enum → registry | `Registry.SOUNDS` / namespaced key |
| **`EntityDamageByEntityEvent` legacy constructor** | 1 | `MagicSpellsEntityDamageByEntityEvent` | Custom damage event wiring | Modern damage event / `DamageSource` construction |

---

## Extra warnings when full `-Xlint:deprecation` / `unchecked` is on

These do not always appear in the default for-removal-only output:

| Cluster | Notes |
|---------|--------|
| **`ChatColor`** | Many call sites (`MagicSpells`, `MagicItemBehaviors`). Migrate to Adventure; spot-check `§` / legacy codes. |
| **Deprecated `Targeted*Spell` overloads** (no-`args`) | Implementations must override until interfaces drop them. Prefer `@SuppressWarnings("deprecation")` on those methods rather than “fixing” the override away. |
| **Unchecked casts** | `CastItem`, `MagicItemData`, `MagicItems` — generics cleanup or suppress. |
| **Misc** | `World.getPVP`, `BookMeta` string pages, `FixedMetadataValue`, `JavaPlugin.getDescription`, WorldEdit `ClipboardFormats.findByFile`, etc. — each needs a small, tested migration. |

---

## Priority suggestion

1. Suppress or isolate **Conversations** if that feature is rare on LoM (stops drowning the warning list).
2. **PotionData** next (touches magic items / matching).
3. **Effect → Particle** for smoke effects (localized, easy to verify visually).
4. Leave Attribute / Pattern / MaterialData for dedicated item-meta passes.
