package com.winhub.hernandez.inputcontrols;

/**
 * How the on-screen control icons are drawn. GLASS is the blurred black glass; the rest are flat styles
 * that share ControlElement's flat palette.
 */
public enum ControlsIconStyle {
    FUSION("fusion", "Fusion"),
    GLASS("glass", "Glass"),
    NEON("neon", "Neon"),
    SOLID("solid", "Solid"),
    MINIMAL("minimal", "Minimal"),
    RETRO("retro", "Retro");

    public static final String PREF_KEY = "input_controls_icon_style";

    private final String key;
    private final String displayName;

    ControlsIconStyle(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /** Unknown or missing values resolve to FUSION, the default. */
    public static ControlsIconStyle fromKey(String key) {
        for (ControlsIconStyle style : values()) if (style.key.equals(key)) return style;
        return FUSION;
    }
}
