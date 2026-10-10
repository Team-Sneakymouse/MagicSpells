# Manual tests — ChatColor → ColorUtil

Spot-check after the legacy-color migration. Config keys are unchanged (`text-color`, mana `color-full` / `color-empty`, `&` / `&#rrggbb` in strings).

## How to run

```bash
./gradlew runServer
# or /ms reload after copying fixtures + enabling mana
```

**Tracked fixture:** [`dev-fixtures/spells-chatcolor-smoke.yml`](dev-fixtures/spells-chatcolor-smoke.yml)

```bash
cp dev-fixtures/spells-chatcolor-smoke.yml core/run/plugins/MagicSpells/
```

**Mana (opt-in for this smoke):** run `mana.yml` should have `enable-mana-system: true` and `show-mana-on-use: true`. Turn `enable-mana-system` back to `false` when done — you normally leave mana off.

Cast: `/ms cast <spell>` or `/c <spell>`.

---

## Checklist

| Check | How | Expect |
|-------|-----|--------|
| Command text color | `/ms reload` or `/ms list` | Messages use `general.yml` `text-color` (default `3` = dark aqua) |
| Ampersand cast msg | `/c cc_msg_amp` | Colored cast text (`&a` / `&b` / `&e`) |
| Hex cast msg | `/c cc_msg_hex` | Hex colors in chat |
| Amp item | `/c cc_give_amp` | Stick name/lore show green/white legacy colors |
| Hex item | `/c cc_give_hex` | Blaze rod name/lore show `#ff55ff` / mixed hex |
| Mana bar colors | `/c cc_mana_ping` (mana on) | Chat bar: green filled (`color-full: a`) + dark gray empty (`color-empty: 8`); mana drops by 10 |

---

## Sign-off

```text
[x] command text-color
[x] cc_msg_amp
[x] cc_msg_hex
[x] cc_give_amp
[x] cc_give_hex
[x] cc_mana_ping (then disable mana again)
```
(signed off 2026-10-10)
