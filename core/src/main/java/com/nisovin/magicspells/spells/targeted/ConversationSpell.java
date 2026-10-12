package com.nisovin.magicspells.spells.targeted;

import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationFactory;

import com.nisovin.magicspells.util.TargetInfo;
import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.TargetedSpell;
import com.nisovin.magicspells.util.ConfigReaderUtil;
import com.nisovin.magicspells.spells.TargetedEntitySpell;
import com.nisovin.magicspells.spelleffects.EffectPosition;
import com.nisovin.magicspells.util.prompt.ConversationContextUtil;

// Bukkit Conversations API is for-removal; keep until a Dialog/chat rewrite. See COMPILE-WARNINGS.md.
@SuppressWarnings("removal")
public class ConversationSpell extends TargetedSpell implements TargetedEntitySpell {

	private ConversationFactory conversationFactory;

	public ConversationSpell(MagicConfig config, String spellName) {
		super(config, spellName);

		conversationFactory = ConfigReaderUtil.readConversationFactory(getConfigSection("conversation"));
	}

	@Override
	public CastResult cast(SpellData data) {
		TargetInfo<Player> targetInfo = getTargetedPlayer(data.caster(), data.power(), data.args());
					if (targetInfo.noTarget()) return new CastResult(noTarget(data.caster(), data.args(), targetInfo), data);
		
					conversate(data.caster(), targetInfo.target(), targetInfo.power(), data.args());
					sendMessages(data.caster(), targetInfo.target(), data.args());
		
					return new CastResult(PostCastAction.NO_MESSAGES, data);
	}

	@Override
	public PostCastAction castSpell(Entity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


	
	@Override
	public CastResult castAtEntity(SpellData data) {
		if (data.hasCaster()) {
			if (!validTargetList.canTarget(data.caster(), data.target()) || !(data.target() instanceof Player player)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							conversate(data.caster(), player, data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		} else {
			if (!validTargetList.canTarget(data.target()) || !(data.target() instanceof Player player)) return new CastResult(PostCastAction.ALREADY_HANDLED, data);
							conversate(null, player, data.power(), data.args());
							return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
		}
	}



	private void conversate(Entity caster, Player target, float power, String[] args) {
		Conversation conversation = conversationFactory.buildConversation(target);
		ConversationContextUtil.setConversable(conversation.getContext(), target);
		conversation.begin();

		if (caster != null) playSpellEffects(caster, target, power, args);
		else playSpellEffects(EffectPosition.TARGET, target, power, args);
	}

}
