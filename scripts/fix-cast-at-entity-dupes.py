#!/usr/bin/env python3
"""Merge duplicate castAtEntity(SpellData) methods and fix boolean returns."""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPELLS = ROOT / "core/src/main/java/com/nisovin/magicspells/spells"


def find_method(content: str, sig: str, from_index: int = 0) -> tuple[int, int] | None:
    idx = content.find(sig, from_index)
    if idx == -1:
        return None
    brace = content.find("{", idx)
    depth = 0
    for i in range(brace, len(content)):
        if content[i] == "{":
            depth += 1
        elif content[i] == "}":
            depth -= 1
            if depth == 0:
                return idx, i + 1
    return None


def extract_methods(content: str) -> list[tuple[int, int, str]]:
    sig = "public CastResult castAtEntity(SpellData data)"
    out = []
    start = 0
    while True:
        idx = content.find(sig, start)
        if idx == -1:
            break
        span = find_method(content, sig, idx)
        if not span:
            break
        body = content[span[0] : span[1]]
        inner = body[body.find("{") + 1 : body.rfind("}")].strip()
        out.append((span[0], span[1], inner))
        start = span[1]
    return out


def fix_boolean_return(body: str) -> str:
    lines = [ln for ln in body.strip().splitlines() if ln.strip()]
    if not lines:
        return body
    last = lines[-1].strip()
    m = re.match(r"return ([^;]+);", last)
    if m and "CastResult" not in m.group(1):
        expr = m.group(1)
        lines[-1] = (
            f"if (!({expr})) return new CastResult(PostCastAction.ALREADY_HANDLED, data);\n"
            f"\t\t\treturn new CastResult(PostCastAction.HANDLE_NORMALLY, data);"
        )
    return "\n\t\t\t".join(lines)


def merge_cast_at_entity(content: str) -> str:
    methods = extract_methods(content)
    if len(methods) < 2:
        # still fix single method boolean returns
        if len(methods) == 1:
            s, e, inner = methods[0]
            if "return new CastResult" not in inner and re.search(r"return [^;]+;", inner):
                fixed_inner = fix_boolean_return(inner)
                if fixed_inner != inner:
                    new_m = f"\t@Override\n\tpublic CastResult castAtEntity(SpellData data) {{\n\t\t{fixed_inner}\n\t}}"
                    content = content[:s] + new_m + content[e:]
        return content

    # Remove all CastResult castAtEntity blocks and following boolean wrapper until we collect bodies
    bodies = [m[2] for m in methods]
    first_start = methods[0][0]
    last_end = methods[-1][1]
    # extend last_end to include boolean wrappers for both overloads
    tail = content[last_end:]
    m2 = re.match(
        r"(\s*@Override\s+public boolean castAtEntity\(LivingEntity caster, LivingEntity target, float power, String\[\] args\) \{.*?\n\t\})?\s*(@Override\s+public boolean castAtEntity\(LivingEntity target, float power, String\[\] args\) \{.*?\n\t\})?",
        tail,
        re.DOTALL,
    )
    if m2:
        last_end += m2.end()

    b0 = fix_boolean_return(bodies[0])
    b1 = fix_boolean_return(bodies[1]) if len(bodies) > 1 else ""

    merged = f"""
\t@Override
\tpublic CastResult castAtEntity(SpellData data) {{
\t\tif (data.hasCaster()) {{
\t\t\t{b0}
\t\t}} else {{
\t\t\t{b1}
\t\t}}
\t}}

\t@Override
\tpublic boolean castAtEntity(LivingEntity caster, LivingEntity target, float power, String[] args) {{
\t\treturn castAtEntity(new SpellData(caster, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
\t}}

\t@Override
\tpublic boolean castAtEntity(LivingEntity target, float power, String[] args) {{
\t\treturn castAtEntity(new SpellData(null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
\t}}"""

    return content[:first_start] + merged.strip() + content[last_end:]


def fix_spell_data_shadow(content: str) -> str:
    sig = "public CastResult cast(SpellData data)"
    span = find_method(content, sig)
    if not span:
        return content
    body = content[span[0] : span[1]]
    if "SpellData data = new SpellData" not in body:
        return content
    new_body = body.replace("SpellData data = new SpellData", "SpellData effectData = new SpellData")
    # only replace uses that refer to the local variable - heuristic: lines with effectData assignment follow
    new_body = re.sub(
        r"effectData = new SpellData\(([^)]+)\);\s*\n(\s+)(playSpellEffects|tracker\.|setupTracker)",
        lambda m: m.group(0).replace("data.", "effectData.", 1) if False else m.group(0),
        new_body,
    )
    # simpler: replace second assignment block references on same lines after effectData decl
    lines = new_body.split("\n")
    in_shadow = False
    for i, ln in enumerate(lines):
        if "SpellData effectData = new SpellData" in ln:
            in_shadow = True
            continue
        if in_shadow and ln.strip().startswith("}"):
            in_shadow = False
        if in_shadow:
            lines[i] = re.sub(r"\bdata\.(?!caster\(\)|power\(\)|args\(\)|target\(\)|location\(\)|hasCaster\(\))", "effectData.", ln)
            lines[i] = lines[i].replace("playSpellEffects(EffectPosition.CASTER, player, data.", "playSpellEffects(EffectPosition.CASTER, player, effectData.")
            lines[i] = re.sub(r"new SpellData\(([^)]*)\bdata\.power", r"new SpellData(\1effectData.power", lines[i])
    return content[: span[0]] + "\n".join(lines) + content[span[1] :]


def main() -> None:
    files = subprocess.check_output(
        ["rg", "-l", "public CastResult castAtEntity\\(SpellData", str(SPELLS), "--glob", "*.java"],
        text=True,
    ).strip().splitlines()
    for fp in files:
        path = Path(fp)
        content = path.read_text()
        new = merge_cast_at_entity(content)
        new = fix_spell_data_shadow(new)
        if new != content:
            path.write_text(new)
            print("fixed", path.relative_to(ROOT))


if __name__ == "__main__":
    main()
