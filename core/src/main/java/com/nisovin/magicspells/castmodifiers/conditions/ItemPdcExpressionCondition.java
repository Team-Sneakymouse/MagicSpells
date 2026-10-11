package com.nisovin.magicspells.castmodifiers.conditions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.util.ItemPdcUtil;
import com.nisovin.magicspells.util.ModifierResult;
import com.nisovin.magicspells.util.ItemPdcKeys.Slot;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.util.config.ConfigDataUtil;
import com.nisovin.magicspells.events.SpellCastEvent;
import com.nisovin.magicspells.events.SpellCastedEvent;
import com.nisovin.magicspells.events.ManaChangeEvent;
import com.nisovin.magicspells.castmodifiers.IModifier;
import com.nisovin.magicspells.events.SpellTargetEvent;
import com.nisovin.magicspells.events.SpellTargetLocationEvent;
import com.nisovin.magicspells.events.MagicSpellsGenericPlayerEvent;
import com.nisovin.magicspells.castmodifiers.conditions.util.OperatorCondition;

/**
 * ConditionVar: {@code slot;key}{@code op}{@code right} — e.g. {@code cast;charges>3}.
 */
public class ItemPdcExpressionCondition extends OperatorCondition implements IModifier {

	private static final Pattern EXPRESSION_MATCHER = Pattern.compile("((?:%..*%|[^%<>:=]*)*)([<>:=])((?:%..*%|[^%<>:=]*)*)");

	private Slot slot;
	private String key;
	private ConfigData<Double> right;

	@Override
	public boolean initialize(String var) {
		if (var == null || var.isEmpty()) return false;
		String[] parts = var.split(";", 2);
		if (parts.length != 2) return false;
		slot = Slot.parse(parts[0]);
		if (slot == null) return false;

		Matcher matcher = EXPRESSION_MATCHER.matcher(parts[1].trim());
		List<String> split = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			if (matcher.matches() && matcher.group(i) != null) split.add(matcher.group(i));
		}
		if (split.size() != 3) return false;

		key = split.get(0).trim();
		right = ConfigDataUtil.getDouble(split.get(2).trim());
		return !key.isEmpty() && right != null && super.initialize(split.get(1));
	}

	private boolean evaluate(SpellData data) {
		ItemStack stack = ItemPdcUtil.resolveSlot(data, slot);
		String raw = ItemPdcUtil.getString(stack, key);
		if (raw == null) return false;
		double left;
		try {
			left = Double.parseDouble(raw.trim());
		} catch (NumberFormatException e) {
			return false;
		}
		Double valueRight = right.get(data);
		if (valueRight == null) return false;
		if (equals) return left == valueRight;
		if (moreThan) return left > valueRight;
		if (lessThan) return left < valueRight;
		return false;
	}

	@Override
	public ModifierResult apply(SpellData data) {
		return new ModifierResult(data, evaluate(data));
	}

	@Override
	public ModifierResult applyTarget(SpellData data) {
		return new ModifierResult(data, evaluate(data));
	}

	@Override
	public ModifierResult applyLocation(SpellData data) {
		return new ModifierResult(data, evaluate(data));
	}

	@Override
	@Deprecated
	public ModifierResult apply(LivingEntity caster, SpellData data) {
		return apply(data.caster(caster));
	}

	@Override
	@Deprecated
	public ModifierResult apply(LivingEntity caster, LivingEntity target, SpellData data) {
		return applyTarget(data.caster(caster).target(target));
	}

	@Override
	@Deprecated
	public ModifierResult apply(LivingEntity caster, Location target, SpellData data) {
		return applyLocation(data.caster(caster).location(target));
	}

	@Override
	public boolean apply(SpellCastEvent event) {
		return evaluate(event.getSpellData());
	}

	@Override
	public boolean apply(SpellCastedEvent event) {
		return evaluate(new SpellData(event.getCaster(), event.getPower(), event.getSpellArgs()));
	}

	@Override
	public boolean apply(SpellTargetEvent event) {
		return evaluate(event.getSpellData());
	}

	@Override
	public boolean apply(SpellTargetLocationEvent event) {
		return evaluate(event.getSpellData());
	}

	@Override
	public boolean apply(ManaChangeEvent event) {
		return evaluate(new SpellData(event.getPlayer()));
	}

	@Override
	public boolean apply(MagicSpellsGenericPlayerEvent event) {
		return evaluate(new SpellData(event.getPlayer()));
	}

	@Override
	public boolean check(LivingEntity caster) {
		return evaluate(new SpellData(caster));
	}

	@Override
	public boolean check(LivingEntity caster, LivingEntity target) {
		return evaluate(new SpellData(caster, target));
	}

	@Override
	public boolean check(LivingEntity caster, Location location) {
		return evaluate(new SpellData(caster, location));
	}

}
