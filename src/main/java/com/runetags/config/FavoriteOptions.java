package com.runetags.config;

/** Independent Favorite scene options, separate from Target configuration. */
public final class FavoriteOptions {
    private FavoriteOptions() {}

    public enum FavoriteNameLocation {
        DISABLED("Disabled"),
        ABOVE("Above"),
        CENTER("Center"),
        RIGHT("Right"),
		BELOW("Below");

        private final String title;
        FavoriteNameLocation(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum FavoriteHighlightMode {
        OFF("Off"),
        OUTLINE("Outline"),
		TILE("Tile"),
        BOTH("Both");

        private final String title;
        FavoriteHighlightMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
        public boolean showsTile() { return this == TILE || this == BOTH; }
        public boolean showsOutline() { return this == OUTLINE || this == BOTH; }
    }
}
