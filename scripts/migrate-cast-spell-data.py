#!/usr/bin/env python3
"""Migrate castSpell-only spells to CastResult cast(SpellData) pattern (Phase 3)."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPELLS = ROOT / "core/src/main/java/com/nisovin/magicspells/spells"

CAST_SPELL_BRIDGE = """
\t@Override
\tpublic PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
\t\tif (state != SpellCastState.NORMAL)
\t\t\treturn PostCastAction.HANDLE_NORMALLY;
\t\treturn cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
\t}
"""


def run_comm() -> list[Path]:
    cmd1 = ["rg", "-l", r"PostCastAction castSpell\(", str(SPELLS), "--glob", "*.java"]
    cmd2 = ["rg", "-l", r"CastResult cast\(SpellData", str(SPELLS), "--glob", "*.java"]
    a = subprocess.check_output(cmd1, text=True).strip().splitlines()
    b = set(subprocess.check_output(cmd2, text=True).strip().splitlines())
    return [Path(p) for p in sorted(p for p in a if p not in b)]


def ensure_imports(content: str) -> str:
    if "CastResult cast(SpellData" not in content:
        return content
    if "import com.nisovin.magicspells.util.CastResult;" not in content:
        if re.search(r"import com\.nisovin\.magicspells\.util\.MagicConfig;", content):
            content = content.replace(
                "import com.nisovin.magicspells.util.MagicConfig;",
                "import com.nisovin.magicspells.util.MagicConfig;\nimport com.nisovin.magicspells.util.CastResult;",
                1,
            )
        else:
            content = re.sub(
                r"(package [^;]+;\n\n)",
                r"\1import com.nisovin.magicspells.util.CastResult;\n",
                content,
                count=1,
            )
    if "import com.nisovin.magicspells.util.SpellData;" not in content and re.search(
        r"\bSpellData\b", content
    ):
        content = content.replace(
            "import com.nisovin.magicspells.util.CastResult;",
            "import com.nisovin.magicspells.util.CastResult;\nimport com.nisovin.magicspells.util.SpellData;",
            1,
        )
    return content


def find_method_body(content: str, signature: str) -> tuple[int, int] | None:
    idx = content.find(signature)
    if idx == -1:
        return None
    brace = content.find("{", idx)
    if brace == -1:
        return None
    depth = 0
    i = brace
    while i < len(content):
        c = content[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return idx, i + 1
        i += 1
    return None


def transform_returns(body: str, data_var: str = "data") -> str:
    body = re.sub(
        r"return PostCastAction\.(\w+);",
        rf"return new CastResult(PostCastAction.\1, {data_var});",
        body,
    )
    body = re.sub(
        r"return (noTarget\([^;]+\));",
        rf"return new CastResult(\1, {data_var});",
        body,
    )
    return body


def replace_params(body: str, caster_expr: str) -> str:
    body = re.sub(r"(?<![.\w])power(?!\()", "data.power()", body)
    body = re.sub(r"(?<![.\w])args(?!\()", "data.args()", body)
    body = re.sub(r"(?<![.\w])caster(?!\()", caster_expr, body)
    # power reassignment
    body = re.sub(
        r"data\.power\(\) = ([^;]+);",
        r"data = data.power(\1);",
        body,
    )
    return body


def is_standard_entity_cast(cast_body: str) -> bool:
    if "getTargetedEntity" not in cast_body and "getTargetedPlayer" not in cast_body:
        return False
    # Heal-style: extra validation before apply
    lines = [ln.strip() for ln in cast_body.splitlines() if ln.strip()]
    after_target = False
    for ln in lines:
        if "noTarget()" in ln:
            after_target = True
            continue
        if not after_target:
            continue
        if ln.startswith("return PostCastAction"):
            break
        if "sendMessages" in ln or re.search(r"\w+\(caster,", ln) or "apply" in ln.lower():
            # direct work in castSpell (not only sendMessages return)
            if "sendMessages" not in ln:
                return False
    return True


def build_standard_cast(cast_body: str) -> str:
    gt = re.search(r"(getTargeted(?:Entity|Player))\(([^)]+)\)", cast_body)
    method = gt.group(1) if gt else "getTargetedEntity"
    args_inside = gt.group(2) if gt else "data.caster(), data.power(), data.args()"
    args_inside = re.sub(r"\bcaster\b", "data.caster()", args_inside)
    args_inside = re.sub(r"(?<![.\w])power(?!\()", "data.power()", args_inside)
    args_inside = re.sub(r"(?<![.\w])args(?!\()", "data.args()", args_inside)

    target_type = "Player" if method == "getTargetedPlayer" else "LivingEntity"

    send_messages = "sendMessages(data.caster(), data.target(), data.args());" if "sendMessages" in cast_body else ""
    final_action = (
        "PostCastAction.NO_MESSAGES"
        if "PostCastAction.NO_MESSAGES" in cast_body
        else "PostCastAction.HANDLE_NORMALLY"
    )
    return f"""
