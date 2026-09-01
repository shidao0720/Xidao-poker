package com.xidao.poker.application.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CosmeticCatalogTest {
    @Test
    void catalogKeysAreUniqueAndProfileStyleRemainsEmpty() {
        var items = CosmeticCatalog.items();

        assertThat(items).hasSize(14);
        assertThat(items).extracting(StoreItem::key).doesNotHaveDuplicates();
        assertThat(items).noneMatch(item -> "PROFILE_STYLE".equals(item.category()));
        assertThat(items).filteredOn(item -> "BUTTON_EFFECT".equals(item.category())).hasSize(3);
        assertThat(items).allMatch(item -> item.priceCrystals() > 0);
    }

    @Test
    void bundleOnlyGrantsKnownEquipableItems() {
        StoreItem bundle = CosmeticCatalog.require("fate-bundle");

        assertThat(bundle.bundle()).isTrue();
        assertThat(bundle.grants()).allSatisfy(key -> {
            StoreItem granted = CosmeticCatalog.require(key);
            assertThat(granted.bundle()).isFalse();
        });
    }
}
