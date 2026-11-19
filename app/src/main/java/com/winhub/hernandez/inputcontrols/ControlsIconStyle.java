package com.winhub.hernandez.inputcontrols;

/** How the on-screen control icons are drawn: GLASS (blurred black glass) or FUSION (tinted pads). */
public enum ControlsIconStyle {
    GLASS("glass"),
    FUSION("fusion");

    public static final String PREF_KEY = "input_controls_icon_style";

    private final String key;

    ControlsIconStyle(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** Unknown or missing values resolve to FUSION, the default. */
    public static ControlsIconStyle fromKey(String key) {
        return GLASS.key.equals(key) ? GLASS : FUSION;
    }
}
