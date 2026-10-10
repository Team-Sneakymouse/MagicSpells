# Manual tests — Targeted* inversion + misc API follow-ups

After flipping `Targeted*Spell` to args-primary and the misc cleanups (`GameRules.PVP`, BookMeta pages, WE `findByPath`, typed magic-item attributes).

## How to run

```bash
./gradlew runServer   # or rebuild + /ms reload
cp dev-fixtures/spells-inversion-smoke.yml core/run/plugins/MagicSpells/
```

Schematic file must exist: `core/run/plugins/MagicSpells/schematics/schem.schem` (already present on this server).

Cast: `/c <spell>` or `/ms cast <spell>`. Also try `/ms caston <player> inv_pain`.

---

## Checklist

| Check | How | Expect |
|-------|-----|--------|
| Magic-item book | `/c inv_give_book` then open book | Title/author/pages readable; page colors OK |
| ConjureBookSpell | `/c inv_conjure_book` then open | Same for spell-built book |
| Paste (findByPath) | Look at flat ground, `/c inv_paste` | `schem.schem` appears; message; auto-undo ~10s |
| Schematic modifier | After paste, look at **same** target block, `/c inv_schem_check` | `INV: schematic match OK` |
| Schematic miss | Look at unrelated block, `/c inv_schem_check` | fail / `does not match schem.schem` |
| PVP (players) | World with PVP **off**, `/c inv_pain` at a player | no player hit (`INV: no valid target…`) |
| PVP (mobs) | Same world, `/c inv_pain` at a mob | damage OK |
| caston | `/ms caston <player> inv_pain` | hits (or fails cleanly if PVP blocks) |
| Multi | `/c inv_multi_pain` at a living target | fires subspell |
| Entity dummy | `/c inv_dummy_entity` at a mob | `INV: entity target OK` |

`general.yml` should keep `check-world-pvp-flag: true` (default on this run).

---

## Sign-off

```text
[x] inv_give_book
[x] inv_conjure_book
[x] inv_paste
[x] inv_schem_check (match)
[x] inv_schem_check (miss)
[x] inv_pain (PVP off → no player)
[x] inv_pain (mob OK)
[x] /ms caston … inv_pain
[x] inv_multi_pain
[x] inv_dummy_entity
```
(signed off 2026-10-10)
