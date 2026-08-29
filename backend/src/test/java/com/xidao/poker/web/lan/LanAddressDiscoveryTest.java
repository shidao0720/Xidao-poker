package com.xidao.poker.web.lan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LanAddressDiscoveryTest {
    @Test
    void keepsAllInterfacesAndPrefersPhysicalLanCandidates() {
        List<LanAddressCandidate> ordered = LanAddressDiscovery.order(List.of(
                new LanAddressCandidate("docker0", "Docker Adapter", "192.168.99.1"),
                new LanAddressCandidate("wlan0", "Wi-Fi", "192.168.1.20"),
                new LanAddressCandidate("eth9", "Unknown Adapter", "10.0.0.8")
        ));

        assertThat(ordered).extracting(LanAddressCandidate::address)
                .containsExactly("192.168.1.20", "10.0.0.8", "192.168.99.1");
    }
}
