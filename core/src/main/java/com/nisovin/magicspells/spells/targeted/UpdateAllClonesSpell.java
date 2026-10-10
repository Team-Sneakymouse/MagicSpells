package com.nisovin.magicspells.spells.targeted;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

public class UpdateAllClonesSpell extends TargetedSpell {

    public UpdateAllClonesSpell(MagicConfig config, String spellName) {
        super(config, spellName);
    }

    @Override
    public CastResult cast(SpellData data) {
        MagicSpells.getVolatileCodeHandler().updateAllFalsePlayers();
        return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    }

    @Override
    public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
        if (state != SpellCastState.NORMAL)
            return PostCastAction.HANDLE_NORMALLY;
        return cast(new SpellData(caster, power, args)).action();
    }
}
