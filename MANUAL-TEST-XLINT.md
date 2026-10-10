# Manual tests — Xlint cluster cleanup

Covers bed/respawn recall, nova `sendMultiBlockChange`, potion registry names, and damage-event helpers after dropping `DamageModifier` usage.

## How to run

```bash
./gradlew runServer
cp dev-fixtures/spells-xlint-smoke.yml core/run/plugins/MagicSpells/
# /ms reload if already up
```

Passive spells use `spell-class: ".PassiveSpell"` (package `spells`, **not** `.passive.PassiveSpell`).
Confirm load with `/ms list` — `xlint_takedamage` / `xlint_blockdamage` must appear.
---

## Checklist

| Check | How | Expect |
|-------|-----|--------|
| Bed recall | Sleep in a bed, walk away, `/c xlint_recall_bed` | Teleport to bed; `XLIN: recalled…` |
| Nova fake blocks | Stand on flat ground, `/c xlint_nova` | Expanding fake fire ring, then clears; **no** real fire left |
| Potion type | `/c xlint_potion` at a mob/player | Speed applied; `XLIN: applied speed` |
| Potion passive | Have `xlint_potion_passive`; get Speed (or cast `xlint_potion` on self via caston) | `XLIN: potioneffect passive fired` |
| Pain / damage event | `/c xlint_pain` at a mob | Damage lands; no console error |
| Take damage | Have `xlint_takedamage`; take HP damage (not creative) | `XLIN: takedamage passive fired` |
| Block damage | Shield in hand, hold right-click block, get hit by a mob | `XLIN: blockdamage passive fired` |

---

## Sign-off

```text
[x] xlint_recall_bed
[x] xlint_nova
[x] xlint_potion
[x] xlint_potion_passive
[x] xlint_pain
[x] xlint_takedamage — fires on normal hits
[x] xlint_blockdamage — fires when blocking and hit from the front
```
(signed off 2026-10-10; PassiveSpell class was `.PassiveSpell`, not `.passive.PassiveSpell`)
