package com.nisovin.magicspells.castmodifiers.conditions;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.castmodifiers.Condition;

public class HasScoreboardTagCondition extends Condition {

    private boolean doReplacement;
    private String tag;

    @Override
    public boolean initialize(String var) {
        if (var == null || var.isEmpty()) return false;

        doReplacement = MagicSpells.requireReplacement(var);
        tag = var;

        return true;
    }

    @Override
    public boolean check(LivingEntity caster) {
        return checkTags(caster, caster);
    }

    @Override
    public boolean check(LivingEntity caster, LivingEntity target) {
        return checkTags(caster, target);
    }

    @Override
    public boolean check(LivingEntity caster, Location location) {
        return false;
    }

    @Override
    public boolean check(Entity entity) {
        return checkTags(entity, entity);
    }

    @Override
    public boolean check(Entity entity, Entity target) {
        return checkTags(entity, target);
    }

    private boolean checkTags(Entity caster, Entity target) {
        if (target == null) return false;
        String localTag = doReplacement ? MagicSpells.doReplacements(tag, caster, target) : tag;
        return target.getScoreboardTags().contains(localTag);
    }

}
