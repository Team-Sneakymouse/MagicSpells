package com.nisovin.magicspells.mana;

import com.nisovin.magicspells.util.ColorUtil;

public class ManaRank {
	
	private String name;
	private String prefix;

	private char symbol;

	private int barSize;
	private int maxMana;
	private int startingMana;
	private int regenAmount;
	private int regenInterval;

	/** Legacy section-sign color code for filled mana (e.g. {@code §a}). */
	private String colorFull;
	/** Legacy section-sign color code for empty mana (e.g. {@code §0}). */
	private String colorEmpty;

	ManaRank() {

	}

	ManaRank(String name, String prefix, char symbol, int barSize, int maxMana, int startingMana, int regenAmount, int regenInterval, String colorFull, String colorEmpty) {
		this.name = name;
		this.prefix = prefix;
		this.symbol = symbol;
		this.barSize = barSize;
		this.maxMana = maxMana;
		this.startingMana = startingMana;
		this.regenAmount = regenAmount;
		this.regenInterval = regenInterval;
		this.colorFull = colorFull;
		this.colorEmpty = colorEmpty;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public char getSymbol() {
		return symbol;
	}

	public void setSymbol(char symbol) {
		this.symbol = symbol;
	}

	public int getBarSize() {
		return barSize;
	}

	public void setBarSize(int barSize) {
		this.barSize = barSize;
	}

	public int getMaxMana() {
		return maxMana;
	}

	public void setMaxMana(int maxMana) {
		this.maxMana = maxMana;
	}

	public int getStartingMana() {
		return startingMana;
	}

	public void setStartingMana(int startingMana) {
		this.startingMana = startingMana;
	}

	public int getRegenAmount() {
		return regenAmount;
	}

	public void setRegenAmount(int regenAmount) {
		this.regenAmount = regenAmount;
	}

	public int getRegenInterval() {
		return regenInterval;
	}

	public void setRegenInterval(int regenInterval) {
		this.regenInterval = regenInterval;
	}

	public String getColorFull() {
		return colorFull;
	}

	public void setColorFull(String colorFull) {
		this.colorFull = colorFull != null ? colorFull : ColorUtil.GREEN;
	}

	public String getColorEmpty() {
		return colorEmpty;
	}

	public void setColorEmpty(String colorEmpty) {
		this.colorEmpty = colorEmpty != null ? colorEmpty : ColorUtil.BLACK;
	}
	
	@Override
	public String toString() {
		return "ManaRank:["
			+ "name=" + name
			+ ",prefix=" + prefix
			+ ",symbol=" + symbol
			+ ",barSize" + barSize
			+ ",maxMana=" + maxMana
			+ ",startingMana=" + startingMana
			+ ",regenAmount=" + regenAmount
			+ ",regenInterval=" + regenInterval
			+ ",colorFull=" + colorFull
			+ ",colorEmpty=" + colorEmpty
			+ ']';
	}
	
}
