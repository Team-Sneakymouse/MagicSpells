# Manual tests — targeted cast paths (post-suppress)

After annotating deprecated no-args `castAt*` overrides with `@SuppressWarnings("deprecation")`, spot-check that **args** paths and normal casts still work. Suppress does not change behavior; this is a regression net before we later invert the interfaces.

## Fixtures

Tracked: [`dev-fixtures/spells-targeted-cast-smoke.yml`](dev-fixtures/spells-targeted-cast-smoke.yml)

```bash
cp dev-fixtures/spells-targeted-cast-smoke.yml core/run/plugins/MagicSpells/
# rebuild/restart plugin, or /ms reload after jar update
```

Cast: `/ms cast <spell>` or `/c <spell>`.

---

## Checklist

| Spell | How | Expect |
|-------|-----|--------|
| `tc_dummy_entity` | Look at a mob/player, cast | Message + no errors (entity target) |
| `tc_dummy_loc` | Look at a solid block, cast | Smoke trail caster→block (location + trail path) |
| `tc_pain` | Look at a living target, cast | Damage applied |
| `tc_zap` | Look at dirt or stone, cast | Block removed + break FX |
| `tc_zap` | Look at sky / no solid block, cast | `TC: no block` (`str-no-target`) |
| `tc_zap` | Look at cobble/wood/grass plant, cast | `TC: cant zap` (`str-cant-zap`) |
| `tc_volley` | Look at ground/mob area, cast | Arrows fire |
| `tc_multi_pain` | Look at a living target, cast | Multi fires `tc_pain` as subspell (hits `castAtEntity` with args) |

---

## Sign-off

```text
[x] tc_dummy_entity
[x] tc_dummy_loc (trail visible)
[x] tc_pain
[x] tc_zap (dirt/stone — success)
[x] tc_zap (sky — no block)
[x] tc_zap (cobble/wood — cant zap)
[x] tc_volley
[x] tc_multi_pain
```
(signed off 2026-10-10)
