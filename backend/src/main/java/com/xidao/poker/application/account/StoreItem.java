package com.xidao.poker.application.account;

import java.util.List;

public record StoreItem(
        String key,
        String category,
        String name,
        String subtitle,
        String series,
        String rarity,
        long priceCrystals,
        String glyph,
        String color,
        String description,
        List<String> tags,
        List<String> features,
        List<String> grants
) {
    public StoreItem {
        tags = List.copyOf(tags);
        features = List.copyOf(features);
        grants = List.copyOf(grants);
    }

    public boolean bundle() { return "BUNDLE".equals(category); }
}