\t@Override
\tpublic CastResult cast(SpellData data) {{
\t\tTargetInfo<{target_type}> targetInfo = {method}({args_inside});
\t\tif (targetInfo.noTarget())
\t\t\treturn new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);

\t\tdata = data.target(targetInfo.target()).power(targetInfo.power());
\t\tCastResult result = castAtEntity(data);
\t\tif (result.action() == PostCastAction.ALREADY_HANDLED)
\t\t\treturn result;

\t\t{send_messages}
\t\treturn new CastResult({final_action}, data);
\t}}
"""


def convert_boolean_cast_at_entity(content: str) -> str:
    if "CastResult castAtEntity(SpellData" in content:
        return content

    def convert_one(match: re.Match) -> str:
        has_caster = "LivingEntity caster" in match.group(0)
        body = match.group(1).strip()
        if has_caster:
            body = replace_params(body, "data.caster()")
            body = re.sub(r"(?<![.\w])target(?!\()", "data.target()", body)
        else:
            body = replace_params(body, "null")
            body = re.sub(r"(?<![.\w])target(?!\()", "data.target()", body)
        body = re.sub(r"return false;", "return new CastResult(PostCastAction.ALREADY_HANDLED, data);", body)
        body = re.sub(r"return true;", "return new CastResult(PostCastAction.HANDLE_NORMALLY, data);", body)
        body = "\n\t\t".join(body.split("\n"))
        caster_param = "LivingEntity caster, " if has_caster else ""
        caster_arg = "caster, " if has_caster else "null, "
        return f"""
\t@Override
\tpublic CastResult castAtEntity(SpellData data) {{
\t\t{body}
\t}}

