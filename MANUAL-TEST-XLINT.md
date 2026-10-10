# Manual tests — final Xlint leftovers

Wipe other `spells-*.yml` in the run config if you want a clean slate, then load this fixture.

## How to run

```bash
./gradlew runServer
cp dev-fixtures/spells-xlint-smoke.yml core/run/plugins/MagicSpells/
# /ms reload if already up
```

Confirm load with `/ms list` — all `xlint_*` names below must appear; console should log **2 recipes loaded** (or more if general.yml also has recipes) with no recipe errors.

---

## Checklist

| Spell | How | Pay attention to |
|-------|-----|------------------|
| `xlint_craft` | `/c xlint_craft` | Crafting UI opens with no crafting table nearby; no console error |
| `xlint_menu` | `/c xlint_menu` | Chest menu opens (title `xlint_menu`); click **xlint** → **menu** → **weapons** tiles to drill in; Back works |
| `xlint_spray` | `/c xlint_spray` | Diamond item spray at feet; no CMD / meta errors |
| `xlint_enchant` | Hold iron/diamond sword, `/c xlint_enchant` | Sharpness III applied; no enchantment lookup error |
| `xlint_arrow` | `/c xlint_arrow` | Arrow fires (legacy `tippedarrow` key); no class error |
| `xlint_give_json` | `/c xlint_give_json` | Named diamond sword in inventory; lenient JSON parse OK |
| `xlint_set_score` | `/c xlint_set_score` | Sidebar shows **XLIN Score** at 42 |
| `xlint_paste` | Look at ground, `/c xlint_paste` | froststone pastes; **undo ~5s after paste** (FAWE: +1 tick before undo is armed); no WE errors |
| `xlint_give_recipe_mats` | `/c xlint_give_recipe_mats` then use stonecutter / smithing table | Stone→stone_slab on stonecutter; iron sword + diamond → diamond sword on smithing (empty template OK) |

---

## Sign-off

```text
[ ] xlint_craft
[ ] xlint_menu
[ ] xlint_spray
[ ] xlint_enchant
[ ] xlint_arrow
[ ] xlint_give_json
[ ] xlint_set_score
[ ] xlint_paste
[ ] xlint_give_recipe_mats (+ stonecutter / smithing)
```
