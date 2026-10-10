# Manual tests — Metadata → PDC migration

Covers firework / lightning / volley / block-beam / bow / missarrow after dropping Bukkit `FixedMetadataValue` for MagicSpells-owned markers.

## How to run

```bash
./gradlew runServer   # or rebuild + /ms reload
cp dev-fixtures/spells-pdc-smoke.yml core/run/plugins/MagicSpells/
```

Cast: `/c <spell>`. For bow/missarrow, use the given bow (or any bow for missarrow).

**Difficulty:** use Easy+ (not Peaceful). Peaceful despawns/hostiles and confuses creeper-charge / pig-zap checks. Lightning with `no-damage: false` still deals bolt damage either way — “safe” only means no charge/zap.

**Bow messages:** `spell` / `spell-on-hit-*` are subcasts. `str-cast-self` on those helpers only shows when the subspell actually runs a normal cast path (fixtures use `.instant.DummySpell` for that). Match the bow with `bow-items`, not fragile `bow-name` Component equality.

Optional helpers (vanilla):

```text
/difficulty easy
/summon creeper ~ ~ ~
/summon pig ~ ~ ~
/summon zombie ~ ~ ~
```

---

## Checklist

| Check | How | Expect |
|-------|-----|--------|
| Fireworks no-damage | Stand still, `/c pdc_fireworks` | Firework detonates on you; **no** HP loss |
| Lightning safe | Easy+, creeper nearby, `/c pdc_lightning_safe` | Bolt may damage; creeper does **not** charge |
| Lightning safe (pig) | Easy+, pig nearby, same spell | Pig does **not** become zombie piglin |
| Lightning charge | Easy+, creeper nearby, `/c pdc_lightning_charge` | Creeper **does** charge |
| Volley damage | Mob nearby, `/c pdc_volley` at ground near it | Arrows hit; damage ~6 (not vanilla default only) |
| Block beam | `/c pdc_blockbeam` look ahead | Stone-head armor stands appear ~3s; no errors |
| Bow shoot | `/c pdc_give_bow`, shoot the cyan **PDC Test Bow** | Immediate `PDC: bow shot` |
| Bow entity hit | Same bow, hit a mob | Then `PDC: bow hit ENTITY` |
| Bow ground hit | Same bow, hit a block | Then `PDC: bow hit GROUND` |
| Miss arrow | Have `pdc_miss_arrow` granted; shoot **any** bow at a **block** | `PDC: missarrow fired (block hit)` |
| Miss arrow (no false fire) | Shoot any bow at a **mob** | missarrow message does **not** fire |

---

## Sign-off

```text
[x] pdc_fireworks
[x] pdc_lightning_safe (creeper) — Easy+; Peaceful false-failed earlier
[x] pdc_lightning_safe (pig)
[x] pdc_lightning_charge
[x] pdc_volley
[x] pdc_blockbeam
[x] pdc_bow (shoot / entity / ground) — bow-items match; subcast messages OK
[x] pdc_miss_arrow (block)
[x] pdc_miss_arrow (entity = no fire)
```
(signed off 2026-10-10)
