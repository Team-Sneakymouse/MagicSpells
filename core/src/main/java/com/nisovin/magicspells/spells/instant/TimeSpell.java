package com.nisovin.magicspells.spells.instant;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import com.nisovin.magicspells.util.MagicConfig;
import com.nisovin.magicspells.util.CastResult;
import com.nisovin.magicspells.util.SpellData;
import com.nisovin.magicspells.spells.InstantSpell;
import com.nisovin.magicspells.util.config.ConfigData;
import com.nisovin.magicspells.spells.TargetedLocationSpell;

public class TimeSpell extends InstantSpell implements TargetedLocationSpell {

	private ConfigData<Integer> timeToSet;

	private String strAnnounce;
		
	public TimeSpell(MagicConfig config, String spellName) {
		super(config, spellName);
		
		timeToSet = getConfigDataInt("time-to-set", 0);
		strAnnounce = getConfigString("str-announce", "The sun suddenly appears in the sky.");
	}

	@Override
	public CastResult cast(SpellData data) {
		World world = data.caster().getWorld();
					setTime(data.caster(), world, data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	}

	@Override
	public PostCastAction castSpell(LivingEntity caster, SpellCastState state, float power, String[] args) {
		if (state != SpellCastState.NORMAL)
			return PostCastAction.HANDLE_NORMALLY;
		return cast(new SpellData(caster, power, args)).action();
	}


		@Override
	public CastResult castAtLocation(SpellData data) {
		setTime(data.caster(), data.location().getWorld(), data.power(), data.args());
		return new CastResult(PostCastAction.HANDLE_NORMALLY, data);
	
	}
@Override
	public boolean castAtLocation(LivingEntity caster, Location target, float power, String[] args) {
		return castAtLocation(new SpellData(caster, null, target, power, args)).action() != PostCastAction.ALREADY_HANDLED;
	}




	private void setTime(LivingEntity caster, World world, float power, String[] args) {
		world.setTime(timeToSet.get(caster, null, power, args));
		for (Player p : world.getPlayers()) sendMessage(strAnnounce, p, args);
	}

	public String getStrAnnounce() {
		return strAnnounce;
	}

	public void setStrAnnounce(String strAnnounce) {
		this.strAnnounce = strAnnounce;
	}

}