\t@Override
\tpublic boolean castAtEntity({caster_param}LivingEntity target, float power, String[] args) {{
\t\treturn castAtEntity(new SpellData({caster_arg}target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
\t}}"""

    patterns = [
        r"@Override\s+public boolean castAtEntity\(LivingEntity caster, LivingEntity target, float power, String\[\] args\) \{(.*?)\n\t\}",
        r"@Override\s+public boolean castAtEntity\(LivingEntity target, float power, String\[\] args\) \{(.*?)\n\t\}",
    ]
    for pat in patterns:
        content = re.sub(pat, convert_one, content, count=1, flags=re.DOTALL)
    return content


def convert_boolean_cast_at_location(content: str) -> str:
    if "CastResult castAtLocation(SpellData" in content:
        # still wrap booleans if needed
        pass
    else:
        return content

    for pat, has_caster in [
        (
            r"@Override\s+public boolean castAtLocation\(LivingEntity caster, Location target, float power, String\[\] args\) \{(.*?)\n\t\}",
            True,
        ),
        (
            r"@Override\s+public boolean castAtLocation\(Location target, float power, String\[\] args\) \{(.*?)\n\t\}",
            False,
        ),
    ]:
        m = re.search(pat, content, re.DOTALL)
        if not m:
            continue
        old = m.group(0)
        if "castAtLocation(new SpellData" in old:
            continue
        body = m.group(1).strip()
        if has_caster:
            body = replace_params(body, "data.caster()")
        body = re.sub(r"(?<![.\w])target(?!\()", "data.location()", body)
        body = re.sub(r"return false;", "return new CastResult(PostCastAction.ALREADY_HANDLED, data);", body)
        body = re.sub(r"return true;", "return new CastResult(PostCastAction.HANDLE_NORMALLY, data);", body)
        # often just `return expr;` for location spells
        if "return " in body and "CastResult" not in body and "return new" not in body:
            body = re.sub(
                r"return ([^;]+);",
                r"return new CastResult(\1 ? PostCastAction.HANDLE_NORMALLY : PostCastAction.ALREADY_HANDLED, data);",
                body,
            )
        body = "\n\t\t".join(body.split("\n"))
        caster_param = "LivingEntity caster, " if has_caster else ""
        caster_arg = "caster, " if has_caster else "null, "
        new = f"""
\t@Override
\tpublic boolean castAtLocation({caster_param}Location target, float power, String[] args) {{
\t\treturn castAtLocation(new SpellData({caster_arg}null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
\t}}"""
        content = content.replace(old, new.strip())
    return content


def migrate_simple_cast_spell(content: str) -> str | None:
    sig = "public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args)"
    span = find_method_body(content, sig)
    if not span:
        return None
    start, end = span
    method = content[start:end]
    inner_start = method.find("{") + 1
    inner_end = method.rfind("}")
    cast_body = method[inner_start:inner_end]

    player_var = None
    normal_body = None

    m = re.search(
        r"if \(state == SpellCastState\.NORMAL && caster instanceof (\w+) (\w+)\) \{(.*)\}\s*return PostCastAction\.HANDLE_NORMALLY;",
        cast_body,
        re.DOTALL,
    )
    if m:
        player_var = m.group(2)
        normal_body = m.group(3)
    else:
        m2 = re.search(
            r"if \(state == SpellCastState\.NORMAL\) \{(.*)\}\s*return PostCastAction\.HANDLE_NORMALLY;",
            cast_body,
            re.DOTALL,
        )
        if m2:
            normal_body = m2.group(1)
            player_var = "caster"

    if normal_body is None:
        return None

    if player_var != "caster":
        guard = f"\t\tif (!(data.caster() instanceof {m.group(1) if m else 'Player'} {player_var}))\n\t\t\treturn new CastResult(PostCastAction.HANDLE_NORMALLY, data);\n\n"
        caster_expr = player_var
    else:
        guard = ""
        caster_expr = "data.caster()"

    body = replace_params(normal_body.strip(), caster_expr)
    body = transform_returns(body)
    body = "\n\t\t".join(body.split("\n"))

    cast_method = f"""
\t@Override
\tpublic CastResult cast(SpellData data) {{
{guard}\t\t{body}
\t}}

{CAST_SPELL_BRIDGE.strip()}
"""
    return content[:start] + cast_method + content[end:]


def migrate_file(path: Path) -> bool:
    content = path.read_text()
    if "CastResult cast(SpellData" in content:
        return False

    sig = "public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args)"
    span = find_method_body(content, sig)
    cast_body = ""
    if span:
        method = content[span[0] : span[1]]
        cast_body = method[method.find("{") + 1 : method.rfind("}")]

    new_content = None
    if "boolean castAtEntity" in content and is_standard_entity_cast(cast_body):
        start, end = span
        cast_method = build_standard_cast(cast_body)
        new_content = content[:start] + cast_method + CAST_SPELL_BRIDGE.strip() + content[end:]
        new_content = convert_boolean_cast_at_entity(new_content)
    else:
        new_content = migrate_simple_cast_spell(content)
        if new_content and "boolean castAtEntity" in content:
            new_content = convert_boolean_cast_at_entity(new_content)

    if new_content:
        new_content = convert_boolean_cast_at_location(new_content)
        path.write_text(ensure_imports(new_content))
        print(f"MIGRATED: {path.relative_to(ROOT)}")
        return True

    print(f"SKIP: {path.relative_to(ROOT)}", file=sys.stderr)
    return False


def main() -> int:
    pending = run_comm()
    migrated = sum(migrate_file(p) for p in pending)
    print(f"migrated={migrated} skipped={len(pending) - migrated}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
