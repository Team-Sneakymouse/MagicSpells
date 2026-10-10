# Manual tests — warning migration (Paper API)

Human / `runServer` checklist for the `[removal]` migrations. Pure-logic pieces are already covered by JUnit (`PotionIdentityTest`, `AttributeSpecParserTest`, `SoundKeyNamesTest`); this doc is only what needs a live world.

## How to run

```bash
./gradlew runServer
```

**Tracked fixture source** (in git):

- [`dev-fixtures/spells-warning-migration.yml`](dev-fixtures/spells-warning-migration.yml)

**Live copy** for `runServer` (gitignored under `**/run/`):

- `core/run/plugins/MagicSpells/spells-warning-migration.yml`

If the live copy is missing after a fresh clone:

```bash
cp dev-fixtures/spells-warning-migration.yml core/run/plugins/MagicSpells/
```

Cast with `/ms cast <spell>` (or bind / list as usual). Prefer creative + a flat test world. Use a second account or a mob for targeted damage/knockback checks.

After a plugin rebuild, restart or `/ms reload` so the fixture file is picked up. When editing fixtures, update **both** `dev-fixtures/` (tracked) and the live `run/` copy.

---

## Checklist

### A. Smoke / destroy effects (Effect → SMOKE_SHOOT / DESTROY_BLOCK)

| Spell | What to look for |
|-------|------------------|
| `wm_smoke_cloud` | Directional smoke puffs at caster (not a particle redesign) |
| `wm_smoke_swirl` | Smoke orbits around caster |
| `wm_smoke_trail` | **Needs rebuilt plugin** (DummySpell now plays trail on block targets). Look at a distant solid block, cast; line of smoke puffs caster→block. Reload YAML alone is not enough for this fix. |
| `wm_zap` | **Primary DESTROY_BLOCK check.** Targeted block vanishes; break particles/sound look normal |
| `wm_build` | Hold a placeable block, cast at air/surface; place FX OK; block placed (also uses DESTROY_BLOCK) |
| `wm_reach` | Toggle buff; place/break at distance; place/break FX OK (also uses DESTROY_BLOCK) |

### B. Potions (PotionData → PotionType)

| Spell | What to look for |
|-------|------------------|
| `wm_give_potion_legacy` | Splash/potion item with **long swiftness** identity (legacy `swiftness extended` config) |
| `wm_give_potion_modern` | Same visual/identity from `long_swiftness` config |
| Compare both | `/ms magicitem` / inventory tooltip: both should behave as long swiftness; matching configs that specify either form should agree |

Unit tests already cover parse/serialize equivalence; this is meta round-trip on a real stack.

### C. Attributes (NamespacedKey modifiers)

| Spell | What to look for |
|-------|------------------|
| `wm_give_attr_sword` | Sword has attack-damage modifier; holding it changes attack |
| `wm_give_attr_boots` | Boots with speed scalar; walking feels faster when worn |
| Relog / rematch | Modifier still present after drop/pickup if magic-item matching is used |

### D. Sounds on equippable items

| Spell | What to look for |
|-------|------------------|
| `wm_give_equip_sound` | Helmet/chest (as configured) plays equip sound when equipped; no console error about invalid sound |

### E. Combat / damage wiring

| Spell | What to look for |
|-------|------------------|
| `wm_pain` | Target takes damage; no errors; other plugins still see damage events if you have listeners |
| `wm_dot` | DoT ticks damage over time |
| `wm_drainlife` | Target loses health; caster gains as configured |

`setLastDamageCause` was removed — confirm nothing LoM-side relied on `entity.getLastDamageCause()` for these spells specifically.

### F. Volley knockback (weapon Punch instead of arrow knockback API)

| Spell | What to look for |
|-------|------------------|
| `wm_volley_kb` | Arrows hit a mob/player and apply noticeable knockback (`knockback-strength` > 0) |
| `wm_volley_nokb` | Control: knockback ~0 |

### G. Enderman carried block

| Spell | What to look for |
|-------|------------------|
| `wm_spawn_enderman` | World must **not** be Peaceful (monsters cannot spawn). Look at a block, cast; Enderman holds dirt; no cast-command stack traces. Config: `entity.entity: enderman`. |

### H. Conversations (suppressed, not rewritten)

No fixture required for LoM. Only re-test if you enable `ConversationSpell` or `ExternalCommandSpell` with `block-chat-output: true` **without** ProtocolLib.

---

## Already covered by JUnit (no need to re-prove on server)

- `PotionIdentity` legacy ↔ modern canonicalization / serialize round-trip
- `AttributeSpecParser` tokens, operation aliases, stable UUID/key paths
- `SoundKeyNames` enum-style → `minecraft:dotted.path` shaping
- `SpellSnippetValidator` for YAML class/option keys (when fixtures are validated in tests)

---

## Sign-off

Copy and tick when done:

```text
[x] A smoke/destroy FX
[x] B potions legacy + modern
[x] C attribute items
[x] D equip sound item
[x] E pain / dot / drainlife
[x] F volley knockback vs control
[x] G enderman carried block
```

Signed off 2026-10-10 (live `wm_*` fixtures). Note: `wm_smoke_trail` needs targeted DummySpell’s location path (`playSpellEffects(caster, loc)` including trail) — that path was recently added/fixed for block targets.
