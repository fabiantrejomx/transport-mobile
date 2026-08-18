package com.bng.drivo.data.model;

import com.bng.drivo.R;

public enum AddressLabel {

    CASA(R.string.address_label_casa, "🏠"),
    TRABAJO(R.string.address_label_trabajo, "💼"),
    OTRO(R.string.address_label_otro, "📍");

    private final int displayNameRes;
    private final String emoji;

    AddressLabel(int displayNameRes, String emoji) {
        this.displayNameRes = displayNameRes;
        this.emoji = emoji;
    }

    public int getDisplayNameRes() {
        return displayNameRes;
    }

    public String getEmoji() {
        return emoji;
    }
}
