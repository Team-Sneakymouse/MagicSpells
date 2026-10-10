package com.nisovin.magicspells.util;

import com.nisovin.magicspells.Spell.PostCastAction;

/**
 * Outcome of a targeted / direct cast that carries the (possibly derived) SpellData
 * used for the attempt.
 */
public record CastResult(PostCastAction action, SpellData data) {
}
