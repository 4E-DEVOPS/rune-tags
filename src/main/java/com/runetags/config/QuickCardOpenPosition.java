package com.runetags.config;

public enum QuickCardOpenPosition {
	RELATIVE_TO_CLICK("Relative"),
	LAST_POSITION("Previous");

	private final String displayName;

	QuickCardOpenPosition(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
