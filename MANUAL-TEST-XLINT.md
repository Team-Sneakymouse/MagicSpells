# Manual tests — Xlint cluster cleanup (attr / swim / fall / tree / stew / registry)

Wipe other `spells-*.yml` in the run config if you want a clean slate, then load this fixture.

## How to run

```bash
./gradlew runServer
# with server stopped or after wipe:
cp dev-fixtures/spells-xlint-smoke.yml core/run/plugins/MagicSpells/
# /ms reload if already up
```

Passive spells use `spell-class: ".PassiveSpell"` (package `spells`, **not** `.passive.PassiveSpell`).
Confirm load with `/ms list` — all `xlint_*` names below must appear with **no** “missing class” errors.

---

## Checklist

| Check | How | Expect |
|-------|-----|--------|
| Attr sword | `/c xlint_give_attr`, hold sword, check attack damage | +8 attack damage on mainhand; no console error |
| Suspicious stew | `/c xlint_give_stew`, eat | Night vision ~5s; no console error |
| Tree | Look at dirt/grass, `/c xlint_tree` | Birch grows with animation; `XLIN: birch tree growing` |
| Falling sand | Look at a block, `/c xlint_fall_sand` | Sand falling block from above target; `XLIN: sand falling…` |
| Cat variant | Look at ground, `/c xlint_spawn_cat` | Black cat; despawns after ~5s |
| Frog variant | Look at ground, `/c xlint_spawn_frog` | Warm frog; despawns after ~5s |
| Start swim | Enter water and start swimming (sprint in water) | `XLIN: startswim` |
| Stop swim | Stop swimming / leave swim state | `XLIN: stopswim` |
| Summon sign | Place a sign, write a **online** player name on **line 1 (front)**, look at it, `/c xlint_summon` | Summon request to that player; or `/c xlint_summon <player>` |

---

## Sign-off

```text
[ ] xlint_give_attr
[ ] xlint_give_stew
[ ] xlint_tree
[ ] xlint_fall_sand
[ ] xlint_spawn_cat
[ ] xlint_spawn_frog
[ ] xlint_startswim
[ ] xlint_stopswim
[ ] xlint_summon
```
