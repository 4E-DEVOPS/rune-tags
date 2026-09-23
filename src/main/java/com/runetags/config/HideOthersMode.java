package com.runetags.config;

public enum HideOthersMode {
	OFF("Off", 0),
	TEN("10 Seconds", 10),
	FIFTEEN("15 Seconds", 15),
	THIRTY("30 Seconds", 30),
	FORTY_FIVE("45 Seconds", 45),
	SIXTY("60 Seconds", 60);

	private final String displayName;
	private final int durationSeconds;

	HideOthersMode(String displayName, int durationSeconds) {
		this.displayName = displayName;
		this.durationSeconds = durationSeconds;
	}

	public boolean isEnabled() {
		return this != OFF;
	}

	public boolean isTimed() {
		return durationSeconds > 0;
	}

	public int getDurationSeconds() {
		return durationSeconds;
	}

	@Override
	public String toString() {
		return displayName;
	}
}