package com.nisovin.magicspells.spells.instant;

import com.comphenix.protocol.PacketType;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.spells.targeted.PasteSpell;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class UndoPasteSpell extends InstantSpell {


    private boolean ignoreOtherPlayers;
    private String pasteSpellName;

    public UndoPasteSpell(MagicConfig config, String spellName) {
        super(config, spellName);

        ignoreOtherPlayers = getConfigBoolean("ignore-other-players", false);
        pasteSpellName = getConfigString("paste-name", "");
    }

    @Override
    public CastResult cast(SpellData data) {
        if (ignoreOtherPlayers) {
            PasteSpell.undoPlayerPastes(pasteSpellName, data.caster().getUniqueId().toString());
        } else {
            PasteSpell.undoPastes(pasteSpellName);
        }
        return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
    }

    @Override
    public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
        if (state != SpellCastState.NORMAL)
            return PostCastAction.HANDLE_NORMALLY;
        return cast(new SpellData(caster, null, getCurrentCastLocation(), power, args)).action();
    }
}
